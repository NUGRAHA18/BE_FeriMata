package com.fertimata.backend.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.automation.AutomationEngine;
import com.fertimata.backend.common.exception.TelemetryValidationException;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.config.TelemetryProperties;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceService;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.sensor.SensorService;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.telemetry.domain.SensorReading;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import com.fertimata.backend.telemetry.domain.TelemetryMessage;
import com.fertimata.backend.telemetry.domain.TelemetryMessageRepository;
import com.fertimata.backend.telemetry.domain.TelemetrySource;
import com.fertimata.backend.telemetry.domain.TelemetryStatus;
import com.fertimata.backend.telemetry.dto.TelemetryIngestResult;
import com.fertimata.backend.websocket.RealtimeEventPublisher;
import com.fertimata.backend.websocket.RealtimeEventType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelemetryIngestServiceTest {

    @Mock
    private TelemetryMessageRepository telemetryMessageRepository;

    @Mock
    private SensorReadingRepository sensorReadingRepository;

    @Mock
    private DeviceService deviceService;

    @Mock
    private SensorService sensorService;

    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

    @Mock
    private AutomationEngine automationEngine;

    @Mock
    private JsonSupport jsonSupport;

    private TelemetryIngestService ingestService;
    private Device device;

    @BeforeEach
    void setUp() {
        TelemetryProperties properties = new TelemetryProperties(true, Duration.ofMinutes(5), 64);
        ingestService = new TelemetryIngestService(
                telemetryMessageRepository,
                sensorReadingRepository,
                deviceService,
                sensorService,
                realtimeEventPublisher,
                automationEngine,
                properties,
                jsonSupport);

        device = TestFixtures.onlineDevice(1L, "JETSON-001");
        when(jsonSupport.toJson(any())).thenReturn("{}");
        when(telemetryMessageRepository.save(any(TelemetryMessage.class))).thenAnswer(invocation -> {
            TelemetryMessage message = invocation.getArgument(0);
            TestFixtures.setId(message, 100L);
            return message;
        });
        when(sensorReadingRepository.saveAll(any())).thenAnswer(invocation -> {
            List<SensorReading> readings = invocation.getArgument(0);
            long id = 1000L;
            for (SensorReading reading : readings) {
                TestFixtures.setId(reading, id++);
            }
            return readings;
        });
    }

    @Test
    @DisplayName("a multi-metric payload becomes one message and one reading per metric")
    void ingestsEveryMetric() {
        when(deviceService.resolveForIngest("JETSON-001")).thenReturn(Optional.of(device));
        registerSensor("temperature", "C", 10L);
        registerSensor("ph", "pH", 11L);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("temperature", 28.4);
        data.put("ph", 6.2);

        TelemetryIngestResult result =
                ingestService.ingest("JETSON-001", Instant.parse("2026-09-06T12:00:00Z"), data, TelemetrySource.MQTT);

        assertThat(result.status()).isEqualTo(TelemetryStatus.ACCEPTED);
        assertThat(result.acceptedMetrics()).isEqualTo(2);
        assertThat(result.rejectedMetrics()).isEmpty();

        ArgumentCaptor<List<SensorReading>> captor = ArgumentCaptor.forClass(List.class);
        verify(sensorReadingRepository).saveAll(captor.capture());
        List<SensorReading> readings = captor.getValue();
        assertThat(readings).hasSize(2);
        assertThat(readings.get(0).getNumericValue()).isEqualByComparingTo(BigDecimal.valueOf(28.4));
        // The unit comes from the registered sensor, never from the payload.
        assertThat(readings.get(0).getUnit()).isEqualTo("C");
        assertThat(readings.get(0).getRecordedAt()).isEqualTo(Instant.parse("2026-09-06T12:00:00Z"));

        verify(realtimeEventPublisher).publish(eq(RealtimeEventType.SENSOR_READING_UPDATED), any());
        verify(deviceService).recordContact(eq(device), any(), any());
        verify(automationEngine, org.mockito.Mockito.times(2)).evaluate(any());
    }

    @Test
    @DisplayName("an unmapped metric is reported but does not discard the metrics next to it")
    void partiallyAcceptsUnknownMetric() {
        when(deviceService.resolveForIngest("JETSON-001")).thenReturn(Optional.of(device));
        registerSensor("temperature", "C", 10L);
        when(sensorService.resolveForMetric(eq(device), eq("mystery"))).thenReturn(Optional.empty());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("temperature", 28.4);
        data.put("mystery", 1);

        TelemetryIngestResult result =
                ingestService.ingest("JETSON-001", Instant.now(), data, TelemetrySource.MQTT);

        assertThat(result.status()).isEqualTo(TelemetryStatus.PARTIALLY_ACCEPTED);
        assertThat(result.acceptedMetrics()).isEqualTo(1);
        assertThat(result.rejectedMetrics()).containsExactly("mystery");
    }

    @Test
    @DisplayName("telemetry from an unregistered device is recorded as REJECTED, not silently dropped")
    void rejectsUnknownDeviceButKeepsThePayload() {
        when(deviceService.resolveForIngest("GHOST")).thenReturn(Optional.empty());

        TelemetryIngestResult result =
                ingestService.ingest("GHOST", Instant.now(), Map.of("temperature", 20), TelemetrySource.MQTT);

        assertThat(result.status()).isEqualTo(TelemetryStatus.REJECTED);
        assertThat(result.acceptedMetrics()).isZero();
        // The envelope is still persisted so the bad traffic can be inspected.
        verify(telemetryMessageRepository).save(any(TelemetryMessage.class));
        verify(sensorReadingRepository, never()).saveAll(any());
        verify(realtimeEventPublisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("a payload with no data is rejected outright")
    void rejectsEmptyPayload() {
        assertThatThrownBy(() -> ingestService.ingest("JETSON-001", Instant.now(), Map.of(), TelemetrySource.MQTT))
                .isInstanceOf(TelemetryValidationException.class)
                .hasMessageContaining("carries no data");
    }

    @Test
    @DisplayName("a payload with more metrics than allowed is rejected")
    void rejectsOversizedPayload() {
        TelemetryProperties tiny = new TelemetryProperties(true, Duration.ofMinutes(5), 1);
        TelemetryIngestService strict = new TelemetryIngestService(
                telemetryMessageRepository,
                sensorReadingRepository,
                deviceService,
                sensorService,
                realtimeEventPublisher,
                automationEngine,
                tiny,
                jsonSupport);

        assertThatThrownBy(() -> strict.ingest(
                        "JETSON-001", Instant.now(), Map.of("a", 1, "b", 2), TelemetrySource.MQTT))
                .isInstanceOf(TelemetryValidationException.class)
                .hasMessageContaining("more than the configured maximum");
    }

    @Test
    @DisplayName("a device clock far in the future is replaced by the server receive time")
    void clampsImplausibleDeviceTimestamp() {
        when(deviceService.resolveForIngest("JETSON-001")).thenReturn(Optional.of(device));
        registerSensor("temperature", "C", 10L);
        Instant farFuture = Instant.now().plus(Duration.ofDays(2));

        ingestService.ingest("JETSON-001", farFuture, Map.of("temperature", 20), TelemetrySource.MQTT);

        ArgumentCaptor<TelemetryMessage> captor = ArgumentCaptor.forClass(TelemetryMessage.class);
        verify(telemetryMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getRecordedAt()).isBefore(farFuture);
    }

    @Test
    @DisplayName("a non-numeric metric is stored as text rather than thrown away")
    void keepsNonNumericValues() {
        when(deviceService.resolveForIngest("JETSON-001")).thenReturn(Optional.of(device));
        registerSensor("pumpState", null, 12L);

        ingestService.ingest("JETSON-001", Instant.now(), Map.of("pumpState", "RUNNING"), TelemetrySource.MQTT);

        ArgumentCaptor<List<SensorReading>> captor = ArgumentCaptor.forClass(List.class);
        verify(sensorReadingRepository).saveAll(captor.capture());
        SensorReading reading = captor.getValue().get(0);
        assertThat(reading.getNumericValue()).isNull();
        assertThat(reading.getTextValue()).isEqualTo("RUNNING");
    }

    private void registerSensor(String metricKey, String unit, Long id) {
        Sensor sensor = TestFixtures.sensor(id, device, metricKey.toUpperCase(), metricKey, unit);
        when(sensorService.resolveForMetric(eq(device), eq(metricKey))).thenReturn(Optional.of(sensor));
    }
}
