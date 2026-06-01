# NBchat — Jakarta EE 实时聊天系统

基于 **Jakarta EE 10 + Jersey REST + WebSocket + MySQL** 的全功能在线聊天应用，支持私聊、群聊、朋友圈、语音通话和 AI 助手。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Jakarta REST 3.1 · WebSocket 2.1 · Servlet 6.0 · JDBC + HikariCP |
| 数据库 | MySQL 8.0（17 张表，Schema 自动迁移） |
| 前端 | 原生 HTML/CSS/JS（零框架依赖，Lucide 图标） |
| AI | DeepSeek API（`@千问小助手` 群聊触发） |
| 构建 | Maven WAR · Java 17 · Tomcat 10.1+ |

## 功能

- **认证** — 用户名/密码登录 · QQ 邮箱验证码注册/找回密码 · BCrypt 哈希 · 频率限制
- **好友** — 搜索用户 · 请求/接受/拒绝 · 好友分组 · 亲密好友标记 · 分组筛选
- **私聊** — 文字 · 图片 · 语音消息 · 文件 · 阅后即焚刮刮乐 · 消息引用回复
- **群聊** — 创建群组 · 邀请成员 · 三级角色（群主/管理/成员）· 踢人 · 修改群名 · 退出
- **消息互动** — Emoji 表情反应 · 2 分钟内撤回 · @提及自动补全 · 搜索 · HTML 导出
- **朋友圈** — 图文发布 · 四种可见性规则 · 点赞 · 评论 · 365 天聊天热力图
- **语音通话** — WebRTC 一对一 · 信令 · 来电弹窗 · ICE 可配置
- **动效** — Canvas 发送粒子 · 消息弹簧入场 · @脉冲高亮 · 图片渐进加载 · 昼夜主题切换

## 快速开始

```bash
# 1. 导入数据库（MySQL）
mysql -u root -p < src/main/resources/schema.sql

# 2. 配置（复制模板，填写本地数据库密码和 DeepSeek API Key）
cp src/main/resources/app.properties.example src/main/resources/app.properties

# 3. 打包
./mvnw clean package -DskipTests

# 4. 部署到 Tomcat
cp target/v1_2026_5_30-1.0-SNAPSHOT.war $CATALINA_HOME/webapps/v1_2026_5_30.war

# 5. 访问
open http://localhost:8080/v1_2026_5_30/
```

## 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| `alice` | `123456` | 普通用户 |
| `bob` | `123456` | 普通用户 |
| `admin` | `admin123` | 管理员 |

## 运行环境要求

- JDK 17+
- Tomcat 10.1+
- MySQL 8.0+
- DeepSeek API Key（可选，`@千问小助手` 功能需配置）

## License

MIT
