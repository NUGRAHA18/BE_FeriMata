package com.fertimata.backend.support;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceStatus;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.user.Role;
import com.fertimata.backend.user.User;
import java.lang.reflect.Field;

/**
 * Builders for the entities the unit tests need.
 *
 * <p>Identifiers are set reflectively because they are database-generated and the tests must not be
 * forced through a persistence context just to have an id.
 */
public final class TestFixtures {

    private TestFixtures() {}

    public static Device device(Long id, String code) {
        Device device = new Device(code, code, "EDGE_GATEWAY");
        setId(device, id);
        return device;
    }

    public static Device onlineDevice(Long id, String code) {
        Device device = device(id, code);
        device.setStatus(DeviceStatus.ONLINE);
        return device;
    }

    public static Sensor sensor(Long id, Device device, String code, String metricKey, String unit) {
        Sensor sensor = new Sensor(device, code, metricKey, code, "TEST", unit);
        setId(sensor, id);
        return sensor;
    }

    public static Actuator actuator(Long id, Device device, String code) {
        Actuator actuator = new Actuator(device, code, code, "OUTPUT");
        setId(actuator, id);
        return actuator;
    }

    public static User operator(Long id, String username) {
        User user = new User(username, "$2a$10$notarealhash", "Test Operator", Role.OPERATOR);
        setId(user, id);
        return user;
    }

    /** Sets the inherited {@code id} field declared on BaseEntity. */
    public static void setId(Object entity, Long id) {
        try {
            Class<?> type = entity.getClass();
            Field field = null;
            while (type != null && field == null) {
                try {
                    field = type.getDeclaredField("id");
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
            if (field == null) {
                throw new IllegalStateException("No id field on " + entity.getClass());
            }
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not set the test id", ex);
        }
    }
}
