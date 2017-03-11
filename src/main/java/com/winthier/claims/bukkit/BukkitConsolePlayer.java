package com.winthier.claims.bukkit;

import com.winthier.claims.Location;
import com.winthier.claims.Player;
import com.winthier.claims.util.Msg;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class BukkitConsolePlayer extends Player {
    public static final BukkitConsolePlayer INSTANCE = new BukkitConsolePlayer();
    private final UUID uuid = null;
    private final String name = "Console";
    private final boolean op = true;
    private final Location location = null;

    @Override
    public void sendMessage(String msg, Object... args) {
        System.out.println(Msg.format(msg, args));
    }

    @Override
    public void tellRaw(Object... raw) { }

    @Override
    public void showTitle(String title, String sub) { }
}
