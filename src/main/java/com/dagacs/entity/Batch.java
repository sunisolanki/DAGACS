package com.dagacs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * An admission cohort (student cohort).
 *
 * <p>A Batch represents the students admitted to a {@link Program} in a single
 * year (e.g. "B.Tech CSE Batch 2025"). It is NOT recreated per academic year:
 * one Batch row persists across the cohort's semesters and its
 * {@link #academicSession} is advanced by admins as the cohort progresses
 * (2025-26 → 2026-27 → ...).</p>
 *
 * <p>{@link #program} is a denormalized String snapshot of
 * {@code academicSession.program.name}, written on every create/update in
 * {@code BatchService}. It is retained for legacy/display compatibility only;
 * the relational chain Batch → AcademicSession → Program is authoritative and
 * this column must never be accepted as client input.</p>
 */
@Entity
@Table(name = "batches",
        uniqueConstraints = @UniqueConstraint(name = "uk_batch_session_name",
                columnNames = {"academic_session_id", "name"}))
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String batchCode;

    @Column(nullable = false)
    private String name;

    /** Admission year of the cohort. */
    @Column(nullable = false)
    private Integer year;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_session_id", nullable = false)
    private AcademicSession academicSession;

    @Column(nullable = false)
    private String program;

    @Column(nullable = false)
    private Integer maxCapacity;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}