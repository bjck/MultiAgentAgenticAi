package com.bko.orchestration.service;

import com.bko.orchestration.model.OrchestratorPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class OrchestrationMetricsService {

    private final AtomicLong llmRequestCount = new AtomicLong();
    private final AtomicLong planResponseCount = new AtomicLong();
    private final AtomicLong taskReceivedCount = new AtomicLong();
    private final AtomicLong taskExecutedCount = new AtomicLong();

    public void recordLlmRequest(String purpose, @Nullable String role) {
        long count = llmRequestCount.incrementAndGet();
        if (StringUtils.hasText(role)) {
            log.info("LLM request #{} sent (purpose={}, role={}). Total requests={}.", count, purpose, role, count);
        } else {
            log.info("LLM request #{} sent (purpose={}). Total requests={}.", count, purpose, count);
        }
    }

    public void recordPlanResponse(String label, @Nullable OrchestratorPlan plan) {
        long planCount = planResponseCount.incrementAndGet();
        if (plan == null || plan.tasks() == null) {
            log.info("Plan response #{} ({}) returned no tasks. Total plans={}.", planCount, label, planCount);
            return;
        }
        int taskCount = plan.tasks().size();
        long totalTasks = taskReceivedCount.addAndGet(taskCount);
        log.info("Plan response #{} ({}) received {} tasks. Total plans={}, total tasks received={}.",
                planCount, label, taskCount, planCount, totalTasks);
    }

    public void recordTasksExecuted(int executedCount) {
        if (executedCount <= 0) {
            return;
        }
        long totalExecuted = taskExecutedCount.addAndGet(executedCount);
        log.info("Executing {} plan tasks. Total tasks executed so far={}.", executedCount, totalExecuted);
    }

    public void recordApprovedTasksExecuted(int executedCount) {
        if (executedCount <= 0) {
            return;
        }
        long totalExecuted = taskExecutedCount.addAndGet(executedCount);
        log.info("Executing {} approved plan tasks. Total tasks executed so far={}.", executedCount, totalExecuted);
    }

    public void logSummary() {
        log.info("LLM stats: totalRequests={}, totalPlans={}, totalTasksReceived={}, totalTasksExecuted={}.",
                llmRequestCount.get(), planResponseCount.get(), taskReceivedCount.get(), taskExecutedCount.get());
    }
}
