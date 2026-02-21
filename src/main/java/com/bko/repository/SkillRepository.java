package com.bko.repository;

import com.bko.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing {@link Skill} entities.
 * Provides methods for querying skills based on their name.
 */
@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {
    /**
     * Finds a {@link Skill} entity by its unique name.
     *
     * @param name The name of the skill.
     * @return An {@link Optional} containing the found {@link Skill}, or empty if no skill with the given name exists.
     */
    Optional<Skill> findByName(String name);
}
