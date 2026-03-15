package com.bko.orchestration;

import java.util.Set;

public final class OrchestrationConstants {

    private OrchestrationConstants() {
        // Private constructor to prevent instantiation
    }

    // Roles
    public static final String ROLE_ANALYSIS = "analysis";
    public static final String ROLE_DESIGN = "design";
    public static final String ROLE_ENGINEERING = "engineering";
    public static final String ROLE_IMPLEMENTER = "implementer";
    public static final String ROLE_GENERAL = "general";

    public static final Set<String> ADVISORY_ROLES = Set.of(ROLE_ANALYSIS, ROLE_DESIGN);

    // LLM Request Purposes
    public static final String PURPOSE_PLAN = "plan";
    public static final String PURPOSE_PLAN_RETRY = "plan-retry";
    public static final String PURPOSE_PLAN_REVIEW = "plan-review";
    public static final String PURPOSE_PLAN_REVIEW_RETRY = "plan-review-retry";
    public static final String PURPOSE_SKILL_PLAN = "skill-plan";
    public static final String PURPOSE_SKILL_PLAN_RETRY = "skill-plan-retry";
    public static final String PURPOSE_WORKER_TASK = "worker-task";

    // Task IDs and descriptions
    public static final String TASK_ID_ANALYSIS = "analysis-1";
    public static final String TASK_ID_DESIGN = "design-1";
    public static final String TASK_ID_FALLBACK = "task-1";
    public static final String TASK_ID_CONTEXT = "task-0-context";
    public static final String TASK_ID_DISCOVERY = "task-0-discovery";
    public static final String TASK_PREFIX = "task-";

    // Default messages and instructions
    public static final String DEFAULT_EXPECTED_OUTPUT = "Provide concise, actionable output.";
    public static final String DEFAULT_COMPLETE_RESPONSE_INSTRUCTION = "Provide a complete response to the user request.";
    public static final String INVALID_JSON_RETRY_PROMPT = "\nYour last response was invalid JSON. Return only valid JSON.";
    public static final String WORKER_FAILED_MESSAGE = "Worker failed: ";

    // Task Descriptions and Expected Outputs
    public static final String ANALYSIS_TASK_DESCRIPTION = "Analyze the user request. Identify requirements, constraints, risks, and edge cases.";
    public static final String ANALYSIS_TASK_EXPECTED_OUTPUT = """
            Return only JSON matching this handoff schema. Do not modify files.

            %s
            """;
    public static final String DESIGN_TASK_DESCRIPTION = "Propose a design/approach for the request, including components, APIs, data flow, and steps.";
    public static final String DESIGN_TASK_EXPECTED_OUTPUT = """
            Return only JSON matching this handoff schema. Do not modify files.

            %s
            """;
    public static final String DISCOVERY_TASK_DESCRIPTION = "Discovery: analyze the user request and use available tools to identify relevant context and key implementation details.";
    public static final String DISCOVERY_TASK_EXPECTED_OUTPUT = """
            Provide a concise discovery report with:
            - Summary (1-3 sentences)
            - Relevant sources (bullets)
            - Key findings (bullets)
            - Assumptions or open questions (bullets)
            Do not modify files.
            """;

    // Handoff schemas
    public static final String ANALYSIS_HANDOFF_SCHEMA = """
            {
              "summary": "1-3 sentences",
              "requirements": ["..."],
              "constraints": ["..."],
              "assumptions": ["..."],
              "risks": [{"risk":"...","impact":"low|med|high","mitigation":"..."}],
              "open_questions": ["..."],
              "out_of_scope": ["..."]
            }
            """;

    public static final String DESIGN_HANDOFF_SCHEMA = """
            {
              "design_summary": "1-3 sentences",
              "decisions": ["..."],
              "architecture": {
                "components": ["..."],
                "data_flow": "short description"
              },
              "api_changes": [{"endpoint":"...","change":"..."}],
              "data_changes": [{"entity":"...","change":"...","migration":true}],
              "files_to_touch": ["..."],
              "implementation_steps": ["..."],
              "test_plan": ["..."],
              "rollback": ["..."]
            }
            """;

    public static final String ENGINEERING_HANDOFF_SCHEMA = """
            {
              "consensus_summary": "1-3 sentences",
              "change_plan": [{"file":"...","change":"...","reason":"..."}],
              "edge_cases": ["..."],
              "tests": ["..."],
              "risks_remaining": ["..."],
              "open_questions": ["..."]
            }
            """;

    public static final String IMPLEMENTER_HANDOFF_SCHEMA = """
            {
              "status": "completed|partial|blocked",
              "changes_made": [{"file":"...","summary":"..."}],
              "files_added": ["..."],
              "files_removed": ["..."],
              "tests_run": [{"command":"...","result":"pass|fail|not-run","notes":"..."}],
              "followups": ["..."],
              "risks_remaining": ["..."]
            }
            """;

    // Prompt Templates

    public static final String ORCHESTRATOR_SYSTEM_PROMPT = """
            You are the planning agent.
            Create a plan for the user request.
            The plan must be assigned to the general role and should describe the step-by-step plan.
            The general worker will execute the plan sequentially.
            Match tasks to the role skill registry below.
            The plan must be concrete execution steps with clear deliverables and acceptance criteria.
            Return only JSON that matches the requested schema.

            Role skill registry:
            %s
            """;

    public static final String EXECUTION_REVIEW_SYSTEM_PROMPT = """
            You are the execution reviewer. Decide if additional work is required to fully satisfy the user request.
            Only assign roles from: %s.
            Do NOT include analysis or design tasks; those are already complete.
            If more work is needed, return a single task for the general role that describes the remaining steps.
            If the work is complete, return an empty tasks array.
            Return only JSON that matches the requested schema.
            """;

    public static final String WORKER_SYSTEM_PROMPT = """
            You are a %s worker agent.
            Focus only on the assigned task. Be concise and practical.
            You must follow the expected output for this task.
            If assumptions are required, list them explicitly.
            """;

    public static final String COLLABORATION_SYSTEM_PROMPT = """
            You are the collaboration lead for %s agents.
            Combine the agents' findings into a single best answer for this stage.
            Resolve conflicts, deduplicate, and surface key insights and open questions.
            Keep it concise and actionable.
            Follow any stage-specific instructions provided below.
            """;

    public static final String SKILL_PLANNER_SYSTEM_PROMPT = """
            You are the skill planner. Select the minimal set of skills required to complete the task.
            Prefer fewer skills even if it means more iterations later. Keep context small.
            Only choose from the provided skills list.
            Skill budget (maximum): %d.
            Return only JSON in this form: {"skills":["Skill Name", "..."], "reason":"short rationale"}.
            If no skills are needed, return an empty skills array.
            """;

    // User Templates
    public static final String ORCHESTRATOR_USER_TEMPLATE = """
            User request:
            {input}

            Context:
            {context}
            """;


    public static final String EXECUTION_REVIEW_USER_TEMPLATE = """
            User request:
            {input}

            Context:
            {context}

            Errors encountered:
            {errors}

            Current plan:
            {plan}

            Worker outputs so far:
            {results}
            """;

    public static final String WORKER_USER_TEMPLATE = """
            User request:
            {input}

            Context:
            {context}

            Assigned task:
            {task}

            Expected output:
            {expectedOutput}
            """;

    public static final String SKILL_PLANNER_USER_TEMPLATE = """
            User request:
            {input}

            Task:
            {task}

            Expected output:
            {expectedOutput}

            Skill budget (max):
            {budget}

            Available skills:
            {skills}
            """;
}
