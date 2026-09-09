package com.dagacs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A section within a {@link Batch}.
 *
 * <p>Legacy/deprecated column: a nullable {@code subject_id} FK may still
 * exist from the old tentative design. No active code path ever writes it and
 * it is intentionally NOT part of the domain. The intended future subject
 * linkage is a {@code Teacher + Subject + Section} teaching assignment, which
 * is a separate future milestone. Existing deployments keep the legacy column
 * in place (see {@code SectionSubjectSchemaMigration}); it must not be read or
 * written.
 */
@Entity
@Table(name = "sections",
        uniqueConstraints = @UniqueConstraint(name = "uk_section_batch_name",
                columnNames = {"batch_id", "name"}))
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sectionCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer maxCapacity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}