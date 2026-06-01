# WhisperChat Jakarta EE Demo

## Built-in Test Accounts

After importing `src/main/resources/schema.sql`, you can log in with:

| Role | Username | Password |
| --- | --- | --- |
| User | `alice` | `123456` |
| User | `bob` | `123456` |
| Admin | `admin` | `admin123` |

The demo data also creates friendships, one private conversation, one group conversation, and several moments.

## Local Run

1. Create a MySQL database by running:

   ```sql
   SOURCE src/main/resources/schema.sql;
   ```

   If you already imported an older version before the `role` column was added, the simplest local reset is:

   ```sql
   DROP DATABASE IF EXISTS jakarta_chat;
   SOURCE src/main/resources/schema.sql;
   ```

2. Copy `src/main/resources/app.properties.example` to `src/main/resources/app.properties`.

3. Update database settings in `app.properties`:

   ```properties
   db.url=jdbc:mysql://localhost:3306/jakarta_chat?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
   db.username=root
   db.password=your_password
   mail.devMode=true
   ```

4. Build the WAR:

   ```bash
   .\mvnw.cmd clean package
   ```

5. Deploy `target/v1_2026_5_30-1.0-SNAPSHOT.war` to Tomcat 10.1+.

6. Open the app in a browser:

   ```text
   http://localhost:8080/v1_2026_5_30-1.0-SNAPSHOT/
   ```

The login page is `index.html`; successful login redirects to `app.html`.
