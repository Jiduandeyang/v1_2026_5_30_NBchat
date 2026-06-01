package com.example.chat.moment;

import com.example.chat.common.Transactional;
import com.example.chat.common.Validation;
import com.example.chat.model.MomentCommentView;
import com.example.chat.model.MomentView;

import java.util.List;

public class MomentService {
    private final MomentDao momentDao = new MomentDao();

    public long create(long userId, MomentCreateRequest request) {
        List<Long> mediaIds = request.mediaIds() == null ? List.of() : request.mediaIds();
        Validation.require(mediaIds.size() <= 6, "A moment can contain at most six images or one video.");
        return Transactional.withConnection(c -> momentDao.create(c, userId, request));
    }

    public List<MomentView> feed(long userId) {
        return feed(userId, 20, null);
    }

    public List<MomentView> feed(long userId, int limit, Long beforeId) {
        return Transactional.withConnection(c -> momentDao.feed(c, userId, limit, beforeId));
    }

    public void like(long userId, long momentId) {
        Transactional.withConnection(c -> {
            momentDao.like(c, userId, momentId);
            return null;
        });
    }

    public void unlike(long userId, long momentId) {
        Transactional.withConnection(c -> {
            momentDao.unlike(c, userId, momentId);
            return null;
        });
    }

    public void comment(long userId, long momentId, String content) {
        Validation.notBlank(content, "Comment is required.");
        Transactional.withConnection(c -> {
            momentDao.comment(c, userId, momentId, content);
            return null;
        });
    }

    public List<MomentCommentView> comments(long userId, long momentId) {
        return Transactional.withConnection(c -> momentDao.comments(c, momentId));
    }

    public void deleteComment(long userId, long commentId) {
        Transactional.withConnection(c -> {
            momentDao.deleteComment(c, userId, commentId);
            return null;
        });
    }

    public void delete(long userId, long momentId) {
        Transactional.withConnection(c -> {
            momentDao.delete(c, userId, momentId);
            return null;
        });
    }
}
