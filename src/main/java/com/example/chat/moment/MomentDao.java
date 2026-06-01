package com.example.chat.moment;

import com.example.chat.common.Jdbc;
import com.example.chat.config.AppConfig;
import com.example.chat.media.MediaUrlBuilder;
import com.example.chat.model.MediaFile;
import com.example.chat.model.MomentCommentView;
import com.example.chat.model.MomentView;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class MomentDao {
    public long create(Connection connection, long authorId, MomentCreateRequest request) throws SQLException {
        long momentId = Jdbc.insert(connection, "INSERT INTO moments(author_id,text,visibility) VALUES(?,?,?)", ps -> {
            ps.setLong(1, authorId);
            ps.setString(2, request.text());
            ps.setString(3, request.visibility());
        });
        List<Long> mediaIds = request.mediaIds() == null ? List.of() : request.mediaIds();
        for (int i = 0; i < mediaIds.size(); i++) {
            int order = i;
            Long mediaId = mediaIds.get(i);
            Jdbc.update(connection, "INSERT INTO moment_media(moment_id,media_id,sort_order) VALUES(?,?,?)", ps -> {
                ps.setLong(1, momentId);
                ps.setLong(2, mediaId);
                ps.setInt(3, order);
            });
        }
        saveRules(connection, momentId, "SELECTED_FRIEND", request.selectedFriendIds());
        saveRules(connection, momentId, "SELECTED_GROUP", request.selectedGroupIds());
        saveRules(connection, momentId, "EXCLUDED_FRIEND", request.excludedFriendIds());
        saveRules(connection, momentId, "EXCLUDED_GROUP", request.excludedGroupIds());
        return momentId;
    }

    public List<MomentView> feed(Connection connection, long userId) throws SQLException {
        return feed(connection, userId, 20, null);
    }

    public List<MomentView> feed(Connection connection, long userId, int limit, Long beforeId) throws SQLException {
        String beforeClause = beforeId == null ? "" : " AND m.id < ? ";
        return Jdbc.list(connection,
                "SELECT m.id,m.author_id,u.nickname author_name,m.text,m.visibility,m.created_at," +
                        "(SELECT COUNT(*) FROM moment_likes ml WHERE ml.moment_id=m.id) like_count," +
                        "(SELECT COUNT(*) FROM moment_comments mc WHERE mc.moment_id=m.id) comment_count," +
                        "EXISTS(SELECT 1 FROM moment_likes my_like WHERE my_like.moment_id=m.id AND my_like.user_id=?) liked_by_me " +
                        "FROM moments m JOIN users u ON u.id=m.author_id WHERE m.deleted=0 AND " +
                        "(m.author_id=? OR (" +
                        "EXISTS (SELECT 1 FROM friendships ff WHERE ff.owner_id=m.author_id AND ff.friend_id=? AND ff.active=1) AND (" +
                        "m.visibility='ALL_FRIENDS' OR " +
                        "(m.visibility='SELECTED' AND (" +
                        "EXISTS (SELECT 1 FROM moment_visibility_rules r WHERE r.moment_id=m.id AND r.rule_type='SELECTED_FRIEND' AND r.target_id=?) OR " +
                        "EXISTS (SELECT 1 FROM moment_visibility_rules r JOIN friendships fg ON fg.owner_id=m.author_id AND fg.friend_id=? AND fg.active=1 AND fg.group_id=r.target_id WHERE r.moment_id=m.id AND r.rule_type='SELECTED_GROUP')" +
                        ")) OR " +
                        "(m.visibility='EXCLUDE' AND " +
                        "NOT EXISTS (SELECT 1 FROM moment_visibility_rules r WHERE r.moment_id=m.id AND r.rule_type='EXCLUDED_FRIEND' AND r.target_id=?) AND " +
                        "NOT EXISTS (SELECT 1 FROM moment_visibility_rules r JOIN friendships fg ON fg.owner_id=m.author_id AND fg.friend_id=? AND fg.active=1 AND fg.group_id=r.target_id WHERE r.moment_id=m.id AND r.rule_type='EXCLUDED_GROUP')" +
                        ")" +
                        ")))" + beforeClause + " ORDER BY m.id DESC LIMIT ?",
                ps -> {
                    ps.setLong(1, userId);
                    ps.setLong(2, userId);
                    ps.setLong(3, userId);
                    ps.setLong(4, userId);
                    ps.setLong(5, userId);
                    ps.setLong(6, userId);
                    ps.setLong(7, userId);
                    int index = 8;
                    if (beforeId != null) {
                        ps.setLong(index++, beforeId);
                    }
                    ps.setInt(index, Math.max(1, Math.min(50, limit)));
                },
                rs -> new MomentView(rs.getLong("id"), rs.getLong("author_id"), rs.getString("author_name"),
                        rs.getString("text"), rs.getString("visibility"), rs.getTimestamp("created_at").toLocalDateTime(),
                        mediaForMoment(connection, rs.getLong("id")), rs.getInt("like_count"), rs.getInt("comment_count"),
                        rs.getBoolean("liked_by_me")));
    }

    private List<MediaFile> mediaForMoment(Connection connection, long momentId) throws SQLException {
        return Jdbc.list(connection,
                "SELECT mf.id,mf.owner_id,mf.kind,mf.original_name,mf.url,mf.content_type,mf.size_bytes " +
                        "FROM moment_media mm JOIN media_files mf ON mf.id=mm.media_id " +
                        "WHERE mm.moment_id=? ORDER BY mm.sort_order,mf.id",
                ps -> ps.setLong(1, momentId),
                rs -> new MediaFile(rs.getLong("id"), rs.getLong("owner_id"), rs.getString("kind"),
                        rs.getString("original_name"), MediaUrlBuilder.normalize(AppConfig.get("public.baseUrl", ""), rs.getString("url")),
                        rs.getString("content_type"), rs.getLong("size_bytes")));
    }

    public void like(Connection connection, long userId, long momentId) throws SQLException {
        Jdbc.update(connection, "INSERT IGNORE INTO moment_likes(moment_id,user_id) VALUES(?,?)", ps -> {
            ps.setLong(1, momentId);
            ps.setLong(2, userId);
        });
    }

    public void unlike(Connection connection, long userId, long momentId) throws SQLException {
        Jdbc.update(connection, "DELETE FROM moment_likes WHERE moment_id=? AND user_id=?", ps -> {
            ps.setLong(1, momentId);
            ps.setLong(2, userId);
        });
    }

    public void comment(Connection connection, long userId, long momentId, String content) throws SQLException {
        Jdbc.update(connection, "INSERT INTO moment_comments(moment_id,user_id,content) VALUES(?,?,?)", ps -> {
            ps.setLong(1, momentId);
            ps.setLong(2, userId);
            ps.setString(3, content);
        });
    }

    public List<MomentCommentView> comments(Connection connection, long momentId) throws SQLException {
        return Jdbc.list(connection,
                "SELECT mc.id,mc.moment_id,mc.user_id,COALESCE(u.nickname,u.username) user_name,mc.content,mc.created_at " +
                        "FROM moment_comments mc JOIN users u ON u.id=mc.user_id WHERE mc.moment_id=? ORDER BY mc.id",
                ps -> ps.setLong(1, momentId),
                rs -> new MomentCommentView(rs.getLong("id"), rs.getLong("moment_id"), rs.getLong("user_id"),
                        rs.getString("user_name"), rs.getString("content"),
                        rs.getTimestamp("created_at").toLocalDateTime()));
    }

    public void deleteComment(Connection connection, long userId, long commentId) throws SQLException {
        Jdbc.update(connection, "DELETE FROM moment_comments WHERE id=? AND user_id=?", ps -> {
            ps.setLong(1, commentId);
            ps.setLong(2, userId);
        });
    }

    public void delete(Connection connection, long userId, long momentId) throws SQLException {
        Jdbc.update(connection, "UPDATE moments SET deleted=1 WHERE id=? AND author_id=?", ps -> {
            ps.setLong(1, momentId);
            ps.setLong(2, userId);
        });
    }

    private void saveRules(Connection connection, long momentId, String ruleType, Iterable<Long> ids) throws SQLException {
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            Jdbc.update(connection,
                    "INSERT INTO moment_visibility_rules(moment_id,rule_type,target_type,target_id) VALUES(?,?,?,?)",
                    ps -> {
                        ps.setLong(1, momentId);
                        ps.setString(2, ruleType);
                        ps.setString(3, ruleType.endsWith("GROUP") ? "GROUP" : "USER");
                        ps.setLong(4, id);
                    });
        }
    }
}
