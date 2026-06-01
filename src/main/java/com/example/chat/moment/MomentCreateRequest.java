package com.example.chat.moment;

import java.util.List;
import java.util.Set;

public record MomentCreateRequest(
        String text,
        String visibility,
        List<Long> mediaIds,
        Set<Long> selectedFriendIds,
        Set<Long> selectedGroupIds,
        Set<Long> excludedFriendIds,
        Set<Long> excludedGroupIds
) {
}
