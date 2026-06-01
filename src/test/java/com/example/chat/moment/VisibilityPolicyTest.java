package com.example.chat.moment;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibilityPolicyTest {
    @Test
    void onlySelfAllowsAuthorOnly() {
        VisibilityPolicy policy = VisibilityPolicy.onlySelf();

        assertTrue(policy.canView(7L, 7L, Set.of(2L), Set.of(1L)));
        assertFalse(policy.canView(8L, 7L, Set.of(2L), Set.of(1L)));
    }

    @Test
    void excludedFriendCannotView() {
        VisibilityPolicy policy = VisibilityPolicy.excludeFriends(Set.of(9L), Set.of());

        assertTrue(policy.canView(8L, 7L, Set.of(2L), Set.of(1L)));
        assertFalse(policy.canView(9L, 7L, Set.of(2L), Set.of(1L)));
    }
}
