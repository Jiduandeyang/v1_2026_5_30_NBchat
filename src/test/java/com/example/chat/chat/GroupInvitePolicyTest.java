package com.example.chat.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupInvitePolicyTest {
    @Test
    void closeFriendCanJoinDirectlyWithoutApproval() {
        assertEquals(GroupInviteDecision.ADD_DIRECTLY, GroupInvitePolicy.decide(true));
    }

    @Test
    void normalFriendRequiresInvitationApproval() {
        assertEquals(GroupInviteDecision.CREATE_INVITATION, GroupInvitePolicy.decide(false));
    }
}
