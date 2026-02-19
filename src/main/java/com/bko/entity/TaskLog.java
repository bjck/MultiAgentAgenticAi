package com.bko.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "task_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private OrchestratorPlanLog plan;

    @Column(name = "task_id_alias", length = 50)
    private String taskIdAlias;

    @Column(name = "role", length = 50, nullable = false)
    private String role;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "expected_output", columnDefinition = "TEXT")
    private String expectedOutput;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
