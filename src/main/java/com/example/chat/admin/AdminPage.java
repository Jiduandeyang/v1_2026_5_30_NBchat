package com.example.chat.admin;

import java.util.List;

public record AdminPage<T>(
        List<T> rows,
        int page,
        int size,
        long total
) {
}
