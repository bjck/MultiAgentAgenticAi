package com.bko.repository;

import com.bko.entity.Tool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToolRepository extends JpaRepository<Tool, UUID> {
    Optional<Tool> findByName(String name);
}
