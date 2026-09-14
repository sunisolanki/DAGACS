package com.dagacs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_student_session",
                columnNames = {"session_id", "student_id"}))
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AttendanceSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /**
     * Denormalized snapshot of the session's subject, kept synchronized with the
     * session. Never accepted independently from the client.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    /**
     * Denormalized snapshot of the session's section, kept synchronized with the
     * session. Never accepted independently from the client. Null for
     * batch-mode sessions.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    /**
     * Denormalized snapshot of the session's batch, kept synchronized with the
     * session. Never accepted independently from the client. Set only for
     * batch-mode sessions.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marked_by", nullable = false)
    private Teacher markedBy;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String lecturePeriod;

    @Column(nullable = false)
    private String date;

    @Column(nullable = false)
    private Boolean isPresent;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}