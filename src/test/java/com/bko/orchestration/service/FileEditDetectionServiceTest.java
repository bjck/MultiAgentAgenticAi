package com.bko.orchestration.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FileEditDetectionServiceTest {

    private final FileEditDetectionService service = new FileEditDetectionService();

    @Test
    void testRequiresFileEdits_EmptyInput() {
        assertFalse(service.requiresFileEdits(null));
        assertFalse(service.requiresFileEdits(""));
        assertFalse(service.requiresFileEdits("   "));
    }

    @Test
    void testRequiresFileEdits_ExplicitPhrases() {
        assertTrue(service.requiresFileEdits("Please modify your own code."));
        assertTrue(service.requiresFileEdits("edit the code to fix the bug"));
        assertTrue(service.requiresFileEdits("I want you to change the code."));
        assertTrue(service.requiresFileEdits("apply the changes now"));
        assertTrue(service.requiresFileEdits("make the following changes: ..."));
        assertTrue(service.requiresFileEdits("implement this new feature"));
    }

    @Test
    void testRequiresFileEdits_VerbAndArtifactDirective() {
        assertTrue(service.requiresFileEdits("I want to update the controller."));
        assertTrue(service.requiresFileEdits("Can you fix the java file?"));
        assertFalse(service.requiresFileEdits("Please add a new test."));
    }

    @Test
    void testRequiresFileEdits_StartsWithVerbAndHasArtifact() {
        assertTrue(service.requiresFileEdits("update the README.md"));
        assertTrue(service.requiresFileEdits("fix the bug in ChatController"));
    }

    @Test
    void testRequiresFileEdits_NegativeCases() {
        assertFalse(service.requiresFileEdits("Tell me a story about a cat."));
        assertFalse(service.requiresFileEdits("What is the current status?"));
        assertFalse(service.requiresFileEdits("update is not a directive here"));
        assertFalse(service.requiresFileEdits("I want coffee")); // Directive but no verb/artifact combo
    }

    @Test
    void testAppendFileEditInstruction() {
        String baseInstruction = "\nApply the requested changes directly to repository files using MCP filesystem tools (read and write).\n";
        
        // Test with canEdit = true
        String result = service.appendFileEditInstruction("My expected output", true);
        assertEquals("My expected output" + baseInstruction, result);

        // Test with canEdit = false
        result = service.appendFileEditInstruction("My expected output", false);
        assertEquals("My expected output", result);

        // Test with empty expectedOutput
        result = service.appendFileEditInstruction("", true);
        assertEquals("Provide concise, actionable output." + baseInstruction, result);
    }
}
