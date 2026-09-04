package com.dagacs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "batches",
        uniqueConstraints = @UniqueConstraint(name = "uk_batch_session_name",
                columnNames = {"academic_session_id", "name"}))
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String batchCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer year;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_session_id", nullable = false)
    private AcademicSession academicSession;

    @Column(nullable = false)
    private String program;

    @Column(nullable = false)
    private Integer maxCapacity;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}