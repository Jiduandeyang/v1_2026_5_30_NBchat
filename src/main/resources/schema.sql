CREATE DATABASE IF NOT EXISTS jakarta_chat DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE jakarta_chat;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(40) NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    qq_email VARCHAR(120) NOT NULL UNIQUE,
    nickname VARCHAR(60) NOT NULL,
    avatar_url VARCHAR(255),
    background_url VARCHAR(255),
    signature VARCHAR(160),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS email_verification_codes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    qq_email VARCHAR(120) NOT NULL,
    code VARCHAR(12) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS password_reset_codes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    qq_email VARCHAR(120) NOT NULL,
    code VARCHAR(12) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS friend_groups (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    name VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS friend_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    message VARCHAR(160),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (sender_id) REFERENCES users(id),
    FOREIGN KEY (receiver_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS friendships (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    friend_id BIGINT NOT NULL,
    group_id BIGINT,
    close_friend TINYINT(1) NOT NULL DEFAULT 0,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_friendship_owner_friend (owner_id, friend_id),
    FOREIGN KEY (owner_id) REFERENCES users(id),
    FOREIGN KEY (friend_id) REFERENCES users(id),
    FOREIGN KEY (group_id) REFERENCES friend_groups(id)
);

CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(80),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversation_members (
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (conversation_id, user_id),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    media_id BIGINT,
    reply_to_message_id BIGINT,
    recalled_at TIMESTAMP NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (sender_id) REFERENCES users(id),
    FOREIGN KEY (reply_to_message_id) REFERENCES messages(id)
);

CREATE TABLE IF NOT EXISTS message_visibility (
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    hidden TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (message_id, user_id),
    FOREIGN KEY (message_id) REFERENCES messages(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS conversation_reads (
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_message_id BIGINT NOT NULL DEFAULT 0,
    read_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, user_id),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS message_reactions (
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    emoji VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id, user_id, emoji),
    FOREIGN KEY (message_id) REFERENCES messages(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS chat_groups (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS chat_group_members (
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    PRIMARY KEY (group_id, user_id),
    FOREIGN KEY (group_id) REFERENCES chat_groups(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS chat_group_invitations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    inviter_id BIGINT NOT NULL,
    invitee_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_invite (group_id, invitee_id),
    FOREIGN KEY (group_id) REFERENCES chat_groups(id),
    FOREIGN KEY (inviter_id) REFERENCES users(id),
    FOREIGN KEY (invitee_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS media_files (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    kind VARCHAR(32) NOT NULL,
    original_name VARCHAR(180) NOT NULL,
    stored_name VARCHAR(180) NOT NULL,
    url VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS moments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    author_id BIGINT NOT NULL,
    text TEXT,
    visibility VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS moment_media (
    moment_id BIGINT NOT NULL,
    media_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (moment_id, media_id),
    FOREIGN KEY (moment_id) REFERENCES moments(id),
    FOREIGN KEY (media_id) REFERENCES media_files(id)
);

CREATE TABLE IF NOT EXISTS moment_visibility_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    moment_id BIGINT NOT NULL,
    rule_type VARCHAR(32) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    FOREIGN KEY (moment_id) REFERENCES moments(id)
);

CREATE TABLE IF NOT EXISTS moment_likes (
    moment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (moment_id, user_id),
    FOREIGN KEY (moment_id) REFERENCES moments(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS moment_comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    moment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (moment_id) REFERENCES moments(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS voice_call_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    caller_id BIGINT NOT NULL,
    callee_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL,
    duration_seconds INT DEFAULT 0,
    FOREIGN KEY (caller_id) REFERENCES users(id),
    FOREIGN KEY (callee_id) REFERENCES users(id)
);

-- Demo accounts for local testing.
-- alice / 123456
-- bob / 123456
-- admin / admin123
INSERT INTO users(id, username, password_hash, qq_email, nickname, avatar_url, background_url, signature, role)
VALUES
    (1, 'alice', '$2a$10$evwCGWLianGYC8BsQa4tVOOIChAWSvab/4j5ROcv.REJrmUOoiWJO', '10001@qq.com', 'Alice', NULL, NULL, 'Ready to chat.', 'USER'),
    (2, 'bob', '$2a$10$evwCGWLianGYC8BsQa4tVOOIChAWSvab/4j5ROcv.REJrmUOoiWJO', '10002@qq.com', 'Bob', NULL, NULL, 'Coffee and code.', 'USER'),
    (3, 'admin', '$2a$10$VTZPs7YWKq/MjfzAZhTMN.ucprfbGd5cPTlKfEKsNMIkaZB6lA5I6', '10000@qq.com', 'Admin', NULL, NULL, 'System administrator.', 'ADMIN')
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    qq_email = VALUES(qq_email),
    nickname = VALUES(nickname),
    signature = VALUES(signature),
    role = VALUES(role);

INSERT INTO friend_groups(id, owner_id, name)
VALUES
    (1, 1, 'My Friends'),
    (2, 2, 'My Friends'),
    (3, 3, 'Administrators')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO friendships(owner_id, friend_id, group_id, active)
VALUES
    (1, 2, 1, 1),
    (2, 1, 2, 1),
    (1, 3, 1, 1),
    (3, 1, 3, 1),
    (2, 3, 2, 1),
    (3, 2, 3, 1)
ON DUPLICATE KEY UPDATE group_id = VALUES(group_id), active = VALUES(active);

INSERT INTO friend_requests(id, sender_id, receiver_id, message, status)
VALUES
    (1, 2, 1, 'Hi Alice, let us test friend requests.', 'ACCEPTED'),
    (2, 3, 1, 'Admin verification message.', 'ACCEPTED')
ON DUPLICATE KEY UPDATE message = VALUES(message), status = VALUES(status);

INSERT INTO conversations(id, type, title)
VALUES
    (1, 'PRIVATE', 'Alice and Bob'),
    (2, 'GROUP', 'Demo Group')
ON DUPLICATE KEY UPDATE title = VALUES(title);

INSERT INTO conversation_members(conversation_id, user_id)
VALUES
    (1, 1),
    (1, 2),
    (2, 1),
    (2, 2),
    (2, 3)
ON DUPLICATE KEY UPDATE user_id = VALUES(user_id);

INSERT INTO chat_groups(id, conversation_id, owner_id, name)
VALUES
    (1, 2, 3, 'Demo Group')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO chat_group_members(group_id, user_id, role)
VALUES
    (1, 3, 'OWNER'),
    (1, 1, 'MEMBER'),
    (1, 2, 'MEMBER')
ON DUPLICATE KEY UPDATE role = VALUES(role);

INSERT INTO messages(id, conversation_id, sender_id, type, content)
VALUES
    (1, 1, 1, 'TEXT', 'Hi Bob, this is a seeded private chat.'),
    (2, 1, 2, 'TEXT', 'Hi Alice, realtime chat is ready to test.'),
    (3, 2, 3, 'TEXT', 'Welcome to the demo group.')
ON DUPLICATE KEY UPDATE content = VALUES(content);

INSERT INTO moments(id, author_id, text, visibility)
VALUES
    (1, 1, 'First demo moment from Alice.', 'ALL_FRIENDS'),
    (2, 2, 'Bob is testing the moments feed.', 'ALL_FRIENDS'),
    (3, 3, 'Admin notice: demo data has been initialized.', 'ALL_FRIENDS')
ON DUPLICATE KEY UPDATE text = VALUES(text), visibility = VALUES(visibility);

INSERT INTO moment_likes(moment_id, user_id)
VALUES
    (1, 2),
    (1, 3),
    (2, 1)
ON DUPLICATE KEY UPDATE user_id = VALUES(user_id);

INSERT INTO moment_comments(id, moment_id, user_id, content)
VALUES
    (1, 1, 2, 'Nice demo moment.'),
    (2, 2, 1, 'I can see this in the feed.')
ON DUPLICATE KEY UPDATE content = VALUES(content);
