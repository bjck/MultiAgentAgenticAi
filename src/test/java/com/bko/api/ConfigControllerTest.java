package com.bko.api;

import com.bko.BaseIntegrationTest;
import com.bko.MockTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest(ConfigController.class)
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.bko.config.MultiAgentProperties properties;

    @Test
    void testGetSkills() throws Exception {
        com.bko.config.AgentSkillsConfig skillsConfig = new com.bko.config.AgentSkillsConfig();
        when(properties.getSkills()).thenReturn(skillsConfig);
        when(properties.getWorkerRoles()).thenReturn(List.of("analysis", "engineering"));

        mockMvc.perform(get("/api/config/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orchestrator").isArray())
                .andExpect(jsonPath("$.workerRoles").isArray());
    }

    @Test
    void testGetRoleSettings() throws Exception {
        when(properties.getWorkerRoles()).thenReturn(List.of("analysis"));
        when(properties.getRoleExecutionDefaults()).thenReturn(new com.bko.config.MultiAgentProperties.RoleExecutionConfig());
        when(properties.getRoleExecutionConfig(anyString())).thenReturn(new com.bko.config.MultiAgentProperties.RoleExecutionConfig());

        mockMvc.perform(get("/api/config/role-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isMap())
                .andExpect(jsonPath("$.defaults").exists());
    }
}
