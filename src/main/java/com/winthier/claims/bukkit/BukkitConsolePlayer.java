package com.winthier.claims.bukkit;

import com.winthier.claims.Claims;
import com.winthier.claims.Location;
import com.winthier.claims.Player;
import com.winthier.claims.PlayerInfo;
import com.winthier.claims.util.Msg;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.json.simple.JSONValue;

public class BukkitConsolePlayer extends Player {
    public static BukkitConsolePlayer instance = new BukkitConsolePlayer();
    public UUID getUuid() { return null; }
    public String getName() { return "Console"; }
    public boolean isOp() { return true; }
    public Location getLocation() { return null; }
    public void sendMessage(String msg, Object... args) {
        System.out.println(Msg.format(msg, args));
    }
    public void tellRaw(Object... raw) {}
    public void showTitle(String title, String sub) {}
    public PlayerInfo info() { return null; }
}
