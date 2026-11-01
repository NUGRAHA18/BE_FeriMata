package com.fertimata.backend.push;

import com.fertimata.backend.alert.AlertRaisedEvent;
import com.fertimata.backend.alert.AlertResponse;
import com.fertimata.backend.alert.AlertSeverity;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.push.dto.PushTestResponse;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Peringatan penting → notifikasi HP, dikirim setelah commit di thread sendiri agar server push yang lambat tidak
 * menahan ingest/perintah. Tanpa antrean ulang: server push menyimpannya selama {@code app.push.ttl}. */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    /** Batas teks supaya payload tetap jauh di bawah batas 4 KB Web Push. */
    private static final int MAX_BODY = 600;

    private final WebPushClient client;
    private final PushSubscriptionService subscriptions;
    private final PushProperties properties;
    private final JsonSupport jsonSupport;
    private final Clock clock;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "web-push");
        thread.setDaemon(true);
        return thread;
    });

    public PushNotificationService(
            WebPushClient client,
            PushSubscriptionService subscriptions,
            PushProperties properties,
            JsonSupport jsonSupport,
            Clock clock) {
        this.client = client;
        this.subscriptions = subscriptions;
        this.properties = properties;
        this.jsonSupport = jsonSupport;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAlertRaised(AlertRaisedEvent event) {
        AlertResponse alert = event.alert();
        if (!client.enabled() || alert.severity().compareTo(properties.minSeverity()) < 0) {
            return;
        }
        byte[] payload = payload(alert);
        WebPushClient.Urgency urgency =
                alert.severity() == AlertSeverity.CRITICAL ? WebPushClient.Urgency.HIGH : WebPushClient.Urgency.NORMAL;
        executor.execute(() -> {
            try {
                PushTestResponse result = deliver(subscriptions.recipients(), payload, urgency);
                log.info(
                        "Alert {} pushed: {} delivered, {} removed, {} failed",
                        alert.id(), result.delivered(), result.removed(), result.failed());
            } catch (RuntimeException ex) {
                log.warn("Could not push alert {}: {}", alert.id(), ex.getMessage());
            }
        });
    }

    /** Notifikasi uji ke semua browser milik akun ini, ditunggu hasilnya supaya operator tahu berhasil. */
    public PushTestResponse sendTest(String username) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("title", "Notifikasi FertiMata aktif");
        message.put("body", "HP ini akan menerima peringatan penting dari kebun.");
        message.put("tag", "fertimata-test");
        message.put("url", "/alerts");
        return deliver(subscriptions.forUser(username), json(message), WebPushClient.Urgency.NORMAL);
    }

    private PushTestResponse deliver(
            List<PushSubscriptionService.Target> targets, byte[] payload, WebPushClient.Urgency urgency) {
        int delivered = 0;
        int removed = 0;
        int failed = 0;
        for (PushSubscriptionService.Target target : targets) {
            WebPushClient.Delivery result = client.send(target.endpoint(), target.p256dh(), target.auth(), payload, urgency);
            switch (result.outcome()) {
                case DELIVERED -> {
                    subscriptions.recordDelivered(target.id(), clock.instant());
                    delivered++;
                }
                case GONE -> {
                    subscriptions.remove(target.id());
                    removed++;
                }
                case FAILED -> {
                    subscriptions.recordFailed(target.id());
                    failed++;
                }
            }
        }
        return new PushTestResponse(delivered, removed, failed);
    }

    /** Payload untuk service worker: {@code tag} mencegah peringatan sama menumpuk, {@code url} dibuka saat diketuk. */
    byte[] payload(AlertResponse alert) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("title", alert.title());
        message.put("body", abbreviate(alert.message()));
        message.put("tag", "alert-" + alert.id());
        message.put("url", "/alerts");
        message.put("alertId", alert.id());
        message.put("severity", alert.severity());
        message.put("type", alert.type());
        message.put("createdAt", alert.createdAt());
        return json(message);
    }

    private byte[] json(Map<String, Object> message) {
        return jsonSupport.toJson(message).getBytes(StandardCharsets.UTF_8);
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_BODY ? text : text.substring(0, MAX_BODY - 1) + "…";
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
