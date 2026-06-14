package com.example.chat.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {
    @Test
    void exposesFriendGroupIdWhenUserIsLoadedAsFriend() {
        User user = new User(2, "bob", "10002@qq.com", "Bob", null, null, "Coffee", "USER", false, 7L, true);

        assertEquals(7L, user.groupId());
        assertTrue(user.closeFriend());
    }
}
