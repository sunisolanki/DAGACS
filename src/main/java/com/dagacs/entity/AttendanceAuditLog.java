package com.dagacs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_audit_logs")
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_id", nullable = false)
    private AttendanceRecord attendance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "roll_no", nullable = false)
    private String rollNo;

    @Column(name = "student_name", nullable = false)
    private String studentName;

    @Column(nullable = false)
    private String subject;

    @Column(name = "subject_name", nullable = false)
    private String subjectName;

    /**
     * Section-mode snapshot (e.g. "CSE-A"); NULL for batch-mode records where a
     * Section does not exist.
     */
    @Column
    private String section;

    @Column(name = "section_name")
    private String sectionName;

    /**
     * Batch-mode snapshot (e.g. "MTECH-SE-2025"), populated only for
     * attendance records created against a batch-level session where no Section
     * exists.
     */
    @Column
    private String batch;

    @Column(name = "batch_name")
    private String batchName;

    @Column(nullable = false)
    private String date;

    @Column(nullable = false)
    private String previousStatus;

    @Column(nullable = false)
    private String newStatus;

    @Column(nullable = false)
    private String updatedBy;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = true)
    private String reason;
}
