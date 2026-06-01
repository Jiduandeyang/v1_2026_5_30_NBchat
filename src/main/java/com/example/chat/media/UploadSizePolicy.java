package com.example.chat.media;

public final class UploadSizePolicy {
    private UploadSizePolicy() {
    }

    public static boolean allowsReportedSize(UploadKind kind, long reportedBytes) {
        return reportedBytes < 0 || kind.allowsBytes(reportedBytes);
    }

    public static boolean allowsStoredSize(UploadKind kind, long storedBytes) {
        return kind.allowsBytes(storedBytes);
    }
}
