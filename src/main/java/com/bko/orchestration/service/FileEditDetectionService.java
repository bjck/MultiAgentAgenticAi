package com.bko.orchestration.service;

import static com.bko.orchestration.OrchestrationConstants.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class FileEditDetectionService {

    public boolean requiresFileEdits(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        String text = userMessage.toLowerCase(Locale.ROOT);
        if (EDIT_PHRASES.stream().anyMatch(text::contains)) {
            return true;
        }

        boolean hasVerb = EDIT_VERBS.stream().anyMatch(text::contains);
        boolean hasArtifact = EDIT_ARTIFACTS.stream().anyMatch(text::contains);
        if (!hasVerb || !hasArtifact) {
            return false;
        }
        String trimmed = text.trim();
        boolean startsWithVerb = EDIT_VERBS.stream().anyMatch(verb -> trimmed.startsWith(verb + " "));
        boolean hasDirective = DIRECTIVE_PHRASES.stream().anyMatch(text::contains);
        return startsWithVerb || hasDirective;
    }

    public String appendFileEditInstruction(String expectedOutput, boolean canEdit) {
        String base = StringUtils.hasText(expectedOutput) ? expectedOutput.trim() : DEFAULT_EXPECTED_OUTPUT;
        if (canEdit) {
            return base + FILE_EDIT_INSTRUCTION_BASE;
        }
        return base;
    }
}
