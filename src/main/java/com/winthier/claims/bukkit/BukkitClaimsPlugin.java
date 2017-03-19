package com.winthier.claims.bukkit;

import com.winthier.claims.Claim;
import com.winthier.claims.Claims;
import com.winthier.claims.CommandException;
import com.winthier.claims.Location;
import com.winthier.claims.PlayerInfo;
import com.winthier.claims.TrustType;
import com.winthier.claims.util.JSON;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

@Getter
public final class BukkitClaimsPlugin extends JavaPlugin {
    private Claims claims;
    private BukkitRunnable task;
    private BukkitRunnable asyncTask;
    private BukkitEventHandler eventHandler;
    private GenericEventsHandler genericEventsHandler;
    private Map<UUID, BukkitPlayer> playerMap = new HashMap<>();
    private boolean disabled = false;
    private Map<UUID, BukkitStuckTask> stucks = new HashMap<>();
    private Map<UUID, Date> stuckCooldowns = new HashMap<>();
    private Economy economy = null;
    private static final double PRICE_PER_CLAIM_BLOCK = 0.1;

    /* Setup routines  * * * * * * * * * * * * * * * * * * * * */

    @Override
    public void onEnable() {
        reloadConfig();
        saveDefaultConfig();
        claims = new Claims();
        claims.setDelegate(new BukkitDelegate(this));
        claims.onEnable();
        task = new BukkitRunnable() {
            @Override public void run() {
                onTick();
            }
        };
        task.runTaskTimer(this, 1L, 1L);
        asyncTask = new BukkitRunnable() {
            @Override public void run() {
                while (!disabled) {
                    claims.asyncLoop();
                }
            }
        };
        asyncTask.runTaskAsynchronously(this);
        this.eventHandler = new BukkitEventHandler(this);
        getServer().getPluginManager().registerEvents(eventHandler, this);
        if (getServer().getPluginManager().getPlugin("GenericEvents") != null) {
            genericEventsHandler = new GenericEventsHandler(this);
            getServer().getPluginManager().registerEvents(genericEventsHandler, this);
        }
    }

    @Override
    public void onDisable() {
        disabled = true;
        task.cancel();
        claims.onDisable();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.equals(getCommand("claimadmin"))) {
            if (sender instanceof org.bukkit.entity.Player) {
                claims.getAdminCommand().command(createPlayer((org.bukkit.entity.Player)sender), args);
            } else {
                claims.getAdminCommand().command(BukkitConsolePlayer.INSTANCE, args);
            }
            return true;
        }
        if (!(sender instanceof org.bukkit.entity.Player)) {
            sender.sendMessage("Player expected");
            return true;
        }
        BukkitPlayer player = createPlayer((org.bukkit.entity.Player)sender);
        if (command.equals(getCommand("claim"))) {
            claims.getClaimCommand().command(player, args);
        } else if (command.equals(getCommand("trust"))) {
            return claims.getClaimCommand().trust(player, TrustType.BUILD, args);
        } else if (command.equals(getCommand("containertrust"))) {
            return claims.getClaimCommand().trust(player, TrustType.CONTAINER, args);
        } else if (command.equals(getCommand("accesstrust"))) {
            return claims.getClaimCommand().trust(player, TrustType.ACCESS, args);
        } else if (command.equals(getCommand("permissiontrust"))) {
            return claims.getClaimCommand().trust(player, TrustType.PERMISSION, args);
        } else if (command.equals(getCommand("untrust"))) {
            return claims.getClaimCommand().untrust(player, args);
        } else if (command.equals(getCommand("ignoreclaims"))) {
            claims.getAdminCommand().ignore(player, args);
        } else if (command.equals(getCommand("stuck"))) {
            try {
                return BukkitStuckTask.onCommand(this, (org.bukkit.entity.Player)sender);
            } catch (CommandException ce) {
                player.sendMessage("" + ChatColor.RED + ce.getMessage());
            }
        } else if (command.equals(getCommand("buyclaimblocks"))) {
            if (args.length == 0 || !claims.isAllowBuyClaimBlocks()) {
                return false;
            } else if ("confirm".equalsIgnoreCase(args[0]) && args.length == 2) {
                PlayerInfo info = player.info();
                final UUID transactionUuid = info.getBuyClaimBlocksId();
                if (transactionUuid == null) return true;
                final UUID userUuid;
                try {
                    userUuid = UUID.fromString(args[1]);
                } catch (IllegalArgumentException iae) {
                    return true;
                }
                if (!userUuid.equals(transactionUuid)) return true;
                int amount = info.getBuyClaimBlocksAmount();
                double price = amount * PRICE_PER_CLAIM_BLOCK;
                // reset
                info.setBuyClaimBlocksAmount(0);
                info.setBuyClaimBlocksId(null);
                // Money
                String formatMoney = getEconomy().format(price);
                if (!getEconomy().has(player.getPlayer(), price)) {
                    player.sendMessage("&cYou cannot affort %s!", formatMoney);
                    return true;
                }
                if (!getEconomy().withdrawPlayer(player.getPlayer(), price).transactionSuccess()) {
                    player.sendMessage("&cYou cannot affort %s!", formatMoney);
                    return true;
                }
                info.addClaimBlocks(amount);
                info.save();
                claims.savePlayers();
                player.sendMessage("&3&lClaims&r&o Purchased &a%d&r&o claim blocks for &a%s&r&o.", amount, formatMoney);
            } else if ("cancel".equalsIgnoreCase(args[0])) {
                PlayerInfo info = player.info();
                info.setBuyClaimBlocksAmount(0);
                info.setBuyClaimBlocksId(null);
                player.sendMessage("&3&lClaims&r&o Purchase cancelled.");
            } else if (args.length == 1) {
                int amount;
                try {
                    amount = Integer.parseInt(args[0]);
                } catch (NumberFormatException nfe) {
                    return false;
                }
                if (amount <= 0) {
                    return false;
                }
                double price = (double)amount * PRICE_PER_CLAIM_BLOCK;
                String formatMoney = getEconomy().format(price);
                if (!getEconomy().has(player.getPlayer(), price)) {
                    player.sendMessage("&cYou cannot affort %s!", formatMoney);
                    return true;
                }
                PlayerInfo info = player.info();
                info.setBuyClaimBlocksAmount(amount);
                UUID transactionUuid = UUID.randomUUID();
                info.setBuyClaimBlocksId(transactionUuid);
                List<Object> msg = new ArrayList<>();
                msg.add(format("&oBuy &a%d&r&o claim blocks for &a%s&r&o? ", amount, formatMoney));
                msg.add(JSON.commandRunButton("&r[&aConfirm&r]", "&aConfirm\nPayment of " + formatMoney, "/buyclaimblocks confirm " + transactionUuid));
                msg.add(" ");
                msg.add(JSON.commandRunButton("&r[&cCancel&r]", "&cCancel\nForget about this transaction", "/buyclaimblocks cancel"));
                player.tellRaw(msg);
            } else {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.equals(getCommand("claim"))) {
            return claims.getClaimCommand().tabComplete(args);
        }
        return null;
    }

    /* Utility * * * * * * * * * * * * * * * * * * * * * * * * */

    public BukkitPlayer createPlayer(org.bukkit.entity.Player bukkitPlayer) {
        BukkitPlayer result = playerMap.get(bukkitPlayer.getUniqueId());
        if (result == null) {
            final UUID uuid = bukkitPlayer.getUniqueId();
            result = new BukkitPlayer(this, uuid);
            playerMap.put(uuid, result);
        }
        return result;
    }

    public Location createLocation(org.bukkit.Location bukkitLocation) {
        final String world = bukkitLocation.getWorld().getName();
        final int x = bukkitLocation.getBlockX();
        final int y = bukkitLocation.getBlockY();
        final int z = bukkitLocation.getBlockZ();
        return new Location(world, x, y, z);
    }

    public Location createLocation(org.bukkit.block.Block block) {
        final String world = block.getWorld().getName();
        final int x = block.getX();
        final int y = block.getY();
        final int z = block.getZ();
        return new Location(world, x, y, z);
    }

    public org.bukkit.Location createBukkitLocation(Location location) {
        World world = Bukkit.getServer().getWorld(location.getWorldName());
        if (world == null) throw new IllegalArgumentException("World not found: " + location.getWorldName());
        return new org.bukkit.Location(world, location.getX(), location.getY(), location.getZ());
    }

    // TODO remove, reformat to Msg...
    public String format(String string, Object... args) {
        string = ChatColor.translateAlternateColorCodes('&', string);
        if (args.length > 0) string = String.format(string, args);
        return string;
    }

    public void onTick() {
        claims.onTick();
    }

    public Claim getClaimAt(org.bukkit.Location location) {
        return claims.getClaimAt(createLocation(location));
    }

    public Claim getClaimAt(org.bukkit.block.Block block) {
        return claims.getClaimAt(createLocation(block));
    }

    public Collection<Claim> findClaimsNear(org.bukkit.Location location, int distance) {
        return claims.findClaimsNear(createLocation(location), distance);
    }

    private Economy getEconomy() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
            if (economyProvider != null) economy = economyProvider.getProvider();
        }
        return economy;
    }

    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
}
