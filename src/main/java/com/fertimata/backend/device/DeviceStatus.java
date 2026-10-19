package com.fertimata.backend.device;

/**
 * Liveness of an edge device as the backend currently understands it.
 *
 * <p>{@link #UNKNOWN} is the state of a device that has been registered but has never reported in.
 * It is kept separate from {@link #OFFLINE} so that the dashboard can distinguish "never seen" from
 * "was alive and stopped answering".
 */
public enum DeviceStatus {
    ONLINE,
    OFFLINE,
    UNKNOWN
}
