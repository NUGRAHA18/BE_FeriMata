package com.fertimata.backend.plant;

import com.fertimata.backend.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tanaman di greenhouse untuk dashboard. Atribut riset dihapus (V14); umur/fase berlaku per greenhouse, bukan per tanaman. */
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

    @Column(nullable = false)
    private boolean active = true;

    public Plant(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
