package com.example.chat.chat;

import com.example.chat.common.Jdbc;
import com.example.chat.config.AppConfig;
import com.example.chat.media.MediaUrlBuilder;
import com.example.chat.model.ChatMessage;
import com.example.chat.model.Conversation;
import com.example.chat.model.DailyMessageCount;
import com.example.chat.model.GroupMemberView;
import com.example.chat.model.GroupInvitationView;
import com.example.chat.model.MessageReactionSummary;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public class ChatDao {
    public long createPrivateConversation(Connection connection, long a, long b) throws SQLException {
        Long existing = Jdbc.one(connection,
                "SELECT c.id FROM conversations c JOIN conversation_members m1 ON m1.conversation_id=c.id AND m1.user_id=? JOIN conversation_members m2 ON m2.conversation_id=c.id AND m2.user_id=? WHERE c.type='PRIVATE' LIMIT 1",
                ps -> {
                    ps.setLong(1, a);
                    ps.setLong(2, b);
                }, rs -> rs.getLong("id"));
        if (existing != null) {
            return existing;
        }
        String title = Jdbc.one(connection, "SELECT COALESCE(nickname, username) FROM users WHERE id=?",
                ps -> ps.setLong(1, b),
                rs -> rs.getString(1));
        long conversationId = Jdbc.insert(connection, "INSERT INTO conversations(type,title) VALUES('PRIVATE',?)",
                ps -> ps.setString(1, title == null ? "Private chat" : title));
        addMember(connection, conversationId, a);
        addMember(connection, conversationId, b);
        return conversationId;
    }

    public long createGroup(Connection connection, long ownerId, String name) throws SQLException {
        long conversationId = Jdbc.insert(connection, "INSERT INTO conversations(type,title) VALUES('GROUP',?)",
                ps -> ps.setString(1, name));
        long groupId = Jdbc.insert(connection, "INSERT INTO chat_groups(conversation_id,owner_id,name) VALUES(?,?,?)", ps -> {
            ps.setLong(1, conversationId);
            ps.setLong(2, ownerId);
            ps.setString(3, name);
        });
        addMember(connection, conversationId, ownerId);
        Jdbc.update(connection, "INSERT INTO chat_group_members(group_id,user_id,role) VALUES(?,?,?)", ps -> {
            ps.setLong(1, groupId);
            ps.setLong(2, ownerId);
            ps.setString(3, "OWNER");
        });
        return conversationId;
    }

    public Long groupIdForConversation(Connection connection, long conversationId) throws SQLException {
        return Jdbc.one(connection, "SELECT id FROM chat_groups WHERE conversation_id=?",
                ps -> ps.setLong(1, conversationId), rs -> rs.getLong("id"));
    }

    public Long conversationIdForGroup(Connection connection, long groupId) throws SQLException {
        return Jdbc.one(connection, "SELECT conversation_id FROM chat_groups WHERE id=?",
                ps -> ps.setLong(1, groupId), rs -> rs.getLong("conversation_id"));
    }

    public List<GroupMemberView> groupMembers(Connection connection, long conversationId) throws SQLException {
        return Jdbc.list(connection,
                "SELECT u.id user_id,u.username,u.nickname,u.avatar_url,cgm.role FROM chat_groups cg " +
                        "JOIN chat_group_members cgm ON cgm.group_id=cg.id JOIN users u ON u.id=cgm.user_id " +
                        "WHERE cg.conversation_id=? ORDER BY FIELD(cgm.role,'OWNER','ADMIN','MEMBER'),u.nickname,u.username",
                ps -> ps.setLong(1, conversationId),
                rs -> new GroupMemberView(rs.getLong("user_id"), rs.getString("username"),
                        rs.getString("nickname"), rs.getString("avatar_url"), rs.getString("role")));
    }

    public String memberRole(Connection connection, long conversationId, long userId) throws SQLException {
        return Jdbc.one(connection,
                "SELECT cgm.role FROM chat_groups cg JOIN chat_group_members cgm ON cgm.group_id=cg.id " +
                        "WHERE cg.conversation_id=? AND cgm.user_id=?",
                ps -> {
                    ps.setLong(1, conversationId);
                    ps.setLong(2, userId);
                }, rs -> rs.getString("role"));
    }

    public void renameGroup(Connection connection, long conversationId, String name) throws SQLException {
        Jdbc.update(connection, "UPDATE chat_groups SET name=? WHERE conversation_id=?", ps -> {
            ps.setString(1, name);
            ps.setLong(2, conversationId);
        });
        Jdbc.update(connection, "UPDATE conversations SET title=? WHERE id=? AND type='GROUP'", ps -> {
            ps.setString(1, name);
            ps.setLong(2, conversationId);
        });
    }

    public void addGroupMember(Connection connection, long groupId, long userId) throws SQLException {
        Long conversationId = conversationIdForGroup(connection, groupId);
        if (conversationId == null) {
            return;
        }
        addMember(connection, conversationId, userId);
        Jdbc.update(connection, "INSERT IGNORE INTO chat_group_members(group_id,user_id,role) VALUES(?,?,?)", ps -> {
            ps.setLong(1, groupId);
            ps.setLong(2, userId);
            ps.setString(3, "MEMBER");
        });
    }

    public void setGroupMemberRole(Connection connection, long conversationId, long memberId, String role) throws SQLException {
        Jdbc.update(connection,
                "UPDATE chat_group_members cgm JOIN chat_groups cg ON cg.id=cgm.group_id " +
                        "SET cgm.role=? WHERE cg.conversation_id=? AND cgm.user_id=? AND cgm.role<>'OWNER'",
                ps -> {
                    ps.setString(1, role);
                    ps.setLong(2, conversationId);
                    ps.setLong(3, memberId);
                });
    }

    public void removeGroupMember(Connection connection, long conversationId, long memberId) throws SQLException {
        Long groupId = groupIdForConversation(connection, conversationId);
        if (groupId == null) {
            return;
        }
        Jdbc.update(connection, "DELETE FROM chat_group_members WHERE group_id=? AND user_id=? AND role<>'OWNER'", ps -> {
            ps.setLong(1, groupId);
            ps.setLong(2, memberId);
        });
        Jdbc.update(connection, "DELETE FROM conversation_members WHERE conversation_id=? AND user_id=?", ps -> {
            ps.setLong(1, conversationId);
            ps.setLong(2, memberId);
        });
    }

    public void createGroupInvitation(Connection connection, long groupId, long inviterId, long inviteeId) throws SQLException {
        Jdbc.update(connection,
                "INSERT INTO chat_group_invitations(group_id,inviter_id,invitee_id,status) VALUES(?,?,?,'PENDING') " +
                        "ON DUPLICATE KEY UPDATE status='PENDING', inviter_id=VALUES(inviter_id)",
                ps -> {
                    ps.setLong(1, groupId);
                    ps.setLong(2, inviterId);
                    ps.setLong(3, inviteeId);
                });
    }

    public GroupInvitationView invitation(Connection connection, long invitationId) throws SQLException {
        return Jdbc.one(connection,
                "SELECT gi.id,gi.group_id,cg.conversation_id,cg.name group_name,gi.inviter_id,COALESCE(iu.nickname,iu.username) inviter_name," +
                        "gi.invitee_id,COALESCE(eu.nickname,eu.username) invitee_name,gi.status,gi.created_at " +
                        "FROM chat_group_invitations gi JOIN chat_groups cg ON cg.id=gi.group_id " +
                        "JOIN users iu ON iu.id=gi.inviter_id JOIN users eu ON eu.id=gi.invitee_id WHERE gi.id=?",
                ps -> ps.setLong(1, invitationId), this::mapInvitation);
    }

    public List<GroupInvitationView> invitations(Connection connection, long userId, String mode) throws SQLException {
        String column = "sent".equals(mode) ? "gi.inviter_id" : "gi.invitee_id";
        return Jdbc.list(connection,
                "SELECT gi.id,gi.group_id,cg.conversation_id,cg.name group_name,gi.inviter_id,COALESCE(iu.nickname,iu.username) inviter_name," +
                        "gi.invitee_id,COALESCE(eu.nickname,eu.username) invitee_name,gi.status,gi.created_at " +
                        "FROM chat_group_invitations gi JOIN chat_groups cg ON cg.id=gi.group_id " +
                        "JOIN users iu ON iu.id=gi.inviter_id JOIN users eu ON eu.id=gi.invitee_id " +
                        "WHERE " + column + "=? ORDER BY gi.id DESC",
                ps -> ps.setLong(1, userId), this::mapInvitation);
    }

    public void updateInvitationStatus(Connection connection, long invitationId, String status) throws SQLException {
        Jdbc.update(connection, "UPDATE chat_group_invitations SET status=? WHERE id=?", ps -> {
            ps.setString(1, status);
            ps.setLong(2, invitationId);
        });
    }

    public List<Conversation> conversations(Connection connection, long userId) throws SQLException {
        return Jdbc.list(connection,
                "SELECT c.id,c.type," +
                        "CASE WHEN c.type='PRIVATE' THEN COALESCE(peer.nickname, peer.username, c.title) ELSE c.title END title," +
                        "peer.id peer_id,COALESCE(peer.nickname, peer.username) peer_name,peer.avatar_url peer_avatar_url," +
                        "lm.content last_message,lm.type last_message_type,lm.sent_at last_sent_at," +
                        "(SELECT COUNT(*) FROM messages mx " +
                        "LEFT JOIN message_visibility mvx ON mvx.message_id=mx.id AND mvx.user_id=? " +
                        "LEFT JOIN conversation_reads crx ON crx.conversation_id=mx.conversation_id AND crx.user_id=? " +
                        "WHERE mx.conversation_id=c.id AND mx.sender_id<>? AND COALESCE(mvx.hidden,0)=0 AND mx.id>COALESCE(crx.last_read_message_id,0)) unread_count " +
                        "FROM conversations c " +
                        "JOIN conversation_members cm ON cm.conversation_id=c.id AND cm.user_id=? " +
                        "LEFT JOIN conversation_members pcm ON pcm.conversation_id=c.id AND pcm.user_id<>? AND c.type='PRIVATE' " +
                        "LEFT JOIN users peer ON peer.id=pcm.user_id " +
                        "LEFT JOIN messages lm ON lm.id=(SELECT MAX(m2.id) FROM messages m2 " +
                        "LEFT JOIN message_visibility mv2 ON mv2.message_id=m2.id AND mv2.user_id=? " +
                        "WHERE m2.conversation_id=c.id AND COALESCE(mv2.hidden,0)=0) " +
                        "ORDER BY COALESCE(lm.sent_at,c.created_at) DESC,c.id DESC",
                ps -> {
                    ps.setLong(1, userId);
                    ps.setLong(2, userId);
                    ps.setLong(3, userId);
                    ps.setLong(4, userId);
                    ps.setLong(5, userId);
                    ps.setLong(6, userId);
                },
                rs -> {
                    long peerIdValue = rs.getLong("peer_id");
                    Long peerId = rs.wasNull() ? null : peerIdValue;
                    var lastSentAt = rs.getTimestamp("last_sent_at");
                    return new Conversation(rs.getLong("id"), rs.getString("type"), rs.getString("title"),
                            peerId, rs.getString("peer_name"), rs.getString("peer_avatar_url"),
                            rs.getString("last_message"), rs.getString("last_message_type"),
                            lastSentAt == null ? null : lastSentAt.toLocalDateTime(), rs.getInt("unread_count"));
                });
    }

    public long saveMessage(Connection connection, long senderId, SendMessageRequest message) throws SQLException {
        return Jdbc.insert(connection, "INSERT INTO messages(conversation_id,sender_id,type,content,media_id,reply_to_message_id) VALUES(?,?,?,?,?,?)",
                ps -> {
                    ps.setLong(1, message.conversationId());
                    ps.setLong(2, senderId);
                    ps.setString(3, message.type());
                    ps.setString(4, message.content());
                    if (message.mediaId() == null) {
                        ps.setNull(5, Types.BIGINT);
                    } else {
                        ps.setLong(5, message.mediaId());
                    }
                    if (message.replyToMessageId() == null) {
                        ps.setNull(6, Types.BIGINT);
                    } else {
                        ps.setLong(6, message.replyToMessageId());
                    }
                });
    }

    public String conversationType(Connection connection, long conversationId) throws SQLException {
        return Jdbc.one(connection, "SELECT type FROM conversations WHERE id=?",
                ps -> ps.setLong(1, conversationId),
                rs -> rs.getString("type"));
    }

    public Long privatePeerId(Connection connection, long conversationId, long userId) throws SQLException {
        return Jdbc.one(connection,
                "SELECT cm.user_id FROM conversation_members cm JOIN conversations c ON c.id=cm.conversation_id AND c.type='PRIVATE' " +
                        "WHERE cm.conversation_id=? AND cm.user_id<>?",
                ps -> {
                    ps.setLong(1, conversationId);
                    ps.setLong(2, userId);
                },
                rs -> rs.getLong("user_id"));
    }

    public boolean isConversationMember(Connection connection, long conversationId, long userId) throws SQLException {
        Long value = Jdbc.one(connection,
                "SELECT 1 FROM conversation_members WHERE conversation_id=? AND user_id=?",
                ps -> {
                    ps.setLong(1, conversationId);
                    ps.setLong(2, userId);
                },
                rs -> rs.getLong(1));
        return value != null;
    }

    public Long conversationIdForMessage(Connection connection, long messageId) throws SQLException {
        return Jdbc.one(connection, "SELECT conversation_id FROM messages WHERE id=?",
                ps -> ps.setLong(1, messageId),
                rs -> rs.getLong("conversation_id"));
    }

    public String displayName(Connection connection, long userId) throws SQLException {
        String name = Jdbc.one(connection, "SELECT COALESCE(nickname, username) display_name FROM users WHERE id=?",
                ps -> ps.setLong(1, userId),
                rs -> rs.getString("display_name"));
        return name == null || name.isBlank() ? "用户 " + userId : name;
    }

    public ChatMessage messageById(Connection connection, long userId, long messageId) throws SQLException {
        return Jdbc.one(connection,
                "SELECT m.id,m.conversation_id,m.sender_id,COALESCE(u.nickname,u.username) sender_name,m.type,m.content,m.media_id,mf.url media_url," +
                        "m.reply_to_message_id,COALESCE(ru.nickname,ru.username) reply_sender_name,rm.content reply_content,m.recalled_at,m.sent_at FROM messages m " +
                        "JOIN users u ON u.id=m.sender_id JOIN conversation_members cm ON cm.conversation_id=m.conversation_id AND cm.user_id=? " +
                        "LEFT JOIN media_files mf ON mf.id=m.media_id " +
                        "LEFT JOIN messages rm ON rm.id=m.reply_to_message_id " +
                        "LEFT JOIN users ru ON ru.id=rm.sender_id " +
                        "LEFT JOIN message_visibility mv ON mv.message_id=m.id AND mv.user_id=? " +
                        "WHERE m.id=? AND COALESCE(mv.hidden,0)=0",
                ps -> {
                    ps.setLong(1, userId);
                    ps.setLong(2, userId);
                    ps.setLong(3, messageId);
                },
                rs -> mapMessage(rs, List.of()));
    }

    public List<ChatMessage> history(Connection connection, long userId, long conversationId, String keyword) throws SQLException {
        return history(connection, userId, conversationId, keyword, ChatHistoryPageRequest.from(null, null));
    }

    public List<ChatMessage> history(Connection connection, long userId, long conversationId, String keyword, ChatHistoryPageRequest page) throws SQLException {
        String like = "%" + (keyword == null ? "" : keyword) + "%";
        String beforeClause = page.beforeId() == null ? "" : " AND m.id < ? ";
        String sql = "SELECT * FROM (" +
                "SELECT m.id,m.conversation_id,m.sender_id,u.nickname sender_name,m.type,m.content,m.media_id,mf.url media_url," +
                        "m.reply_to_message_id,ru.nickname reply_sender_name,rm.content reply_content,m.recalled_at,m.sent_at FROM messages m " +
                "JOIN users u ON u.id=m.sender_id JOIN conversation_members cm ON cm.conversation_id=m.conversation_id AND cm.user_id=? " +
                "LEFT JOIN media_files mf ON mf.id=m.media_id " +
                "LEFT JOIN messages rm ON rm.id=m.reply_to_message_id " +
                "LEFT JOIN users ru ON ru.id=rm.sender_id " +
                "LEFT JOIN message_visibility mv ON mv.message_id=m.id AND mv.user_id=? " +
                "WHERE m.conversation_id=? AND COALESCE(mv.hidden,0)=0 AND m.content LIKE ?" +
                beforeClause +
                " ORDER BY m.id DESC LIMIT ?" +
                ") page ORDER BY page.id";
        return Jdbc.list(connection,
                sql,
                ps -> {
                    ps.setLong(1, userId);
                    ps.setLong(2, userId);
                    ps.setLong(3, conversationId);
                    ps.setString(4, like);
                    int index = 5;
                    if (page.beforeId() != null) {
                        ps.setLong(index++, page.beforeId());
                    }
                    ps.setInt(index, page.limit());
                },
                rs -> mapMessage(rs, List.of()));
    }

    public void markConversationRead(Connection connection, long userId, long conversationId) throws SQLException {
        Long lastVisibleMessageId = Jdbc.one(connection,
                "SELECT MAX(m.id) FROM messages m " +
                        "JOIN conversation_members cm ON cm.conversation_id=m.conversation_id AND cm.user_id=? " +
                        "LEFT JOIN message_visibility mv ON mv.message_id=m.id AND mv.user_id=? " +
                        "WHERE m.conversation_id=? AND COALESCE(mv.hidden,0)=0",
                ps -> {
                    ps.setLong(1, userId);
                    ps.setLong(2, userId);
                    ps.setLong(3, conversationId);
                },
                rs -> rs.getLong(1));
        if (lastVisibleMessageId == null || lastVisibleMessageId == 0) {
            return;
        }
        Jdbc.update(connection,
                "INSERT INTO conversation_reads(conversation_id,user_id,last_read_message_id) VALUES(?,?,?) " +
                        "ON DUPLICATE KEY UPDATE last_read_message_id=GREATEST(last_read_message_id,VALUES(last_read_message_id)),read_at=CURRENT_TIMESTAMP",
                ps -> {
                    ps.setLong(1, conversationId);
                    ps.setLong(2, userId);
                    ps.setLong(3, lastVisibleMessageId);
                });
    }

    public boolean hideBurnMessageForUser(Connection connection, long userId, long messageId) throws SQLException {
        return Jdbc.update(connection,
                "INSERT INTO message_visibility(message_id,user_id,hidden) " +
                        "SELECT m.id,?,1 FROM messages m " +
                        "JOIN conversation_members cm ON cm.conversation_id=m.conversation_id AND cm.user_id=? " +
                        "LEFT JOIN message_visibility mv ON mv.message_id=m.id AND mv.user_id=? " +
                        "WHERE m.id=? AND m.type='BURN' AND COALESCE(mv.hidden,0)=0 " +
                        "ON DUPLICATE KEY UPDATE hidden=1",
                ps -> {
                    ps.setLong(1, userId);
                    ps.setLong(2, userId);
                    ps.setLong(3, userId);
                    ps.setLong(4, messageId);
                }) > 0;
    }

    public List<MessageReactionSummary> reactions(Connection connection, long userId, long messageId) throws SQLException {
        return Jdbc.list(connection,
                "SELECT mr.emoji,COUNT(*) reaction_count,MAX(CASE WHEN mr.user_id=? THEN 1 ELSE 0 END) mine " +
                        "FROM message_reactions mr WHERE mr.message_id=? GROUP BY mr.emoji ORDER BY reaction_count DESC,mr.emoji",
                ps -> {
                    ps.setLong(1, userId);
                    ps.setLong(2, messageId);
                },
                rs -> new MessageReactionSummary(rs.getString("emoji"), rs.getInt("reaction_count"), rs.getBoolean("mine")));
    }

    public void addReaction(Connection connection, long userId, long messageId, String emoji) throws SQLException {
        Jdbc.update(connection,
                "INSERT IGNORE INTO message_reactions(message_id,user_id,emoji) " +
                        "SELECT ?,?,? FROM messages m JOIN conversation_members cm ON cm.conversation_id=m.conversation_id AND cm.user_id=? WHERE m.id=?",
                ps -> {
                    ps.setLong(1, messageId);
                    ps.setLong(2, userId);
                    ps.setString(3, emoji);
                    ps.setLong(4, userId);
                    ps.setLong(5, messageId);
                });
    }

    public void removeReaction(Connection connection, long userId, long messageId, String emoji) throws SQLException {
        Jdbc.update(connection, "DELETE FROM message_reactions WHERE message_id=? AND user_id=? AND emoji=?", ps -> {
            ps.setLong(1, messageId);
            ps.setLong(2, userId);
            ps.setString(3, emoji);
        });
    }

    public boolean recallMessage(Connection connection, long userId, long messageId) throws SQLException {
        return Jdbc.update(connection,
                "UPDATE messages SET type='RECALLED',content='消息已撤回',recalled_at=NOW() " +
                        "WHERE id=? AND sender_id=? AND recalled_at IS NULL AND sent_at>=DATE_SUB(NOW(), INTERVAL 2 MINUTE)",
                ps -> {
                    ps.setLong(1, messageId);
                    ps.setLong(2, userId);
                }) > 0;
    }

    public List<Long> recipients(Connection connection, long conversationId, long senderId) throws SQLException {
        return Jdbc.list(connection, "SELECT user_id FROM conversation_members WHERE conversation_id=? AND user_id<>?",
                ps -> {
                    ps.setLong(1, conversationId);
                    ps.setLong(2, senderId);
                }, rs -> rs.getLong("user_id"));
    }

    public List<DailyMessageCount> dailyMessageCounts(Connection connection, long userId, long conversationId) throws SQLException {
        return Jdbc.list(connection,
                "SELECT DATE(m.sent_at) day,COUNT(*) message_count FROM messages m " +
                        "JOIN conversation_members cm ON cm.conversation_id=m.conversation_id AND cm.user_id=? " +
                        "LEFT JOIN message_visibility mv ON mv.message_id=m.id AND mv.user_id=? " +
                        "WHERE m.conversation_id=? AND m.sent_at>=DATE_SUB(CURDATE(), INTERVAL 364 DAY) AND COALESCE(mv.hidden,0)=0 " +
                        "GROUP BY DATE(m.sent_at) ORDER BY day",
                ps -> {
                    ps.setLong(1, userId);
                    ps.setLong(2, userId);
                    ps.setLong(3, conversationId);
                },
                rs -> new DailyMessageCount(rs.getDate("day").toLocalDate(), rs.getInt("message_count")));
    }

    public boolean leaveGroup(Connection connection, long conversationId, long userId) throws SQLException {
        Long groupId = groupIdForConversation(connection, conversationId);
        if (groupId == null) {
            return false;
        }
        int removed = Jdbc.update(connection, "DELETE FROM chat_group_members WHERE group_id=? AND user_id=? AND role<>'OWNER'", ps -> {
            ps.setLong(1, groupId);
            ps.setLong(2, userId);
        });
        if (removed > 0) {
            Jdbc.update(connection, "DELETE FROM conversation_members WHERE conversation_id=? AND user_id=?", ps -> {
                ps.setLong(1, conversationId);
                ps.setLong(2, userId);
            });
        }
        return removed > 0;
    }

    private void addMember(Connection connection, long conversationId, long userId) throws SQLException {
        Jdbc.update(connection, "INSERT IGNORE INTO conversation_members(conversation_id,user_id) VALUES(?,?)", ps -> {
            ps.setLong(1, conversationId);
            ps.setLong(2, userId);
        });
    }

    private GroupInvitationView mapInvitation(java.sql.ResultSet rs) throws SQLException {
        return new GroupInvitationView(rs.getLong("id"), rs.getLong("group_id"), rs.getLong("conversation_id"),
                rs.getString("group_name"), rs.getLong("inviter_id"), rs.getString("inviter_name"),
                rs.getLong("invitee_id"), rs.getString("invitee_name"), rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }

    private ChatMessage mapMessage(java.sql.ResultSet rs, List<MessageReactionSummary> reactions) throws SQLException {
        long mediaIdValue = rs.getLong("media_id");
        Long mediaId = rs.wasNull() ? null : mediaIdValue;
        long replyIdValue = rs.getLong("reply_to_message_id");
        Long replyId = rs.wasNull() ? null : replyIdValue;
        String replyContent = rs.getString("reply_content");
        String replyPreview = replyContent == null ? null : (replyContent.length() > 80 ? replyContent.substring(0, 80) + "..." : replyContent);
        var recalledAt = rs.getTimestamp("recalled_at");
        String mediaUrl = MediaUrlBuilder.normalize(AppConfig.get("public.baseUrl", ""), rs.getString("media_url"));
        return new ChatMessage(rs.getLong("id"), rs.getLong("conversation_id"), rs.getLong("sender_id"),
                rs.getString("sender_name"), rs.getString("type"), rs.getString("content"), mediaId,
                mediaUrl, replyId, rs.getString("reply_sender_name"), replyPreview,
                reactions, recalledAt == null ? null : recalledAt.toLocalDateTime(),
                rs.getTimestamp("sent_at").toLocalDateTime());
    }
}
