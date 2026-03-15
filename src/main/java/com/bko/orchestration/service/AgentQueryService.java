package com.bko.orchestration.service;

import com.bko.api.AgentController.AgentQueryRequest;
import com.bko.api.AgentController.AgentQueryResponse;
import com.bko.entity.ExternalDocument;
import com.bko.entity.ScheduledAgent;
import com.bko.orchestration.OrchestratorService;
import com.bko.repository.ExternalDocumentRepository;
import com.bko.repository.ScheduledAgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentQueryService {

    private final ScheduledAgentRepository agentRepository;
    private final ExternalDocumentRepository externalDocumentRepository;
    private final OrchestratorService orchestratorService;

    @Transactional
    public AgentQueryResponse queryAgent(UUID agentId, AgentQueryRequest request) {
        ScheduledAgent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agent id: " + agentId));

        OffsetDateTime from = request.from();
        OffsetDateTime to = request.to();
        int limit = request.limit() != null && request.limit() > 0 ? request.limit() : 100;
        org.springframework.data.domain.Pageable page = org.springframework.data.domain.PageRequest.of(0, limit);

        List<ExternalDocument> docs;
        if (from != null && to != null) {
            docs = externalDocumentRepository
                    .findBySourcePublishedAtBetweenOrderBySourcePublishedAtDescCreatedAtDesc(from, to, page);
        } else if (from != null) {
            docs = externalDocumentRepository
                    .findBySourcePublishedAtGreaterThanEqualOrderBySourcePublishedAtDescCreatedAtDesc(from, page);
        } else if (to != null) {
            docs = externalDocumentRepository
                    .findBySourcePublishedAtLessThanEqualOrderBySourcePublishedAtDescCreatedAtDesc(to, page);
        } else {
            docs = externalDocumentRepository
                    .findByOrderBySourcePublishedAtDescCreatedAtDesc(page);
        }

        String context = buildContext(docs);
        String userMessage = """
                You are summarizing stored documents for an autonomous agent.
                Agent name: %s
                Agent objective: %s

                User question:
                %s

                Use only the provided document snippets as your knowledge base.
                """.formatted(
                agent.getName(),
                agent.getObjectivePrompt(),
                request.query()
        );

        String combinedPrompt = userMessage + "\n\nDocuments:\n" + context;
        var result = orchestratorService.orchestrate(combinedPrompt, null, null);
        String answer = StringUtils.hasText(result.finalAnswer()) ? result.finalAnswer() : "";
        return new AgentQueryResponse(answer, docs.size());
    }

    private String buildContext(List<ExternalDocument> docs) {
        StringBuilder sb = new StringBuilder();
        for (ExternalDocument doc : docs) {
            sb.append("- Title: ").append(nullSafe(doc.getTitle())).append("\n")
                    .append("  Source: ").append(nullSafe(doc.getSource())).append(" (")
                    .append(nullSafe(doc.getSourceId())).append(")\n")
                    .append("  URL: ").append(nullSafe(doc.getUrl())).append("\n")
                    .append("  Abstract: ").append(truncate(nullSafe(doc.getAbstractText()), 2000))
                    .append("\n\n");
        }
        return sb.toString();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}

