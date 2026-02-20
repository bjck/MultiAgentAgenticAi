package com.bko.stream;

import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Component
public class OrchestrationStreamService {
    private static final int CHUNK_SIZE = 600;

    private final OrchestrationStreamHub hub;

    public OrchestrationStreamService(OrchestrationStreamHub hub) {
        this.hub = hub;
    }

    public String createRun() {
        return hub.createRun();
    }

    public void emitStatus(String runId, String message) {
        hub.emit(runId, "status", Map.of("message", message));
    }

    public void emitPlan(String runId, OrchestratorPlan plan) {
        hub.emit(runId, "plan", plan);
    }

    public void emitPlanUpdate(String runId, OrchestratorPlan plan) {
        hub.emit(runId, "plan-update", plan);
    }

    public void emitTaskStart(String runId, TaskSpec task) {
        hub.emit(runId, "task-start", taskPayload(task, null));
    }

    public void emitTaskOutput(String runId, WorkerResult result) {
        if (!StringUtils.hasText(result.output())) {
            hub.emit(runId, "task-output", Map.of(
                    "taskId", result.taskId(),
                    "role", result.role(),
                    "chunk", "",
                    "sequence", 0,
                    "done", true
            ));
            return;
        }
        int sequence = 0;
        String output = result.output();
        for (int index = 0; index < output.length(); index += CHUNK_SIZE) {
            int end = Math.min(output.length(), index + CHUNK_SIZE);
            boolean done = end >= output.length();
            hub.emit(runId, "task-output", Map.of(
                    "taskId", result.taskId(),
                    "role", result.role(),
                    "chunk", output.substring(index, end),
                    "sequence", sequence++,
                    "done", done
            ));
        }
    }

    public void emitTaskComplete(String runId, WorkerResult result) {
        hub.emit(runId, "task-complete", Map.of(
                "taskId", result.taskId(),
                "role", result.role()
        ));
    }

    public void emitFinalAnswer(String runId, String finalAnswer) {
        hub.emit(runId, "final", Map.of("finalAnswer", finalAnswer));
    }

    public void emitRunComplete(String runId, String status) {
        hub.emit(runId, "run-complete", Map.of("status", status));
    }

    public void emitError(String runId, String message) {
        hub.emit(runId, "error", Map.of("message", message));
    }

    private Map<String, Object> taskPayload(TaskSpec task, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", task.id());
        payload.put("role", task.role());
        payload.put("description", task.description());
        payload.put("expectedOutput", task.expectedOutput());
        if (status != null) {
            payload.put("status", status);
        }
        return payload;
    }
}
