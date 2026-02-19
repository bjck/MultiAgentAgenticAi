package com.bko.orchestration.service;

import com.bko.entity.*;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.TaskSpec;
import com.bko.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class OrchestrationPersistenceService {

    private final OrchestrationSessionRepository sessionRepository;
    private final PromptLogRepository promptLogRepository;
    private final OrchestratorPlanLogRepository planLogRepository;
    private final TaskLogRepository taskLogRepository;
    private final WorkerResultLogRepository workerResultLogRepository;

    public OrchestrationSession startSession(String userPrompt, @Nullable String provider, @Nullable String model) {
        OrchestrationSession session = OrchestrationSession.builder()
                .userPrompt(userPrompt)
                .provider(provider)
                .model(model)
                .status("IN_PROGRESS")
                .build();
        return sessionRepository.save(session);
    }

    public void completeSession(OrchestrationSession session, @Nullable String finalAnswer, String status) {
        session.setFinalAnswer(finalAnswer);
        session.setStatus(status);
        sessionRepository.save(session);
    }

    public void logPrompt(OrchestrationSession session, String purpose, @Nullable String role,
                          @Nullable String systemPrompt, @Nullable String userTemplate,
                          Map<String, String> params, @Nullable String fullResponse) {
        String userPrompt = userTemplate == null ? null : fillTemplate(userTemplate, params);
        PromptLog log = PromptLog.builder()
                .session(session)
                .purpose(purpose)
                .role(role)
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .fullResponse(fullResponse)
                .build();
        promptLogRepository.save(log);
    }

    public OrchestratorPlanLog logPlan(OrchestrationSession session, OrchestratorPlan plan, boolean isInitial) {
        OrchestratorPlanLog pl = OrchestratorPlanLog.builder()
                .session(session)
                .objective(plan.objective())
                .initial(isInitial)
                .build();
        return planLogRepository.save(pl);
    }

    public Map<String, TaskLog> logTasks(OrchestratorPlanLog planLog, List<TaskSpec> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Map.of();
        }
        Map<String, TaskLog> index = new LinkedHashMap<>();
        for (TaskSpec t : tasks) {
            TaskLog tl = TaskLog.builder()
                    .plan(planLog)
                    .taskIdAlias(t.id())
                    .role(t.role())
                    .description(t.description())
                    .expectedOutput(t.expectedOutput())
                    .build();
            tl = taskLogRepository.save(tl);
            if (t.id() != null) {
                index.put(t.id(), tl);
            }
        }
        return index;
    }

    public WorkerResultLog logWorkerResult(OrchestrationSession session, @Nullable TaskLog taskLog, @Nullable String role, String output) {
        WorkerResultLog wr = WorkerResultLog.builder()
                .session(session)
                .taskLog(taskLog)
                .role(role)
                .output(output)
                .build();
        return workerResultLogRepository.save(wr);
    }

    private String fillTemplate(String template, Map<String, String> params) {
        String out = template;
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                String key = "{" + e.getKey() + "}";
                out = out.replace(key, e.getValue() == null ? "" : e.getValue());
            }
        }
        return out;
    }
}
