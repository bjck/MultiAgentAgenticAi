package com.bko.orchestration.collaboration;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CollaborationStrategyService {

    private static final CollaborationStage SIMPLE_SUMMARY_STAGE = new CollaborationStage(
            "summary",
            "Summary",
            null,
            "",
            true
    );

    private static final String PROPOSAL_OUTPUT_TEMPLATE = """
            Return JSON only.

            {
              "proposal_id": "%s",
              "summary": "...",
              "changes": ["..."],
              "files": ["..."],
              "tests": ["..."],
              "risks": ["..."]
            }

            Do not edit files.
            """;

    private static final String VOTE_OUTPUT_TEMPLATE = """
            Return JSON only.

            {
              "voter_id": "%s",
              "votes": [
                {"proposal_id": "...", "vote": "approve|block", "reason": "..."}
              ],
              "notes": "..."
            }

            Do not edit files.
            """;

    private static final String SCORECARD_OUTPUT_TEMPLATE = """
            Return JSON only.

            {
              "scorer_id": "%s",
              "scores": [
                {"proposal_id": "...", "correctness": 1-5, "scope": 1-5, "risk": 1-5, "effort": 1-5, "notes": "..."}
              ],
              "overall_pick": "proposal_id"
            }

            Do not edit files.
            """;

    private static final String CONVERGE_OUTPUT_TEMPLATE = """
            Provide a converged plan with final changes, files, tests, and risks.
            Do not edit files.
            """;

    private static final CollaborationStage PROPOSAL_STAGE = new CollaborationStage(
            "proposal",
            "Proposal",
            PROPOSAL_OUTPUT_TEMPLATE,
            """
            Summarize proposals into a compact canonical list.
            Preserve proposal_id values and key details.
            Output JSON: {"proposals":[{"proposal_id":"...","summary":"...","changes":[...],"files":[...],"tests":[...],"risks":[...]}]}.
            """,
            false
    );

    private static final CollaborationStage VOTE_STAGE = new CollaborationStage(
            "vote",
            "Vote",
            VOTE_OUTPUT_TEMPLATE,
            """
            Tally votes, identify the winning proposal, and explain why it won.
            Provide the final handoff output using the role schema when available.
            Reflect the winning proposal details within the handoff fields (changes, files, tests, risks).
            """,
            false
    );

    private static final CollaborationStage CONVERGE_STAGE = new CollaborationStage(
            "converge",
            "Converge",
            CONVERGE_OUTPUT_TEMPLATE,
            """
            Merge the best ideas into a single converged plan.
            Provide the final plan with changes, files, tests, and risks.
            If a handoff schema is provided, output must match it.
            """,
            false
    );

    private static final CollaborationStage SCORECARD_STAGE = new CollaborationStage(
            "scorecard",
            "Scorecard",
            SCORECARD_OUTPUT_TEMPLATE,
            """
            Aggregate scorecards, rank proposals by average score, and pick the top option.
            Provide the final handoff output using the role schema when available.
            Reflect the winning proposal details within the handoff fields (changes, files, tests, risks).
            """,
            false
    );

    public List<CollaborationStage> stagesFor(CollaborationStrategy strategy) {
        if (strategy == null) {
            return List.of(SIMPLE_SUMMARY_STAGE);
        }
        return switch (strategy) {
            case PROPOSAL_VOTE -> List.of(PROPOSAL_STAGE, VOTE_STAGE);
            case TWO_ROUND_CONVERGE -> List.of(PROPOSAL_STAGE, CONVERGE_STAGE);
            case SCORECARD_RANKING -> List.of(PROPOSAL_STAGE, SCORECARD_STAGE);
            case SIMPLE_SUMMARY -> List.of(SIMPLE_SUMMARY_STAGE);
        };
    }
}
