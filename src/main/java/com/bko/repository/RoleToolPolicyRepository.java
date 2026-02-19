package com.bko.repository;

import com.bko.entity.RoleToolPolicy;
import com.bko.entity.ToolPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoleToolPolicyRepository extends JpaRepository<RoleToolPolicy, UUID> {
    List<RoleToolPolicy> findByRoleId(UUID roleId);
    List<RoleToolPolicy> findByRoleIdAndPolicy(UUID roleId, ToolPolicy policy);
}
