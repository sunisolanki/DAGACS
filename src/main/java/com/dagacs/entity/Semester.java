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