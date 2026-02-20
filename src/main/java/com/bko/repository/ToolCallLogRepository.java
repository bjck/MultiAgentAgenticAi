package com.bko.repository;

import com.bko.entity.ToolCallLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ToolCallLogRepository extends JpaRepository<ToolCallLog, UUID> {
}
