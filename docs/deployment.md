# Deployment Guide

## Build

```bash
.\mvnw.cmd clean package
```

The WAR file is generated under `target/`.

## MySQL

1. Create a MySQL database user.
2. Run `src/main/resources/schema.sql`.
3. Copy `src/main/resources/app.properties.example` to `src/main/resources/app.properties`.
4. Fill in `db.url`, `db.username`, and `db.password`.

## QQ Email

Use QQ Mail SMTP authorization code, not the QQ login password.

```properties
mail.devMode=false
qq.smtp.username=your@qq.com
qq.smtp.authCode=your_authorization_code
```

During local development, keep `mail.devMode=true`; verification codes are printed to the server console.

## Tomcat

Use Tomcat 10.1 or newer. Deploy the WAR into `webapps/`, then restart Tomcat.

## Nginx

```nginx
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }

    location /ws/ {
        proxy_pass http://127.0.0.1:8080/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }
}
```

HTTPS is required for browser microphone access and WebRTC.

## Uploads

Configure `upload.root` to a persistent directory. The application serves uploaded files through `/uploads/*`.
