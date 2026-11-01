package com.fertimata.backend.actuator.safety;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceService;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Refuses configured types on backup power: Rev A dosing flow is calibrated at 27.2 V float and drops on battery;
 * the trolley stops to save energy. The edge enforces this too; this guard makes the refusal visible. */
@Component
@Order(30)
public class BackupPowerGuard implements CommandGuard {

    private final ActuatorSafetyProperties properties;
    private final DeviceService deviceService;

    public BackupPowerGuard(ActuatorSafetyProperties properties, DeviceService deviceService) {
        this.properties = properties;
        this.deviceService = deviceService;
    }

    @Override
    public void check(CommandContext context) {
        Actuator actuator = context.actuator();
        if (!context.activating()
                || !ActuatorSafetyProperties.contains(properties.blockedOnBackupPowerTypes(), actuator.getType())) {
            return;
        }
        List<Device> onBackup = deviceService.devicesOnBackupPower();
        if (onBackup.isEmpty()) {
            return;
        }
        Device reporter = onBackup.get(0);
        throw new SafetyInterlockException(
                "%s (%s) is blocked while running on backup power: %s reports %s"
                        .formatted(actuator.getCode(), actuator.getType(), reporter.getDeviceCode(),
                                reporter.getPowerSource()),
                Map.of(
                        "interlock", "BACKUP_POWER",
                        "actuatorType", actuator.getType(),
                        "reportedBy", reporter.getDeviceCode(),
                        "powerSource", reporter.getPowerSource()));
    }
}
