# 朋友圈模块 (Moment) 详细报告

---

## 一、需求与设计

### 1.1 功能需求

| 需求编号 | 需求描述 | 优先级 |
|---------|---------|-------|
| MOMENT-01 | 发布动态 (文本+图片/视频) | P0 |
| MOMENT-02 | 朋友圈 Feed 流 (Cursor分页) | P0 |
| MOMENT-03 | 点赞/取消点赞 | P1 |
| MOMENT-04 | 评论/删除评论 | P1 |
| MOMENT-05 | 可见性控制 (ALL_FRIENDS/ONLY_SELF/SELECTED/EXCLUDE) | P1 |
| MOMENT-06 | 动态软删除 | P1 |
| MOMENT-07 | 图片数量限制 (最多6张或1个视频) | P2 |

### 1.2 设计决策

1. **可见性在SQL层面实现** — 四种模式的可见性判断完全在 `MomentDao.feed()` SQL 的 EXISTS/NOT EXISTS 子查询中处理
2. **SELECTED/EXCLUDE 双重粒度** — 支持按用户和按好友分组两个维度筛选
3. **作者永远可见自己的动态** — `OR m.author_id = ?` 在 SQL 中
4. **Cursor分页** — 使用 `beforeId` 游标，limit 范围 [1, 50]
5. **软删除** — `deleted=1` 而非物理删除

---

## 二、后端设计

### 2.1 核心文件清单

| 文件 | 行数 | 位置 |
|------|------|------|
| MomentResource.java | 74 | `src/main/java/com/example/chat/moment/MomentResource.java` |
| MomentService.java | 67 | `src/main/java/com/example/chat/moment/MomentService.java` |
| MomentDao.java | 155 | `src/main/java/com/example/chat/moment/MomentDao.java` |
| VisibilityPolicy.java | 61 | `src/main/java/com/example/chat/moment/VisibilityPolicy.java` |
| MomentCreateRequest.java | 16 | `src/main/java/com/example/chat/moment/MomentCreateRequest.java` |
| CommentRequest.java | 4 | `src/main/java/com/example/chat/moment/CommentRequest.java` |

### 2.2 REST API 端点

| HTTP | 路径 | 功能 |
|------|------|------|
| GET | `/api/moments?limit=&beforeId=` | Feed 流 (Cursor分页) |
| POST | `/api/moments` | 发布动态 |
| POST | `/api/moments/{id}/likes` | 点赞 |
| DELETE | `/api/moments/{id}/likes` | 取消点赞 |
| GET | `/api/moments/{id}/comments` | 获取评论列表 |
| POST | `/api/moments/{id}/comments` | 发表评论 |
| DELETE | `/api/moments/{id}/comments/{commentId}` | 删除评论 |
| DELETE | `/api/moments/{id}` | 删除动态 (软删除) |

代码位置：`src/main/java/com/example/chat/moment/MomentResource.java`

### 2.3 VisibilityPolicy — 可见性系统

**四种模式 (VisibilityMode 枚举):**

| 模式 | 说明 | 可见范围 |
|------|------|---------|
| `ALL_FRIENDS` | 全部好友可见 | 作者的所有活跃好友 |
| `ONLY_SELF` | 仅自己可见 | 仅作者 |
| `SELECTED` | 指定好友/分组可见 | selectedFriendIds ∪ selectedGroupIds 的成员 |
| `EXCLUDE` | 排除指定好友/分组 | 不在 excludedFriendIds 且不在 excludedGroupIds 中 |

**SQL 实现 (MomentDao.feed()):**
```sql
-- ALL_FRIENDS: EXISTS (SELECT 1 FROM friendships WHERE friend_id = author_id AND owner_id = viewer_id AND active = 1)
-- SELECTED: EXISTS (SELECT 1 FROM moment_visibility_rules WHERE rule_type='SELECTED' AND target_id IN (...))
-- EXCLUDE: NOT EXISTS (SELECT 1 FROM moment_visibility_rules WHERE rule_type='EXCLUDE' AND target_id IN (...))
-- 通用: OR m.author_id = viewer_id (作者可见)
```

代码位置：
- Java模型：`src/main/java/com/example/chat/moment/VisibilityPolicy.java` (61行)
- SQL实现：`src/main/java/com/example/chat/moment/MomentDao.java` — `feed()` 方法 (~40-100行)

### 2.4 动态创建流程
```
1. 验证 mediaIds.size() <= 6 (最多6张图或1个视频)
2. INSERT INTO moments (author_id, text, visibility)
3. 依据visibility生成 moment_visibility_rules 记录
4. 若有mediaIds → INSERT INTO moment_media (moment_id, media_id, sort_order)
```

代码位置：`src/main/java/com/example/chat/moment/MomentService.java` — `create()` + `MomentDao.java` — `create()` + `saveRules()`

---

## 三、数据库设计

### 3.1 moments 表
| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 动态ID |
| author_id | BIGINT | FK → users, NOT NULL | 发布者 |
| text | TEXT | — | 文本内容 |
| visibility | VARCHAR(32) | NOT NULL | ALL_FRIENDS/ONLY_SELF/SELECTED/EXCLUDE |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| deleted | TINYINT(1) | NOT NULL, DEFAULT 0 | 软删除标记 |

建表SQL：`src/main/resources/schema.sql` 第180-188行

### 3.2 moment_media 表
| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| moment_id | BIGINT | PK, FK → moments | 动态ID |
| media_id | BIGINT | PK, FK → media_files | 媒体文件ID |
| sort_order | INT | NOT NULL | 排序 |

建表SQL：`src/main/resources/schema.sql` 第190-197行

### 3.3 moment_visibility_rules 表
| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | — |
| moment_id | BIGINT | FK → moments, NOT NULL | 动态ID |
| rule_type | VARCHAR(32) | NOT NULL | SELECTED/EXCLUDE |
| target_type | VARCHAR(20) | NOT NULL | USER/GROUP |
| target_id | BIGINT | NOT NULL | 好友ID或分组ID |

建表SQL：`src/main/resources/schema.sql` 第199-206行

### 3.4 moment_likes 表
| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| moment_id | BIGINT | PK, FK → moments | 动态ID |
| user_id | BIGINT | PK, FK → users | 用户ID |
| created_at | TIMESTAMP | NOT NULL | 点赞时间 |

建表SQL：`src/main/resources/schema.sql` 第208-215行

### 3.5 moment_comments 表
| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 评论ID |
| moment_id | BIGINT | FK → moments, NOT NULL | 动态ID |
| user_id | BIGINT | FK → users, NOT NULL | 评论者 |
| content | VARCHAR(500) | NOT NULL | 评论内容 |
| created_at | TIMESTAMP | NOT NULL | 评论时间 |

建表SQL：`src/main/resources/schema.sql` 第217-225行

---

## 四、前端设计

### 4.1 页面布局
朋友圈模块在 app.html 的 `#momentsView` 工作区中：
- 顶部发布框（文本+图片上传+可见性选择器）
- Feed 流列表（动态卡片: 头像、昵称、时间、文本、图片、点赞数、评论数、操作按钮）
- 评论展开区域

代码位置：`src/main/webapp/app.html` — `#momentsView` 区域

### 4.2 核心JS函数

| 函数 | 功能 | 位置 |
|------|------|------|
| `loadMoments()` | GET `/api/moments` → 渲染Feed | `moments.js` |
| `publishMoment()` | POST `/api/moments` → 刷新Feed | `moments.js` |
| `toggleLike(momentId)` | POST/DELETE `/api/moments/{id}/likes` | `moments.js` |
| `postComment(momentId)` | POST `/api/moments/{id}/comments` | `moments.js` |
| `deleteComment(commentId)` | DELETE `/api/moments/{id}/comments/{cid}` | `moments.js` |
| `renderMomentCard(moment)` | 渲染单条动态卡片 | `moments.js` |

代码位置：`src/main/webapp/assets/js/moments.js`

### 4.3 可见性选择器
前端提供可见性下拉菜单：
- 全部好友可见
- 仅自己可见
- 指定好友可见 → 展开好友多选器
- 排除好友 → 展开好友排除器

代码位置：`src/main/webapp/assets/js/moments.js` — 可见性选择器逻辑

---

## 五、数据模型

| Model | 位置 | 字段 |
|-------|------|------|
| MomentView | `model/MomentView.java` (19行) | id, authorId, authorName, text, visibility, createdAt, media[], likeCount, commentCount, likedByMe |
| MomentCommentView | `model/MomentCommentView.java` (14行) | id, momentId, userId, userName, content, createdAt |
