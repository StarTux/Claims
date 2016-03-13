package com.winthier.claims.util;

import com.winthier.claims.Claims;
import java.util.UUID;

public class Msg {
    public static boolean send(UUID player, String message, Object... args) {
        return Claims.getInstance().getDelegate().sendMessage(player, message, args);
    }
}
