package com.fertimata.backend.push;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    /** Penerima notifikasi: semua langganan milik akun yang masih aktif. */
    @Query("select s from PushSubscription s join fetch s.user u where u.enabled = true")
    List<PushSubscription> findAllOfEnabledUsers();

    List<PushSubscription> findByUserId(Long userId);

    long countByUserId(Long userId);
}
