# WhisperChat 创新功能 & 动效 — 详细实现方案

---

## Idea 1: 消息弹簧入场 + 发送粒子爆发

### 效果描述
- 新消息不是 static append 进 DOM，而是带**弹性缓动（spring）**从下方弹入
- 发送消息时，从发送按钮/输入框炸出 **彩色 confetti 粒子**
- AI 回复消息用不同的入场方式（打字机逐字出现）

### 涉及文件

| 文件 | 做什么 |
|------|--------|
| `assets/js/chat.js` | 修改 `appendMessage()`，加入动画 class 和 confetti 触发 |
| `assets/css/dashboard.css` | 新增 `@keyframes messageSpringIn`、`@keyframes aiTypewriter` |
| 新增 `assets/js/confetti.js` | 轻量 confetti（约 80 行 Canvas） |

### 实现细节

**1. CSS 弹性入场动画**
```css
/* 普通消息：弹簧弹入 */
@keyframes messageSpringIn {
    0%   { opacity: 0; transform: translateY(28px) scale(.93); }
    60%  { opacity: 1; transform: translateY(-4px) scale(1.02); }
    100% { opacity: 1; transform: translateY(0) scale(1); }
}
.message-row {
    animation: messageSpringIn .42s cubic-bezier(.34, 1.56, .64, 1) both;
}
.message-row.mine {
    animation: messageSpringIn .42s cubic-bezier(.34, 1.56, .64, 1) .06s both;
}

/* AI 消息：打字机逐字出现 */
.message-row.ai .message-text {
    overflow: hidden;
    white-space: normal;
    /* 用 JS 逐字追加 span，每个 span 有 stagger fade-in */
}
.message-row.ai .message-text .char {
    display: inline;
    opacity: 0;
    animation: charFadeIn .04s ease forwards;
}
@keyframes charFadeIn { to { opacity: 1; } }
```

**2. 前端 confetti 函数**（手写 ~80 行，零依赖）
```javascript
// assets/js/confetti.js
window.burstConfetti = function(originElement) {
    const rect = originElement.getBoundingClientRect();
    const cx = rect.left + rect.width / 2;
    const cy = rect.top;
    const colors = ['#536dfe','#d94a56','#ffd77a','#58d79a','#b885ff','#ff7777','#8fd8ff'];
    const canvas = document.createElement('canvas');

    canvas.style.cssText = 'position:fixed;inset:0;pointer-events:none;z-index:9999;';
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
    document.body.appendChild(canvas);
    const ctx = canvas.getContext('2d');

    const particles = Array.from({length: 60}, () => ({
        x: cx, y: cy,
        vx: (Math.random() - 0.5) * 14,
        vy: Math.random() * -14 - 4,
        color: colors[Math.random() * colors.length | 0],
        radius: Math.random() * 6 + 2,
        rotation: Math.random() * 360,
        rotationSpeed: (Math.random() - 0.5) * 12,
        opacity: 1,
        gravity: 0.22,
        friction: 0.985
    }));

    function draw() {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        let alive = false;
        particles.forEach(p => {
            if (p.opacity <= 0) return;
            alive = true;
            p.vy += p.gravity;
            p.vx *= p.friction;
            p.x += p.vx;
            p.y += p.vy;
            p.rotation += p.rotationSpeed;
            p.opacity -= 0.012;
            ctx.save();
            ctx.translate(p.x, p.y);
            ctx.rotate(p.rotation * Math.PI / 180);
            ctx.globalAlpha = Math.max(0, p.opacity);
            ctx.fillStyle = p.color;
            ctx.fillRect(-p.radius/2, -p.radius/2, p.radius, p.radius);
            ctx.restore();
        });
        if (alive) requestAnimationFrame(draw);
        else canvas.remove();
    }
    requestAnimationFrame(draw);
};
```

**3. 集成到发送消息流程**
```javascript
// chat.js sendMessage() 成功回调中：
await sendMessage(payload);
burstConfetti($("#messageForm .send-button"));
// 如果 WebSocket 失败走了 REST fallback，也触发
```

**4. 表情反应快捷条也触发 mini confetti**
- 长按消息出现 reaction bar → 选择 emoji → 小粒子从消息位置飞出（10 个粒子）

---

## Idea 2: 消息反应系统（Message Reactions）

### 效果描述
- 鼠标悬浮消息 → 出现 emoji 快捷反应条（👍😂❤️😮😢🔥）
- 点击 emoji → 反应添加，消息下方显示 `👍 Alice, Bob`
- 已有反应时显示 mini emoji badge + 人数

### 涉及文件

| 文件 | 做什么 |
|------|--------|
| `schema.sql` | 新增 `message_reactions` 表 |
| 新增 `com.example.chat.reaction/MessageReaction.java` | record model |
| 新增 `com.example.chat.reaction/ReactionDao.java` | 持久层 |
| 新增 `com.example.chat.reaction/ReactionService.java` | 业务逻辑 |
| 新增 `com.example.chat.reaction/ReactionResource.java` | REST API |
| `ChatEndpoint.java` | WebSocket 推送 reaction 事件 |
| `chat.js` | 前端交互：hover 条、点击、渲染 |
| `dashboard.css` | reaction bar 样式 |

### 后端实现

**数据库表**
```sql
CREATE TABLE IF NOT EXISTS message_reactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    emoji VARCHAR(8) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_message_user_emoji (message_id, user_id, emoji),
    FOREIGN KEY (message_id) REFERENCES messages(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**REST API**
```
POST   /api/chat/messages/{messageId}/reactions      body: { "emoji": "❤️" }
DELETE /api/chat/messages/{messageId}/reactions/{emoji}     (取消反应)
GET    /api/chat/messages/{messageId}/reactions             (获取反应列表)
```

**ReactionResource.java**
```java
@POST
@Path("/messages/{messageId}/reactions")
public ApiResponse<Void> addReaction(@PathParam("messageId") long messageId,
                                       ReactionRequest request,
                                       @Context HttpServletRequest httpRequest) {
    long userId = SessionSupport.requireUserId(httpRequest);
    reactionService.add(userId, messageId, request.emoji());
    // 通知会话内其他人（WebSocket）
    reactionNotifier.notify(messageId, userId, request.emoji(), "ADD");
    return ApiResponse.ok(null);
}
```

**ReactionDao 核心 SQL**
```java
public void addReaction(Connection c, long userId, long messageId, String emoji) throws SQLException {
    Jdbc.update(c,
        "INSERT INTO message_reactions(message_id,user_id,emoji) VALUES(?,?,?) " +
        "ON DUPLICATE KEY UPDATE emoji=emoji", // idempotent
        ps -> { ps.setLong(1, messageId); ps.setLong(2, userId); ps.setString(3, emoji); });
}

public List<MessageReactionView> reactions(Connection c, long messageId) throws SQLException {
    return Jdbc.list(c,
        "SELECT mr.emoji, mr.user_id, u.nickname, COUNT(*) OVER(PARTITION BY mr.emoji) cnt " +
        "FROM message_reactions mr JOIN users u ON u.id=mr.user_id WHERE mr.message_id=?",
        ps -> ps.setLong(1, messageId),
        rs -> new MessageReactionView(rs.getString("emoji"), rs.getLong("user_id"),
                                      rs.getString("nickname"), rs.getInt("cnt")));
}
```

**WebSocket 广播**
在 `ChatEndpoint.java` 中新增信号处理：
```java
// 收到 reaction 类型的 JSON 信号 → 做 DB 操作 → 广播给会话内所有人
// 信号格式: {"type":"REACTION","conversationId":1,"messageId":42,"emoji":"❤️","action":"ADD"}
```

### 前端实现

**Reaction Bar（hover 出现）**
```css
/* dashboard.css */
.reaction-bar {
    position: absolute;
    top: -36px;
    right: 0;
    display: none;
    gap: 2px;
    padding: 4px 6px;
    background: #fff;
    border: 1px solid var(--dash-line);
    border-radius: 20px;
    box-shadow: 0 8px 24px rgba(31,43,77,.14);
    z-index: 5;
    animation: reactionBarIn .18s ease;
}
.message-bubble:hover .reaction-bar,
.message-bubble:active .reaction-bar {
    display: flex;
}
.reaction-bar button {
    width: 32px; height: 32px;
    border-radius: 999px;
    background: transparent;
    font-size: 1.1rem;
    transition: transform .15s ease;
}
.reaction-bar button:hover {
    transform: scale(1.35);
    background: #f2f5ff;
}
```

**渲染逻辑**（在 `messageBody()` 下方追加）
```javascript
function renderReactions(message) {
    if (!message.reactions?.length) return '';
    const grouped = {};
    message.reactions.forEach(r => {
        grouped[r.emoji] = grouped[r.emoji] || { emoji: r.emoji, count: 0, users: [] };
        grouped[r.emoji].count++;
        grouped[r.emoji].users.push(r.nickname);
    });
    return `<div class="message-reactions">${Object.values(grouped).map(g =>
        `<span class="reaction-badge" title="${g.users.join(', ')}">${g.emoji} ${g.count}</span>`
    ).join('')}</div>`;
}
```

**技术要点**
- 同一用户可对同一消息加多个不同 emoji（UNIQUE KEY 只限制同 emoji 重复）
- 点击已存在的 reaction badge → 取消自己的反应
- WebSocket 推送 reaction 事件 → 所有在线用户实时看到反应更新

---

## Idea 3: 刮刮乐阅后即焚消息

### 效果描述
- 发送时可选"刮刮乐模式" + 限时（10s / 30s / 60s）
- 接收端消息显示为模糊/马赛克覆盖层 + 倒计时
- 用户用鼠标/手指在消息上滑动 → Canvas `destination-out` 擦除覆盖层 → 露出内容
- 超时未刮开 → 消息自动替换为 `💀 这条消息已消失`
- 刮开后 5 秒自动销毁，或点击"销毁"按钮

### 涉及文件

| 文件 | 做什么 |
|------|--------|
| `schema.sql` | `messages` 表新增 `self_destruct_seconds` 列 |
| `SendMessageRequest.java` | 新增 `selfDestructSeconds` 字段 |
| `ChatDao.java` | saveMessage 时写入该字段 |
| `ChatEndpoint.java` | WebSocket 推送时带上该字段 |
| `chat.js` | 前端 Canvas 刮刮乐 + 倒计时逻辑 |
| `dashboard.css` | 刮刮乐覆盖层样式 |

### 后端实现

**数据库变更**
```sql
ALTER TABLE messages ADD COLUMN self_destruct_seconds INT DEFAULT 0;
-- 0 = 普通消息，>0 = 阅后即焚秒数
```

**SendMessageRequest 扩展**
```java
public record SendMessageRequest(
    long conversationId,
    String type,
    String content,
    Long mediaId,
    Integer selfDestructSeconds  // 新增，可为 null
) {}
```

**ChatDao 适配**
```java
// saveMessage 中：
ps.setInt(6, message.selfDestructSeconds() == null ? 0 : message.selfDestructSeconds());
```

**消息销毁**（后台定时任务或懒惰清理）
```java
// 简单方案：history 查询时加过滤
WHERE (m.self_destruct_seconds = 0
       OR TIMESTAMPDIFF(SECOND, m.sent_at, NOW()) < m.self_destruct_seconds)
// 超过时限的消息不返回给客户端
```

### 前端实现

**发送端：刮刮乐开关**
```html
<!-- 在 composer 区域加一个切换按钮 -->
<button class="composer-tool" id="destructToggle" title="阅后即焚">🔥</button>
<!-- 点击展开倒计时选择器 -->
```

```javascript
let destructSeconds = 0; // 0 = 普通消息

$("#destructToggle").addEventListener("click", () => {
    const options = [10, 30, 60];
    const current = options.indexOf(destructSeconds);
    destructSeconds = options[(current + 1) % (options.length + 1)] || 0;
    const label = destructSeconds ? `${destructSeconds}s 阅后即焚` : '普通消息';
    toast(label);
    // toggle button 高亮
});
```

**接收端：Canvas 刮刮乐覆盖层**
```javascript
function renderScratchMessage(message, container) {
    const canvas = document.createElement('canvas');
    canvas.className = 'scratch-canvas';
    canvas.width = container.offsetWidth;
    canvas.height = container.offsetHeight;

    const ctx = canvas.getContext('2d');
    // 填充磨砂/毛玻璃效果
    ctx.fillStyle = '#c8d2de';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    // 绘制提示文字
    ctx.fillStyle = '#6b7a90';
    ctx.font = '16px Inter, Microsoft YaHei';
    ctx.textAlign = 'center';
    ctx.fillText('👆 刮开查看', canvas.width/2, canvas.height/2 + 6);

    let scratching = false;

    function scratch(e) {
        const rect = canvas.getBoundingClientRect();
        const x = (e.touches ? e.touches[0].clientX : e.clientX) - rect.left;
        const y = (e.touches ? e.touches[0].clientY : e.clientY) - rect.top;
        ctx.globalCompositeOperation = 'destination-out';
        ctx.beginPath();
        ctx.arc(x, y, 28, 0, Math.PI * 2);
        ctx.fill();
        checkRevealed();
    }

    canvas.addEventListener('mousedown', e => { scratching = true; scratch(e); });
    canvas.addEventListener('mousemove', e => scratching && scratch(e));
    canvas.addEventListener('mouseup', () => { scratching = false; });
    canvas.addEventListener('touchmove', e => { e.preventDefault(); scratch(e); });
    canvas.addEventListener('touchstart', e => { e.preventDefault(); scratch(e); });
    canvas.addEventListener('touchend', () => { scratching = false; });

    function checkRevealed() {
        const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
        const pixels = imageData.data;
        let transparent = 0;
        for (let i = 3; i < pixels.length; i += 4) {
            if (pixels[i] === 0) transparent++;
        }
        if (transparent / (pixels.length / 4) > 0.5) {
            // 刮开超过 50% → 5 秒后自动销毁
            setTimeout(() => canvas.parentElement?.querySelector('.message-bubble')?.remove(), 5000);
        }
    }

    // 倒计时逻辑
    let remaining = message.selfDestructSeconds;
    const countdownEl = document.createElement('span');
    countdownEl.className = 'destruct-countdown';
    countdownEl.textContent = `⏳ ${remaining}s`;
    container.appendChild(countdownEl);

    const timer = setInterval(() => {
        remaining--;
        if (remaining <= 0) {
            clearInterval(timer);
            container.innerHTML = '<div class="destruct-expired">💀 这条消息已消失</div>';
        } else {
            countdownEl.textContent = `⏳ ${remaining}s`;
        }
    }, 1000);

    container.appendChild(canvas);
}
```

**技术要点**
- `globalCompositeOperation: "destination-out"` → 擦除像素，露出下方消息内容（消息文字在 canvas 层的下层 DOM 中）
- 移动端 `touchmove` 必须 `preventDefault()` 防止页面滚动
- 倒计时在客户端执行，服务端只负责"不返回过期消息"
- 阅后即焚消息不包含在 HTML 导出中

---

## Idea 4: AI 虚拟宠物（聊天小精灵）

### 效果描述
- 登录页的偷看角色"入驻"主 App 侧边栏
- 根据聊天活跃度实时变换表情/动画
- 点击宠物 → 弹出今日聊天数据卡片
- 深夜变成睡觉模式、消息爆发时开心跳跃
- 解锁新外观作为成就奖励

### 涉及文件

| 文件 | 做什么 |
|------|--------|
| `app.html` | 侧边栏 `nav-illustration` 区域改为宠物容器 |
| 新增 `assets/js/pet.js` | 宠物状态机、动画调度、数据绑定 |
| 新增 `assets/css/pet.css` | 宠物 CSS 动画 sprites |
| `schema.sql` | 新增 `user_pets` 表存储宠物外观 |
| 新增 `com.example.chat.pet/PetResource.java` | 宠物外观 API |

### 后端实现

**宠物外观表**
```sql
CREATE TABLE IF NOT EXISTS user_pets (
    user_id BIGINT PRIMARY KEY,
    pet_skin VARCHAR(40) NOT NULL DEFAULT 'coral',
    pet_name VARCHAR(40),
    unlocked_skins JSON DEFAULT '["coral"]',
    FOREIGN KEY (user_id) REFERENCES users(id)
);
-- 皮肤: coral(默认), midnight, forest, golden, ghost
-- 解锁条件: midnight = 连续3天活跃, forest = 100条消息...
```

**API**
```
GET  /api/pets/me          → 返回当前宠物状态
PUT  /api/pets/me          → 换皮肤/改名
POST /api/pets/feed        → 喂食（根据聊天活跃度自动触发）
```

### 前端实现

**宠物状态机**（约 200 行 JS）
```javascript
// assets/js/pet.js
const PetState = {
    IDLE: 'idle',           // 发呆、偶尔眨眼
    HAPPY: 'happy',         // 消息多时跳跃
    CURIOUS: 'curious',     // 新会话打开时探头
    SLEEPY: 'sleepy',       // 深夜 23:00-06:00
    EXCITED: 'excited',     // 收到 confetti/成就解锁时
    EATING: 'eating',       // "喂食"动作
};

class ChatPet {
    constructor(container) {
        this.container = container;
        this.state = PetState.IDLE;
        this.messageCount = 0;
        this.lastActive = Date.now();
        this.init();
    }

    init() {
        // 从 API 加载皮肤和名字
        // 启动定时器：每 30s 评估状态
        setInterval(() => this.evaluateState(), 30000);
        // 绑定全局事件
        window.addEventListener('chat:sent', () => this.onMessageSent());
        window.addEventListener('chat:received', () => this.onMessageReceived());
        this.render();
    }

    evaluateState() {
        const hour = new Date().getHours();
        if (hour >= 23 || hour <= 5) return this.transition(PetState.SLEEPY);
        if (Date.now() - this.lastActive > 600_000) return this.transition(PetState.IDLE);
        return this.transition(PetState.HAPPY);
    }

    transition(newState) {
        if (this.state === newState) return;
        this.container.classList.remove(`pet-${this.state}`);
        this.state = newState;
        this.container.classList.add(`pet-${newState}`);
        if (newState === PetState.EXCITED) {
            setTimeout(() => this.transition(PetState.HAPPY), 3000);
        }
    }

    onMessageSent() {
        this.lastActive = Date.now();
        this.messageCount++;
        this.transition(PetState.EXCITED);
    }

    onMessageReceived() {
        this.lastActive = Date.now();
        this.transition(PetState.CURIOUS);
        setTimeout(() => this.evaluateState(), 2000);
    }

    render() {
        // 在 container 中渲染宠物 HTML/CSS
        // 复用登录页的 peeper 设计元素
    }
}
```

**CSS 宠物动画**（每个状态一套 keyframes）
```css
/* pet.css */
.chat-pet {
    position: relative;
    width: 120px; height: 140px;
    cursor: pointer;
    transition: transform .3s ease;
}
.chat-pet:hover { transform: scale(1.08); }

/* 开心：小幅度弹跳 */
.pet-happy .pet-body {
    animation: petBounce .6s ease-in-out infinite alternate;
}
@keyframes petBounce {
    from { transform: translateY(0); }
    to   { transform: translateY(-12px); }
}

/* 兴奋：快速跳跃 + 旋转 */
.pet-excited .pet-body {
    animation: petJump .3s ease-in-out 3;
}
@keyframes petJump {
    0%,100% { transform: translateY(0) rotate(0); }
    50%     { transform: translateY(-24px) rotate(-8deg); }
}

/* 瞌睡：缓慢呼吸 */
.pet-sleepy .pet-body {
    animation: petSleep 3s ease-in-out infinite;
}
@keyframes petSleep {
    0%,100% { transform: scaleY(1); }
    50%     { transform: scaleY(.94); }
}

/* 点击弹出气泡 */
.pet-speech-bubble {
    position: absolute;
    top: -42px;
    left: 50%;
    transform: translateX(-50%);
    padding: 8px 12px;
    border-radius: 12px;
    background: #fff;
    border: 1px solid #e0e6ff;
    box-shadow: 0 8px 24px rgba(83,109,254,.16);
    font-size: .82rem;
    white-space: nowrap;
    opacity: 0;
    transition: opacity .2s ease, transform .2s ease;
}
.chat-pet:active .pet-speech-bubble {
    opacity: 1;
    transform: translateX(-50%) translateY(-4px);
}
```

**HTML 集成**
```html
<!-- 替换原有的 nav-illustration -->
<div class="chat-pet" id="chatPet">
    <div class="pet-speech-bubble" id="petBubble">今天发了 42 条消息！</div>
    <div class="pet-body">
        <!-- 复用 peeper 结构 -->
        <div class="peeper peeper-coral">
            <div class="peeper-face">
                <div class="peeper-brow brow-a"></div>
                <div class="peeper-brow brow-b"></div>
                <div class="eye"><span></span></div>
                <div class="eye"><span></span></div>
            </div>
        </div>
    </div>
</div>
```

**技术要点**
- 宠物状态由前端状态机全权管理，不依赖服务端推送
- 外观数据从 REST API 加载和持久化
- 成就解锁新皮肤时通过 WebSocket 推送通知
- CSS-only 动画优先（GPU 加速），JS 只负责 class 切换

---

## Idea 5: 成就徽章系统

### 效果描述
- 用户完成特定行为 → 解锁徽章
- 解锁时全屏 confetti + 徽章卡片弹出动画
- 个人主页展示所有徽章（已解锁亮色，未解锁灰色剪影）
- 排行榜（好友间的徽章数 PK）

### 涉及文件

| 文件 | 做什么 |
|------|--------|
| `schema.sql` | 新增 `achievements`、`user_achievements` 表 |
| 新增 `com.example.chat.achievement/Achievement.java` | record |
| 新增 `com.example.chat.achievement/AchievementDao.java` | 持久层 |
| 新增 `com.example.chat.achievement/AchievementService.java` | 业务 + 检测逻辑 |
| 新增 `com.example.chat.achievement/AchievementResource.java` | REST API |
| `ChatService.java` / `MomentService.java` 等 | 行为触发点调用 `achievementService.check(userId, action)` |
| 新增 `assets/js/achievements.js` | 前端解锁弹窗、主页展示 |
| 新增 `assets/css/achievements.css` | 徽章卡片样式 |

### 后端实现

**数据表**
```sql
CREATE TABLE IF NOT EXISTS achievements (
    id VARCHAR(40) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(200) NOT NULL,
    icon VARCHAR(20) NOT NULL,
    tier VARCHAR(20) NOT NULL DEFAULT 'BRONZE'  -- BRONZE, SILVER, GOLD, LEGENDARY
);

CREATE TABLE IF NOT EXISTS user_achievements (
    user_id BIGINT NOT NULL,
    achievement_id VARCHAR(40) NOT NULL,
    unlocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, achievement_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (achievement_id) REFERENCES achievements(id)
);
```

**种子数据**（12 个初始徽章）
```sql
INSERT INTO achievements(id, name, description, icon, tier) VALUES
('first_msg',      '初来乍到',    '发送第一条消息',           '👶', 'BRONZE'),
('ten_msgs',       '小试牛刀',    '累计发送 100 条消息',      '💬', 'BRONZE'),
('hundred_msgs',   '话痨达人',    '累计发送 1000 条消息',     '📢', 'SILVER'),
('thousand_msgs',  '聊天狂魔',    '累计发送 10000 条消息',    '🔥', 'GOLD'),
('three_day',      '三日热聊',    '连续 3 天活跃',           '📅', 'BRONZE'),
('seven_day',      '一周坚持',    '连续 7 天活跃',           '🔥', 'SILVER'),
('thirty_day',     '月之传说',    '连续 30 天活跃',          '👑', 'GOLD'),
('first_friend',   '交个朋友',    '添加第一位好友',          '🤝', 'BRONZE'),
('ten_friends',    '社交花蝴蝶',  '拥有 10 位好友',          '🦋', 'SILVER'),
('first_group',    '群聊创建者',  '创建第一个群聊',          '🏗️', 'BRONZE'),
('first_moment',   '分享时刻',    '发布第一条朋友圈',        '📸', 'BRONZE'),
('night_owl',      '夜猫子',      '在凌晨 0-5 点活跃',      '🦉', 'SILVER'),
('reaction_king',  '反应之王',    '发送 100 次消息反应',     '🎯', 'SILVER'),
('mystery',        '神秘来客',    '???（隐藏条件：连续点击宠物 20 次）', '❓', 'LEGENDARY');
```

**检测引擎**
```java
// AchievementService.java
public class AchievementService {
    private final AchievementDao dao = new AchievementDao();

    public void check(long userId, String action) {
        // 根据 action 查找可能解锁的成就
        List<String> candidateIds = candidatesFor(action);
        for (String achievementId : candidateIds) {
            if (!dao.hasAchievement(userId, achievementId) && evaluate(userId, achievementId)) {
                dao.unlock(userId, achievementId);
                notifyUnlock(userId, achievementId); // 通过 WebSocket 推送
            }
        }
    }

    private boolean evaluate(long userId, String achievementId) {
        return switch (achievementId) {
            case "first_msg"     -> dao.totalMessages(userId) >= 1;
            case "hundred_msgs"  -> dao.totalMessages(userId) >= 100;
            case "thousand_msgs" -> dao.totalMessages(userId) >= 1000;
            case "three_day"     -> dao.currentStreak(userId) >= 3;
            case "seven_day"     -> dao.currentStreak(userId) >= 7;
            case "thirty_day"    -> dao.currentStreak(userId) >= 30;
            case "first_friend"  -> dao.totalFriends(userId) >= 1;
            case "night_owl"     -> dao.nightOwlCount(userId) >= 5;
            // ...
            default -> false;
        };
    }

    private void notifyUnlock(long userId, String achievementId) {
        // 1. 在 ChatEndpoint 的 SocketRegistry 中发送给该用户
        // 2. 信号: {"type":"ACHIEVEMENT_UNLOCK","achievementId":"first_msg","name":"初来乍到","icon":"👶"}
    }
}
```

**触发点注入**
```java
// 在 ChatService.send() 中：
chatDao.saveMessage(...);
achievementService.check(userId, "message_sent");

// 在 FriendService.accept() 中：
friendDao.createFriendshipPair(...);
achievementService.check(userId, "friend_added");

// 等等...每个业务方法末尾调用 check
```

**每日活跃/连续登录追踪**
```sql
CREATE TABLE IF NOT EXISTS user_daily_activity (
    user_id BIGINT NOT NULL,
    activity_date DATE NOT NULL,
    PRIMARY KEY (user_id, activity_date),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
-- 登录时 INSERT IGNORE，查询 streak 时按日期连续性计数
```

### 前端实现

**徽章解锁弹窗**
```javascript
// achievements.js
function showAchievementUnlock(achievement) {
    const modal = document.createElement('div');
    modal.className = 'achievement-unlock-modal';
    modal.innerHTML = `
        <div class="achievement-unlock-backdrop"></div>
        <div class="achievement-unlock-card tier-${achievement.tier}">
            <div class="achievement-glow"></div>
            <span class="achievement-icon">${achievement.icon}</span>
            <strong>成就解锁！</strong>
            <h2>${achievement.name}</h2>
            <p>${achievement.description}</p>
        </div>
    `;
    document.body.appendChild(modal);

    // CSS 动画：卡片从中心放大 + 旋转弹入
    // 同时触发 confetti
    burstConfetti(modal.querySelector('.achievement-unlock-card'));

    // 3 秒后自动消失
    setTimeout(() => modal.remove(), 3500);
}

// WebSocket 监听
// socket.addEventListener('message', e => {
//     const msg = JSON.parse(e.data);
//     if (msg.type === 'ACHIEVEMENT_UNLOCK') showAchievementUnlock(msg);
// });
```

**徽章解锁 CSS**
```css
/* achievements.css */
.achievement-unlock-modal {
    position: fixed; inset: 0; z-index: 9999;
    display: grid; place-items: center;
}
.achievement-unlock-backdrop {
    position: absolute; inset: 0;
    background: rgba(20,24,45,.6);
    animation: fadeIn .3s ease;
}
.achievement-unlock-card {
    position: relative;
    padding: 2rem 2.5rem;
    border-radius: 16px;
    background: #fff;
    text-align: center;
    animation: achievementPopIn .5s cubic-bezier(.34,1.56,.64,1) both;
    box-shadow: 0 32px 80px rgba(83,109,254,.3);
}
.achievement-unlock-card.tier-LEGENDARY {
    background: linear-gradient(135deg, #ffd700, #fff8dc);
    box-shadow: 0 32px 80px rgba(255,215,0,.4);
}
.achievement-icon { font-size: 3.5rem; display: block; margin-bottom: .5rem; }
@keyframes achievementPopIn {
    0%   { opacity: 0; transform: scale(.3) rotate(-8deg); }
    60%  { opacity: 1; transform: scale(1.08) rotate(2deg); }
    100% { opacity: 1; transform: scale(1) rotate(0); }
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
```

**个人主页徽章墙**（在 profileView 中）
```javascript
// 已解锁 → 彩色卡片；未解锁 → 灰色剪影 + 锁定图标
function renderBadgeWall(achievements) {
    return achievements.map(a => `
        <div class="badge-card ${a.unlocked ? '' : 'locked'}">
            <span class="badge-icon">${a.unlocked ? a.icon : '🔒'}</span>
            <strong>${a.unlocked ? a.name : '???'}</strong>
            <span>${a.unlocked ? a.description : '继续探索来解锁'}</span>
        </div>
    `).join('');
}
```

---

## Idea 6: 聊天热力图（GitHub 贡献图风格）

### 效果描述
- 个人主页展示过去 365 天的消息活跃热力图
- 7×52 网格，每个格子代表一天
- 颜色深浅表示消息数量（浅绿 → 深绿 / 浅蓝 → 深蓝）

### 实现细节

**后端 API**
```
GET /api/users/me/heatmap?year=2025
```
```java
// UserDao.java
public List<DayActivity> heatmap(Connection c, long userId, int year) throws SQLException {
    return Jdbc.list(c,
        "SELECT activity_date, COUNT(*) msg_count FROM user_daily_activity " +
        "WHERE user_id=? AND YEAR(activity_date)=? GROUP BY activity_date",
        ps -> { ps.setLong(1, userId); ps.setInt(2, year); },
        rs -> new DayActivity(rs.getDate("activity_date").toLocalDate(), rs.getInt("msg_count")));
}
```

**前端渲染**（约 60 行 JS）
```javascript
function renderHeatmap(data) {
    const container = $("#heatmapGrid");
    const now = new Date();
    const startDate = new Date(now.getFullYear(), 0, 1);
    const cells = [];

    // 生成 365 天 + 补齐到周日
    for (let d = new Date(startDate); d <= now; d.setDate(d.getDate() + 1)) {
        const dateStr = d.toISOString().slice(0, 10);
        const count = data[dateStr] || 0;
        const level = count === 0 ? 0 : count < 5 ? 1 : count < 20 ? 2 : count < 50 ? 3 : 4;
        cells.push({ date: dateStr, level });
    }

    container.innerHTML = cells.map(c => `
        <div class="heat-cell level-${c.level}" title="${c.date}: ${data[c.date]||0} 条消息"></div>
    `).join('');
}
```

**CSS**
```css
.heat-cell {
    width: 14px; height: 14px;
    border-radius: 3px;
    background: #ebedf0;
}
.heat-cell.level-1 { background: #9be9a8; }
.heat-cell.level-2 { background: #40c463; }
.heat-cell.level-3 { background: #30a14e; }
.heat-cell.level-4 { background: #216e39; }
```

---

## Idea 7: 群聊主题 / 氛围背景

### 效果描述
- 群主可为群聊设置主题色 + 背景图 + 聊天气泡风格
- 不同群聊有不同的视觉氛围（赛博朋克、森林、海洋、暗黑等）
- 切换群聊时消息区域背景和气泡颜色渐变过渡

### 涉及文件

| 文件 | 做什么 |
|------|--------|
| `schema.sql` | `chat_groups` 新增 `theme` 列 |
| `ChatDao.java` | 读写 theme |
| `ChatResource.java` | 新增 PUT 接口 |
| `chat.js` | 加载主题并应用到消息面板 |
| `dashboard.css` | 主题 CSS 变量覆盖 |

**数据库变更**
```sql
ALTER TABLE chat_groups ADD COLUMN theme VARCHAR(40) DEFAULT 'default';
```

**预置主题**（纯 CSS 变量切换）
```css
/* dashboard.css */
.message-list[data-theme="cyberpunk"] {
    --bg: #0a0a1a;
    --bubble-self: linear-gradient(135deg, #ff00ff, #00ffff);
    --bubble-other: rgba(255,255,255,.08);
    --text: #e0e0ff;
    --text-self: #000;
}
.message-list[data-theme="forest"] {
    --bg: #f0f7f0;
    --bubble-self: linear-gradient(135deg, #4caf50, #81c784);
    --bubble-other: #fff;
    --text: #2e3b2e;
    --text-self: #fff;
}
/* ... 更多主题 */
```

**前端应用**
```javascript
function applyGroupTheme(conversation) {
    const list = $("#messageList");
    list.dataset.theme = conversation.theme || 'default';
    // 同时设置 CSS 自定义属性实现平滑过渡
    list.style.transition = 'background .5s ease';
}
```

---

## Idea 8: "聊天年度报告"（Chat Wrapped）

### 效果描述
- 每年年底或用户主动触发时，生成一份精美的聊天数据报告
- 报告以朋友圈卡片形式展示，可分享到朋友圈
- 数据亮点：总消息数、最活跃月份、最活跃好友、最爱用的词等

### 实现细节

**后端**
```
GET /api/users/me/wrapped?year=2025
```
```java
// WrappedService.java 返回：
public record ChatWrapped(
    int totalMessages,
    String mostActiveMonth,
    int mostActiveDayHour,
    String topFriendName,
    long topFriendMessageCount,
    String topKeyword,
    int totalGroupsCreated,
    int totalMoments,
    int totalReactionsGiven,
    int currentStreak,
    String personalityLabel  // "话痨" / "倾听者" / "深夜诗人" / "社交花蝴蝶"
) {}
```

**前端展示**（朋友圈风格的卡片轮播）
```javascript
// 类似 Instagram Stories 的全屏卡片，左滑/右滑切换数据页
// 每页一个数据亮点 + 大号字体 + 背景动画
// 最后一页是"分享到朋友圈"按钮
```

---

## Idea 9: 密码输入时角色偷看动效增强

### 效果描述
你们登录页的角色已经有 `password-secret`（转头回避）和 `password-peeking`（偷看）动效。进一步增强：
- 密码输入框聚焦时，黑/紫卡片角色用手遮住眼睛（从指缝里偷看）
- 输入错误密码时角色摇头 + 红色闪烁
- 登录成功时角色跳跃 + 花瓣/confetti

### 实现细节

**auth.js 增强**
```javascript
// 登录失败时
document.querySelector("#loginPanel").addEventListener("submit", async event => {
    // ... 现有逻辑 ...
    } catch (error) {
        stage.classList.add("login-failed");
        setTimeout(() => stage.classList.remove("login-failed"), 800);
        toast(error.message);
    }
});
```

**CSS 新增**
```css
/* 登录失败：摇头 + 红色闪烁 */
.peek-stage.login-failed {
    animation: shakeHead .5s ease;
}
.peek-stage.login-failed .purple-card,
.peek-stage.login-failed .black-card {
    box-shadow: 0 28px 70px rgba(255,60,60,.4);
    transition: box-shadow .3s ease;
}
@keyframes shakeHead {
    0%,100% { transform: translate(-50%, -50%) rotate(0); }
    20%     { transform: translate(calc(-50% - 8px), -50%) rotate(-3deg); }
    40%     { transform: translate(calc(-50% + 8px), -50%) rotate(3deg); }
    60%     { transform: translate(calc(-50% - 6px), -50%) rotate(-2deg); }
    80%     { transform: translate(calc(-50% + 6px), -50%) rotate(2deg); }
}

/* 登录成功：跳跃 + 花瓣粒子 */
.peek-stage.login-success .peeper-coral {
    animation: petJump .3s ease 3;
}
```

---

## 技术栈总结

| 层级 | 新增/使用技术 | 用途 |
|------|-------------|------|
| **后端** | Jakarta REST + JDBC | 所有新增 API |
| **WebSocket** | 现有 ChatEndpoint 扩展 | 推送 reaction、成就解锁、宠物状态 |
| **数据库** | MySQL 新增 5~7 张表 | reactions, achievements, pets, daily activity |
| **前端动画** | CSS `@keyframes` + `cubic-bezier` spring | 消息入场、宠物动画、徽章弹出 |
| **Canvas 2D** | 原生 Canvas API | confetti 粒子、刮刮乐擦除 |
| **零外部依赖** | 所有动效用原生 CSS/JS 实现 | 保持 WAR 包体积不变 |

---

## 实施优先级

| 阶段 | 内容 | 预计工作量 |
|------|------|-----------|
| **Phase 1** (立即) | 消息弹簧入场 + confetti（Idea 1）、密码动效增强（Idea 9） | 3~4 小时 |
| **Phase 2** (本周) | 消息反应系统（Idea 2）、群聊主题（Idea 7） | 1~2 天 |
| **Phase 3** (下周) | 刮刮乐阅后即焚（Idea 3）、AI 虚拟宠物（Idea 4） | 2~3 天 |
| **Phase 4** (迭代) | 成就徽章（Idea 5）、热力图（Idea 6）、年度报告（Idea 8） | 3~4 天 |
