package com.fertimata.backend.alert;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Menerjemahkan {@link AlertFilter} menjadi klausa WHERE; filter yang kosong tidak menambah apa pun. */
final class AlertSpecifications {

    private AlertSpecifications() {}

    static Specification<Alert> matching(AlertFilter filter) {
        AlertFilter f = filter == null ? AlertFilter.none() : filter;
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.acknowledged() != null) {
                predicates.add(cb.equal(root.get("acknowledged"), f.acknowledged()));
            }
            if (f.severity() != null) {
                predicates.add(cb.equal(root.get("severity"), f.severity()));
            }
            if (f.deviceId() != null) {
                predicates.add(cb.equal(root.get("relatedDevice").get("id"), f.deviceId()));
            }
            if (f.sensorId() != null) {
                predicates.add(cb.equal(root.get("relatedSensor").get("id"), f.sensorId()));
            }
            if (f.actuatorId() != null) {
                predicates.add(cb.equal(root.get("relatedActuator").get("id"), f.actuatorId()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
