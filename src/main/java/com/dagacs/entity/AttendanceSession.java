package com.dagacs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_session_class",
                columnNames = {"subject_code", "section_code", "date", "lecture_period"}))
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_code", nullable = false)
    private Subject subjectEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_code", nullable = false)
    private Section sectionEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacherEntity;

    /**
     * Legacy snapshot copy of {@code subjectEntity} kept synchronized with the
     * entity reference. {@code subjectEntity} is the source of truth; this string
     * is never accepted independently from the client.
     */
    @Column(nullable = false)
    private String subject;

    /**
     * Legacy snapshot copy of {@code sectionEntity} kept synchronized with the
     * entity reference. {@code sectionEntity} is the source of truth; this string
     * is never accepted independently from the client.
     */
    @Column(nullable = false)
    private String section;

    /**
     * Legacy snapshot copy of {@code teacherEntity} kept synchronized with the
     * entity reference. {@code teacherEntity} is the source of truth; this string
     * is never accepted independently from the client.
     */
    @Column(nullable = false)
    private String teacher;

    @Column(name = "lecture_period", nullable = false)
    private String lecturePeriod;

    @Column(nullable = false)
    private String date;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}