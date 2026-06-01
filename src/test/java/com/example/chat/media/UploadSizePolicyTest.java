package com.example.chat.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadSizePolicyTest {
    @Test
    void allowsUnknownReportedMultipartSizeBeforeStreamIsCopied() {
        assertTrue(UploadSizePolicy.allowsReportedSize(UploadKind.MOMENT_IMAGE, -1));
    }

    @Test
    void rejectsKnownAndStoredSizesOverLimit() {
        long overLimit = UploadKind.MOMENT_IMAGE.maxBytes() + 1;

        assertFalse(UploadSizePolicy.allowsReportedSize(UploadKind.MOMENT_IMAGE, overLimit));
        assertFalse(UploadSizePolicy.allowsStoredSize(UploadKind.MOMENT_IMAGE, overLimit));
    }
}
