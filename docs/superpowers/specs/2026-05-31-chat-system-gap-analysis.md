# 聊天系统完整差距分析与升级方案

## 📊 当前状态速览（对比上次审查后的改进）

| 改进项 | 状态 |
|--------|------|
| 群聊邀请系统 (邀请/接受/拒绝) | ✅ 已实现 |
| 密友标记 (close_friend) | ✅ 已实现 |
| 群聊邀请策略 (密友直加/非密友邀请) | ✅ 已实现 |
| 语音通话接受/拒绝/挂断 API | ✅ 已实现 |
| TURN 服务器可配置 | ✅ 已实现 |
| 语音通话来电弹窗 UI | ✅ 已实现 |
| BusyUserRegistry 独立提取 | ✅ 已实现 |
| 通话信令完整流程 | ✅ 已实现 |
| VoiceDao.find() | ✅ 已实现 |

---

## 🔴 第一部分：群聊管理 — 核心功能缺口（6 项）

### 对比标准：微信/Telegram/Discord 的群聊管理功能

```
功能                       微信    Telegram   Discord   我们   状态
────────────────────────────────────────────────────────────
创建群聊                    ✅      ✅         ✅        ✅    有
邀请成员                    ✅      ✅         ✅        ✅    有
接受/拒绝邀请               ✅      ✅         ✅        ✅    有
查看成员列表                ✅      ✅         ✅        ❌    缺 ⬅
修改群名                    ✅      ✅         ✅        ❌    缺 ⬅
修改群头像/描述             ✅      ✅         ✅        ❌    缺 ⬅
踢出成员                    ✅      ✅         ✅        ❌    缺 ⬅
设置管理员                  ✅      ✅         ✅        ❌    缺 ⬅
退出/离开群聊               ✅      ✅         ✅        ❌    缺 ⬅
转让群主                    ✅      ✅         ✅        ❌    缺 ⬅
解散群聊                    ✅      ✅         ✅        ❌    缺 ⬅
@所有人                     ✅      ✅         ✅        ❌    缺 ⬅
群公告                      ✅      ✅         ✅        ❌    缺 ⬅
```

---

### 缺口 1：查看群成员列表

**当前状态**：`conversation_members` 和 `chat_group_members` 表有数据，但没有 API 返回。

**新增 API**
```
GET /api/chat/groups/{conversationId}/members
```

**返回数据**
```json
{
  "ok": true,
  "data": [
    {
      "userId": 1,
      "nickname": "Alice",
      "avatarUrl": null,
      "role": "OWNER",
      "joinedAt": "2026-05-30T12:00:00"
    },
    {
      "userId": 2,
      "nickname": "Bob",
      "avatarUrl": null,
      "role": "MEMBER",
      "joinedAt": "2026-05-30T13:00:00"
    }
  ]
}
```

**后端实现**
```java
// ChatDao.java — 新增方法
public List<GroupMemberView> members(Connection c, long conversationId, long viewerId) throws SQLException {
    return Jdbc.list(c,
        "SELECT cm.user_id, u.nickname, u.avatar_url, cgm.role, cgm.created_at joined_at " +
        "FROM conversation_members cm " +
        "JOIN chat_group_members cgm ON cgm.group_id = (SELECT cg.id FROM chat_groups cg WHERE cg.conversation_id=?) AND cgm.user_id=cm.user_id " +
        "JOIN users u ON u.id=cm.user_id " +
        "WHERE cm.conversation_id=? " +
        "ORDER BY FIELD(cgm.role,'OWNER','ADMIN','MEMBER'), u.nickname",
        ps -> { ps.setLong(1, conversationId); ps.setLong(2, conversationId); },
        rs -> new GroupMemberView(rs.getLong("user_id"), rs.getString("nickname"),
            rs.getString("avatar_url"), rs.getString("role"),
            rs.getTimestamp("joined_at").toLocalDateTime()));
}

// 还需要先检查 viewerId 是否是该会话成员
```

**新增 model**
```java
// com.example.chat.model.GroupMemberView
public record GroupMemberView(
    long userId, String nickname, String avatarUrl, String role, LocalDateTime joinedAt
) {}
```

**前端 — 聊天头部点击群名 → 弹出成员列表**
```html
<!-- 群聊模式下 chat-actions 增加一个按钮 -->
<button class="icon-button" id="groupMembersButton" title="群成员">
    <i data-lucide="users"></i>
</button>
```

```javascript
// chat.js
async function showGroupMembers() {
    if (!AppState.selectedConversation || AppState.selectedConversation.type !== 'GROUP') return;
    const members = await ChatApi.get(`/chat/groups/${AppState.conversationId}/members`);
    renderMemberList(members);
}
```

---

### 缺口 2：退出/离开群聊

**新增 API**
```
POST /api/chat/groups/{conversationId}/leave
```

**后端实现**
```java
// ChatService.java
public void leaveGroup(long userId, long conversationId) {
    withConnection(c -> {
        Long groupId = chatDao.groupIdForConversation(c, conversationId);
        if (groupId == null) throw AppException.badRequest("Group not found.");
        String role = chatDao.memberRole(c, groupId, userId);
        if ("OWNER".equals(role)) {
            // 群主不能直接退出，必须先转让群主或解散群
            throw AppException.badRequest("Owner must transfer ownership or disband the group.");
        }
        chatDao.removeMember(c, conversationId, userId);
        chatDao.removeGroupMember(c, groupId, userId);
        return null;
    });
}
```

**ChatDao 新增**
```java
public String memberRole(Connection c, long groupId, long userId) throws SQLException {
    return Jdbc.one(c, "SELECT role FROM chat_group_members WHERE group_id=? AND user_id=?",
        ps -> { ps.setLong(1, groupId); ps.setLong(2, userId); },
        rs -> rs.getString("role"));
}

public void removeMember(Connection c, long conversationId, long userId) throws SQLException {
    Jdbc.update(c, "DELETE FROM conversation_members WHERE conversation_id=? AND user_id=?",
        ps -> { ps.setLong(1, conversationId); ps.setLong(2, userId); });
}

public void removeGroupMember(Connection c, long groupId, long userId) throws SQLException {
    Jdbc.update(c, "DELETE FROM chat_group_members WHERE group_id=? AND user_id=?",
        ps -> { ps.setLong(1, groupId); ps.setLong(2, userId); });
}
```

**前端**：群成员列表中对自己有"退出群聊"按钮（danger 样式）

---

### 缺口 3：踢出成员（群主/管理员权限）

**新增 API**
```
DELETE /api/chat/groups/{conversationId}/members/{userId}
```

**后端实现**
```java
// ChatService.java
public void kickMember(long operatorId, long conversationId, long targetUserId) {
    withConnection(c -> {
        Long groupId = chatDao.groupIdForConversation(c, conversationId);
        if (groupId == null) throw AppException.badRequest("Group not found.");
        String operatorRole = chatDao.memberRole(c, groupId, operatorId);
        String targetRole = chatDao.memberRole(c, groupId, targetUserId);
        if (!canManage(operatorRole, targetRole)) {
            throw AppException.badRequest("No permission to kick this member.");
        }
        chatDao.removeMember(c, conversationId, targetUserId);
        chatDao.removeGroupMember(c, groupId, targetUserId);
        return null;
    });
}

private boolean canManage(String operatorRole, String targetRole) {
    if ("OWNER".equals(operatorRole)) return !"OWNER".equals(targetRole);
    if ("ADMIN".equals(operatorRole)) return "MEMBER".equals(targetRole);
    return false;
}
```

**前端**：成员列表中非 OWNER/ADMIN 的成员行有"移除"按钮（群主和管理员可见）

---

### 缺口 4：设置管理员 / 转让群主

**新增 API**
```
PUT /api/chat/groups/{conversationId}/members/{userId}/role
body: { "role": "ADMIN" }   // 或 "OWNER"（转让群主）
```

**后端实现**
```java
// ChatService.java
public void changeRole(long operatorId, long conversationId, long targetUserId, String newRole) {
    withConnection(c -> {
        Long groupId = chatDao.groupIdForConversation(c, conversationId);
        String operatorRole = chatDao.memberRole(c, groupId, operatorId);
        Validation.require("OWNER".equals(operatorRole), "Only the group owner can manage roles.");
        if ("OWNER".equals(newRole)) {
            // 转让群主：把原群主的 role 降为 ADMIN，把目标的 role 升为 OWNER
            chatDao.updateMemberRole(c, groupId, operatorId, "ADMIN");
            chatDao.updateGroupOwner(c, groupId, targetUserId);
        }
        chatDao.updateMemberRole(c, groupId, targetUserId, newRole);
        return null;
    });
}
```

**ChatDao 新增**
```java
public void updateMemberRole(Connection c, long groupId, long userId, String role) throws SQLException {
    Jdbc.update(c, "UPDATE chat_group_members SET role=? WHERE group_id=? AND user_id=?",
        ps -> { ps.setString(1, role); ps.setLong(2, groupId); ps.setLong(3, userId); });
}

public void updateGroupOwner(Connection c, long groupId, long newOwnerId) throws SQLException {
    Jdbc.update(c, "UPDATE chat_groups SET owner_id=? WHERE id=?",
        ps -> { ps.setLong(1, newOwnerId); ps.setLong(2, groupId); });
}
```

---

### 缺口 5：修改群聊信息（名称、头像、描述）

**当前状态**：`chat_groups` 表有 `name` 但没有 `avatar_url` 和 `description`。

**Schema 变更**
```sql
ALTER TABLE chat_groups ADD COLUMN avatar_url VARCHAR(255);
ALTER TABLE chat_groups ADD COLUMN description VARCHAR(500);
ALTER TABLE chat_groups ADD COLUMN theme VARCHAR(40) DEFAULT 'default';
```

**新增 API**
```
PUT /api/chat/groups/{conversationId}
body: { "name": "新群名", "description": "群描述", "avatarUrl": "...", "theme": "cyberpunk" }
```

**后端**
```java
// ChatService.java
public void updateGroupInfo(long userId, long conversationId, GroupUpdateRequest request) {
    withConnection(c -> {
        Long groupId = chatDao.groupIdForConversation(c, conversationId);
        String role = chatDao.memberRole(c, groupId, userId);
        Validation.require("OWNER".equals(role) || "ADMIN".equals(role),
            "Only owner or admin can edit group info.");
        chatDao.updateGroupInfo(c, groupId, request);
        // 同步更新 conversations.title
        if (request.name() != null) {
            chatDao.updateConversationTitle(c, conversationId, request.name());
        }
        return null;
    });
}
```

---

### 缺口 6：群公告

**Schema 变更**
```sql
ALTER TABLE chat_groups ADD COLUMN announcement TEXT;
ALTER TABLE chat_groups ADD COLUMN announcement_updated_at TIMESTAMP NULL;
```

**新增 API**
```
GET  /api/chat/groups/{conversationId}/announcement
PUT  /api/chat/groups/{conversationId}/announcement
body: { "content": "群公告内容..." }
```

**前端**：群聊消息列表顶部展示 pinned 公告条（可折叠），只有群主/管理员能编辑

---

## 🔴 第二部分：聊天界面 — 核心功能缺口（10 项）

### 缺口 7：消息引用回复（Quote Reply）

**当前状态**：不支持。对标微信/Telegram/Discord 的回复功能。

**Schema 变更**
```sql
ALTER TABLE messages ADD COLUMN reply_to_id BIGINT NULL;
ALTER TABLE messages ADD FOREIGN KEY (reply_to_id) REFERENCES messages(id);
```

**前端交互**
```
用户右键/长按消息 → 弹出菜单 → "回复"
→ composer 上方出现被引用消息的缩略预览
→ 发送时带上 replyToId
→ 接收端消息气泡内嵌被引用消息的缩略框
```

**后端**
```java
// SendMessageRequest 新增字段
public record SendMessageRequest(
    long conversationId, String type, String content,
    Long mediaId, Integer selfDestructSeconds, Long replyToId
) {}

// ChatDao.saveMessage 新增 ps.setLong(6, replyToId)
```

**history 查询时联表取被引用消息**
```sql
SELECT m.*, u.nickname sender_name,
    rm.content reply_content, rm.sender_id reply_sender_id,
    ru.nickname reply_sender_name
FROM messages m
LEFT JOIN messages rm ON rm.id=m.reply_to_id
LEFT JOIN users ru ON ru.id=rm.sender_id
...
```

---

### 缺口 8：消息撤回/删除（Recall）

**当前状态**：只能隐藏私聊消息（`message_visibility`），不能真正撤回。

**设计**
- 撤回仅限 2 分钟内的消息
- 撤回后消息内容变成 `"XXX 撤回了一条消息"` 系统提示
- 群聊中所有人都能看到撤回提示
- 用 `messages` 表新增 `deleted` 字段，而不是真删除

**Schema 变更**
```sql
ALTER TABLE messages ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0;
```

**新增 API**
```
DELETE /api/chat/messages/{messageId}
```

**后端**
```java
// ChatService.java
public void recallMessage(long userId, long messageId) {
    withConnection(c -> {
        ChatMessage msg = chatDao.messageById(c, messageId);
        Validation.require(msg != null, "Message not found.");
        Validation.require(msg.senderId() == userId, "You can only recall your own messages.");
        long seconds = ChronoUnit.SECONDS.between(msg.sentAt(), LocalDateTime.now());
        Validation.require(seconds <= 120, "Messages can only be recalled within 2 minutes.");
        chatDao.markDeleted(c, messageId);
        return null;
    });
}
// 然后通过 WebSocket 广播 "message_recalled" 事件
```

**前端**：撤回后的消息显示为灰色系统提示

---

### 缺口 9：@提及自动完成

**当前状态**：`@千问小助手` 硬编码检测，无自动完成。

**前端实现**（纯 JS，约 120 行）
```javascript
// chat.js — composer input 事件监听
let mentionState = { active: false, query: '', startIndex: -1 };

composerInput.addEventListener('input', e => {
    const value = e.target.value;
    const cursorPos = e.target.selectionStart;
    const textBeforeCursor = value.slice(0, cursorPos);
    const atMatch = textBeforeCursor.match(/@(\S*)$/);

    if (atMatch) {
        mentionState.active = true;
        mentionState.query = atMatch[1].toLowerCase();
        mentionState.startIndex = atMatch.index;
        // 过滤群成员
        const matches = groupMembers.filter(m =>
            m.nickname.toLowerCase().includes(mentionState.query)
        );
        showMentionDropdown(matches, cursorPos);
    } else {
        mentionState.active = false;
        hideMentionDropdown();
    }
});

function showMentionDropdown(matches, cursorPos) {
    // 在输入框上方渲染一个绝对定位的列表
    // 用户点击或按回车选择 → 替换 @query 为 @nickname
}
```

**后端**：需要配合 `GET /api/chat/groups/{conversationId}/members` 获取成员列表

---
<thought>
Let me also think about what other chat interface gaps exist:

10. Message pin/bookmark - pin important messages to top of chat
11. Typing indicator - show when others are typing
12. Emoji picker - real emoji picker, not just ":)"
13. File message type - PDF, DOC, etc.
14. Message forward - forward a message to another conversation
15. Voice message (walkie-talkie) - hold to record, release to send
16. Read receipts - who has read a message
17. Chat background customization per conversation
18. Message search within conversation (they already have this, but let me check)
19. Batch delete messages / clear chat history
20. Conversation mute (mute notifications from a specific group)

Let me write about the most impactful ones.
</thought>

### 缺口 10：消息置顶（Pin Message）

**新增 API**
```
POST   /api/chat/conversations/{id}/pinned-messages/{messageId}
DELETE /api/chat/conversations/{id}/pinned-messages/{messageId}
GET    /api/chat/conversations/{id}/pinned-messages
```

**Schema**
```sql
CREATE TABLE IF NOT EXISTS pinned_messages (
    conversation_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    pinned_by BIGINT NOT NULL,
    pinned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, message_id),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (message_id) REFERENCES messages(id),
    FOREIGN KEY (pinned_by) REFERENCES users(id)
);
```

**前端**：消息列表顶部展示 pinned 消息条，点击跳转到对应消息位置

---

### 缺口 11：输入状态指示器（Typing Indicator）

**当前状态**：没有。对标微信的"对方正在输入..."。

**方案**：WebSocket 推送 typing 事件（不需要持久化）

```javascript
// chat.js — 输入框事件
let typingTimer;
composerInput.addEventListener('input', () => {
    clearTimeout(typingTimer);
    sendTypingSignal(true);
    typingTimer = setTimeout(() => sendTypingSignal(false), 3000);
});

function sendTypingSignal(typing) {
    socket.send(JSON.stringify({
        type: 'TYPING',
        conversationId: AppState.conversationId,
        typing
    }));
}
```

```java
// ChatEndpoint.onMessage 处理 TYPING 信号
// 收到 → 转发给会话内其他成员
// 信号: {"type":"TYPING","conversationId":1,"userId":2,"typing":true}
```

**前端展示**：聊天头部状态栏显示 `"Alice 正在输入..."` 或底部显示 `●●●` 动画

---

### 缺口 12：Emoji 选择器

**当前状态**：点击表情按钮只是追加 `:)` 字符串。

**方案**：内嵌一个轻量 emoji picker（可以用简单的 grid + Unicode emoji）

```javascript
// 预置 6 组常用 emoji（笑脸/手势/动物/食物/交通/符号）
const EMOJI_GROUPS = {
    faces: ['😀','😂','🤣','😍','😎','🥳','😢','😡','😱','🤔','🙄','😴'],
    gestures: ['👍','👎','👏','🙌','💪','✌️','🤝','🙏','👋','🖐️'],
    hearts: ['❤️','🧡','💛','💚','💙','💜','🖤','🤍','💔','💖','💗','💝'],
    // ...
};

function showEmojiPicker(anchorEl) {
    // 渲染一个绝对定位的 emoji grid
    // 点击 emoji → 插入到输入框光标位置
}
```

**CSS**：一个 `position: absolute` 的 grid 弹窗，每组一个 tab，点击外部关闭

---

### 缺口 13：浏览器通知（Web Notification API）

**当前状态**：用户切换到其他标签页时完全不知道有新消息。

**实现**
```javascript
// chat.js — WebSocket onmessage
Notification.requestPermission().then(perm => {
    if (perm === 'granted' && document.hidden && message.senderId !== AppState.me.id) {
        new Notification(conversationName(conv), {
            body: message.type === 'IMAGE' ? '[图片]' : message.content.slice(0, 80),
            icon: '/assets/favicon.ico',
            tag: `chat-${message.conversationId}`,
        });
    }
});
```

---

### 缺口 14：全局搜索（跨会话消息搜索）

**当前状态**：搜索只在当前会话内（`/conversations/{id}/history?q=`），不能跨会话全局搜索。

**新增 API**
```
GET /api/chat/search?q=keyword&limit=20
```

**后端**
```java
// ChatDao.java
public List<ChatMessage> searchAll(Connection c, long userId, String keyword, int limit) throws SQLException {
    String like = "%" + keyword + "%";
    return Jdbc.list(c,
        "SELECT m.*, u.nickname sender_name, c.title conversation_title, c.type conversation_type " +
        "FROM messages m JOIN users u ON u.id=m.sender_id " +
        "JOIN conversations c ON c.id=m.conversation_id " +
        "JOIN conversation_members cm ON cm.conversation_id=c.id AND cm.user_id=? " +
        "LEFT JOIN message_visibility mv ON mv.message_id=m.id AND mv.user_id=? " +
        "WHERE COALESCE(mv.hidden,0)=0 AND m.content LIKE ? " +
        "ORDER BY m.id DESC LIMIT ?",
        ps -> { ps.setLong(1, userId); ps.setLong(2, userId); ps.setString(3, like); ps.setInt(4, limit); },
        rs -> /* map to ChatMessage with conversationTitle */);
}
```

**前端**：在侧边栏搜索框按回车 → 弹出一个全局搜索结果面板，显示匹配消息 + 所在会话，点击跳转

---

### 缺口 15：会话免打扰（Mute Conversation）

**Schema 变更**
```sql
CREATE TABLE IF NOT EXISTS conversation_settings (
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    muted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (conversation_id, user_id),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**新增 API**
```
PUT /api/chat/conversations/{id}/mute   body: { "muted": true }
```

**前端**：会话列表右键 → "免打扰" → 该会话新消息不触发通知

---

### 缺口 16：消息转发

**新增 API**
```
POST /api/chat/messages/{messageId}/forward
body: { "targetConversationId": 5 }
```

**后端**
```java
// 本质上是把原消息的 content + type 复制一份新消息，sender 为转发者
// 新消息 content 前加 "📤 转发: " 前缀（或嵌套引用原消息）
```

**前端**：消息右键菜单 → "转发" → 弹窗选择目标会话 → 确认发送

---

## 🔴 第三部分：角色权限体系 — 当前缺陷

**当前 `chat_group_members.role` 只有两个值**：`OWNER` 和 `MEMBER`。

### 需要升级为三级角色

| 角色 | 权限 |
|------|------|
| **OWNER** | 全部权限：踢人、设管理、改群名、解散群、发公告、pin 消息 |
| **ADMIN** | 中级权限：踢普通成员、改群名、pin 消息、发公告、审核入群 |
| **MEMBER** | 基本权限：发消息、邀请新人（如果允许）、查看成员 |

**权限矩阵**
```
操作                  OWNER   ADMIN   MEMBER
─────────────────────────────────────────
发消息                 ✅      ✅      ✅
邀请成员               ✅      ✅      ✅ (可配置)
踢出 MEMBER            ✅      ✅      ❌
踢出 ADMIN             ✅      ❌      ❌
设/撤 ADMIN           ✅      ❌      ❌
修改群信息             ✅      ✅      ❌
发群公告               ✅      ✅      ❌
pin 消息               ✅      ✅      ❌
转让群主               ✅      ❌      ❌
解散群聊               ✅      ❌      ❌
退出群聊               ✅      ✅      ✅
```

---

## 🔴 第四部分：消息类型 — 当前缺失

**当前支持**：`TEXT`、`IMAGE`、`AI`

**需要新增**：

| 类型 | 说明 | 复杂度 |
|------|------|--------|
| `FILE` | 普通文件（PDF/DOC/ZIP），显示文件名+大小+下载按钮 | 低 |
| `VOICE` | 语音消息，前端录制 WebM/Opus，显示波形+播放按钮 | 中 |
| `SYSTEM` | 系统消息（XX加入群聊、XX撤回消息、群名被修改） | 低 |
| `STICKER` | 表情贴纸（存 URL） | 低 |
| `FORWARD` | 转发消息（嵌入原消息引用） | 低 |

### 优先实现：`FILE` 消息 + `SYSTEM` 消息

**FILE 消息** — 复用现有 `MediaResource`，新增 `UploadKind.CHAT_FILE(50)`

**SYSTEM 消息** — 不需要 `sender_id` 绑定到真实用户
```sql
-- 系统消息的 sender_id 设为 NULL 或 0
-- 前端根据 type='SYSTEM' 渲染特殊样式（居中灰色小字）
```

当发生以下事件时，服务端自动写入 SYSTEM 消息：
- 群名被修改 → `"Alice 修改群名为「新群名」"`
- 成员加入 → `"Bob 加入了群聊"`
- 成员退出 → `"Charlie 退出了群聊"`
- 成员被踢 → `"David 被移出了群聊"`
- 消息被 pin → `"Alice 置顶了一条消息"`
- 群主转让 → `"Eve 将群主转让给了 Frank"`

---

## 📋 实施优先级

### Phase 1 — 基础补齐（本阶段）3~5 天

| # | 项 | 预计时间 |
|---|-----|---------|
| 1 | 查看群成员列表 API + UI | 2h |
| 2 | 退出/离开群聊 | 1.5h |
| 3 | 踢出成员 (OWNER/ADMIN) | 2h |
| 4 | 设置/撤销 ADMIN | 1.5h |
| 5 | SYSTEM 消息类型 | 2h |
| 6 | 消息撤回 (2分钟内) | 2h |
| 7 | 消息引用回复 | 3h |
| 8 | @提及自动完成 | 3h |
| **合计** | **~2 工作日** |

### Phase 2 — 体验提升 3~5 天

| # | 项 | 预计时间 |
|---|-----|---------|
| 9 | 修改群信息 (名称/头像/描述/主题) | 2h |
| 10 | 群公告 | 1.5h |
| 11 | 消息置顶 Pin | 2h |
| 12 | 输入状态指示器 | 1.5h |
| 13 | Emoji 选择器 | 3h |
| 14 | 浏览器通知 | 1.5h |
| 15 | 全局搜索 | 2h |
| 16 | FILE 文件消息类型 | 2h |
| 17 | 会话免打扰 | 1.5h |
| 18 | 转让群主 | 1h |
| **合计** | **~2.5 工作日** |

### Phase 3 — 进阶功能 3~5 天

| # | 项 | 预计时间 |
|---|-----|---------|
| 19 | 消息转发 | 2h |
| 20 | VOICE 语音消息 (walkie-talkie) | 4h |
| 21 | 已读回执 (read receipts) | 3h |
| 22 | 群聊设置面板 (邀请权限开关等) | 3h |
| 23 | 在线状态 (last seen) | 3h |
| 24 | 解散群聊 | 1h |
| **合计** | **~2 工作日** |

---

## 🏗️ 后端文件变更清单

### 新增文件
```
com.example.chat.model.GroupMemberView.java           — 群成员视图 record
com.example.chat.model.PinnedMessage.java              — 置顶消息 record
com.example.chat.chat.GroupUpdateRequest.java          — 群信息更新请求
com.example.chat.chat.SystemMessageBuilder.java        — 系统消息构造器
```

### 修改文件
```
schema.sql                    — 新增列: messages.reply_to_id, messages.deleted,
                                           chat_groups.avatar_url, chat_groups.description,
                                           chat_groups.theme, chat_groups.announcement
                               新增表: pinned_messages, conversation_settings
ChatResource.java             — 新增 10+ 端点
ChatService.java              — 新增 leaveGroup, kickMember, changeRole, updateGroupInfo,
                                         recallMessage, sendSystemMessage
ChatDao.java                  — 新增 15+ SQL 方法
ChatEndpoint.java             — 处理 typing/reaction/recall 等新信号类型
SendMessageRequest.java       — 新增 replyToId 字段
ChatMessage.java              — 新增 replyToId, replyContent, replySenderName 字段
Conversation.java             — 新增 muted, theme 字段
```

## 🎨 前端文件变更清单

```
chat.js                       — 新增回复/撤回/右键菜单/成员列表/typing indicator/
                               @自动完成/emoji picker/通知/全局搜索 等逻辑
app.html                      — 新增群成员弹窗/右键菜单/全局搜索面板/
                               emoji picker 容器/pinned 消息条
dashboard.css                 — 新增回复缩略框/右键菜单/emoji grid/
                               系统消息/typing dots/成员列表 等样式
friends.js                    — 群聊邀请 panel 已存在，需联调
voice.js                      — 已基本完善
```
