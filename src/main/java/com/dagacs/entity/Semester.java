package com.dagacs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "semesters",
        uniqueConstraints = @UniqueConstraint(name = "uk_semester_session",
                columnNames = {"name", "academic_session_id"}))
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class Semester {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    /**
     * Semester metadata (legacy/ambiguous). This is NOT the admission year and
     * is NOT used by any domain logic today; its exact meaning (study-year,
     * academic year, or ordinal metadata) is not enforced. The desired
     * year-of-study mapping (e.g. Semesters 1-2 → Year 1) is a later-milestone
     * concern that must be derived from semester-ordinal semantics, not this
     * column.
     */
    @Column(nullable = false)
    private Integer year;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_session_id", nullable = false)
    private AcademicSession academicSession;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}