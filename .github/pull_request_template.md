## Summary of Changes
<!-- Provide a concise description of what this PR introduces, fixes, or refactors. -->

## Related Issues
<!-- Link related issues, e.g. Fixes #12, Closes #34 -->

## Type of Change
- [ ] 🐛 Bug fix (non-breaking change fixing an issue)
- [ ] ✨ New feature (non-breaking change adding functionality)
- [ ] ♻️ Refactoring / Architectural cleanup
- [ ] ⚡ Performance improvement
- [ ] 📝 Documentation update
- [ ] 🧪 Tests expansion

## Quality & Architecture Checklist
- [ ] Follows Aggarly architectural guardrails (No `@PreAuthorize("isAuthenticated()")`, central security config)
- [ ] All database changes include a valid Flyway migration script (`V__*.sql`)
- [ ] Mutating / sensitive controller endpoints declare appropriate authorization
- [ ] All new/modified public endpoints return `ApiResponse<T>`
- [ ] Unit & Integration tests added or updated
- [ ] `mvn test` executed and all tests pass with zero regressions
- [ ] Swagger / OpenAPI annotations documented
