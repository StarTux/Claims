package com.winthier.claims;

import java.io.File;
import java.util.List;
import java.util.UUID;

public interface PluginDelegate {
    public File getDataFolder();
    public String getPlayerName(UUID uuid);
    public UUID getPlayerUuid(String name);
    public List<Player> getOnlinePlayers();
    public Highlight createHighlight(UUID playerUuid, Claim claim, Highlight.Type type);
    public String format(String string, Object... args);
    public boolean sendMessage(UUID uuid, String message, Object... args);
}
