package com.example.chat.chat;

import java.util.List;

public record GroupInviteRequest(List<Long> memberIds) {
}
