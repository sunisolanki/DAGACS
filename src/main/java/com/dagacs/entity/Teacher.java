package com.dagacs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "teachers")
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /**
     * Department the teacher belongs to (M6.1 HOD binding). Nullable so that
     * existing teachers without a department association remain valid.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /**
     * HOD designation flag (M6.1). Only marks the teacher as the HOD of
     * {@link #department}; the role system itself is unchanged (HOD is a User
     * role, not a Teacher role).
     */
    @Column(name = "is_hod", nullable = false)
    @Builder.Default
    private Boolean isHod = false;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String designation;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private String avatarUrl;
}