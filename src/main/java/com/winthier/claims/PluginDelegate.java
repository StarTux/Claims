package com.winthier.claims;

import java.io.File;
import java.util.List;
import java.util.UUID;

public interface PluginDelegate {
    File getDataFolder();
    String getPlayerName(UUID uuid);
    UUID getPlayerUuid(String name);
    List<Player> getOnlinePlayers();
    Highlight createHighlight(UUID playerUuid, Claim claim, Highlight.Type type);
    String format(String string, Object... args);
    boolean sendMessage(UUID uuid, String message, Object... args);
}
