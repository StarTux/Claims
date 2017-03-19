package com.winthier.claims.bukkit;

import com.winthier.claims.Claim;
import com.winthier.claims.TrustType;
import com.winthier.generic_events.PlayerCanBuildEvent;
import com.winthier.generic_events.PlayerCanDamageEntityEvent;
import com.winthier.generic_events.PlayerCanGriefEvent;
import lombok.RequiredArgsConstructor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

@RequiredArgsConstructor
public final class GenericEventsHandler implements Listener {
    private final BukkitClaimsPlugin plugin;

    private boolean isWorldBlacklisted(World world) {
        return plugin.getClaims().getWorldBlacklist().contains(world.getName());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerCanBuild(PlayerCanBuildEvent event) {
        if (isWorldBlacklisted(event.getBlock().getWorld())) return;
        final Claim claim = plugin.getClaimAt(event.getBlock());
        if (claim == null) return;
        if (claim.checkTrust(event.getPlayer().getUniqueId(), TrustType.BUILD)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerCanGrief(PlayerCanGriefEvent event) {
        if (isWorldBlacklisted(event.getBlock().getWorld())) return;
        final Claim claim = plugin.getClaimAt(event.getBlock());
        if (claim == null) {
            event.setCancelled(true);
            return;
        }
        if (claim.checkTrust(event.getPlayer().getUniqueId(), TrustType.BUILD)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerCanDamageEntity(PlayerCanDamageEntityEvent event) {
        if (isWorldBlacklisted(event.getEntity().getWorld())) return;
        if (!BukkitEventHandler.isProtected(event.getEntity())) return;
        final Claim claim = plugin.getClaimAt(event.getEntity().getLocation());
        if (claim == null) return;
        if (event.getEntity() instanceof Player) {
            String value = claim.getOptions().getOption("pvp");
            if (value == null || value.equals("false")) {
                event.setCancelled(true);
            }
        } else {
            if (BukkitEventHandler.isOwner(event.getPlayer(), event.getEntity())) return;
            if (BukkitEventHandler.isFarmAnimal(event.getEntity()) && claim.isAdminClaim()) return;
            if (claim.checkTrust(event.getPlayer().getUniqueId(), TrustType.BUILD)) return;
            event.setCancelled(true);
        }
    }
}
