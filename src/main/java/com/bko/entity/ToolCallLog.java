package com.bko.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tool_call_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolCallLog {

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

    @Column(name = "tool_name", length = 120, nullable = false)
    private String toolName;

    @Column(name = "tool_input", columnDefinition = "TEXT")
    private String toolInput;

    @Column(name = "tool_output", columnDefinition = "TEXT")
    private String toolOutput;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
