package com.fertimata.backend.alert;

/** Alert baru tersimpan. Pendengar ke luar (push) memakai {@code @TransactionalEventListener} agar jalan setelah commit. */
public record AlertRaisedEvent(AlertResponse alert) {}
