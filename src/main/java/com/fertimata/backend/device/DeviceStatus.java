package com.fertimata.backend.device;

/** Device liveness; {@link #UNKNOWN} = registered but never seen, distinct from {@link #OFFLINE} (was alive, stopped). */
public enum DeviceStatus {
    ONLINE,
    OFFLINE,
    UNKNOWN
}
