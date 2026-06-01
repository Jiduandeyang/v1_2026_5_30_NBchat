package com.example.chat.chat;

public final class GroupInvitePolicy {
    private GroupInvitePolicy() {
    }

    public static GroupInviteDecision decide(boolean invitedUserMarkedOwnerClose) {
        return invitedUserMarkedOwnerClose ? GroupInviteDecision.ADD_DIRECTLY : GroupInviteDecision.CREATE_INVITATION;
    }
}
