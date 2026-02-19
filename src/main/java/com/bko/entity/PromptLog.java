package com.bko.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "prompt_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromptLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private OrchestrationSession session;

    @Column(name = "purpose", length = 100, nullable = false)
    private String purpose;

    @Column(name = "role", length = 50)
    private String role;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "user_prompt", columnDefinition = "TEXT")
    private String userPrompt;

    @Column(name = "full_response", columnDefinition = "TEXT")
    private String fullResponse;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
