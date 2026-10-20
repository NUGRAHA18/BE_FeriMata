package com.fertimata.backend.cultivation;

/** Status satu siklus tanam. Hanya satu siklus boleh {@link #ACTIVE} pada satu waktu. */
public enum CycleStatus {
    PLANNED,
    ACTIVE,
    ENDED
}
