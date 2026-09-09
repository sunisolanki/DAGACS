package com.dagacs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * An academic year within a {@link Program}.
 *
 * <p>Legacy/deprecated columns: deployments created before the master data
 * correction may still hold {@code semester}, {@code duration_hours},
 * {@code lecture_periods} and {@code credits} columns. They are NOT part of
 * the active domain and must NOT be read or written. They are left in place
 * only because the application has no migration framework; an explicit,
 * later schema cleanup can drop them safely.
 */
@Entity
@Table(name = "academic_sessions",
        uniqueConstraints = @UniqueConstraint(name = "uk_academic_session_program",
                columnNames = {"name", "program_id"}))
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class AcademicSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}