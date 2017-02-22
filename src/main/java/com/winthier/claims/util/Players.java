package com.winthier.claims.util;

import com.winthier.claims.Claims;
import java.util.UUID;

public final class Players {
    private Players() { }

    public static UUID getUuid(String name) {
        return Claims.getInstance().getDelegate().getPlayerUuid(name);
    }

    public static String getName(UUID uuid) {
        String result = Claims.getInstance().getDelegate().getPlayerName(uuid);
        if (result == null) result = "Player";
        return result;
    }
}
