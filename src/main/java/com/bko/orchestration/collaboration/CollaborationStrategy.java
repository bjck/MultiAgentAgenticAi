package com.bko.orchestration.collaboration;

public enum CollaborationStrategy {
    SIMPLE_SUMMARY("Simple summary"),
    PROPOSAL_VOTE("Proposal + structured vote"),
    TWO_ROUND_CONVERGE("Two-round converge"),
    SCORECARD_RANKING("Scorecard ranking");

    private final String label;

    CollaborationStrategy(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
