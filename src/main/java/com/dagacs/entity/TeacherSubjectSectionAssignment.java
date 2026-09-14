package com.dagacs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * M9.3 canonical teaching assignment: a {@link Teacher} assigned to teach a
 * {@link SubjectOffering} to a concrete {@link Section} (section mode) or, for
 * zero-section batches, directly to a {@link Batch} (batch mode). Exactly one
 * of {@link #section} and {@link #batch} is set; the invariant is enforced at
 * the service boundary.
 *
 * <p>The academic identity of the subject is carried exclusively by
 * {@link #subjectOffering} (SubjectOffering -> Semester -> AcademicSession ->
 * Program -> Department). The pre-M9.3 {@code teacher_subject_section_assignments.subject_id}
 * column is a deprecated compatibility column that this entity no longer maps;
 * no active code reads or writes it (see TeacherAssignmentSchemaMigration).</p>
 *
 * <p>Uniqueness is {@code uk_assignment (teacher_id, subject_offering_id,
 * section_id)} for section mode and {@code uk_assignment_batch (teacher_id,
 * subject_offering_id, batch_id)} for batch mode: one teacher teaches at most
 * one subject+section (or subject+batch), while many different teachers MAY be
 * assigned to the same SubjectOffering + Section (or + Batch). No uniqueness
 * exists on (subject_offering_id, section_id) or (subject_offering_id, batch_id)
 * alone.</p>
 */
@Entity
@Table(name = "teacher_subject_section_assignments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_assignment",
                        columnNames = {"teacher_id", "subject_offering_id", "section_id"}),
                @UniqueConstraint(name = "uk_assignment_batch",
                        columnNames = {"teacher_id", "subject_offering_id", "batch_id"})})
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class TeacherSubjectSectionAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_offering_id", nullable = false)
    private SubjectOffering subjectOffering;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}