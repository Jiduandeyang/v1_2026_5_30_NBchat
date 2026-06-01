package com.example.chat.voice;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BusyUserRegistry {
    private final Set<Long> busyUsers = ConcurrentHashMap.newKeySet();

    public boolean reserve(long callerId, long calleeId) {
        if (!busyUsers.add(callerId)) {
            return false;
        }
        if (!busyUsers.add(calleeId)) {
            busyUsers.remove(callerId);
            return false;
        }
        return true;
    }

    public void release(long userA, long userB) {
        busyUsers.remove(userA);
        busyUsers.remove(userB);
    }
}
