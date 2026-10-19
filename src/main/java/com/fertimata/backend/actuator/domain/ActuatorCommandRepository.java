package com.fertimata.backend.actuator.domain;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ActuatorCommandRepository extends JpaRepository<ActuatorCommand, Long> {

    Optional<ActuatorCommand> findByCommandUid(String commandUid);

    Page<ActuatorCommand> findByActuatorIdOrderByRequestedAtDesc(Long actuatorId, Pageable pageable);

    @Query("select c from ActuatorCommand c join fetch c.actuator join fetch c.device order by c.requestedAt desc")
    Page<ActuatorCommand> findRecent(Pageable pageable);

    long countByStatusAndRequestedAtAfter(CommandStatus status, Instant since);
}
