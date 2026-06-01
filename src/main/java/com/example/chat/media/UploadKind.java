package com.example.chat.media;

public enum UploadKind {
    CHAT_IMAGE(5),
    MOMENT_IMAGE(5),
    MOMENT_VIDEO(50),
    VOICE_MESSAGE(10),
    AVATAR(2),
    BACKGROUND(5);

    private final long maxMegabytes;

    UploadKind(long maxMegabytes) {
        this.maxMegabytes = maxMegabytes;
    }

    public long maxBytes() {
        return maxMegabytes * 1024L * 1024L;
    }

    public boolean allowsBytes(long bytes) {
        return bytes >= 0 && bytes <= maxBytes();
    }
}
