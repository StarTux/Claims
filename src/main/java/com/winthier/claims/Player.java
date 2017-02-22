package com.winthier.claims;

import java.util.UUID;

public abstract class Player {
    public abstract UUID getUuid();
    public abstract String getName();
    public abstract boolean isOp();
    public abstract Location getLocation();
    public abstract void sendMessage(String msg, Object... args);
    public abstract void tellRaw(Object... raw);
    public abstract void showTitle(String title, String sub);

    public final PlayerInfo info() {
        return PlayerInfo.forPlayer(getUuid());
    }
}
