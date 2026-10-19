package com.fertimata.backend.plant;

import com.fertimata.backend.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tracked plant or growing position.
 *
 * <p>Intentionally minimal. Whether cultivation is tracked per plant, per row or per greenhouse
 * zone is an open question for the agronomy and AI teams, so this entity carries only an identifier
 * and a name. It exists so that an AI detection can optionally point at something without that
 * decision being made here.
 */
@Entity
@Table(name = "plant")
@Getter
@Setter
@NoArgsConstructor
public class Plant extends AuditedEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 255)
    private String description;

    public Plant(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
