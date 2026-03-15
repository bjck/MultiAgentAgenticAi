package com.bko.repository;

import com.bko.entity.RoleToolPolicy;
import com.bko.entity.ToolPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing {@link RoleToolPolicy} entities.
 * Provides methods for querying role tool policies based on role ID and policy type.
 */
@Repository
public interface RoleToolPolicyRepository extends JpaRepository<RoleToolPolicy, UUID> {
    /**
     * Finds all {@link RoleToolPolicy} entities associated with a given role ID.
     *
     * @param roleId The UUID of the role.
     * @return A list of {@link RoleToolPolicy} entities.
     */
    List<RoleToolPolicy> findByRoleId(UUID roleId);

    /**
     * Finds {@link RoleToolPolicy} entities for a given role ID and tool policy.
     *
     * @param roleId The UUID of the role.
     * @param policy The {@link ToolPolicy} type.
     * @return A list of {@link RoleToolPolicy} entities matching the role ID and policy.
     */
    List<RoleToolPolicy> findByRoleIdAndPolicy(UUID roleId, ToolPolicy policy);

    /**
     * Returns tool names for a given role and policy without triggering lazy loading
     * of the {@code Tool} association outside an active session.
     */
    @Query("select lower(trim(t.name)) from RoleToolPolicy p join p.tool t " +
           "where p.role.id = :roleId and p.policy = :policy and t.name is not null")
    List<String> findToolNamesByRoleIdAndPolicy(@Param("roleId") UUID roleId,
                                                @Param("policy") ToolPolicy policy);

    /**
     * Deletes all tool policy entries for a role.
     *
     * @param roleId The role ID.
     */
    void deleteByRoleId(UUID roleId);
}
