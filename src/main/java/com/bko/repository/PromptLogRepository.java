package com.bko.repository;

import com.bko.entity.PromptLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository interface for managing {@link PromptLog} entities.
 */
public interface PromptLogRepository extends JpaRepository<PromptLog, UUID> {
}
