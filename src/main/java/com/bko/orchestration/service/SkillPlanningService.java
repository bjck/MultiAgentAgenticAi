package com.bko.orchestration.service;

import com.bko.config.AgentSkill;
import com.bko.config.MultiAgentProperties;
import com.bko.entity.OrchestrationSession;
import com.bko.orchestration.api.AgentInvocationService;
import com.bko.orchestration.model.SkillSelection;
import com.bko.orchestration.model.SkillSummary;
import com.bko.orchestration.model.TaskSkillPlan;
import com.bko.orchestration.model.TaskSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SkillPlanningService {

    private final MultiAgentProperties properties;
    private final WorkerSkillLibraryService workerSkillLibraryService;
    private final AgentInvocationService agentInvocationService;

    private final Map<String, SkillPlanningResult> cache = new ConcurrentHashMap<>();

    public SkillPlanningResult planForTask(OrchestrationSession session,
                                           String userMessage,
                                           TaskSpec task,
                                           @Nullable String context,
                                           String provider,
                                           String model) {
        if (task == null || !StringUtils.hasText(task.id())) {
            return new SkillPlanningResult(emptyPlan(task, 0, "No task."), List.of());
        }
        String cacheKey = session.getId() + ":" + task.id();
        SkillPlanningResult cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<AgentSkill> available = workerSkillLibraryService.skillsForWorkerRole(task.role());
        int budget = resolveBudget(task.role());
        if (budget <= 0 || available.isEmpty()) {
            SkillPlanningResult result = new SkillPlanningResult(emptyPlan(task, budget, "No skills selected."), List.of());
            cache.put(cacheKey, result);
            return result;
        }
        SelectionOutcome outcome = selectSkills(session, userMessage, task, available, budget, context, provider, model);
        List<AgentSkill> selected = outcome.skills();
        String rationale = StringUtils.hasText(outcome.rationale())
                ? outcome.rationale()
                : (selected.size() == available.size()
                    ? "All available skills are within budget."
                    : "Selected minimal skills for the task.");
        TaskSkillPlan plan = new TaskSkillPlan(task.id(), task.role(), budget, summarize(selected), rationale);
        SkillPlanningResult result = new SkillPlanningResult(plan, selected);
        cache.put(cacheKey, result);
        return result;
    }

    public List<TaskSkillPlan> planForTasks(OrchestrationSession session,
                                            String userMessage,
                                            List<TaskSpec> tasks,
                                            @Nullable String context,
                                            String provider,
                                            String model) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<TaskSkillPlan> plans = new ArrayList<>(tasks.size());
        for (TaskSpec task : tasks) {
            SkillPlanningResult result = planForTask(session, userMessage, task, context, provider, model);
            if (result != null && result.plan() != null) {
                plans.add(result.plan());
            }
        }
        return plans;
    }

    private SelectionOutcome selectSkills(OrchestrationSession session,
                                          String userMessage,
                                          TaskSpec task,
                                          List<AgentSkill> available,
                                          int budget,
                                          @Nullable String context,
                                          String provider,
                                          String model) {
        if (available.size() <= budget) {
            return new SelectionOutcome(available, "All available skills are within budget.");
        }
        List<SkillSummary> summaries = summarize(available);
        SkillSelection selection = agentInvocationService.requestSkillSelection(session, userMessage, task, summaries,
                budget, context, provider, model);
        List<AgentSkill> matched = matchSkills(selection, available);
        if (matched.isEmpty()) {
            return new SelectionOutcome(available.subList(0, Math.min(budget, available.size())),
                    "Defaulted to top skills within budget.");
        }
        if (matched.size() > budget) {
            return new SelectionOutcome(matched.subList(0, budget), selection != null ? selection.reason() : null);
        }
        return new SelectionOutcome(matched, selection != null ? selection.reason() : null);
    }

    private List<AgentSkill> matchSkills(@Nullable SkillSelection selection, List<AgentSkill> available) {
        if (selection == null || selection.skills() == null || selection.skills().isEmpty()) {
            return List.of();
        }
        Map<String, AgentSkill> byName = new LinkedHashMap<>();
        for (AgentSkill skill : available) {
            if (skill == null || !StringUtils.hasText(skill.getName())) {
                continue;
            }
            byName.put(skill.getName().trim().toLowerCase(Locale.ROOT), skill);
        }
        List<AgentSkill> matched = new ArrayList<>();
        for (String name : selection.skills()) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            AgentSkill hit = byName.get(name.trim().toLowerCase(Locale.ROOT));
            if (hit != null) {
                matched.add(hit);
            }
        }
        return matched;
    }

    private int resolveBudget(@Nullable String role) {
        int defaultBudget = properties.getSkillPlanning().getDefaultSkillBudget();
        if (StringUtils.hasText(role)) {
            Integer override = properties.getSkillPlanning().getRoleBudgets().get(role.toLowerCase(Locale.ROOT));
            if (override != null) {
                return override;
            }
        }
        return defaultBudget;
    }

    private List<SkillSummary> summarize(List<AgentSkill> skills) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        List<SkillSummary> summaries = new ArrayList<>(skills.size());
        for (AgentSkill skill : skills) {
            if (skill == null || !StringUtils.hasText(skill.getName())) {
                continue;
            }
            summaries.add(new SkillSummary(skill.getName(), skill.getDescription()));
        }
        return summaries;
    }

    private TaskSkillPlan emptyPlan(TaskSpec task, int budget, String rationale) {
        String id = task != null ? task.id() : null;
        String role = task != null ? task.role() : null;
        return new TaskSkillPlan(id, role, budget, List.of(), rationale);
    }

    public record SkillPlanningResult(TaskSkillPlan plan, List<AgentSkill> selectedSkills) {
    }

    private record SelectionOutcome(List<AgentSkill> skills, String rationale) {
    }
}
