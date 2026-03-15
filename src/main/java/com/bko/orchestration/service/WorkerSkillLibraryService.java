package com.bko.orchestration.service;

import com.bko.config.AgentSkill;
import com.bko.config.MultiAgentProperties;
import com.bko.entity.AgentRole;
import com.bko.entity.PhaseType;
import com.bko.entity.RoleSkill;
import com.bko.repository.AgentRoleRepository;
import com.bko.repository.RoleSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkerSkillLibraryService {

    private final MultiAgentProperties properties;

    /**
     * Resolve the effective skills for a worker role using configuration only.
     */
    public List<AgentSkill> skillsForWorkerRole(String role) {
        return properties.getSkills().getSkillsForWorkerRole(role);
    }

    // Skill budgets are no longer persisted in the database.
}

