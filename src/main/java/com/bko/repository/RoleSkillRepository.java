package com.bko.repository;

import com.bko.entity.RoleSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing {@link RoleSkill} entities.
 * Provides methods for querying role skills based on role ID.
 */
@Repository
public interface RoleSkillRepository extends JpaRepository<RoleSkill, UUID> {
    /**
     * Finds all {@link RoleSkill} entities associated with a given role ID, ordered by their sort order in ascending manner.
     *
     * @param roleId The UUID of the role.
     * @return A list of {@link RoleSkill} entities.
     */
    List<RoleSkill> findByRoleIdOrderBySortOrderAsc(UUID roleId);
}
