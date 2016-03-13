package com.winthier.claims.bukkit;

import com.winthier.claims.Claims;
import com.winthier.claims.Location;
import com.winthier.claims.Player;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.json.simple.JSONValue;

/**
 * Instances of this class are created by and belong to
 * BukkitClaimsPlugin.
 */
@Value
@EqualsAndHashCode(callSuper=false, of="uuid")
public class BukkitPlayer extends Player {
    private final BukkitClaimsPlugin plugin;
    private final UUID uuid;

    org.bukkit.entity.Player getPlayer() {
        return Bukkit.getServer().getPlayer(uuid);
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }
    
    @Override
    public String getName() {
        org.bukkit.entity.Player player = getPlayer();
        if (player == null) return Claims.getInstance().getDelegate().getPlayerName(uuid);
        return player.getName();
    }
    
    @Override
    public boolean isOp() {
        org.bukkit.entity.Player player = getPlayer();
        if (player == null) return false;
        return player.isOp();
    }
    
    @Override
    public Location getLocation() {
        return plugin.createLocation(getPlayer().getLocation());
    }
    
    @Override
    public void sendMessage(String msg, Object... args) {
        org.bukkit.entity.Player player = getPlayer();
        if (player == null) return;
        player.sendMessage(plugin.format(msg, args));
    }

    @Override
    public void tellRaw(Object raw) {
        org.bukkit.entity.Player player = getPlayer();
        if (player == null) return;
        String json = JSONValue.toJSONString(raw);
        String command = "tellraw " + player.getName() + " " + json;
        CommandSender sender = Bukkit.getServer().getConsoleSender();
        Bukkit.getServer().dispatchCommand(sender, command);
    }

    @Override
    public void showTitle(String title, String sub) {
        getPlayer().sendTitle(plugin.format(title), plugin.format(sub));
    }
}
