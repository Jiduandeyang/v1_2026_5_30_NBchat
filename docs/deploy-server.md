# Ubuntu 24.04 LTS 部署说明

本项目推荐部署在 Ubuntu 24.04 LTS 上。2 核 2G 服务器可以承载核心聊天系统的小规模使用；AI 助手使用 DeepSeek 外部 API，不在服务器本地运行大模型。

## 推荐架构

```text
Nginx 80/443
  -> Tomcat 11 8080
      -> Jakarta Chat WAR
  -> MySQL 8
```

8080 只给本机访问，公网只开放 22、80、443。语音通话需要 TURN 时，再按需开放 3478/UDP 和 TURN 中继端口。

## 安装基础环境

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk mysql-server nginx unzip curl
java -version
```

安装 Tomcat 11：

```bash
cd /opt
sudo curl -LO https://dlcdn.apache.org/tomcat/tomcat-11/v11.0.18/bin/apache-tomcat-11.0.18.tar.gz
sudo tar -xzf apache-tomcat-11.0.18.tar.gz
sudo mv apache-tomcat-11.0.18 tomcat
sudo chmod +x /opt/tomcat/bin/*.sh
```

## 初始化 MySQL

```bash
sudo mysql
```

```sql
CREATE DATABASE jakarta_chat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'chat_user'@'localhost' IDENTIFIED BY '替换成强密码';
GRANT ALL PRIVILEGES ON jakarta_chat.* TO 'chat_user'@'localhost';
FLUSH PRIVILEGES;
```

导入项目的 `src/main/resources/schema.sql`：

```bash
mysql -u chat_user -p jakarta_chat < schema.sql
```

## 服务器配置

在服务器的 `app.properties` 中使用：

```properties
db.url=jdbc:mysql://localhost:3306/jakarta_chat?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
db.username=chat_user
db.password=替换成强密码
upload.root=/opt/jakarta-chat/uploads
public.baseUrl=https://你的域名/v1_2026_5_30
mail.devMode=false
qq.smtp.username=你的QQ邮箱@qq.com
qq.smtp.authCode=你的QQ邮箱SMTP授权码
deepseek.baseUrl=https://api.deepseek.com
deepseek.apiKey=你的DeepSeek_API_Key
deepseek.model=deepseek-chat
ai.assistantName=千问小助手
ai.assistantUserId=3
rtc.stunUrls=stun:stun.l.google.com:19302
rtc.turnUrl=
rtc.turnUsername=
rtc.turnCredential=
```

如果还没有 `tomcat` 用户：

```bash
sudo useradd -r -m -U -d /opt/tomcat -s /bin/false tomcat
sudo chown -R tomcat:tomcat /opt/tomcat
```

创建上传目录：

```bash
sudo mkdir -p /opt/jakarta-chat/uploads
sudo chown -R tomcat:tomcat /opt/jakarta-chat
```

## 部署 WAR

本地打包：

```powershell
.\mvnw.cmd clean package
```

上传 `target/v1_2026_5_30-1.0-SNAPSHOT.war` 到服务器并重命名：

```bash
sudo cp v1_2026_5_30-1.0-SNAPSHOT.war /opt/tomcat/webapps/v1_2026_5_30.war
sudo chown tomcat:tomcat /opt/tomcat/webapps/v1_2026_5_30.war
```

## Tomcat systemd

创建 `/etc/systemd/system/tomcat.service`：

```ini
[Unit]
Description=Apache Tomcat 11
After=network.target mysql.service

[Service]
Type=forking
User=tomcat
Group=tomcat
Environment="JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64"
Environment="JAVA_OPTS=-Xms256m -Xmx768m -Dfile.encoding=UTF-8"
Environment="CATALINA_HOME=/opt/tomcat"
Environment="CATALINA_BASE=/opt/tomcat"
ExecStart=/opt/tomcat/bin/startup.sh
ExecStop=/opt/tomcat/bin/shutdown.sh
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now tomcat
sudo systemctl status tomcat
```

## Nginx 反向代理

创建 `/etc/nginx/sites-available/jakarta-chat`：

```nginx
server {
    listen 80;
    server_name 你的域名;

    client_max_body_size 30m;

    location /v1_2026_5_30/ {
        proxy_pass http://127.0.0.1:8080/v1_2026_5_30/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /v1_2026_5_30/ws/ {
        proxy_pass http://127.0.0.1:8080/v1_2026_5_30/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;
    }
}
```

启用：

```bash
sudo ln -s /etc/nginx/sites-available/jakarta-chat /etc/nginx/sites-enabled/jakarta-chat
sudo nginx -t
sudo systemctl reload nginx
```

正式上线再配置 HTTPS：

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d 你的域名
```

## DeepSeek AI 助手

群聊中发送：

```text
@千问小助手 总结这个群聊
```

后端会把最近聊天上下文发送到 DeepSeek Chat Completions 接口，返回内容会保存成普通聊天消息，消息类型为 `AI`。

如果没有配置 `deepseek.apiKey`，系统会返回明确提示，不会阻塞普通聊天。
