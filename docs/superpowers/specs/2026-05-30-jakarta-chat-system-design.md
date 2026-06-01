# Jakarta Chat System Design

## 1. Project Summary
Build a Jakarta EE based online chat system similar to WeChat. The system will support cross-location real-time messaging, friend management, private and group chat, chat history query and download, moments/feed sharing, profile pages, and one-to-one voice calling.

The current repository is a Maven `war` project with a Jakarta REST entry point. The implementation will extend that project rather than replace it.

## 2. Goals
- Username/password login.
- QQ email registration verification and QQ email password reset.
- Friend requests, friend groups, friend moving and deletion.
- Private chat and group chat.
- Chat history query and `.html` export.
- Moments/feed posting with text, images, and video.
- Moments visibility controls.
- Profile page with public background image.
- One-to-one voice chat.
- Public deployment with domain, Nginx, HTTPS, Tomcat, and MySQL.

## 3. Functional Scope

### 3.1 Authentication
- Register with username, password, QQ email, and email verification code.
- Login with username and password.
- Password reset through QQ email verification.
- Passwords stored with hashing, not plaintext.
- Session-based login state for the first release.

### 3.2 Friend Management
- Search users by username or email.
- Send friend requests and verification messages.
- Accept or reject requests.
- Re-send verification after rejection or timeout.
- Maintain friend groups.
- Move friends between groups.
- Delete friends.

Friend deletion rule:
- The deleting user no longer sees the private chat history.
- The deleted user still sees the shared chat history.
- Private chatting requires a valid friendship again.

### 3.3 Chat Management
- One-to-one private chat.
- Group chat.
- Text messages and image messages only in the first version.
- Real-time transport through Jakarta WebSocket.
- Historical query by keyword and time range.
- Export chat history as `.html`.

### 3.4 Moments / Feed
- Post text plus media.
- Up to 6 images per moment.
- Up to 1 video per moment.
- Like and comment support.
- View only friends' moments and own moments, subject to visibility rules.
- Delete own moments.

Visibility rules:
- Public to all friends.
- Visible only to self.
- Visible to selected friends or groups.
- Hidden from selected friends or groups.

### 3.5 User Profile
- Avatar.
- Nickname.
- Username.
- QQ email.
- Signature.
- Public background image.
- Public profile background visible to everyone, including non-friends.

### 3.6 Voice Chat
- One-to-one private voice calls only.
- WebRTC for media transport.
- WebSocket for signaling.
- Call states: ringing, accepted, rejected, busy, ended, missed.
- Store call records in the database.

### 3.7 Media Upload
Shared upload service for:
- Chat images.
- Moment images.
- Moment video.
- Avatar.
- Profile background.

Limits:
- Chat image: max 5 MB.
- Moment image: max 5 MB.
- Moment video: max 50 MB.
- Avatar: max 2 MB.
- Background image: max 5 MB.

Storage:
- Local server file system.
- Database stores metadata and file URLs only.

## 4. Non-Goals
- No group voice chat in the first version.
- No file message type in chat for the first version.
- No third-party object storage in the first version.
- No mobile app client in the first version.
- No payment, billing, or analytics module.

## 5. Architecture

### 5.1 Backend Stack
- Jakarta REST for HTTP APIs.
- Jakarta WebSocket for live chat and voice signaling.
- Jakarta Servlet for session support and file access.
- JDBC with DAO pattern for persistence.
- MySQL database.
- Maven WAR packaging.

### 5.2 Code Structure
Recommended package layout:

```text
com.example.chat
├── auth
├── common
├── config
├── friend
├── media
├── moment
├── chat
├── user
├── websocket
└── model
```

Each feature module should be split into:
- resource/controller
- service
- dao/repository
- dto
- entity

## 6. Data Model
Core tables:
- `users`
- `email_verification_codes`
- `password_reset_codes`
- `friend_groups`
- `friend_requests`
- `friendships`
- `conversations`
- `messages`
- `message_visibility`
- `chat_groups`
- `chat_group_members`
- `media_files`
- `moments`
- `moment_media`
- `moment_visibility_rules`
- `moment_likes`
- `moment_comments`
- `voice_call_sessions`

## 7. API Outline
Main endpoint groups:
- `/api/auth`
- `/api/users`
- `/api/friend-requests`
- `/api/friends`
- `/api/friend-groups`
- `/api/chat`
- `/api/chat-groups`
- `/api/moments`
- `/api/media`
- `/ws/chat`
- `/ws/voice`

Suggested HTTP operations:
- register, login, logout, me
- search users
- create/accept/reject friend request
- list friends
- manage friend groups
- fetch private and group history
- export history to HTML
- create/list/delete moments
- like/comment moments
- upload media

## 8. Frontend Direction
Frontend stack:
- HTML
- CSS
- Vanilla JavaScript
- REST API calls
- WebSocket connections

Page list:
- Login page
- Register page
- Main chat page
- Friend management page
- Group chat management page
- Moments feed page
- Profile page
- Chat history page

Login page visual direction:
- Split-screen layout.
- Left side uses a more refined peeking-character illustration.
- Right side uses a clean login form.
- The illustration will support subtle motion:
  - eyes tracking the pointer
  - blinking
  - slight body tilt
  - password-focus turn/peek effect

## 9. Deployment Plan
Production deployment target:
- Domain name.
- Nginx as reverse proxy.
- HTTPS certificate enabled.
- Tomcat 10.1+ for Jakarta EE WAR deployment.
- MySQL on the server or on a managed database host.

Deployment flow:
1. Build WAR with Maven.
2. Deploy to Tomcat.
3. Put Nginx in front of Tomcat.
4. Enable HTTPS for browser security and WebRTC compatibility.
5. Expose file uploads through the application or mapped static paths.

## 10. Initial Implementation Order
1. Authentication.
2. User profile.
3. Friend management.
4. Private chat.
5. Group chat.
6. Chat history export.
7. Moments and media uploads.
8. Voice chat signaling and WebRTC integration.
9. Deployment hardening.

## 11. UI Reference
The login illustration direction is based on the provided peeking-character reference, but reworked into a higher-end and more polished visual language:
- more complex character forms
- smoother materials
- cleaner composition
- more premium and mature tone

