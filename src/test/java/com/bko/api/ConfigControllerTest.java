package com.bko.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bko.orchestration.service.ToolPolicyService;
import com.bko.entity.AgentRole;
import com.bko.entity.PhaseType;
import com.bko.repository.AgentRoleRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(ConfigController.class)
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private com.bko.config.MultiAgentProperties properties;

    @MockitoBean
    private ToolPolicyService toolPolicyService;

    @MockitoBean
    private AgentRoleRepository agentRoleRepository;

    @Test
    void testGetSkills() throws Exception {
        com.bko.config.AgentSkillsConfig skillsConfig = new com.bko.config.AgentSkillsConfig();
        when(properties.getSkills()).thenReturn(skillsConfig);
        when(agentRoleRepository.findByPhaseAndActiveTrueOrderByCodeAsc(PhaseType.WORKER))
                .thenReturn(List.of(workerRole("analysis"), workerRole("engineering")));

        mockMvc.perform(get("/api/config/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orchestrator").isArray())
                .andExpect(jsonPath("$.workerRoles").isArray());
    }

    @Test
    void testGetRoleSettings() throws Exception {
        when(properties.getRoleExecutionDefaults()).thenReturn(new com.bko.config.MultiAgentProperties.RoleExecutionConfig());
        when(properties.getRoleExecutionConfig(anyString())).thenReturn(new com.bko.config.MultiAgentProperties.RoleExecutionConfig());
        when(agentRoleRepository.findByPhaseAndActiveTrueOrderByCodeAsc(PhaseType.WORKER))
                .thenReturn(List.of(workerRole("analysis")));

        mockMvc.perform(get("/api/config/role-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isMap())
                .andExpect(jsonPath("$.defaults").exists());
    }

    private AgentRole workerRole(String code) {
        AgentRole role = new AgentRole();
        role.setPhase(PhaseType.WORKER);
        role.setCode(code);
        role.setActive(true);
        return role;
    }
}
