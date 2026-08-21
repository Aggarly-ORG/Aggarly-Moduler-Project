---
trigger: always_on
description: Mandatory architectural, security, and JPA constraints for the Aggarly platform.
---

# Aggarly Architectural & Security Guardrails

## 1. Controller Security & User Resolution
- **NEVER** use `@PreAuthorize("isAuthenticated()")` on controller methods.
- **ALWAYS** enforce authentication boundaries centrally in `SecurityConfig.java` using `.anyRequest().authenticated()`, specifying explicit `permitAll()` only for public auth routes, Swagger, and WebSocket handshakes (`/ws/**`, `/ws/chat/**`).

## 2. Chat & AI Concierge Fallback Handling
- When a chat request or WebSocket STOMP payload receives `conversationId == 00000000-0000-0000-0000-000000000000` (or `null` or unassigned):
  - Automatically resolve or create the user's active AI Concierge thread (`conversationService.getOrCreateAiConciergeConversation(userId)`).
  - Return the real generated `conversationId` in the `MessageResponse` DTO and broadcast it over WebSocket/STOMP.

## 3. Database Seeding & JPA Cascade Safety
- **System Bot Identities**: Any hardcoded system bot user ID (e.g. `aaac7011-3626-460c-a47e-c94535d34c65`) must have a corresponding row in the `users` table seeded via Flyway migrations (`V26__seed_system_ai_user.sql`).
- **JPA Cascade vs. Repository Saves**: Do not use `CascadeType.ALL` on parent `@OneToMany` collections when child entities are saved through their respective repositories, to avoid duplicate insert violations on composite unique constraints (e.g. `uq_conversation_user`).

## 4. Agent Tool Assignment & Schema Injection
- **Self-Sufficient Tool Definitions**: Do not hardcode tool schemas or lists inside agent system prompts; tools register their parameter/response schemas via the `Tool<?, ?>` interface and are injected dynamically by the LLM client at runtime.
- **Complete Tool Mapping**: Every Spring `@Component Tool` must be mapped to at least one specialized agent's `SUPPORTED_TOOLS` set and corresponding `IntentCategory`.
- **Null-Safe Tool Execution**: Mutating or user-private tools must declare `requiresAuthentication() = true` and check `user != null` safely before retrieving `user.getUserId()`.

