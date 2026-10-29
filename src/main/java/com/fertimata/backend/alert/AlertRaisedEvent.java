package com.fertimata.backend.alert;

/**
 * Peringatan baru sudah dibuat. Diterbitkan di dalam transaksi pembuatnya; pendengar yang menghubungi
 * dunia luar (notifikasi push) memakai {@code @TransactionalEventListener} agar hanya bereaksi setelah
 * peringatan benar-benar tersimpan.
 */
public record AlertRaisedEvent(AlertResponse alert) {}
