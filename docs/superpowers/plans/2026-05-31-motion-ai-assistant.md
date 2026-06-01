# Motion And AI Assistant Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add polished lightweight UI motion across the chat dashboard and add a group-chat `@千问小助手` integration backed by local Ollama.

**Architecture:** UI motion stays in CSS plus small DOM state hooks in existing frontend modules. The AI assistant is isolated in `com.example.chat.ai`; `ChatService` detects group messages mentioning the assistant, stores the user message, asks Ollama, and stores the assistant reply as a normal chat message from the admin/assistant account.

**Tech Stack:** Jakarta EE, Jersey, WebSocket, Java 17 `HttpClient`, MySQL, CSS animations/transitions, local Ollama HTTP API.

---

### Task 1: AI Mention Parsing And Prompt Builder

**Files:**
- Create: `src/main/java/com/example/chat/ai/AiAssistantService.java`
- Test: `src/test/java/com/example/chat/ai/AiAssistantServiceTest.java`

- [ ] Write tests for detecting `@千问小助手` mentions, extracting the prompt, and building a chat-summary prompt from recent messages.
- [ ] Implement `AiAssistantService.isMentioned(String content)`.
- [ ] Implement `AiAssistantService.extractUserPrompt(String content)`.
- [ ] Implement `AiAssistantService.buildPrompt(String userPrompt, List<ChatMessage> history)`.
- [ ] Run `.\mvnw.cmd -q -Dtest=AiAssistantServiceTest test`.

### Task 2: Ollama Client

**Files:**
- Create: `src/main/java/com/example/chat/ai/OllamaClient.java`
- Modify: `src/main/resources/app.properties`
- Modify: `src/main/resources/app.properties.example`
- Test: `src/test/java/com/example/chat/ai/OllamaClientTest.java`

- [ ] Add config keys: `ollama.baseUrl`, `ollama.model`, `ai.assistantName`, `ai.assistantUserId`.
- [ ] Write tests for request JSON generation and graceful fallback when Ollama is unavailable.
- [ ] Implement Java 17 `HttpClient` POST to `/api/chat` with `stream=false`.
- [ ] Parse the assistant text from Ollama response JSON.
- [ ] Run `.\mvnw.cmd -q -Dtest=OllamaClientTest test`.

### Task 3: ChatService AI Reply Flow

**Files:**
- Modify: `src/main/java/com/example/chat/chat/ChatService.java`
- Modify: `src/main/java/com/example/chat/chat/ChatDao.java`
- Test: `src/test/java/com/example/chat/chat/ChatServiceAiAssistantTest.java`

- [ ] Add DAO method to load conversation type and recent visible messages.
- [ ] Add DAO method to save an assistant message using configured assistant user id.
- [ ] After a group message is saved, if it mentions `@千问小助手`, call `AiAssistantService`.
- [ ] Store the AI reply as type `AI` in `messages`.
- [ ] Keep private chats from triggering AI.
- [ ] Run `.\mvnw.cmd -q -Dtest=ChatServiceAiAssistantTest test`.

### Task 4: Frontend AI And Motion States

**Files:**
- Modify: `src/main/webapp/app.html`
- Modify: `src/main/webapp/assets/js/chat.js`
- Modify: `src/main/webapp/assets/js/app.js`
- Modify: `src/main/webapp/assets/css/dashboard.css`
- Test: `src/test/java/com/example/chat/web/WebAssetContractTest.java`

- [ ] Add AI helper hint near the composer for group chats.
- [ ] Render `AI` messages with a distinct assistant bubble.
- [ ] Add `thinking` visual state when a sent group message mentions the assistant.
- [ ] Add page-switch, conversation-list, message-entry, upload-preview, toast, and quick-action motion classes.
- [ ] Run `node --check` for all frontend JS.
- [ ] Run `.\mvnw.cmd -q -Dtest=WebAssetContractTest test`.

### Task 5: Dynamic Left Illustration

**Files:**
- Modify: `src/main/webapp/app.html`
- Modify: `src/main/webapp/assets/css/dashboard.css`

- [ ] Replace the static left-bottom scene with animated sun, clouds, layered mountains, water, and boat.
- [ ] Keep all animation on `transform`, `opacity`, or background-position.
- [ ] Add `prefers-reduced-motion` handling to disable nonessential animation.
- [ ] Verify the scene does not overlap nav/user controls at desktop and mobile widths.

### Task 6: Documentation And Verification

**Files:**
- Modify: `docs/deploy-server.md`

- [ ] Add Ollama startup instructions: `ollama serve` and `ollama pull qwen2.5:7b`.
- [ ] Add config notes for model name and assistant user id.
- [ ] Run `.\mvnw.cmd -q test`.
- [ ] Run `.\mvnw.cmd -q clean package`.
- [ ] Deploy the WAR to Tomcat and verify `alice / 123456` can login.
- [ ] In browser, send `@千问小助手 总结这个群聊` in a group conversation and verify the assistant reply appears.
