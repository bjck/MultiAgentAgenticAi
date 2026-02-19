package com.bko.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orchestrator_plan_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrchestratorPlanLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private OrchestrationSession session;

    @Column(name = "objective", columnDefinition = "TEXT")
    private String objective;

    @Column(name = "is_initial", nullable = false)
    private boolean initial;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TaskLog> tasks;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
