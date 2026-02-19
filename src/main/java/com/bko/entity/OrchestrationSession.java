package com.bko.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "orchestration_session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrchestrationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_prompt", nullable = false, columnDefinition = "TEXT")
    private String userPrompt;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "final_answer", columnDefinition = "TEXT")
    private String finalAnswer;

    @Column(name = "status", length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
