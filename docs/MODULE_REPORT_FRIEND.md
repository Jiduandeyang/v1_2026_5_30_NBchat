# 好友模块 (Friend) 详细报告

---

## 一、需求与设计

### 1.1 功能需求

| 需求编号 | 需求描述 | 优先级 |
|---------|---------|-------|
| FRIEND-01 | 发送/接受/拒绝好友申请 | P0 |
| FRIEND-02 | 删除好友 (软删除: active=0) | P0 |
| FRIEND-03 | 好友分组管理 (创建/重命名/移动好友) | P1 |
| FRIEND-04 | 亲密好友标记 (close_friend) | P1 |
| FRIEND-05 | 好友列表 (含分组+亲密标记) | P0 |
| FRIEND-06 | 用户搜索 | P1 |
| FRIEND-07 | 删除好友时隐藏私聊消息 | P1 |

### 1.2 设计决策

1. **双向好友关系** — `createFriendshipPair()` 同时插入 A→B 和 B→A 两条记录
2. **软删除好友** — `deleteForOwnerOnly()` 只设置 `active=0`，同时隐藏私聊消息
3. **亲密好友影响群邀请** — 群邀请策略中，亲密好友可直接入群 (`ADD_DIRECTLY`)
4. **好友分组隔离** — 每个用户独立管理好自己的分组（owner_id 隔离）
5. **申请模式约束** — 只有接收者可以 accept，只有接收者可以 reject

---

## 二、后端设计

### 2.1 核心文件清单

| 文件 | 行数 | 位置 |
|------|------|------|
| FriendResource.java | 94 | `src/main/java/com/example/chat/friend/FriendResource.java` |
| FriendService.java | 84 | `src/main/java/com/example/chat/friend/FriendService.java` |
| FriendDao.java | 151 | `src/main/java/com/example/chat/friend/FriendDao.java` |

### 2.2 REST API 端点

| HTTP | 路径 | 功能 |
|------|------|------|
| GET | `/api/friend-groups` | 获取好友分组列表 |
| POST | `/api/friend-groups` | 创建好友分组 |
| PUT | `/api/friend-groups/{id}` | 重命名分组 |
| GET | `/api/friends` | 获取好友列表 (含分组+亲密标记) |
| PUT | `/api/friends/{id}/group` | 移动好友到其他分组 |
| PUT | `/api/friends/{id}/close-friend` | 切换亲密好友标记 |
| DELETE | `/api/friends/{id}` | 删除好友 (软删除) |
| POST | `/api/friend-requests` | 发送好友申请 |
| GET | `/api/friend-requests?mode=received\|sent` | 查看申请列表 |
| POST | `/api/friend-requests/{id}/accept` | 接受申请 |
| POST | `/api/friend-requests/{id}/reject` | 拒绝申请 |

代码位置：`src/main/java/com/example/chat/friend/FriendResource.java`

### 2.3 FriendService — 核心业务逻辑

**发送好友申请 (`sendRequest()`):**
- 验证 `senderId != receiverId`
- 调用 DAO 创建 PENDING 申请

**接受申请 (`accept()`):**
- 加载申请 → 验证 `receiverId == userId`
- 更新申请状态为 ACCEPTED
- 调用 `createFriendshipPair()` 创建双向好友关系

**删除好友 (`deleteFriend()`):**
- 调用 `deleteForOwnerOnly()` 设置 `active=0`
- 同时插入 `message_visibility` 隐藏双方私聊消息

代码位置：`src/main/java/com/example/chat/friend/FriendService.java`

### 2.4 FriendDao — 核心SQL

| 方法 | SQL操作 |
|------|---------|
| `createFriendshipPair()` | `INSERT INTO friendships ... ON DUPLICATE KEY UPDATE active=1` (支持重新加好友) |
| `deleteForOwnerOnly()` | `UPDATE friendships SET active=0 WHERE owner_id=? AND friend_id=?` + 批量 INSERT message_visibility 隐藏私聊消息 |
| `friends()` | JOIN users + friendships + friend_groups，返回含 groupId 和 closeFriend 的 User 列表 |
| `requests()` | 动态 WHERE 列名 (sender_id 或 receiver_id) 依据 mode 参数 |
| `isActiveFriend()` | `SELECT 1 FROM friendships WHERE owner_id=? AND friend_id=? AND active=1` |
| `isCloseFriend()` | `SELECT 1 FROM friendships WHERE owner_id=? AND friend_id=? AND close_friend=1 AND active=1` |

代码位置：`src/main/java/com/example/chat/friend/FriendDao.java`

---

## 三、数据库设计

### 3.1 friend_groups 表
| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 分组ID |
| owner_id | BIGINT | FK → users, NOT NULL | 所属用户 |
| name | VARCHAR(40) | NOT NULL | 分组名 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |

建表SQL：`src/main/resources/schema.sql` 第37-43行

### 3.2 friend_requests 表
| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 申请ID |
| sender_id | BIGINT | FK → users, NOT NULL | 发送者 |
| receiver_id | BIGINT | FK → users, NOT NULL | 接收者 |
| message | VARCHAR(160) | — | 申请附言 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | PENDING/ACCEPTED/REJECTED |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | ON UPDATE | 更新时间 |

建表SQL：`src/main/resources/schema.sql` 第45-55行

### 3.3 friendships 表
| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | — |
| owner_id | BIGINT | FK → users, NOT NULL | 好友持有者 |
| friend_id | BIGINT | FK → users, NOT NULL | 好友 |
| group_id | BIGINT | FK → friend_groups | 所属分组 |
| close_friend | TINYINT(1) | NOT NULL, DEFAULT 0 | 亲密好友标记 |
| active | TINYINT(1) | NOT NULL, DEFAULT 1 | 是否激活 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |

UNIQUE KEY: `uk_friendship_owner_friend (owner_id, friend_id)`
建表SQL：`src/main/resources/schema.sql` 第57-69行

---

## 四、前端设计

### 4.1 页面布局
好友模块在 app.html 的 `#friendsView` 工作区中，包含：
- 好友分组筛选栏
- 好友卡片列表（头像、昵称、签名、分组选择器、亲密标记按钮）
- 好友申请/群邀请卡片
- 添加好友表单（用户搜索+申请发送）

代码位置：`src/main/webapp/app.html` — `#friendsView` 区域

### 4.2 核心JS函数

| 函数 | 功能 | 位置 (friends.js行号) |
|------|------|---------------------|
| `loadFriends()` | GET `/api/friends` → 渲染好友列表 | ~71 |
| `loadFriendGroups()` | GET `/api/friend-groups` → 渲染分组 | ~97 |
| `loadRequests()` | 并行获取: 好友申请 + 群邀请 | ~185 |
| `renderFriendList()` | 渲染好友卡片（含移动分组、亲密标记、删除按钮） | ~45 |
| `renderSearchResults()` | 渲染用户搜索结果 | ~197 |

### 4.3 事件委托
所有操作通过 `document` click 委托 + `data-*` 属性分发：
- `data-accept-request` — 接受好友申请
- `data-reject-request` — 拒绝好友申请
- `data-delete-friend` — 删除好友
- `data-move-friend` (select change) — 移动好友到其他分组
- `data-toggle-close-friend` — 切换亲密好友
- `data-accept-group-invite` — 接受群邀请
- `data-reject-group-invite` — 拒绝群邀请

代码位置：`src/main/webapp/assets/js/friends.js` — 事件监听器 (~200-310行)

### 4.4 导出给其他模块
```javascript
window.loadFriends      // 供 app.js 初始化调用
window.loadFriendGroups
window.loadRequests
```
代码位置：`src/main/webapp/assets/js/friends.js` 末尾

---

## 五、与 Chat 模块的交叉

- **好友关系门禁**: `ChatService.ensurePrivateFriendshipCanSend()` 通过 `FriendDao.isActiveFriend()` 检查
  - 代码位置：`src/main/java/com/example/chat/chat/ChatService.java`
- **亲密好友群邀请策略**: `GroupInvitePolicy.decide(boolean isCloseFriend)` → `ADD_DIRECTLY`
  - 代码位置：`src/main/java/com/example/chat/chat/GroupInvitePolicy.java`
- **删除好友隐藏消息**: `FriendDao.deleteForOwnerOnly()` → 批量写入 `message_visibility`
  - 代码位置：`src/main/java/com/example/chat/friend/FriendDao.java`
