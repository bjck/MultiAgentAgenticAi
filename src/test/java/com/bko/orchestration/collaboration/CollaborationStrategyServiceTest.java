package com.bko.orchestration.collaboration;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CollaborationStrategyServiceTest {

    private final CollaborationStrategyService service = new CollaborationStrategyService();

    @Test
    void testSimpleSummary() {
        List<CollaborationStage> stages = service.stagesFor(CollaborationStrategy.SIMPLE_SUMMARY);
        assertEquals(1, stages.size());
        assertEquals("summary", stages.get(0).key());
    }

    @Test
    void testProposalVote() {
        List<CollaborationStage> stages = service.stagesFor(CollaborationStrategy.PROPOSAL_VOTE);
        assertEquals(2, stages.size());
        assertEquals("proposal", stages.get(0).key());
        assertEquals("vote", stages.get(1).key());
    }

    @Test
    void testTwoRoundConverge() {
        List<CollaborationStage> stages = service.stagesFor(CollaborationStrategy.TWO_ROUND_CONVERGE);
        assertEquals(2, stages.size());
        assertEquals("proposal", stages.get(0).key());
        assertEquals("converge", stages.get(1).key());
    }

    @Test
    void testScorecardRanking() {
        List<CollaborationStage> stages = service.stagesFor(CollaborationStrategy.SCORECARD_RANKING);
        assertEquals(2, stages.size());
        assertEquals("proposal", stages.get(0).key());
        assertEquals("scorecard", stages.get(1).key());
    }

    @Test
    void testNullStrategy() {
        List<CollaborationStage> stages = service.stagesFor(null);
        assertEquals(1, stages.size());
        assertEquals("summary", stages.get(0).key());
    }
}
