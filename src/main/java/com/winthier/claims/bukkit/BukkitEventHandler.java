package com.winthier.claims.bukkit;

import com.winthier.claims.Action;
import com.winthier.claims.Claim;
import com.winthier.claims.Claims;
import com.winthier.claims.PlayerInfo;
import com.winthier.claims.TrustType;
import com.winthier.claims.util.Msg;
import java.util.Iterator;
import java.util.Set;
import lombok.val;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.NPC;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Openable;
import org.bukkit.scheduler.BukkitRunnable;
import org.spigotmc.event.entity.EntityMountEvent;

/**
 * Listen to claim protection related events
 */
public class BukkitEventHandler implements Listener {
    private final BukkitClaimsPlugin plugin;
    private static final ItemStack TOOL_ITEM = new ItemStack(Material.GOLD_SPADE);

    BukkitEventHandler(BukkitClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
    /* Utility * * * * * * * * * * * * * * * * * * * * * * * * */

    private boolean autoCheckAction(Player player, Location location, Action action) {
        return Claims.getInstance().autoCheckAction(plugin.createPlayer(player), plugin.createLocation(location), action);
    }

    private boolean autoCheckAction(Player player, Location location, Action action, Cancellable cancel) {
        boolean result = autoCheckAction(player, location, action);
        if (!result) cancel.setCancelled(true);
        return result;
    }

    private boolean isWorldBlacklisted(World world) {
        return plugin.getClaims().getWorldBlacklist().contains(world.getName());
    }

    /**
     * Utility function to determine whether a player owns an
     * entity. To own an entity, it has to be tameable and the
     * player has to have tamed it. Tamed animals should always be
     * able to be interacted with, or even damaged, by their
     * owner.
     * @arg player the player
     * @arg entity the entity
     * @return true if player owns entity, false otherwise
     */
    static boolean isOwner(Player player, Entity entity) {
        if (!(entity instanceof Tameable)) return false;
        Tameable tameable = (Tameable)entity;
        if (!tameable.isTamed()) return false;
        AnimalTamer owner = tameable.getOwner();
        if (owner == null) return false;
        if (owner.getUniqueId().equals(player.getUniqueId())) return true;
        return false;
    }

    private boolean isHostile(Entity e) {
        switch (e.getType()) {
        case CREEPER:
        case SKELETON:
        case SPIDER:
        case GIANT:
        case ZOMBIE:
        case SLIME:
        case GHAST:
        case PIG_ZOMBIE:
        case ENDERMAN:
        case CAVE_SPIDER:
        case SILVERFISH:
        case BLAZE:
        case MAGMA_CUBE:
        case ENDER_DRAGON:
        case WITHER:
        case WITCH:
        case ENDERMITE:
        case GUARDIAN:
            return true;
        default:
            return e instanceof Monster;
        }
    }

    private boolean isProtected(Entity e) {
        if (e.getCustomName() != null) return true;
        if (e instanceof Animals) return true;
        if (e instanceof Monster) return false;
        if (e instanceof NPC) return true;
        switch (e.getType()) {
        case ENDER_DRAGON:
        case SLIME:
        case MAGMA_CUBE:
            return false;
        default:
            return true;
        }
    }

    static boolean isFarmAnimal(Entity entity) {
        switch (entity.getType()) {
        case COW:
        case SHEEP:
        case RABBIT:
        case PIG:
        case CHICKEN:
            return true;
        default:
            return false;
        }
    }

    private boolean isDoor(Block block) {
        if (block.getState().getData() instanceof Openable) return true;
        switch (block.getType()) {
        case ACACIA_DOOR:
        case BIRCH_DOOR:
        case DARK_OAK_DOOR:
        case IRON_DOOR_BLOCK:
        case IRON_TRAPDOOR:
        case JUNGLE_DOOR:
        case SPRUCE_DOOR:
        case TRAP_DOOR:
        case WOOD_DOOR: // wtf
        case WOODEN_DOOR: // wtf
        case BIRCH_FENCE_GATE:
        case DARK_OAK_FENCE_GATE:
        case FENCE_GATE:
        case JUNGLE_FENCE_GATE:
        case SPRUCE_FENCE_GATE:
            return true;
        default:
            return false;
        }
    }

    /**
     * Check if block is of a type which requires special
     * protection from interacting as they would be modified by a
     * simple right click. Only add blocks that don't have their
     * own event when modified.
     *
     * The point of this is to whitelist (via NONE) or blacklist
     * (via any other Action type) specific blocks that we know
     * the result of. All block interactions not covered by this
     * or later checks (see the event handler) will be denied by
     * default.
     */
    private Action getRightClickAction(Block block) {
        switch (block.getType()) {
        case NOTE_BLOCK:
        case DIODE_BLOCK_OFF:
        case DIODE_BLOCK_ON:
        case REDSTONE_COMPARATOR_ON:
        case REDSTONE_COMPARATOR_OFF:
        case TNT:
        case DRAGON_EGG:
        case JUKEBOX:
            return Action.BUILD;
        case LEVER:
        case STONE_BUTTON:
        case WOOD_BUTTON:
            return Action.SWITCH_TRIGGER;
        case BED_BLOCK:
            return Action.SLEEP_BED;
        case WORKBENCH:
        case ENCHANTMENT_TABLE:
        case ENDER_CHEST:
        case SIGN_POST:
        case WALL_SIGN:
            return Action.NONE;
        case ANVIL:
            return Action.ACCESS_ANVIL;
        default:
            return null;
        }
    }

    private Action getRightClickBlockAction(ItemStack item) {
        Material material = item.getType();
        if (material.isEdible()) return Action.NONE;
        return null;
    }

    private boolean isLauncher(ItemStack item) {
        switch (item.getType()) {
        case BOW:
        case ENDER_PEARL:
        case EYE_OF_ENDER:
        case EGG:
        case FIREWORK:
        case FISHING_ROD:
            return true;
        default:
            return false;
        }
    }

    private Action getLeftClickAction(Block block) {
        switch (block.getType()) {
        case DRAGON_EGG:
            return Action.BUILD;
        case NOTE_BLOCK:
        case SIGN_POST:
        case WALL_SIGN:
            return Action.NONE;
        default:
            return null;
        }
    }

    private Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player) {
            return (Player)damager;
        } else if (damager instanceof Projectile) {
            Projectile projectile = (Projectile)damager;
            if (projectile.getShooter() instanceof Player) {
                return (Player)projectile.getShooter();
            }
        }
        return null;
    }

    /* Event handlers  * * * * * * * * * * * * * * * * * * * * */

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        autoCheckAction(event.getPlayer(), event.getBlock().getLocation(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        boolean allowed = autoCheckAction(event.getPlayer(), event.getBlock().getLocation(), Action.BUILD, event);
        // Warn player about block placement in unclaimed areas.
        if (!allowed) return;
        if (isWorldBlacklisted(event.getBlock().getWorld())) return;
        if (plugin.getClaimAt(event.getBlock().getLocation()) != null) return;
        PlayerInfo info = plugin.getClaims().getPlayerInfo(event.getPlayer().getUniqueId());
        if (info.didGetIt()) return;
        int placed = info.getUnclaimedBlocksPlaced();
        com.winthier.claims.Location oldloc = info.getLastUnclaimedBlockPlaced();
        com.winthier.claims.Location newloc = plugin.createLocation(event.getBlock());
        if (oldloc == null || !oldloc.getWorldName().equals(newloc.getWorldName()) || oldloc.horizontalDistanceSquared(newloc) > 64 * 64) {
            placed = 0;
        } else {
            placed += 1;
        }
        info.setLastUnclaimedBlockPlaced(newloc);
        if (placed > 16) {
            info.setUnclaimedBlocksPlaced(0);
            Msg.raw(event.getPlayer(),
                    Msg.button(ChatColor.DARK_RED, "&lClaims", "&a/claim\n&oOverview\nLook at the claims menu.", "/claim "),
                    " ",
                    Msg.button(ChatColor.RED, "This area is not claimed. Make sure to ", null, null),
                    Msg.button(ChatColor.RED, "&ncreate", "&a/claim new\n&oCommand\nCreate a new claim here.", "/claim new "),
                    Msg.button(ChatColor.RED, " or ", null, null),
                    Msg.button(ChatColor.RED, "&nexpand", "&a/claim grow\n&oCommand\nExpand the claim you're in.", "/claim grow "),
                    Msg.button(ChatColor.RED, " a claim to protect your build!", null, null),
                    " ",
                    Msg.button(ChatColor.GREEN, "&r[&aGot It&r]", "Got it", "/claim gotit"));
            event.getPlayer().playSound(event.getPlayer().getEyeLocation(), Sound.ENTITY_POLAR_BEAR_WARNING, 1.0f, 1.0f);
        } else {
            info.setUnclaimedBlocksPlaced(placed);
        }
    }

    // Frost Walker
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        final Claim claim = plugin.getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;
        Player player = (Player)event.getEntity();
        if (!claim.checkTrust(player.getUniqueId(), TrustType.BUILD)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (isWorldBlacklisted(event.getEntity().getWorld())) return;
        // Get entity
        final Entity entity = event.getEntity();
        if (!isProtected(entity)) return;
        // Get player
        final Player player = getPlayerDamager(event.getDamager());
        if (player != null) {
            // Check ownership
            if (isOwner(player, entity)) return;
            // Animals are special
            if (isFarmAnimal(entity)) {
                autoCheckAction(player, entity.getLocation(), Action.DAMAGE_FARM_ANIMAL, event);
                return;
            }
            // Auto check action
            autoCheckAction(player, entity.getLocation(), Action.DAMAGE_ENTITY, event);
        } else {
            if (entity instanceof Player) return;
            Claim claim = plugin.getClaimAt(entity.getLocation());
            if (claim == null) return;
            EntityType et = event.getDamager().getType();
            if (et == EntityType.PRIMED_TNT) {
                if (claim.getTopLevelClaim().shouldDenyTNTDamage()) {
                    event.setCancelled(true);
                }
            } else if (et == EntityType.CREEPER) {
                if (claim.getTopLevelClaim().shouldDenyCreeperDamage()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();
        final Entity entity = event.getRightClicked();
        switch (entity.getType()) {
        case PIG:
        case BOAT:
        case MINECART:
        case SHEEP: // Handled by PlayerShearEvent. Hopefully.
            return;
        case VILLAGER:
        case MINECART_CHEST:
            autoCheckAction(player, entity.getLocation(), Action.OPEN_INVENTORY, event);
            return;
        case ITEM_FRAME:
        case ARMOR_STAND:
            autoCheckAction(player, entity.getLocation(), Action.TRANSFORM_ENTITY, event);
            return;
        default:
            break;
        }
        if (entity instanceof ChestedHorse) {
            ChestedHorse horse = (ChestedHorse)entity;
            if (horse.isCarryingChest()) {
                AnimalTamer owner = horse.getOwner();
                if (owner == null || !player.getUniqueId().equals(owner.getUniqueId())) {
                    autoCheckAction(player, entity.getLocation(), Action.OPEN_INVENTORY, event);
                }
            }
            return;
        }
        if (isLauncher(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        autoCheckAction(player, entity.getLocation(), Action.INTERACT_ENTITY, event);
    }

    // Should this just be the same as onPlayerInteractEntity() ?
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        final Entity entity = event.getRightClicked();
        if (entity.getType() != EntityType.ARMOR_STAND) return;
        final Player player = event.getPlayer();
        if (!isProtected(entity)) return;
        if (isOwner(player, entity)) return;
        autoCheckAction(player, entity.getLocation(), Action.TRANSFORM_ENTITY, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        final Player player = event.getPlayer();
        final Entity entity = event.getRightClicked();
        autoCheckAction(player, entity.getLocation(), Action.TRANSFORM_ENTITY, event);
    }

    /**
     * Make sure to whitelist anything that should be caught here
     * in the PlayerInteractEntityEvent.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerShearEntity(PlayerShearEntityEvent event) {
        final Player player = event.getPlayer();
        final Entity entity = event.getEntity();
        if (isOwner(player, entity)) return;
        Action action = entity.getType() == EntityType.MUSHROOM_COW ? Action.TRANSFORM_ENTITY : Action.SHEAR_ENTITY;
        autoCheckAction(player, entity.getLocation(), action, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        final Player player = (Player)event.getEntity();
        final Entity mount = event.getMount();
        if (isOwner(player, mount)) return;
        switch (mount.getType()) {
        case MINECART:
        case BOAT:
        case PIG:
            autoCheckAction(player, mount.getLocation(), Action.RIDE_VEHICLE, event);
            return;
        default:
            autoCheckAction(player, mount.getLocation(), Action.MOUNT_ENTITY, event);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        final Player player = event.getPlayer();
        final Entity entity = event.getEntity();
        if (isOwner(player, entity)) return;
        autoCheckAction(player, entity.getLocation(), Action.LEASH_ENTITY, event);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onPlayerInteractShovel(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        final Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != Material.GOLD_SPADE) return;
        event.setCancelled(true);
        Block block = event.getClickedBlock();
        if (block == null || block.getType() == Material.AIR) {
            block = player.getTargetBlock((Set<Material>)null, 128);
        }
        val p = plugin.createPlayer(player);
        val l = plugin.createLocation(block);
        p.info().getTool().onTouch(p, l);
        return;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        final Block block = event.getClickedBlock();
        if (block == null) return;
        // Consider soil trampling
        if (event.getAction() == org.bukkit.event.block.Action.PHYSICAL) {
            if (block.getType() == Material.SOIL) {
                autoCheckAction(event.getPlayer(), event.getClickedBlock().getLocation(), Action.TRAMPLE, event);
            }
            return;
        }
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            // Consider especially protected blocks
            Action action = getRightClickAction(block);
            if (action != null) {
                if (action == Action.NONE) return;
                autoCheckAction(player, block.getLocation(), action, event);
                return;
            }
            // Consider chests
            if (block.getState() instanceof InventoryHolder) {
                autoCheckAction(player, block.getLocation(), Action.OPEN_INVENTORY, event);
                return;
            }
            // Consider doors
            if (isDoor(block)) {
                autoCheckAction(player, block.getLocation(), Action.SWITCH_DOOR, event);
                return;
            }
            // Consider item in hand
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            action = getRightClickBlockAction(itemInHand);
            if (action != null) {
                if (action == Action.NONE) return;
                autoCheckAction(player, block.getLocation(), action, event);
                return;
            }
            // Special case for launchers: Only deny using the block, not the item
            if (itemInHand.getType() == Material.FIREWORK) {
                return;
            } else if (isLauncher(itemInHand)) {
                if (!autoCheckAction(player, block.getLocation(), Action.INTERACT_BLOCK)) {
                    event.setUseInteractedBlock(Event.Result.DENY);
                }
                return;
            }
            if (itemInHand.getType().isBlock()) return;
            // Fallback; always deny
            autoCheckAction(player, block.getLocation(), Action.INTERACT_BLOCK, event);
        }
        if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            Action action = getLeftClickAction(block);
            if (action == Action.NONE) {
                return;
            } else if (action != null) {
                autoCheckAction(player, block.getLocation(), action, event);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (isWorldBlacklisted(event.getEntity().getWorld())) return;
        if (event.getRemover() instanceof Player) {
            final Player player = (Player)event.getRemover();
            autoCheckAction(player, event.getEntity().getLocation(), Action.BUILD, event);
        } else {
            Claim claim = plugin.getClaimAt(event.getEntity().getLocation());
            EntityType et = event.getRemover().getType();
            if (et == EntityType.PRIMED_TNT) {
                if (claim == null || claim.getTopLevelClaim().shouldDenyTNTDamage()) {
                    event.setCancelled(true);
                }
            } else if (et == EntityType.CREEPER) {
                if (claim == null || claim.getTopLevelClaim().shouldDenyCreeperDamage()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingPlace(HangingPlaceEvent event) {
        autoCheckAction(event.getPlayer(), event.getEntity().getLocation(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        autoCheckAction(event.getPlayer(), event.getBlockClicked().getLocation(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        autoCheckAction(event.getPlayer(), event.getBlockClicked().getLocation(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        final Entity entity = event.getEntity();
        if (isHostile(entity)) {
            if (!Claims.getInstance().checkEntitySpawn(plugin.createLocation(entity.getLocation()))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Claims.getInstance().onPlayerJoin(plugin.createPlayer(event.getPlayer()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Claims.getInstance().onPlayerQuit(plugin.createPlayer(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        final Player player = event.getPlayer();
        final int slot = event.getNewSlot();
        final ItemStack item = player.getInventory().getItem(slot);
        if (item == null) return;
        if (!TOOL_ITEM.isSimilar(item)) return;
        if (!player.hasPermission("claims.claim")) return;
        final BukkitPlayer bp = plugin.createPlayer(player);
        if (bp.info().isClaimToolReminded()) return;
        bp.info().setClaimToolReminded(true);
        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isValid()) return;
                if (slot != player.getInventory().getHeldItemSlot()) return;
                bp.sendMessage("&3&lClaims&r&o Click with the &a&oGolden Shovel&r&o to check for or edit claims.");
            }
        }.runTaskLater(plugin, 20L);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (isWorldBlacklisted(event.getEntity().getWorld())) return;
        for (Iterator<Block> iter = event.blockList().iterator(); iter.hasNext();) {
            Claim claim = plugin.getClaimAt(iter.next().getLocation());
            if (claim == null) {
                // Explosion damage is disabled by default.
                iter.remove();
            } else {
                claim = claim.getTopLevelClaim();
                if (event.getEntity().getType() == EntityType.PRIMED_TNT) {
                    if (claim.shouldDenyTNTDamage()) {
                        iter.remove();
                    }
                } else if (event.getEntity().getType() == EntityType.CREEPER) {
                    if (claim.shouldDenyCreeperDamage()) {
                        iter.remove();
                    }
                }
            }
        }
    }

    // Claim Settings

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isWorldBlacklisted(event.getBlock().getWorld())) return;
        Claim claim = plugin.getClaimAt(event.getBlock().getLocation());
        if (claim == null) {
            event.setCancelled(true);
            return;
        }
        claim = claim.getTopLevelClaim();
        String value = claim.getOptions().getOption("fireSpread");
        if (value == null || value.equals("false")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockIgnite(BlockIgniteEvent event) {
        switch (event.getCause()) {
        case ENDER_CRYSTAL:
        case EXPLOSION:
        case FIREBALL:
        case FLINT_AND_STEEL:
            return;
        case LAVA:
        case LIGHTNING:
        case SPREAD:
        default:
            // Handle these
            break;
        }
        if (isWorldBlacklisted(event.getBlock().getWorld())) return;
        Claim claim = plugin.getClaimAt(event.getBlock().getLocation());
        if (claim == null) {
            event.setCancelled(true);
            return;
        }
        claim = claim.getTopLevelClaim();
        String value = claim.getOptions().getOption("fireSpread");
        if (value == null || value.equals("false")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityDamageByEntityPVP(EntityDamageByEntityEvent event) {
        if (isWorldBlacklisted(event.getEntity().getWorld())) return;
        Player damagee = event.getEntity() instanceof Player ? (Player)event.getEntity() : null;
        if (damagee == null) return;
        Player damager = getPlayerDamager(event.getDamager());
        if (damager == null) return;
        if (damager.equals(damagee)) return;
        Claim claim = plugin.getClaimAt(event.getEntity().getLocation());
        if (claim == null) {
            // PvP is disabled by default
            event.setCancelled(true);
            return;
        }
        claim = claim.getTopLevelClaim();
        String value = claim.getOptions().getOption("pvp");
        if (value == null || value.equals("false")) {
            // PvP is disabled by default
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        if (isWorldBlacklisted(event.getEntity().getWorld())) return;
        Player damager = getPlayerDamager(event.getCombuster());
        if (damager == null) return;
        Player damagee = event.getEntity() instanceof Player ? (Player)event.getEntity() : null;
        if (damagee == null) {
            Entity entity = event.getEntity();
            if (!isProtected(entity)) return;
            if (isOwner(damager, entity)) return;
            if (isFarmAnimal(entity)) {
                autoCheckAction(damager, entity.getLocation(), Action.DAMAGE_FARM_ANIMAL, event);
            } else {
                autoCheckAction(damager, entity.getLocation(), Action.DAMAGE_ENTITY, event);
            }
            return;
        }
        // From here: PvP
        if (damager.equals(damagee)) return;
        Claim claim = plugin.getClaimAt(event.getEntity().getLocation());
        if (claim == null) {
            // PvP is disabled by default
            event.setCancelled(true);
            return;
        }
        claim = claim.getTopLevelClaim();
        String value = claim.getOptions().getOption("pvp");
        if (value == null || value.equals("false")) {
            // PvP is disabled by default
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player)) return;
        Player player = (Player)projectile.getShooter();
        final Claim claim;
        if (event.getHitBlock() != null) {
            if (!autoCheckAction(player, event.getHitBlock().getLocation(), Action.INTERACT_BLOCK)) {
                projectile.remove();
            }
        } else if (event.getHitEntity() != null) {
            Entity entity = event.getHitEntity();
            if (!isProtected(entity)) return;
            if (isFarmAnimal(entity)) {
                if (!autoCheckAction(player, entity.getLocation(), Action.DAMAGE_FARM_ANIMAL)) {
                    projectile.remove();
                }
                return;
            } else {
                if (!autoCheckAction(player, entity.getLocation(), Action.DAMAGE_ENTITY)) {
                    projectile.remove();
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Player player = getPlayerDamager(event.getEntity());
        if (player == null) return;
        autoCheckAction(player, event.getBlock().getLocation(), Action.BUILD, event);
    }
}
