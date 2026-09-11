package com.dagacs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * M9.2 academic applicability mapping: a {@link Subject} offered within a
 * {@link Semester}.
 *
 * <p>A {@code Subject} is a reusable catalog master; a {@code SubjectOffering}
 * records that the subject is taught in a specific academic context. The full
 * academic context is resolved relationally through the semester:
 * {@code Semester -> AcademicSession -> Program -> Department}. No redundant
 * program/academic-session/department columns or foreign keys exist here.</p>
 *
 * <p>One subject may be mapped to at most one semester (enforced by
 * {@code uk_subject_offering (subject_id, semester_id)} and a service-level
 * 409 check); the same subject may be mapped to many different semesters.</p>
 */
@Entity
@Table(name = "subject_offerings",
        uniqueConstraints = @UniqueConstraint(name = "uk_subject_offering",
                columnNames = {"subject_id", "semester_id"}))
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubjectOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}