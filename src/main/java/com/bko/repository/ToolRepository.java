package com.bko.repository;

import com.bko.entity.Tool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing {@link Tool} entities.
 * Provides methods for querying tools based on their name.
 */
@Repository
public interface ToolRepository extends JpaRepository<Tool, UUID> {
    /**
     * Finds a {@link Tool} entity by its unique name.
     *
     * @param name The name of the tool.
     * @return An {@link Optional} containing the found {@link Tool}, or empty if no tool with the given name exists.
     */
    Optional<Tool> findByName(String name);
}
