package com.example.chat.model;

import java.time.LocalDate;

public record DailyMessageCount(LocalDate day, int count) {
}
