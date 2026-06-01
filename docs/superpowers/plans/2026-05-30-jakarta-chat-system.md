# Jakarta Chat System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Jakarta EE / JavaWeb online chat system with account auth, friends, private and group chat, chat history export, moments, profile pages, media uploads, one-to-one voice signaling, and a refined light frontend.

**Architecture:** The app remains a Maven WAR and is reorganized into a focused `com.example.chat` package. REST resources handle HTTP workflows, Jakarta WebSocket endpoints handle realtime chat and WebRTC signaling, DAO classes isolate JDBC/MySQL persistence, and static HTML/CSS/vanilla JS pages provide the client UI.

**Tech Stack:** Java 17, Jakarta REST, Jakarta Servlet, Jakarta WebSocket, Jersey, MySQL, HikariCP, JDBC DAOs, Jakarta Mail for QQ SMTP, HTML/CSS/vanilla JavaScript, WebRTC browser APIs.

---

## File Structure

Create or modify these main areas:

- `pom.xml`: Java release, Jersey, WebSocket, MySQL, HikariCP, BCrypt, Jakarta Mail, test dependencies.
- `src/main/java/com/example/chat/ChatApplication.java`: REST application entry point.
- `src/main/java/com/example/chat/common/*`: API responses, exceptions, validation, session helpers, password hashing, HTML export helpers.
- `src/main/java/com/example/chat/config/*`: application properties and database connection factory.
- `src/main/java/com/example/chat/model/*`: compact entity records/classes for users, friends, messages, groups, moments, media, calls.
- `src/main/java/com/example/chat/auth/*`: registration, login, logout, QQ email verification, password reset.
- `src/main/java/com/example/chat/user/*`: profile and public homepage APIs.
- `src/main/java/com/example/chat/friend/*`: friend requests, groups, moving, deletion visibility.
- `src/main/java/com/example/chat/chat/*`: conversations, private messages, group messages, history query, HTML export.
- `src/main/java/com/example/chat/media/*`: multipart upload and local media metadata.
- `src/main/java/com/example/chat/moment/*`: moments, media attachments, visibility rules, likes, comments.
- `src/main/java/com/example/chat/voice/*`: call records and one-to-one call state.
- `src/main/java/com/example/chat/websocket/*`: chat endpoint, voice signaling endpoint, session registry, HTTP session configurator.
- `src/main/resources/app.properties.example`: local configuration template.
- `src/main/resources/schema.sql`: MySQL schema.
- `src/main/webapp/index.html`: login/register/password reset page with peeking-character animation.
- `src/main/webapp/app.html`: main chat shell.
- `src/main/webapp/assets/css/app.css`: refined light theme.
- `src/main/webapp/assets/js/*.js`: API client, auth, friends, chat, moments, profile, voice, UI helpers.
- `src/test/java/com/example/chat/*`: focused unit tests for password hashing, validation, HTML export, and visibility rules.
- `docs/deployment.md`: domain, Nginx, HTTPS, Tomcat, MySQL deployment guide.

Remove the generated demo classes after replacement:

- `src/main/java/com/example/v1_2026_5_30/HelloApplication.java`
- `src/main/java/com/example/v1_2026_5_30/HelloResource.java`

---

## Task 1: Project Foundation

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/example/chat/ChatApplication.java`
- Create: `src/main/java/com/example/chat/common/ApiResponse.java`
- Create: `src/main/java/com/example/chat/common/AppException.java`
- Create: `src/main/java/com/example/chat/common/SessionKeys.java`
- Create: `src/main/java/com/example/chat/common/PasswordHasher.java`
- Create: `src/main/java/com/example/chat/config/AppConfig.java`
- Create: `src/main/java/com/example/chat/config/Database.java`
- Create: `src/main/resources/app.properties.example`
- Delete: `src/main/java/com/example/v1_2026_5_30/HelloApplication.java`
- Delete: `src/main/java/com/example/v1_2026_5_30/HelloResource.java`
- Test: `src/test/java/com/example/chat/common/PasswordHasherTest.java`

- [ ] **Step 1: Add foundation dependencies**

  Update `pom.xml` to compile for Java 17 and include the runtime libraries used by the app:

  ```xml
  <properties>
      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
      <maven.compiler.release>17</maven.compiler.release>
      <junit.version>5.13.2</junit.version>
  </properties>
  ```

  Add dependencies for `jakarta.websocket-api`, `mysql-connector-j`, `HikariCP`, `jbcrypt`, `jakarta.mail`, `jersey-media-multipart`, and `junit-jupiter`.

- [ ] **Step 2: Replace the demo REST application**

  Create `ChatApplication.java`:

  ```java
  package com.example.chat;

  import jakarta.ws.rs.ApplicationPath;
  import jakarta.ws.rs.core.Application;

  @ApplicationPath("/api")
  public class ChatApplication extends Application {
  }
  ```

- [ ] **Step 3: Add common API and security primitives**

  Add `ApiResponse<T>` with `ok`, `fail`, and `data` fields, `AppException` with HTTP status, `SessionKeys.USER_ID`, and `PasswordHasher` wrapping BCrypt.

- [ ] **Step 4: Write password hashing test**

  ```java
  @Test
  void hashDoesNotExposePlainTextAndCanVerify() {
      String hash = PasswordHasher.hash("Secret123!");
      assertNotEquals("Secret123!", hash);
      assertTrue(PasswordHasher.verify("Secret123!", hash));
      assertFalse(PasswordHasher.verify("wrong", hash));
  }
  ```

  Run: `mvn -q -Dtest=PasswordHasherTest test`
  Expected: PASS.

- [ ] **Step 5: Commit foundation**

  ```bash
  git add pom.xml src/main/java src/test/java src/main/resources
  git commit -m "chore: add jakarta chat foundation"
  ```

## Task 2: Database Schema

**Files:**
- Create: `src/main/resources/schema.sql`
- Create: `src/main/java/com/example/chat/model/User.java`
- Create: `src/main/java/com/example/chat/model/Profile.java`
- Create: `src/main/java/com/example/chat/model/FriendRequest.java`
- Create: `src/main/java/com/example/chat/model/Message.java`
- Create: `src/main/java/com/example/chat/model/Moment.java`
- Create: `src/test/java/com/example/chat/config/SchemaSmokeTest.java`

- [ ] **Step 1: Create schema**

  Define tables for `users`, `email_verification_codes`, `password_reset_codes`, `friend_groups`, `friend_requests`, `friendships`, `conversations`, `messages`, `message_visibility`, `chat_groups`, `chat_group_members`, `media_files`, `moments`, `moment_media`, `moment_visibility_rules`, `moment_likes`, `moment_comments`, and `voice_call_sessions`.

  Use `deleted_for_owner` rows in `message_visibility` so deleting a friend hides history only for the deleting user.

- [ ] **Step 2: Add compact model classes**

  Use Java records for simple read models, for example:

  ```java
  public record User(long id, String username, String qqEmail, String nickname, String avatarUrl) {
  }
  ```

- [ ] **Step 3: Add schema smoke test**

  Load `schema.sql` as a resource and assert it contains the critical table names and the `message_visibility` table.

  Run: `mvn -q -Dtest=SchemaSmokeTest test`
  Expected: PASS.

- [ ] **Step 4: Commit schema**

  ```bash
  git add src/main/resources/schema.sql src/main/java/com/example/chat/model src/test/java/com/example/chat/config
  git commit -m "feat: add chat database schema"
  ```

## Task 3: Authentication

**Files:**
- Create: `src/main/java/com/example/chat/auth/AuthResource.java`
- Create: `src/main/java/com/example/chat/auth/AuthService.java`
- Create: `src/main/java/com/example/chat/auth/AuthDao.java`
- Create: `src/main/java/com/example/chat/auth/EmailCodeService.java`
- Create: `src/main/java/com/example/chat/auth/QqMailSender.java`
- Create: `src/main/java/com/example/chat/auth/dto/*.java`
- Test: `src/test/java/com/example/chat/auth/AuthServiceTest.java`

- [ ] **Step 1: Implement auth DTOs**

  Add requests for register, login, email code, password reset code, and password reset. Validate username length, password length, and QQ email suffix `@qq.com`.

- [ ] **Step 2: Implement QQ email code flow**

  Generate six-digit numeric codes, store hashed or plain short-lived verification code rows with purpose `REGISTER` or `RESET_PASSWORD`, and send through QQ SMTP when SMTP config exists. In development mode, log the code to console.

- [ ] **Step 3: Implement register/login/logout/me**

  `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, and `GET /api/auth/me` use `HttpSession`.

- [ ] **Step 4: Implement reset password**

  `POST /api/auth/password-reset/code` sends a QQ code and `POST /api/auth/password-reset` updates the BCrypt password hash.

- [ ] **Step 5: Test validation and password flow**

  Use fake DAO and fake mail sender in `AuthServiceTest` to verify invalid email is rejected and login succeeds after register.

- [ ] **Step 6: Commit authentication**

  ```bash
  git add src/main/java/com/example/chat/auth src/test/java/com/example/chat/auth
  git commit -m "feat: add qq email authentication"
  ```

## Task 4: Profile And Media Uploads

**Files:**
- Create: `src/main/java/com/example/chat/user/UserResource.java`
- Create: `src/main/java/com/example/chat/user/UserService.java`
- Create: `src/main/java/com/example/chat/user/UserDao.java`
- Create: `src/main/java/com/example/chat/media/MediaResource.java`
- Create: `src/main/java/com/example/chat/media/MediaService.java`
- Create: `src/main/java/com/example/chat/media/MediaDao.java`
- Create: `src/main/java/com/example/chat/media/UploadKind.java`
- Test: `src/test/java/com/example/chat/media/MediaServiceTest.java`

- [ ] **Step 1: Implement media upload limits**

  Enforce these limits: chat image 5 MB, moment image 5 MB, moment video 50 MB, avatar 2 MB, background 5 MB.

- [ ] **Step 2: Implement local file storage**

  Save uploads under configured `upload.root`, return URLs under `/uploads/...`, and insert metadata into `media_files`.

- [ ] **Step 3: Implement profile APIs**

  Add `GET /api/users/me`, `PUT /api/users/me`, `GET /api/users/{id}/profile`, and `GET /api/users/search?q=...`.

- [ ] **Step 4: Keep profile background public**

  Public profile lookup returns nickname, username, signature, avatar, and background image even when users are not friends.

- [ ] **Step 5: Commit profile and media**

  ```bash
  git add src/main/java/com/example/chat/user src/main/java/com/example/chat/media src/test/java/com/example/chat/media
  git commit -m "feat: add profile and media uploads"
  ```

## Task 5: Friend Management

**Files:**
- Create: `src/main/java/com/example/chat/friend/FriendResource.java`
- Create: `src/main/java/com/example/chat/friend/FriendService.java`
- Create: `src/main/java/com/example/chat/friend/FriendDao.java`
- Create: `src/main/java/com/example/chat/friend/dto/*.java`
- Test: `src/test/java/com/example/chat/friend/FriendServiceTest.java`

- [ ] **Step 1: Implement friend groups**

  Provide create, rename, list, delete group, and move friend operations. Create default group `My Friends` for new users.

- [ ] **Step 2: Implement friend requests**

  Provide send, resend, list received, list sent, accept, and reject operations. Store verification message and current status.

- [ ] **Step 3: Implement friendship deletion rule**

  When user A deletes user B, set the friendship inactive for A and add visibility rows that hide private conversation history from A only. User B keeps seeing old history.

- [ ] **Step 4: Test deletion visibility**

  Verify service calls `hideConversationForUser(deletingUserId, conversationId)` and does not call the same operation for the deleted user.

- [ ] **Step 5: Commit friends**

  ```bash
  git add src/main/java/com/example/chat/friend src/test/java/com/example/chat/friend
  git commit -m "feat: add friend management"
  ```

## Task 6: Chat And History Export

**Files:**
- Create: `src/main/java/com/example/chat/chat/ChatResource.java`
- Create: `src/main/java/com/example/chat/chat/ChatService.java`
- Create: `src/main/java/com/example/chat/chat/ChatDao.java`
- Create: `src/main/java/com/example/chat/chat/HtmlHistoryExporter.java`
- Create: `src/main/java/com/example/chat/websocket/ChatEndpoint.java`
- Create: `src/main/java/com/example/chat/websocket/SocketRegistry.java`
- Create: `src/main/java/com/example/chat/websocket/HttpSessionConfigurator.java`
- Test: `src/test/java/com/example/chat/chat/HtmlHistoryExporterTest.java`

- [ ] **Step 1: Implement private and group conversation APIs**

  Add conversation list, history query by keyword/time range, and group creation/member APIs.

- [ ] **Step 2: Implement text and image messages**

  Persist message sender, conversation, message type `TEXT` or `IMAGE`, content, media id, and sent time.

- [ ] **Step 3: Implement WebSocket chat delivery**

  `@ServerEndpoint("/ws/chat")` reads logged-in user id from HTTP session, receives JSON messages, persists them, and pushes JSON to online recipients.

- [ ] **Step 4: Implement `.html` history export**

  Escape HTML special characters, render messages in time order, include sender names, image thumbnails, and export with `Content-Disposition: attachment`.

- [ ] **Step 5: Test HTML escaping**

  Assert exported content turns `<script>` into escaped text and includes the expected `.chat-message` markup.

- [ ] **Step 6: Commit chat**

  ```bash
  git add src/main/java/com/example/chat/chat src/main/java/com/example/chat/websocket src/test/java/com/example/chat/chat
  git commit -m "feat: add realtime chat and history export"
  ```

## Task 7: Moments

**Files:**
- Create: `src/main/java/com/example/chat/moment/MomentResource.java`
- Create: `src/main/java/com/example/chat/moment/MomentService.java`
- Create: `src/main/java/com/example/chat/moment/MomentDao.java`
- Create: `src/main/java/com/example/chat/moment/VisibilityPolicy.java`
- Create: `src/main/java/com/example/chat/moment/dto/*.java`
- Test: `src/test/java/com/example/chat/moment/VisibilityPolicyTest.java`

- [ ] **Step 1: Implement moment creation**

  Allow text plus either up to six images or one video. Reject mixed image/video payloads.

- [ ] **Step 2: Implement visibility rules**

  Support all friends, only self, selected friends/groups, and excluded friends/groups.

- [ ] **Step 3: Implement feed, likes, comments, delete**

  Feed returns own moments plus visible friend moments. Only the author can delete a moment.

- [ ] **Step 4: Test visibility policy**

  Cover all friends, only self, selected groups, and excluded friend cases.

- [ ] **Step 5: Commit moments**

  ```bash
  git add src/main/java/com/example/chat/moment src/test/java/com/example/chat/moment
  git commit -m "feat: add moments feed"
  ```

## Task 8: One-To-One Voice Calling

**Files:**
- Create: `src/main/java/com/example/chat/voice/VoiceResource.java`
- Create: `src/main/java/com/example/chat/voice/VoiceService.java`
- Create: `src/main/java/com/example/chat/voice/VoiceDao.java`
- Create: `src/main/java/com/example/chat/websocket/VoiceEndpoint.java`
- Test: `src/test/java/com/example/chat/voice/VoiceServiceTest.java`

- [ ] **Step 1: Implement call records**

  Track caller, callee, status, started time, accepted time, ended time, and duration.

- [ ] **Step 2: Implement WebRTC signaling**

  `@ServerEndpoint("/ws/voice")` relays `offer`, `answer`, `ice-candidate`, `reject`, `busy`, and `end` messages only between the caller and callee.

- [ ] **Step 3: Enforce one-to-one busy state**

  If either user already has a ringing or active call, return `busy`.

- [ ] **Step 4: Test busy state**

  Verify a second call to a busy user is rejected with `BUSY`.

- [ ] **Step 5: Commit voice**

  ```bash
  git add src/main/java/com/example/chat/voice src/main/java/com/example/chat/websocket src/test/java/com/example/chat/voice
  git commit -m "feat: add one to one voice signaling"
  ```

## Task 9: Refined Light Frontend

**Files:**
- Create: `src/main/webapp/index.html`
- Create: `src/main/webapp/app.html`
- Create: `src/main/webapp/assets/css/app.css`
- Create: `src/main/webapp/assets/js/api.js`
- Create: `src/main/webapp/assets/js/auth.js`
- Create: `src/main/webapp/assets/js/app.js`
- Create: `src/main/webapp/assets/js/friends.js`
- Create: `src/main/webapp/assets/js/chat.js`
- Create: `src/main/webapp/assets/js/moments.js`
- Create: `src/main/webapp/assets/js/profile.js`
- Create: `src/main/webapp/assets/js/voice.js`

- [ ] **Step 1: Build login/register page**

  Create a premium light login page with a peeking character. The animation includes pointer-tracking eyes, random blinking, subtle body tilt, and password-focus turn/peek behavior.

- [ ] **Step 2: Build main app shell**

  Use a light, elegant interface with soft neutrals, gentle blue/green accents, crisp cards, and dense but calm chat layout.

- [ ] **Step 3: Wire auth and session state**

  `api.js` wraps `fetch` with JSON handling, error toasts, and credential cookies. `auth.js` controls login, register, QQ code, and reset forms.

- [ ] **Step 4: Wire friends, chat, moments, profile, voice**

  Each module owns its view rendering and API calls. `chat.js` opens `/ws/chat`; `voice.js` opens `/ws/voice` and uses `navigator.mediaDevices.getUserMedia({ audio: true })`.

- [ ] **Step 5: Responsive verification**

  Verify the login page and main app at desktop and mobile widths. Text must not overlap, buttons must keep stable dimensions, and the chat panel must remain usable.

- [ ] **Step 6: Commit frontend**

  ```bash
  git add src/main/webapp
  git commit -m "feat: add refined chat frontend"
  ```

## Task 10: Deployment And Final Verification

**Files:**
- Create: `docs/deployment.md`
- Modify: `src/main/resources/app.properties.example`

- [ ] **Step 1: Write deployment guide**

  Document MySQL initialization, QQ SMTP config, upload directory config, Maven WAR build, Tomcat deployment, Nginx reverse proxy, HTTPS certificate, WebSocket proxy headers, and WebRTC HTTPS requirement.

- [ ] **Step 2: Add production config template**

  Include `db.url`, `db.username`, `db.password`, `upload.root`, `public.baseUrl`, `qq.smtp.username`, `qq.smtp.authCode`, and `mail.devMode`.

- [ ] **Step 3: Run full verification**

  Run:

  ```bash
  mvn test
  mvn package
  ```

  Expected: tests pass and a WAR file appears under `target/`.

- [ ] **Step 4: Start local verification server**

  If Tomcat is available locally, deploy the WAR and open the app. Otherwise, verify static pages by opening `src/main/webapp/index.html` and `src/main/webapp/app.html`.

- [ ] **Step 5: Commit docs and verification fixes**

  ```bash
  git add docs src/main/resources/app.properties.example
  git commit -m "docs: add deployment guide"
  ```

---

## Self-Review

- Spec coverage: authentication, QQ email registration/reset, friend groups, deletion visibility, private chat, group chat, history query/export, moments, profile background, media upload limits, one-to-one voice, and deployment are covered.
- Scope: the project is large but sequential; each subsystem is isolated in a package and can be tested independently.
- Placeholder scan: this plan contains concrete files, commands, and acceptance checks instead of empty filler language.
- Type consistency: package root is consistently `com.example.chat`; frontend modules consistently call REST under `/api` and WebSockets under `/ws/chat` and `/ws/voice`.
