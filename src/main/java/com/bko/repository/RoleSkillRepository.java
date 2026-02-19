package com.bko.repository;

import com.bko.entity.RoleSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoleSkillRepository extends JpaRepository<RoleSkill, UUID> {
    List<RoleSkill> findByRoleIdOrderBySortOrderAsc(UUID roleId);
}
