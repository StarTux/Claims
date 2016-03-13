package com.winthier.claims.bukkit;

import com.winthier.claims.Claim;
import com.winthier.claims.Claims;
import com.winthier.claims.Highlight;
import com.winthier.claims.Player;
import com.winthier.claims.PlayerInfo;
import com.winthier.claims.PluginDelegate;
import com.winthier.playercache.PlayerCache;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class BukkitDelegate implements PluginDelegate {
    private final BukkitClaimsPlugin plugin;

    public BukkitDelegate(BukkitClaimsPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public File getDataFolder() {
        File result = plugin.getDataFolder();
        result.mkdirs();
        return result;
    }

    @Override
    public String getPlayerName(UUID uuid) {
        String result = PlayerCache.nameForUuid(uuid);
        if (result != null) return result;
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
        if (player != null) return player.getName();
        return "someone";
    }

    @Override
    public UUID getPlayerUuid(String name) {
        return PlayerCache.uuidForName(name);
    }

    @Override
    public List<Player> getOnlinePlayers() {
        List<Player> result = new ArrayList<>();
        for (org.bukkit.entity.Player bukkitPlayer : plugin.getServer().getOnlinePlayers()) {
            result.add(plugin.createPlayer(bukkitPlayer));
        }
        return result;
    }

    @Override
    public Highlight createHighlight(UUID playerUuid, Claim claim, Highlight.Type type) {
        return BukkitHighlight.createHighlight(playerUuid, claim, type);
    }

    @Override
    public String format(String string, Object... args) {
        return plugin.format(string, args);
    }

    @Override
    public boolean sendMessage(UUID uuid, String message, Object... args) {
        org.bukkit.entity.Player player = Bukkit.getServer().getPlayer(uuid);
        if (player == null) return false;
        player.sendMessage(format(message, args));
        return true;
    }
}
