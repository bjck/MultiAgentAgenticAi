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
    public static final String ROLE_GENERAL = "general";

    public static final Set<String> ADVISORY_ROLES = Set.of(ROLE_ANALYSIS, ROLE_DESIGN);

    // Operational constants
    public static final int MAX_EXECUTION_ITERATIONS = 3;

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
    public static final String TASK_PREFIX = "task-";

    // Default messages and instructions
    public static final String DEFAULT_EXPECTED_OUTPUT = "Provide concise, actionable output.";
    public static final String DEFAULT_IMPLEMENTATION_INSTRUCTION = "Implement the requested changes.";
    public static final String DEFAULT_COMPLETE_RESPONSE_INSTRUCTION = "Provide a complete response to the user request.";
    public static final String INVALID_JSON_RETRY_PROMPT = "\nYour last response was invalid JSON. Return only valid JSON.";
    public static final String WORKER_FAILED_MESSAGE = "Worker failed: ";

    // Task Descriptions and Expected Outputs
    public static final String ANALYSIS_TASK_DESCRIPTION = "Analyze the user request. Identify requirements, constraints, risks, and edge cases.";
    public static final String ANALYSIS_TASK_EXPECTED_OUTPUT = "Provide structured analysis and open questions if any. Do not modify files.";
    public static final String DESIGN_TASK_DESCRIPTION = "Propose a design/approach for the request, including components, APIs, data flow, and steps.";
    public static final String DESIGN_TASK_EXPECTED_OUTPUT = "Provide a clear design and implementation guidance. Do not modify files.";

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
            Order roles in likely execution order (analysis/design first, engineering later).
            Return only JSON that matches: {"roles": ["role1", ...]}.
            """;

    public static final String ROLE_SELECTION_EDITS_INSTRUCTION = "\nAlways include the engineering role because code changes are required.\n";

    public static final String ORCHESTRATOR_SYSTEM_PROMPT = """
            You are the Orchestrator agent. Break the user's request into up to %d parallel tasks.
            Only assign roles from: %s.
            Match tasks to the role skill registry below.
            Analysis and design tasks are advisory and must not modify files.
            Engineering tasks should apply file edits when required.
            Keep tasks independent and specific. Each task should be actionable by a single worker.
            You may use MCP filesystem tools to inspect the workspace when needed.
            If the user requests code or content changes, ensure at least one task is explicitly responsible for applying file edits via MCP filesystem tools.
            Make it clear which task should write files and which should not.
            Return only JSON that matches the requested schema.

            Role skill registry:
            %s
            """;

    public static final String ORCHESTRATOR_EDITS_INSTRUCTION = """

            The user request requires code changes. Ensure the plan includes implementation tasks that will modify files.
            """;

    public static final String EXECUTION_REVIEW_SYSTEM_PROMPT = """
            You are the execution reviewer. Decide if additional tasks are required to fully satisfy the user request.
            Only assign roles from: %s.
            Do NOT include analysis or design tasks; those are already complete.
            If the work is complete, return an empty tasks array.
            Return only JSON that matches the requested schema.
            """;

    public static final String EXECUTION_REVIEW_EDITS_INSTRUCTION = "\nIf edits are still required, ensure engineering tasks perform them.\n";

    public static final String WORKER_SYSTEM_PROMPT = """
            You are a %s worker agent.
            Focus only on the assigned task. Be concise and practical.
            You must follow the expected output for this task.
            You may use MCP filesystem tools to read or list files in the workspace.
            When the task requires code changes, you MUST use MCP filesystem tools to read and write files to apply the changes.
            If the task does not explicitly instruct file edits, do not write files.
            Only the engineering role should apply file edits.
            If assumptions are required, list them explicitly.
            """;

    public static final String WORKER_EDITS_INSTRUCTION = """

            This request involves code changes. If your task's expected output says to apply changes, you must do so by editing files.
            """;

    public static final String SYNTHESIS_SYSTEM_PROMPT = """
            You are the synthesis agent. Combine worker outputs into a single, coherent response.
            Resolve conflicts, remove duplication, and answer the user's request directly.
            If MCP tool output was used, summarize relevant file changes accurately.
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
}
