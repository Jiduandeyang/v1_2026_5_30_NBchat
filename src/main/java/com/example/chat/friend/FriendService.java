package com.example.chat.friend;

import com.example.chat.common.Transactional;
import com.example.chat.common.Validation;
import com.example.chat.model.FriendGroup;
import com.example.chat.model.FriendRequestView;
import com.example.chat.model.User;

import java.util.List;

public class FriendService {
    private final FriendDao friendDao = new FriendDao();

    public List<FriendGroup> groups(long userId) {
        return Transactional.withConnection(c -> friendDao.groups(c, userId));
    }

    public long createGroup(long userId, String name) {
        Validation.notBlank(name, "Group name is required.");
        return Transactional.withConnection(c -> friendDao.createGroup(c, userId, name));
    }

    public void renameGroup(long userId, long groupId, String name) {
        Transactional.withConnection(c -> {
            friendDao.renameGroup(c, userId, groupId, name);
            return null;
        });
    }

    public void moveFriend(long userId, long friendId, long groupId) {
        Transactional.withConnection(c -> {
            friendDao.moveFriend(c, userId, friendId, groupId);
            return null;
        });
    }

    public void setCloseFriend(long userId, long friendId, boolean closeFriend) {
        Transactional.withConnection(c -> {
            friendDao.setCloseFriend(c, userId, friendId, closeFriend);
            return null;
        });
    }

    public void sendRequest(long senderId, FriendRequestCreate request) {
        Validation.require(senderId != request.receiverId(), "You cannot add yourself.");
        Transactional.withConnection(c -> {
            friendDao.sendRequest(c, senderId, request.receiverId(), request.message());
            return null;
        });
    }

    public List<FriendRequestView> requests(long userId, String mode) {
        return Transactional.withConnection(c -> friendDao.requests(c, userId, mode));
    }

    public void accept(long userId, long requestId) {
        Transactional.withConnection(c -> {
            FriendRequestView request = friendDao.request(c, requestId);
            Validation.require(request != null && request.receiverId() == userId, "Friend request not found.");
            friendDao.updateRequest(c, requestId, userId, "ACCEPTED");
            friendDao.createFriendshipPair(c, request.senderId(), request.receiverId());
            return null;
        });
    }

    public void reject(long userId, long requestId) {
        Transactional.withConnection(c -> {
            friendDao.updateRequest(c, requestId, userId, "REJECTED");
            return null;
        });
    }

    public List<User> friends(long userId) {
        return Transactional.withConnection(c -> friendDao.friends(c, userId));
    }

    public void deleteFriend(long userId, long friendId) {
        Transactional.withConnection(c -> {
            friendDao.deleteForOwnerOnly(c, userId, friendId);
            return null;
        });
    }
}
