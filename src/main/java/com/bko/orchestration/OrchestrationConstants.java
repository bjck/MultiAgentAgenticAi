package com.bko.orchestration;

import java.util.List;
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

    // Operational constants
    public static final int MAX_EXECUTION_ITERATIONS = 15;

    // LLM Request Purposes
    public static final String PURPOSE_PLAN = "plan";
    public static final String PURPOSE_PLAN_RETRY = "plan-retry";
    public static final String PURPOSE_PLAN_REVIEW = "plan-review";
    public static final String PURPOSE_PLAN_REVIEW_RETRY = "plan-review-retry";
    public static final String PURPOSE_ROLE_SELECTION = "role-selection";
    public static final String PURPOSE_ROLE_SELECTION_RETRY = "role-selection-retry";
    public static final String PURPOSE_WORKER_TASK = "worker-task";
    public static final String PURPOSE_SYTHESIS = "synthesis";

    // Task IDs and descriptions
    public static final String TASK_ID_ANALYSIS = "analysis-1";
    public static final String TASK_ID_DESIGN = "design-1";
    public static final String TASK_ID_IMPLEMENTATION = "task-impl";
    public static final String TASK_ID_FALLBACK = "task-1";
    public static final String TASK_ID_CONTEXT = "task-0-context";
    public static final String TASK_PREFIX = "task-";

    // Default messages and instructions
    public static final String DEFAULT_EXPECTED_OUTPUT = "Provide concise, actionable output.";
    public static final String DEFAULT_IMPLEMENTATION_INSTRUCTION = "Implement the requested changes and return the implementer handoff schema.";
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
    public static final String CONTEXT_SYNC_TASK_DESCRIPTION = "Context sync: read AGENTS.md (and frontend/AGENTS.md if relevant), then list the most relevant files/dirs to inspect for this request.";
    public static final String CONTEXT_SYNC_TASK_EXPECTED_OUTPUT = "Summarize key guidance from AGENTS.md (and frontend/AGENTS.md if relevant) and list the top relevant files/dirs to inspect next. Do not modify files.";

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

    // Detection Phrases
    public static final List<String> EDIT_PHRASES = List.of(
            "modify your own code",
            "edit the code",
            "change the code",
            "apply the changes",
            "make the following changes",
            "implement this"
    );

    public static final List<String> EDIT_VERBS = List.of(
            "modify", "change", "update", "fix", "add", "implement", "remove",
            "delete", "refactor", "rename", "create", "build", "wire", "adjust", "edit", "patch"
    );

    public static final List<String> EDIT_ARTIFACTS = List.of(
            "code", "repo", "repository", "project", "app", "application", "api",
            "endpoint", "controller", "service", "ui", "frontend", "backend", "css",
            "html", "javascript", "js", "java", "spring", "config", "yaml", "yml",
            "file", "files", "tests", "database", "schema", "table"
    );

    public static final List<String> DIRECTIVE_PHRASES = List.of(
            "please ",
            "can you",
            "could you",
            "i want",
            "i need",
            "i'd like",
            "we need",
            "we want"
    );

    public static final String FILE_EDIT_INSTRUCTION_BASE = "\nApply the requested changes directly to repository files using MCP filesystem tools (read and write).\n";

    // Prompt Templates
    public static final String ROLE_SELECTION_SYSTEM_PROMPT = """
            You are the agent selector. Choose the minimal set of worker roles needed to satisfy the user request.
            Only choose from: %s.
            Use the role skill registry to match roles to required skills.
            Order roles in likely execution order (analysis/design first, engineering next, implementer last).
            Return only JSON that matches: {"roles": ["role1", ...]}.
            Project guidance is documented in AGENTS.md at the repo root. For frontend work, also see frontend/AGENTS.md.
            """;

    public static final String ROLE_SELECTION_EDITS_INSTRUCTION = "\nAlways include the engineering and implementer roles because code changes are required.\n";

    public static final String ORCHESTRATOR_SYSTEM_PROMPT = """
            You are the Orchestrator agent. Break the user's request into up to %d parallel tasks.
            Only assign roles from: %s.
            Match tasks to the role skill registry below.
            Analysis and design tasks are advisory and must not modify files.
            Engineering tasks are advisory and should not modify files.
            Implementer tasks should apply file edits when required, based on engineering consensus.
            Keep tasks independent and specific. Each task should be actionable by a single worker.
            You may use MCP filesystem tools to inspect the workspace when needed.
            If the user requests code or content changes, ensure at least one task is explicitly responsible for applying file edits via MCP filesystem tools.
            Make it clear which task should write files and which should not.
            Return only JSON that matches the requested schema.
            Project guidance is documented in AGENTS.md at the repo root. For frontend work, also see frontend/AGENTS.md.
            The first task MUST be a context sync: read AGENTS.md (and frontend/AGENTS.md if relevant) and list the most relevant files/dirs to inspect.

            Role skill registry:
            %s
            """;

    public static final String ORCHESTRATOR_EDITS_INSTRUCTION = """

            The user request requires code changes. Ensure the plan includes engineering advisory tasks and implementer tasks that will modify files.
            """;

    public static final String EXECUTION_REVIEW_SYSTEM_PROMPT = """
            You are the execution reviewer. Decide if additional tasks are required to fully satisfy the user request.
            Only assign roles from: %s.
            Do NOT include analysis or design tasks; those are already complete.
            If the work is complete, return an empty tasks array.
            Return only JSON that matches the requested schema.
            Project guidance is documented in AGENTS.md at the repo root. For frontend work, also see frontend/AGENTS.md.
            """;

    public static final String EXECUTION_REVIEW_EDITS_INSTRUCTION = "\nIf edits are still required, ensure implementer tasks perform them.\n";

    public static final String WORKER_SYSTEM_PROMPT = """
            You are a %s worker agent.
            Focus only on the assigned task. Be concise and practical.
            You must follow the expected output for this task.
            You may use MCP filesystem tools to read or list files in the workspace.
            When the task requires code changes, you MUST use MCP filesystem tools to read and write files to apply the changes.
            If the task does not explicitly instruct file edits, do not write files.
            Only the implementer role should apply file edits.
            If assumptions are required, list them explicitly.
            Project guidance is documented in AGENTS.md at the repo root. For frontend work, also see frontend/AGENTS.md.
            """;

    public static final String WORKER_EDITS_INSTRUCTION = """

            This request involves code changes. If your task's expected output says to apply changes, you must do so by editing files.
            """;

    public static final String SYNTHESIS_SYSTEM_PROMPT = """
            You are the synthesis agent. Combine worker outputs into a single, coherent response.
            Resolve conflicts, remove duplication, and answer the user's request directly.
            If MCP tool output was used, summarize relevant file changes accurately.
            Project guidance is documented in AGENTS.md at the repo root. For frontend work, also see frontend/AGENTS.md.
            """;

    public static final String COLLABORATION_SYSTEM_PROMPT = """
            You are the collaboration lead for %s agents.
            Combine the agents' findings into a single best answer for this stage.
            Resolve conflicts, deduplicate, and surface key insights and open questions.
            Keep it concise and actionable.
            Follow any stage-specific instructions provided below.
            Project guidance is documented in AGENTS.md at the repo root. For frontend work, also see frontend/AGENTS.md.
            """;

    // User Templates
    public static final String ORCHESTRATOR_USER_TEMPLATE = """
            User request:
            {input}

            Context:
            {context}
            """;

    public static final String ROLE_SELECTION_USER_TEMPLATE = """
            User request:
            {input}

            Context:
            {context}

            Available roles and skills:
            {roles}
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

    public static final String SYNTHESIS_USER_TEMPLATE = """
            User request:
            {input}

            Plan:
            {plan}

            Worker outputs:
            {results}
            """;

    public static final String COLLABORATION_USER_TEMPLATE = """
            User request:
            {input}

            Stage task:
            {task}

            Round:
            {round}

            Strategy:
            {strategy}

            Stage:
            {stage}

            Agent outputs:
            {results}
            """;
}
