package com.fertimata.backend.actuator.safety;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.device.DeviceStatus;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Optionally refuses to switch something on for a device that is not currently heard from.
 *
 * <p>With a persistent MQTT session the broker would hold the command and deliver it whenever the
 * device returns - possibly hours later, into a tank nobody is watching. On real hardware that is
 * worse than a refusal the operator can see.
 */
@Component
@Order(20)
public class DeviceOnlineGuard implements CommandGuard {

    private final ActuatorSafetyProperties properties;

    public DeviceOnlineGuard(ActuatorSafetyProperties properties) {
        this.properties = properties;
    }

    @Override
    public void check(CommandContext context) {
        if (!context.activating() || !properties.refuseWhenDeviceOffline()) {
            return;
        }
        Actuator actuator = context.actuator();
        DeviceStatus status = actuator.getDevice().getStatus();
        if (status != DeviceStatus.ONLINE) {
            throw new SafetyInterlockException(
                    "Device %s is %s; %s cannot be switched on until it reports in"
                            .formatted(actuator.getDevice().getDeviceCode(), status, actuator.getCode()),
                    Map.of(
                            "interlock", "DEVICE_NOT_ONLINE",
                            "deviceCode", actuator.getDevice().getDeviceCode(),
                            "deviceStatus", String.valueOf(status)));
        }
    }
}
