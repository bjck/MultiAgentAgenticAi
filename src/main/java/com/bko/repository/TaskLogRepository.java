package com.bko.repository;

import com.bko.entity.TaskLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository interface for managing {@link TaskLog} entities.
 */
public interface TaskLogRepository extends JpaRepository<TaskLog, UUID> {
}
