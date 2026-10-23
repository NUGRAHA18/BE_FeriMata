package com.fertimata.backend.alert;

/**
 * Well-known alert type identifiers.
 *
 * <p>Deliberately constants rather than an enum: agronomic alert types (threshold breaches, disease
 * findings) are not decided yet, and an edge device or a future rule must be able to raise a type
 * this build has never heard of. The column stores free text; these are the ones the backend itself
 * produces today.
 */
public final class AlertType {

    /** A registered device stopped reporting within its configured timeout. */
    public static final String DEVICE_OFFLINE = "DEVICE_OFFLINE";

    /** A device that was offline started reporting again. */
    public static final String DEVICE_RECOVERED = "DEVICE_RECOVERED";

    /** An actuator command could not be delivered or the device reported it failed. */
    public static final String ACTUATOR_COMMAND_FAILED = "ACTUATOR_COMMAND_FAILED";

    /** A telemetry message arrived that the backend could not fully process. */
    public static final String TELEMETRY_REJECTED = "TELEMETRY_REJECTED";

    /** A device reported a power source that is not a configured normal supply (e.g. BATTERY). */
    public static final String POWER_BACKUP = "POWER_BACKUP";

    /** A device that reported backup power reports a normal supply again. */
    public static final String POWER_RESTORED = "POWER_RESTORED";

    /** A sensor in the drift-check list has not been checked against a standard solution recently. */
    public static final String DRIFT_CHECK_DUE = "DRIFT_CHECK_DUE";

    /** A dosing pump's flow calibration is missing or older than the recalibration interval. */
    public static final String PUMP_CALIBRATION_DUE = "PUMP_CALIBRATION_DUE";

    /**
     * Fallback type for a device-reported alert that did not name one.
     *
     * <p>Devices normally send their own type (for example {@code SSR_SHORT} or
     * {@code DOSING_NOT_CONVERGING}); the backend stores it as given.
     */
    public static final String DEVICE_REPORTED = "DEVICE_REPORTED";

    private AlertType() {}
}
