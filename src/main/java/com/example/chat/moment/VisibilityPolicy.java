package com.example.chat.moment;

import java.util.Collections;
import java.util.Set;

public record VisibilityPolicy(
        VisibilityMode mode,
        Set<Long> selectedFriendIds,
        Set<Long> selectedGroupIds,
        Set<Long> excludedFriendIds,
        Set<Long> excludedGroupIds
) {
    public static VisibilityPolicy allFriends() {
        return new VisibilityPolicy(VisibilityMode.ALL_FRIENDS, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static VisibilityPolicy onlySelf() {
        return new VisibilityPolicy(VisibilityMode.ONLY_SELF, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static VisibilityPolicy selected(Set<Long> friendIds, Set<Long> groupIds) {
        return new VisibilityPolicy(VisibilityMode.SELECTED, copy(friendIds), copy(groupIds), Set.of(), Set.of());
    }

    public static VisibilityPolicy excludeFriends(Set<Long> friendIds, Set<Long> groupIds) {
        return new VisibilityPolicy(VisibilityMode.EXCLUDE, Set.of(), Set.of(), copy(friendIds), copy(groupIds));
    }

    public boolean canView(long viewerId, long authorId, Set<Long> viewerFriendGroupIds, Set<Long> authorGroupIds) {
        if (viewerId == authorId) {
            return true;
        }
        return switch (mode) {
            case ALL_FRIENDS -> true;
            case ONLY_SELF -> false;
            case SELECTED -> selectedFriendIds.contains(viewerId) || intersects(selectedGroupIds, viewerFriendGroupIds);
            case EXCLUDE -> !excludedFriendIds.contains(viewerId) && !intersects(excludedGroupIds, viewerFriendGroupIds);
        };
    }

    private static Set<Long> copy(Set<Long> values) {
        return values == null ? Set.of() : Collections.unmodifiableSet(values);
    }

    private boolean intersects(Set<Long> left, Set<Long> right) {
        for (Long value : left) {
            if (right.contains(value)) {
                return true;
            }
        }
        return false;
    }

    public enum VisibilityMode {
        ALL_FRIENDS,
        ONLY_SELF,
        SELECTED,
        EXCLUDE
    }
}
