package com.fertimata.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.actuator.safety.ActuatorSafetyProperties;
import com.fertimata.backend.actuator.safety.CommandVocabularyProperties;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.config.PowerProperties;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceRepository;
import com.fertimata.backend.metrics.MetricsProperties;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.sensor.SensorRepository;
import com.fertimata.backend.support.TestFixtures;
import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The shipped catalog is configuration that controls real pumps, so it is loaded and bound exactly
 * the way Spring will bind it, and checked for the mistakes that would otherwise only show at startup
 * on the Jetson: duplicate codes, duplicate metric keys, lost metadata case, unbound interlock lists.
 */
class HardwareCatalogTest {

    private static Binder binder;

    @BeforeAll
    static void loadCatalog() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("fertimata-rev-a", new ClassPathResource("hardware/fertimata-rev-a.yml"));
        binder = new Binder(ConfigurationPropertySources.from(sources));
    }

    @Test
    @DisplayName("the Rev A catalog binds with every device, sensor and actuator from the architecture document")
    void bindsRevACatalog() {
        HardwareCatalogProperties catalog =
                binder.bind("app.catalog", HardwareCatalogProperties.class).get();

        assertThat(catalog.devices())
                .extracting(HardwareCatalogProperties.DeviceEntry::code)
                .containsExactly("JETSON-01", "PZEM-01", "PANEL-01", "GREENHOUSE-01", "RIO-TANK-01");
        long sensors = catalog.devices().stream().mapToLong(d -> d.sensors().size()).sum();
        long actuators = catalog.devices().stream().mapToLong(d -> d.actuators().size()).sum();
        // One soil sensor (7 values), SHT40 (2), light, and EC, pH, water temperature and float LOW at the tank.
        // The dosing.* iteration values left with the dosing loop (V9).
        assertThat(sensors).isEqualTo(4 + 6 + 10 + 4);
        // Relay 20 R0-R3, relay 21 R0-R2, R4 and R5, plus the virtual patrol and goto (BE-1). The dosing loop
        // belongs to the AI team's service, which drives the pumps through the command API (V9).
        assertThat(actuators).isEqualTo(9 + 2);
        assertThat(catalog.devices())
                .flatExtracting(HardwareCatalogProperties.DeviceEntry::actuators)
                .extracting(HardwareCatalogProperties.ActuatorEntry::type)
                .doesNotContain("DOSING_LOOP");

        HardwareCatalogProperties.ActuatorEntry dosingN = catalog.devices().get(4).actuators().get(0);
        assertThat(dosingN.metadata()).containsEntry("modbusAddress", "21").containsEntry("channel", "R0");
        assertThat(dosingN.maxRunSeconds()).isEqualTo(120);
        HardwareCatalogProperties.ActuatorEntry pan = catalog.devices().get(2).actuators().get(1);
        assertThat(pan.type()).isEqualTo("CAMERA_PAN");
        assertThat(pan.metadata()).containsEntry("channel", "R1");
        assertThat(catalog.devices().get(2).actuators().get(2).maxRunSeconds()).isEqualTo(60);
        assertThat(catalog.devices().get(1).metadata()).containsEntry("modbusAddress", "30");

        // Every type that must be timed carries its own ceiling, since Rev A has no mechanical timer.
        ActuatorSafetyProperties safety =
                binder.bind("app.actuator.safety", ActuatorSafetyProperties.class).get();
        catalog.devices().stream()
                .flatMap(device -> device.actuators().stream())
                .filter(actuator -> safety.timedTypes().contains(actuator.type()))
                .forEach(actuator -> assertThat(actuator.maxRunSeconds()).as(actuator.code()).isNotNull());

        // A signal interlock pointing at a sensor the catalog never creates would block its pumps forever.
        safety.signalInterlocks().forEach(signal -> assertThat(catalog.devices().stream()
                        .filter(device -> device.code().equals(signal.device()))
                        .flatMap(device -> device.sensors().stream())
                        .map(HardwareCatalogProperties.SensorEntry::metricKey))
                .as(signal.name())
                .contains(signal.metricKey()));
    }

    @Test
    @DisplayName("codes and metric keys are unique per device, and metric keys are valid on the wire")
    void codesAreUnique() {
        HardwareCatalogProperties catalog =
                binder.bind("app.catalog", HardwareCatalogProperties.class).get();

        for (HardwareCatalogProperties.DeviceEntry device : catalog.devices()) {
            assertThat(device.code()).matches("[A-Za-z0-9._-]+");
            Set<String> codes = new HashSet<>();
            Set<String> keys = new HashSet<>();
            device.sensors().forEach(sensor -> {
                assertThat(codes.add(sensor.code())).as("duplicate code %s", sensor.code()).isTrue();
                assertThat(keys.add(sensor.metricKey())).as("duplicate key %s", sensor.metricKey()).isTrue();
                assertThat(sensor.metricKey()).matches("[A-Za-z0-9._-]+");
            });
            device.actuators().forEach(actuator ->
                    assertThat(codes.add(actuator.code())).as("duplicate code %s", actuator.code()).isTrue());
        }
    }

    @Test
    @DisplayName("the interlock policy and power vocabulary bind from the same file")
    void bindsPolicy() {
        ActuatorSafetyProperties safety =
                binder.bind("app.actuator.safety", ActuatorSafetyProperties.class).get();
        PowerProperties power = binder.bind("app.power", PowerProperties.class).get();

        // Guards against YAML 1.1 reading an unquoted ON as the boolean true, which silently turned
        // every interlock off during end-to-end verification.
        assertThat(safety.activatingCommands()).containsExactly("ON", "START", "HOME", "GOTO_STATION");
        assertThat(safety.activeStates()).containsExactly("ON");
        assertThat(safety.isActivating("on")).isTrue();
        // A stop is never an activating command, so no interlock can ever hold it back.
        assertThat(safety.isActivating("STOP")).isFalse();
        assertThat(safety.isActivating("OFF")).isFalse();
        assertThat(safety.timedTypes())
                .containsExactly("DISTRIBUTION_PUMP", "TROLLEY_MOTOR", "DOSING_PUMP", "STIRRER");
        assertThat(safety.blockedOnBackupPowerTypes()).containsExactly(
                "DOSING_PUMP", "STIRRER", "TROLLEY_MOTOR", "DISTRIBUTION_PUMP", "TROLLEY_PATROL", "TROLLEY_POSITION");
        assertThat(safety.exclusiveTypeGroups()).containsExactly(
                List.of("DOSING_PUMP", "TROLLEY_MOTOR"),
                List.of("STIRRER", "TROLLEY_MOTOR"),
                List.of("DOSING_PUMP", "DISTRIBUTION_PUMP"),
                List.of("STIRRER", "DISTRIBUTION_PUMP"),
                List.of("TROLLEY_MOTOR", "TROLLEY_PATROL", "TROLLEY_POSITION"),
                List.of("DOSING_PUMP", "TROLLEY_PATROL", "TROLLEY_POSITION"),
                List.of("STIRRER", "TROLLEY_PATROL", "TROLLEY_POSITION"));
        // Dosing and stirring run together by design, so no group may hold both.
        assertThat(safety.exclusiveTypeGroups())
                .noneMatch(group -> group.contains("DOSING_PUMP") && group.contains("STIRRER"));
        assertThat(safety.blockedWhileActive()).singleElement().satisfies(rule -> {
            assertThat(rule.types()).containsExactly("CAMERA_PAN");
            assertThat(rule.whileActive()).containsExactly("TROLLEY_MOTOR", "TROLLEY_PATROL", "TROLLEY_POSITION");
        });
        assertThat(safety.signalInterlocks()).singleElement().satisfies(signal -> {
            assertThat(signal.name()).isEqualTo("FLOAT_LOW");
            assertThat(signal.device()).isEqualTo("RIO-TANK-01");
            assertThat(signal.metricKey()).isEqualTo("tank.float_low_ok");
            assertThat(signal.okValues()).contains("1");
            assertThat(signal.maxAge()).isEqualTo(java.time.Duration.ofMinutes(10));
            assertThat(signal.blockedTypes())
                    .containsExactly("DISTRIBUTION_PUMP", "DOSING_PUMP", "STIRRER");
        });
        assertThat(power.isNormal("mains")).isTrue();
        assertThat(power.isNormal("UNKNOWN")).isFalse();
    }

    @Test
    @DisplayName("every catalog actuator has a command vocabulary, and every activating command belongs to one")
    void bindsCommandVocabulary() {
        HardwareCatalogProperties catalog =
                binder.bind("app.catalog", HardwareCatalogProperties.class).get();
        ActuatorSafetyProperties safety =
                binder.bind("app.actuator.safety", ActuatorSafetyProperties.class).get();
        CommandVocabularyProperties vocabulary =
                binder.bind("app.actuator.commands", CommandVocabularyProperties.class).get();

        catalog.devices().stream()
                .flatMap(device -> device.actuators().stream())
                .forEach(actuator -> assertThat(vocabulary.forType(actuator.type())).as(actuator.code()).isPresent());
        // An activating command no vocabulary accepts would be dead configuration, or a typo in one list.
        safety.activatingCommands().forEach(command -> assertThat(vocabulary.vocabularies())
                .as(command)
                .anyMatch(entry -> entry.accepts(command)));

        assertThat(vocabulary.commandsFor("TROLLEY_PATROL")).containsExactly("START", "STOP");
        assertThat(vocabulary.commandsFor("DISTRIBUTION_PUMP")).containsExactly("ON", "OFF");
        assertThat(vocabulary.forType("DOSING_LOOP")).isEmpty();
        assertThat(vocabulary.forType("TROLLEY_POSITION").orElseThrow().rulesFor("GOTO_STATION"))
                .singleElement()
                .satisfies(station -> {
                    assertThat(station.kind()).isEqualTo(CommandVocabularyProperties.Kind.INTEGER);
                    assertThat(station.min()).isEqualByComparingTo("1");
                    assertThat(station.max()).isEqualByComparingTo("10");
                });
    }

    @Test
    @DisplayName("per-device telemetry intervals bind and only name catalog devices")
    void bindsTelemetryIntervals() {
        HardwareCatalogProperties catalog =
                binder.bind("app.catalog", HardwareCatalogProperties.class).get();
        MetricsProperties metrics = binder.bind("app.metrics", MetricsProperties.class).get();

        assertThat(metrics.intervalsFor("PZEM-01")).containsExactly(Duration.ofSeconds(3));
        assertThat(metrics.intervalsFor("GREENHOUSE-01")).containsExactly(Duration.ofSeconds(60), Duration.ofMinutes(5));
        assertThat(metrics.intervalsFor("RIO-TANK-01"))
                .containsExactly(Duration.ofSeconds(5), Duration.ofMinutes(10));
        metrics.deviceIntervals().forEach(entry -> assertThat(catalog.devices())
                .extracting(HardwareCatalogProperties.DeviceEntry::code)
                .as(entry.device())
                .contains(entry.device()));
    }

    @Test
    @DisplayName("an empty ACTUATOR_COMMAND_TTL binds to no expiry rather than failing startup")
    void emptyTtlMeansNoExpiry() {
        Binder empty = new Binder(new MapConfigurationPropertySource(Map.of(
                "app.actuator.safety.command-ttl", "",
                "app.actuator.safety.refuse-when-device-offline", "false")));

        ActuatorSafetyProperties safety =
                empty.bind("app.actuator.safety", ActuatorSafetyProperties.class).get();

        assertThat(safety.commandTtl()).isNull();
        assertThat(safety.activatingCommands()).containsExactly("ON");
    }

    @Test
    @DisplayName("synchronisation is create-only: existing rows are left exactly as the operator left them")
    void synchronizerIsCreateOnly() {
        DeviceRepository devices = mock(DeviceRepository.class);
        SensorRepository sensors = mock(SensorRepository.class);
        ActuatorRepository actuators = mock(ActuatorRepository.class);
        JsonSupport json = mock(JsonSupport.class);
        when(json.toJson(any())).thenReturn("{}");

        Device existing = TestFixtures.device(1L, "PANEL-01");
        when(devices.findByDeviceCode("PANEL-01")).thenReturn(Optional.of(existing));
        when(devices.findByDeviceCode("NEW-01")).thenReturn(Optional.empty());
        when(devices.save(any(Device.class))).thenAnswer(invocation -> {
            Device saved = invocation.getArgument(0);
            TestFixtures.setId(saved, 2L);
            return saved;
        });
        when(actuators.findByDeviceIdAndCode(1L, "DIST-PUMP"))
                .thenReturn(Optional.of(TestFixtures.actuator(9L, existing, "DIST-PUMP")));
        when(actuators.findByDeviceIdAndCode(1L, "LED")).thenReturn(Optional.empty());
        when(sensors.findByDeviceIdAndCode(anyLong(), anyString())).thenReturn(Optional.empty());
        when(sensors.findByDeviceIdAndMetricKey(anyLong(), anyString())).thenReturn(Optional.empty());

        HardwareCatalogProperties catalog = new HardwareCatalogProperties(true, "test", List.of(
                new HardwareCatalogProperties.DeviceEntry("PANEL-01", "Panel", "MODBUS_IO", null, null, null, List.of(
                        new HardwareCatalogProperties.ActuatorEntry("DIST-PUMP", "Pump", "DISTRIBUTION_PUMP", null, 5,
                                null, null),
                        new HardwareCatalogProperties.ActuatorEntry("LED", "LED", "ILLUMINATION", null, null, null,
                                Map.of("channel", "R1")))),
                new HardwareCatalogProperties.DeviceEntry("NEW-01", null, null, null, null, List.of(
                        new HardwareCatalogProperties.SensorEntry("T", "air.temperature", null, "AIR_TEMPERATURE", "C",
                                null, null, null)), null)));

        HardwareCatalogSynchronizer synchronizer = new HardwareCatalogSynchronizer(
                catalog, devices, sensors, actuators, json, mock(TransactionTemplate.class));

        HardwareCatalogSynchronizer.Result result = synchronizer.apply();

        assertThat(result).isEqualTo(new HardwareCatalogSynchronizer.Result(1, 1, 1));
        verify(devices, times(1)).save(any(Device.class));
        verify(actuators, times(1)).save(any(Actuator.class));
        verify(sensors, times(1)).save(any(Sensor.class));
        // The existing pump was not touched, so its operator-set limit survives the restart.
        verify(actuators, never()).save(org.mockito.ArgumentMatchers.argThat(a -> "DIST-PUMP".equals(a.getCode())));
    }
}
