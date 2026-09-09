package com.dagacs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A teachable course offered by a {@link Department}.
 *
 * <p>Legacy/deprecated column: deployments created before the master data
 * correction may still hold a free-text {@code department} (VARCHAR) column
 * from the old flat model. It is NOT part of the active domain and must NOT
 * be read or written. Existing values are preserved in place and are never
 * deleted by the application; a later, explicit schema cleanup can drop it.
 * The active relationship is the {@link Department} FK below.
 * <p>
 * {@code creditHours} remains persisted as a String for backward compatibility
 * with existing rows, but the API/UI now enforce a strict positive numeric
 * format. Converting the column to a numeric type is deferred to a dedicated
 * migration.
 */
@Entity
@Table(name = "subjects")
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String creditHours;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}