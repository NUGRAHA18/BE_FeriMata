package com.fertimata.backend.actuator.safety;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.device.DeviceStatus;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Optionally refuses activating commands for a device that is not ONLINE: a persistent session would deliver
 * them whenever it returns, maybe hours later into an unwatched tank. */
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
