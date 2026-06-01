package com.example.chat.chat;

import java.util.List;

public record GroupCreateRequest(String name, List<Long> memberIds) {
}
