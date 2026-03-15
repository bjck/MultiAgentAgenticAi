package com.bko.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "scheduled_agent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledAgent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * High-level natural language objective that will seed orchestration prompts.
     */
    @Column(name = "objective_prompt", columnDefinition = "TEXT", nullable = false)
    private String objectivePrompt;

    /**
     * Canonical cron-like schedule expression understood by the scheduler.
     */
    @Column(name = "schedule_expression", nullable = false)
    private String scheduleExpression;

    /**
     * Original human-friendly schedule input from the user (e.g. \"every hour\").
     */
    @Column(name = "raw_schedule_input", columnDefinition = "TEXT")
    private String rawScheduleInput;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "default_provider")
    private String defaultProvider;

    @Column(name = "default_model")
    private String defaultModel;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @Column(name = "next_run_at")
    private OffsetDateTime nextRunAt;

    /**
     * Optional safety limit for how many tokens this agent is allowed
     * to consume in a single run. Enforcement can be implemented in
     * the orchestration layer.
     */
    @Column(name = "token_limit_per_run")
    private Long tokenLimitPerRun;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

