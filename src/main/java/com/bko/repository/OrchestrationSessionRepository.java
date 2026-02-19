package com.bko.repository;

import com.bko.entity.OrchestrationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrchestrationSessionRepository extends JpaRepository<OrchestrationSession, UUID> {
}
