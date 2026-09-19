package com.dagacs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "students")
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String rollNumber;

    /**
     * Email of the login account that maps to this student profile. Used to resolve
     * the authenticated student from the JWT principal (mirrors {@code teachers.email}).
     * Nullable so that existing/unknown-profile students are not invalidated; never
     * trusted from a client request.
     */
    @Column(name = "email")
    private String email;

    @Column(nullable = false)
    private String name;

    /**
     * Optional fields - nullable for bulk import where these may be absent.
     */
    @Column(nullable = true)
    private String gender;

    @Column(nullable = true)
    private String fatherName;

    @Column(nullable = true)
    private String motherName;

    @Column(nullable = true)
    private String photoUrl;

    @Column(nullable = true)
    private String enrollmentNumber;

    @Column(nullable = true)
    private Integer age;

    @Column(nullable = true)
    private String admissionDate;

    @Column(nullable = true)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = true)
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = true)
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_session_id", nullable = false)
    private AcademicSession academicSession;

    /**
     * The student's current semester, reusing the existing master-data
     * {@link Semester} entity (like {@code SubjectOffering.semester}).
     * Nullable so that existing/simple-imported students are not invalidated;
     * the filter UI treats a null semester as a valid, selectable state under
     * "All". Semester is deliberately stored, not derived (see the frozen
     * export/analytics stance: "semester is NOT derivable").
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = true)
    private Semester semester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
