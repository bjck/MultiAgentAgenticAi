import { describe, it, expect } from 'vitest';

describe('Basic Frontend Test', () => {
    it('should pass', () => {
        expect(1 + 1).toBe(2);
    });

    it('should have access to environment', () => {
        expect(true).toBe(true);
    });
});
