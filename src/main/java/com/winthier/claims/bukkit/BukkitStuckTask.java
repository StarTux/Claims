package com.winthier.claims.bukkit;

import com.winthier.claims.Claim;
import com.winthier.claims.CommandException;
import com.winthier.claims.TrustType;
import com.winthier.claims.util.Msg;
import com.winthier.claims.util.Strings;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

@RequiredArgsConstructor
class BukkitStuckTask extends BukkitRunnable {
    private final static long WARMUP = 60L;
    private final static long COOLDOWN = 60L * 60L;
    private final static long TICKS = 20L;
    private final BukkitClaimsPlugin plugin;
    private final UUID uuid;
    private final Location location;
    private final Claim claim;
    
    @Override
    public void run() {
        portOut();
        cancelTask();
    }

    private void portOut() {
        Player player = getPlayer();
        if (player == null) return;
        if (claim == null || !claim.isValid()) return;
        World world = plugin.getServer().getWorld(claim.getWorldName());
        if (world == null) return;
        if (moved()) {
            Msg.send(uuid, "&cYou moved while stuck! No teleport for you.");
            return;
        }
        List<com.winthier.claims.Location> locations = claim.getBorderBlocks();
        Collections.shuffle(locations);
        for (com.winthier.claims.Location loc : locations) {
            Block block = world.getHighestBlockAt(loc.getX(), loc.getZ());
            if (block.getRelative(0, -1, 0).getType().isSolid()) {
                Msg.send(uuid, "&3Porting you out of the claim...");
                Location target = block.getLocation().add(0.5, 0.5, 0.5);
                plugin.getLogger().info(String.format("[Stuck] teleporting %s to %s %d,%d,%d", player.getName(), target.getWorld().getName(), target.getBlockX(), target.getBlockY(), target.getBlockZ()));
                player.teleport(target);
                plugin.stuckCooldowns.put(uuid, new Date(System.currentTimeMillis() + COOLDOWN * 1000L));
                return;
            }
        }
        Msg.send(uuid, "&cCould not find a suitable location to port you to");
    }

    private Player getPlayer() {
        return plugin.getServer().getPlayer(uuid);
    }

    void cancelTask() {
        plugin.stucks.remove(uuid);
        try {
            cancel();
        } catch (IllegalStateException ise) {
            ise.printStackTrace();
        }
    }

    boolean moved() {
        Player player = getPlayer();
        if (player == null) return false;
        return player.getLocation().distanceSquared(location) > 4;
    }

    static boolean onCommand(BukkitClaimsPlugin plugin, Player player) {
        UUID uuid = player.getUniqueId();
        if (plugin.stucks.containsKey(uuid)) {
            BukkitStuckTask task = plugin.stucks.get(uuid);
            if (task.moved()) {
                task.cancelTask();
            } else {
                throw new CommandException("Stuck is already running. Please be patient.");
            }
        }
        if (plugin.stuckCooldowns.containsKey(uuid)) {
            Date cooldown = plugin.stuckCooldowns.get(uuid);
            Date now = new Date();
            if (cooldown.before(now)) {
                plugin.stuckCooldowns.remove(uuid);
            } else {
                long delay = (cooldown.getTime() - now.getTime()) / 1000L;
                throw new CommandException("You are on cooldown for " + Strings.formatSeconds(delay));
            }
        }
        Location location = player.getLocation();
        Claim claim = plugin.getClaimAt(location);
        if (claim == null) {
            throw new CommandException("You are not in a claim");
        } else if (claim.checkTrust(uuid, TrustType.BUILD)) {
            throw new CommandException("You can build here. Help yourself");
        }
        while (claim.getSuperClaim() != null && !claim.getSuperClaim().checkTrust(uuid, TrustType.BUILD)) claim = claim.getSuperClaim();
        BukkitStuckTask task = new BukkitStuckTask(plugin, uuid, location, claim);
        plugin.stucks.put(uuid, task);
        task.runTaskLater(plugin, WARMUP * TICKS);
        Msg.send(uuid, "&bWe will port you out of this claim. Please don't move for %d seconds...", WARMUP);
        return true;
    }
}
