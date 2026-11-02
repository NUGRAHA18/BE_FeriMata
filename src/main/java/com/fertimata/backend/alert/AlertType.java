package com.fertimata.backend.alert;

/** Well-known alert types. Constants, not an enum: the column is free text and devices/future rules may raise new types. */
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

    /** Jadwal siram tidak dijalankan: ditolak aturan keselamatan atau pompa tidak tersedia. */
    public static final String IRRIGATION_SKIPPED = "IRRIGATION_SKIPPED";

    /** Durasi siram dari fuzzy AI tidak bisa dipakai; jadwal berjalan dengan durasi cadangan petani. */
    public static final String IRRIGATION_AI_FALLBACK = "IRRIGATION_AI_FALLBACK";

    /** Pemupukan yang jatuh tempo tidak menuang apa pun (data basi, AI tidak bisa dipakai, perintah ditolak). */
    public static final String FERTILIZATION_SKIPPED = "FERTILIZATION_SKIPPED";

    /** Pemupukan berhenti di tengah: sebagian pompa sempat menuang. */
    public static final String FERTILIZATION_PARTIAL = "FERTILIZATION_PARTIAL";

    /** A sensor in the drift-check list has not been checked against a standard solution recently. */
    public static final String DRIFT_CHECK_DUE = "DRIFT_CHECK_DUE";

    /** A dosing pump's flow calibration is missing or older than the recalibration interval. */
    public static final String PUMP_CALIBRATION_DUE = "PUMP_CALIBRATION_DUE";

    /** Fallback for a device alert without a type; devices normally send their own (e.g. {@code SSR_SHORT}), stored as given. */
    public static final String DEVICE_REPORTED = "DEVICE_REPORTED";

    private AlertType() {}
}
