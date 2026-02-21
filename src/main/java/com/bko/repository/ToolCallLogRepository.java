package com.bko.repository;

import com.bko.entity.ToolCallLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository interface for managing {@link ToolCallLog} entities.
 */
public interface ToolCallLogRepository extends JpaRepository<ToolCallLog, UUID> {
}
