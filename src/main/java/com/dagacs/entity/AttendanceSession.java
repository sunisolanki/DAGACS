package com.dagacs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_sessions")
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String section;

    @Column(nullable = false)
    private String teacher;

    @Column(nullable = false)
    private String lecturePeriod;

    @Column(nullable = false)
    private String date;

    @Column(nullable = false)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_code")
    private Subject subjectEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_code")
    private Section sectionEntity;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}