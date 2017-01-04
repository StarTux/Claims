package com.winthier.claims.bukkit;

import com.winthier.claims.Claim;
import com.winthier.claims.Highlight;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class BukkitHighlight implements Highlight {
    private final static int DISTANCE = 5;
    private final static int RADIUS = 3 * 16;
    private final UUID playerUuid;
    private final Claim claim;
    private final Type type;
    private int centerX, centerY, centerZ;
    //
    private final List<Block> blocks = new ArrayList<>();
    private final List<Location> locations = new ArrayList<>();
    private final List<Location> particleLocations = new ArrayList<>();
    private int cornerCount;
    private int seconds = 10;

    private BukkitHighlight(UUID playerUuid, Claim claim, Type type) {
        this.playerUuid = playerUuid;
        this.claim = claim;
        this.type = type;
    }

    public static BukkitHighlight createHighlight(UUID playerUuid, Claim claim, Type type) {
        BukkitHighlight result = new BukkitHighlight(playerUuid, claim, type);
        final Player player = Bukkit.getServer().getPlayer(playerUuid);
        if (player != null) {
            Location location = player.getLocation();
            result.centerX = location.getBlockX();
            result.centerY = location.getBlockY();
            result.centerZ = location.getBlockZ();
            result.initBlocks();
        }
        return result;
    }

    private static int distance(Location loc1, Location loc2) {
        return Math.max(Math.abs(loc2.getBlockX() - loc1.getBlockX()),
                        Math.abs(loc2.getBlockY() - loc1.getBlockY()));
    }

    private Block findBlockAt(World world, int x, int height, int z) {
        if (Math.abs(x - centerX) > RADIUS) return null;
        if (Math.abs(z - centerZ) > RADIUS) return null;
        Block pivot = world.getBlockAt(x, height, z);
        if (pivot.getType().isTransparent()) {
            // Go down
            Block block = pivot;
            for (int y = height - 1; y >= 0; --y) {
                Block nextBlock = world.getBlockAt(x, y, z);
                if (!nextBlock.getType().isTransparent()) return nextBlock;
                block = nextBlock;
            }
            return block;
        } else {
            // Go up
            Block block = pivot;
            for (int y = height + 1; y <= world.getMaxHeight(); ++y) {
                Block nextBlock = world.getBlockAt(x, y, z);
                if (nextBlock.getType().isTransparent()) return block;
                block = nextBlock;
            }
            return block;
        }
    }

    private void addBlockAt(World world, int x, int y, int z) {
        Block block = findBlockAt(world, x, y, z);
        if (block != null) {
            blocks.add(block);
            Location loc = block.getLocation();
            locations.add(loc);
            particleLocations.add(loc.clone().add(0.5, 3.0, 0.5));
        }
    }

    private void initBlocks() {
        final int height = centerY;
        //
        final int north = claim.getNorthBorder();
        final int south = claim.getSouthBorder();
        final int west = claim.getWestBorder();
        final int east = claim.getEastBorder();
        final com.winthier.claims.Location center = claim.getCenterLocation();
        World world = Bukkit.getServer().getWorld(claim.getWorldName());
        // Corner blocks
        addBlockAt(world, west, height, north);
        addBlockAt(world, east, height, north);
        addBlockAt(world, east, height, south);
        addBlockAt(world, west, height, south);
        // Middle blocks
        addBlockAt(world, center.getX(), height, north);
        addBlockAt(world, center.getX(), height, south);
        addBlockAt(world, west, height, center.getZ());
        addBlockAt(world, east, height, center.getZ());
        cornerCount = blocks.size();
        for (int i = 1; i < claim.getWidth() / 2; i += DISTANCE) {
            addBlockAt(world, west + i, height, north);
            addBlockAt(world, east - i, height, north);
            addBlockAt(world, west + i, height, south);
            addBlockAt(world, east - i, height, south);
        }
        for (int i = 1; i < claim.getHeight() / 2; i += DISTANCE) {
            addBlockAt(world, west, height, north + i);
            addBlockAt(world, west, height, south - i);
            addBlockAt(world, east, height, north + i);
            addBlockAt(world, east, height, south - i);
        }
    }

    private void showBlocks(boolean highlight) {
        final Player player = Bukkit.getServer().getPlayer(playerUuid);
        if (player == null) return;
        final Location playerLoc = player.getLocation();
        if (!player.getWorld().getName().equals(claim.getWorldName())) return;
        if (highlight) {
            for (int i = 0; i < blocks.size(); ++i) {
                final int mat;
                switch (type) {
                case FRIENDLY:
                    mat = i < cornerCount ? 124 : 41;
                    break;
                case ENEMY:
                    mat = i < cornerCount ? 124 : 152;
                    break;
                case ADMIN:
                    mat = i < cornerCount ? 169 : 22;
                    break;
                case SUBCLAIM:
                    mat = i < cornerCount ? 42 : 35;
                    break;
                default:
                    mat = 49;
                }
                Location loc = locations.get(i);
                if (distance(playerLoc, loc) < RADIUS) {
                    player.sendBlockChange(loc, mat, (byte)0);
                }
            }
        } else {
            for (int i = 0; i < blocks.size(); ++i) {
                Block block = blocks.get(i);
                int mat = block.getType().getId();
                byte data = block.getData();
                Location loc = locations.get(i);
                if (distance(playerLoc, loc) < RADIUS) {
                    player.sendBlockChange(loc, mat, data);
                }
            }
        }
    }

    @Override
    public void highlight() {
        showBlocks(true);
    }

    @Override
    public void restore() {
        showBlocks(false);
    }

    @Override
    public void flash() {
        final Player player = Bukkit.getServer().getPlayer(playerUuid);
        if (player == null) return;
        if (!player.getWorld().getName().equals(claim.getWorldName())) return;
        for (Location location : particleLocations) {
            player.spawnParticle(Particle.VILLAGER_HAPPY, location, 16, 0.3f, 0.7f, 0.3f, 0.1f);
        }
    }

    @Override
    public boolean secondTick() {
        if (--seconds <= 0) return false;
        flash();
        return true;
    }
}
