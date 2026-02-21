import { describe, it, expect } from 'vitest';

describe('FileEditDetection Logic Mock', () => {
    const EDIT_PHRASES = [
        "modify your own code",
        "edit the code",
        "change the code",
        "apply the changes",
        "make the following changes",
        "implement this"
    ];

    const EDIT_VERBS = [
        "modify", "change", "update", "fix", "add", "implement", "remove",
        "delete", "refactor", "rename", "create", "build", "wire", "adjust", "edit", "patch"
    ];

    const EDIT_ARTIFACTS = [
        "code", "repo", "repository", "project", "app", "application", "api",
        "endpoint", "controller", "service", "ui", "frontend", "backend", "css",
        "html", "javascript", "js", "java", "spring", "config", "yaml", "yml",
        "file", "files", "tests", "database", "schema", "table"
    ];

    const DIRECTIVE_PHRASES = [
        "please ",
        "can you",
        "could you",
        "i want",
        "i need",
        "i'd like",
        "we need",
        "we want"
    ];

    function requiresFileEdits(userMessage) {
        if (!userMessage || !userMessage.trim()) {
            return false;
        }
        const text = userMessage.toLowerCase();
        if (EDIT_PHRASES.some(phrase => text.includes(phrase))) {
            return true;
        }

        const hasVerb = EDIT_VERBS.some(verb => text.includes(verb));
        const hasArtifact = EDIT_ARTIFACTS.some(artifact => text.includes(artifact));
        if (!hasVerb || !hasArtifact) {
            return false;
        }

        const trimmed = text.trim();
        const startsWithVerb = EDIT_VERBS.some(verb => trimmed.startsWith(verb + " "));
        const hasDirective = DIRECTIVE_PHRASES.some(phrase => text.includes(phrase));
        
        return startsWithVerb || hasDirective;
    }

    it('should detect file edits correctly', () => {
        expect(requiresFileEdits("Please update the controller")).toBe(true);
        expect(requiresFileEdits("fix the java file")).toBe(true);
        expect(requiresFileEdits("I want to add a test")).toBe(false);
        expect(requiresFileEdits("What is the time?")).toBe(false);
        expect(requiresFileEdits("")).toBe(false);
    });
});
