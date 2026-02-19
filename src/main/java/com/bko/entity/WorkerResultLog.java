package com.bko.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "worker_result_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerResultLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private OrchestrationSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_log_id")
    private TaskLog taskLog;

    @Column(name = "role", length = 50)
    private String role;

    @Column(name = "output", columnDefinition = "TEXT")
    private String output;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
