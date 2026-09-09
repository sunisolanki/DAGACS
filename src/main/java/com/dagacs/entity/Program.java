package com.dagacs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "programs")
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    /** Informational program duration (e.g. "4 years"). Display-only; no business logic consumes it. */
    @Column(nullable = false)
    private String duration;

    @Column(nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL)
    private List<AcademicSession> academicSessions = new ArrayList<>();
}