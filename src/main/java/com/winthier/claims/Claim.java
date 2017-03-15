package com.winthier.claims;

import com.winthier.claims.util.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Data
@EqualsAndHashCode(of = {"world", "rectangle"})
public final class Claim {
    private enum Config {
        RECTANGLE,
        WORLD,
        OWNER,
        TRUSTED,
        PUBLIC_TRUST,
        CREATION_TIME,
        OPTIONS,
        SUBCLAIMS;

        public final String key;

        Config() {
            this.key = name().toLowerCase();
        }
    }

    // Essential properties
    private Rectangle rectangle;
    private String world;
    private UUID owner;
    // Trust
    private final Map<TrustType, List<UUID>> trusted = new EnumMap<>(TrustType.class);
    private final Set<TrustType> publicTrusted = EnumSet.noneOf(TrustType.class);
    // Claim hierarchy
    private Claim superClaim = null;
    private final List<Claim> subclaims = new LinkedList<>();
    // Details
    private Date creationTime;
    @Getter
    private final ClaimOptions options = new ClaimOptions();
    private boolean valid = false;

    private Claim() {
        for (TrustType trust : TrustType.values()) this.trusted.put(trust, new ArrayList<UUID>());
        this.creationTime = new Date();
    }

    private Claim(Rectangle rectangle, String world, UUID owner) {
        this();
        if (rectangle == null) throw new NullPointerException();
        if (world == null) throw new NullPointerException();
        if (owner == null) throw new NullPointerException();
        this.rectangle = rectangle;
        this.world = world;
        this.owner = owner;
    }

    private Claim(Claim copy) {
        this();
        this.rectangle = copy.rectangle;
        this.world = copy.world;
        this.owner = copy.owner;
        for (TrustType trust : TrustType.values()) this.trusted.get(trust).addAll(copy.trusted.get(trust));
        this.publicTrusted.addAll(copy.publicTrusted);
        this.superClaim = copy.superClaim;
        this.subclaims.addAll(copy.subclaims); // Their respective superclaims *have* to be adjusted
        this.creationTime = copy.creationTime;
        this.options.copy(copy.options);
        this.valid = false; // Validity has to be enabled after copying
    }

    public static Claim newClaim(Rectangle rectangle, String world, UUID owner) {
        return new Claim(rectangle, world, owner);
    }

    public Claim copy() {
        return new Claim(this);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(Config.RECTANGLE.key, rectangle.serialize());
        result.put(Config.WORLD.key, world);
        result.put(Config.OWNER.key, owner.toString());
        // Trusted map
        boolean anyoneTrusted = false;
        Map<String, List<String>> trustedMap = new HashMap<>();
        for (TrustType trust : TrustType.values()) {
            if (!trusted.get(trust).isEmpty()) {
                trustedMap.put(trust.toString(), Strings.serializeUuids(trusted.get(trust)));
                anyoneTrusted = true;
            }
        }
        if (anyoneTrusted) result.put(Config.TRUSTED.key, trustedMap);
        // Public trust map
        if (!publicTrusted.isEmpty()) {
            List<String> publicTrustedList = new ArrayList<>();
            for (TrustType trust : publicTrusted) publicTrustedList.add(trust.toString());
            result.put(Config.PUBLIC_TRUST.key, publicTrustedList);
        }
        result.put(Config.CREATION_TIME.key, creationTime.getTime());
        result.put(Config.OPTIONS.key, options.serialize());
        if (hasSubclaims()) {
            List<Object> list = new ArrayList<Object>();
            result.put(Config.SUBCLAIMS.key, list);
            for (Claim subclaim : subclaims) {
                list.add(subclaim.serialize());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Claim deserialize(Map<String, Object> map) {
        Rectangle rectangle = Rectangle.deserialize((Map<String, Object>)map.get(Config.RECTANGLE.key));
        String world = (String)map.get(Config.WORLD.key);
        UUID owner = UUID.fromString(map.get(Config.OWNER.key).toString());
        Claim result = newClaim(rectangle, world, owner);
        Object tmpTrusted = map.get(Config.TRUSTED.key);
        if (tmpTrusted != null) {
            Map<String, Object> trustedMap = (Map<String, Object>)tmpTrusted;
            for (TrustType trust : TrustType.values()) {
                List<String> list = (List<String>)trustedMap.get(trust.toString());
                if (list != null) result.trusted.get(trust).addAll(Strings.deserializeUuids(list));
            }
        }
        if (map.containsKey(Config.PUBLIC_TRUST.key)) {
            List<String> list = (List<String>)map.get(Config.PUBLIC_TRUST.key);
            for (String string : list) result.publicTrusted.add(TrustType.forString(string));
        }
        result.creationTime = new Date((Long)map.get(Config.CREATION_TIME.key));
        if (map.containsKey(Config.OPTIONS.key)) {
            result.options.deserialize((Map<String, Object>)map.get(Config.OPTIONS.key));
        }
        if (map.containsKey(Config.SUBCLAIMS.key)) {
            List<Object> list = (List<Object>)map.get(Config.SUBCLAIMS.key);
            for (Object o : list) {
                Claim subclaim = deserialize((Map<String, Object>)o);
                subclaim.setSuperClaim(result);
                Claims.getInstance().addClaim(subclaim);
            }
        }
        return result;
    }

    public boolean isOwner(UUID uuid) {
        return uuid.equals(owner);
    }

    /**
     * Fetch trust info in this claim and any sub claim.
     */
    private boolean hasTrust(UUID uuid, TrustType trust) {
        if (publicTrusted.contains(trust)) return true;
        if (trusted.get(trust).contains(uuid)) return true;
        if (trust != TrustType.PERMISSION && hasSuperClaim()) return superClaim.hasTrust(uuid, trust);
        return false;
    }

    public boolean checkTrust(UUID uuid, TrustType trust) {
        if (isOwner(uuid) && !isAdminClaim()) return true;
        if (PlayerInfo.forPlayer(uuid).doesIgnoreClaims()) return true;
        switch (trust) {
        case BUILD:
            return hasTrust(uuid, TrustType.BUILD);
        case CONTAINER:
            return hasTrust(uuid, TrustType.BUILD) || hasTrust(uuid, TrustType.CONTAINER);
        case ACCESS:
            return hasTrust(uuid, TrustType.BUILD) || hasTrust(uuid, TrustType.CONTAINER) || hasTrust(uuid, TrustType.ACCESS);
        case PERMISSION:
            return hasTrust(uuid, TrustType.PERMISSION);
        default:
            throw new IllegalArgumentException("TrustType not handled: " + trust.name());
        }
    }

    private boolean autoCheckTrust(Player player, TrustType trust) {
        return checkTrust(player.getUuid(), trust);
    }

    private boolean autoCheckTrust(Player player, TrustType trust, String message, Object... args) {
        boolean result = autoCheckTrust(player, trust);
        if (!result) {
            player.sendMessage(message, args);
            PlayerInfo.forPlayer(player.getUuid()).highlightClaim(this, isAdminClaim() ? Highlight.Type.ADMIN : Highlight.Type.ENEMY);
        }
        return result;
    }

    private void sendBuildDirectionMessage(Player player) {
        Claim claim = this;
        while (!checkTrust(player.getUuid(), TrustType.BUILD) && claim.getSuperClaim() != null) claim = claim.getSuperClaim();
        if (claim == null) return;
        Location location = player.getLocation();
        if (!claim.contains(location)) return;
        CardinalDirection direction = null;
        int distance = Integer.MAX_VALUE;
        for (CardinalDirection dir : CardinalDirection.values()) {
            int dist = Math.abs(claim.getBorder(dir) - location.getCoordinate(dir));
            if (dist < distance) {
                distance = dist;
                direction = dir;
            }
        }
        if (direction == null) return;
        if (claim.isAdminClaim()) {
            player.sendMessage("&cYou can type &o/wild&c or walk %d blocks %s to find a place to build.", distance + 1, direction);
        } else {
            player.sendMessage("&cYou can walk %d blocks %s to leave this claim.", distance, direction);
        }
    }

    public boolean autoCheckAction(Player player, Location location, Action action) {
        if (isExempt(location)) return true;
        String ownerName = isAdminClaim() ? "an administrator" : getOwnerName();
        Claims.getInstance().sendDebugMessage(player, "Action: %s", action.name()); // debug
        boolean result;
        switch (action) {
        case NONE:
            return true;
        case BUILD:
            result = autoCheckTrust(player, TrustType.BUILD, "&cThis area belongs to %s", ownerName);
            if (!result) sendBuildDirectionMessage(player);
            return result;
        case TRAMPLE:
            return autoCheckTrust(player, TrustType.BUILD);
        case INTERACT_BLOCK:
        case INTERACT_ENTITY:
            return autoCheckTrust(player, TrustType.BUILD);
        case SWITCH_DOOR:
            if (isAdminClaim()) return true;
            return autoCheckTrust(player, TrustType.ACCESS, "&cThis area belongs to %s", ownerName);
        case SWITCH_TRIGGER:
            if (isAdminClaim()) return true;
            return autoCheckTrust(player, TrustType.ACCESS, "&cThis area belongs to %s", ownerName);
        case OPEN_INVENTORY:
            if (isAdminClaim()) return true;
        case ACCESS_ANVIL:
            return autoCheckTrust(player, TrustType.CONTAINER, "&cThis area belongs to %s", ownerName);
        case DAMAGE_ENTITY:
            return autoCheckTrust(player, TrustType.BUILD, "&cThis belongs to %s", ownerName);
        case DAMAGE_FARM_ANIMAL:
            if (isAdminClaim()) return true;
            return autoCheckTrust(player, TrustType.BUILD, "&cThis belongs to %s", ownerName);
        case MOUNT_ENTITY:
            if (isAdminClaim()) return true;
            return autoCheckTrust(player, TrustType.BUILD, "&cThis belongs to %s", ownerName);
        case RIDE_VEHICLE:
            return true;
        case LEASH_ENTITY:
            return autoCheckTrust(player, TrustType.ACCESS, "&cThis belongs to %s", ownerName);
        case SHEAR_ENTITY:
            if (isAdminClaim()) return true;
            return autoCheckTrust(player, TrustType.ACCESS, "&cThis belongs to %s", ownerName);
        case TRANSFORM_ENTITY:
            return autoCheckTrust(player, TrustType.BUILD, "&cThis belongs to %s", ownerName);
        case SLEEP_BED:
            if (isAdminClaim()) return true;
            return autoCheckTrust(player, TrustType.ACCESS, "&cThis area belongs to %s", ownerName);
        case SET_SPAWN:
            return autoCheckTrust(player, TrustType.BUILD, "&cThis area belongs to %s", ownerName);
        default:
            System.err.println("Claim.autoCheckAction ignores action: " + action.name());
            return false;
        }
        //"&cThis area belongs to %s. Ask them to give you trust in this claim if you want to build."
    }

    public boolean contains(Location location) {
        if (!location.getWorldName().equals(world)) return false;
        return rectangle.contains(location.getX(), location.getZ());
    }

    public boolean isNear(Location location, int distance) {
        if (!location.getWorldName().equals(world)) return false;
        return rectangle.isNear(location.getX(), location.getZ(), distance);
    }

    public boolean contains(Claim other) {
        return rectangle.inset(1).contains(other.rectangle);
    }

    public Location getCenterLocation() {
        return new Location(world, rectangle.getCenterX(), 65, rectangle.getCenterY());
    }

    public int getArea() {
        return rectangle.getArea();
    }

    public boolean isAdminClaim() {
        return options.isAdminClaim();
    }

    public void setAdminClaim(boolean adminClaim) {
        options.setAdminClaim(adminClaim);
    }

    public String getOwnerName() {
        PlayerInfo info = Claims.getInstance().getPlayerInfo(owner);
        if (info != null) {
            String name = info.getName();
            if (name != null) return name;
        }
        return Claims.getInstance().getDelegate().getPlayerName(owner);
    }

    public List<String> getTrustedNames(TrustType trust) {
        List<String> result = new ArrayList<>(trusted.get(trust).size());
        for (UUID uuid : trusted.get(trust)) result.add(Claims.getInstance().getDelegate().getPlayerName(uuid));
        return result;
    }

    public boolean addTrusted(UUID uuid, TrustType trust) {
        if (trusted.get(trust).contains(uuid)) return false;
        this.trusted.get(trust).add(uuid);
        return true;
    }

    public boolean addPublicTrust(TrustType trust) {
        if (publicTrusted.contains(trust)) return false;
        publicTrusted.add(trust);
        return true;
    }

    public boolean removePublicTrust(TrustType trust) {
        if (!publicTrusted.contains(trust)) return false;
        publicTrusted.remove(trust);
        return true;
    }

    public boolean isPublicTrusted(TrustType trust) {
        return publicTrusted.contains(trust);
    }

    public boolean removeTrusted(UUID uuid, TrustType trust) {
        return this.trusted.get(trust).remove(uuid);
    }

    /**
     * Make sure that all subclaims' super claim point to this and
     * the own super claim contains this.  This may must be called
     * after the claim was created from a copy, and must not be
     * called at any other time.
     */
    public void insertIntoHierarchy() {
        for (Claim subclaim : subclaims) subclaim.setSuperClaim(this);
        if (hasSuperClaim()) superClaim.getSubclaims().add(this);
    }

    /**
     * If this claim contains any subclaims, a successive call of
     * insertIntoHierarchy() from a copied replacement claim is
     * absolutely necessary.
     * If not, all subclaims will point to an invalid claim and
     * will eventually disappear.
     */
    public void removeFromHierarchy() {
        if (hasSuperClaim()) superClaim.getSubclaims().remove(this);
        for (Claim subclaim : subclaims) subclaim.setSuperClaim(null);
        subclaims.clear();
    }

    public boolean collidesWith(Claim other) {
        if (!world.equals(other.world)) return false;
        if (!rectangle.intersects(other.rectangle)) return false;
        return true;
    }

    public int getWestBorder() {
        return rectangle.getWestBorder();
    }

    public int getEastBorder() {
        return rectangle.getEastBorder();
    }

    public int getNorthBorder() {
        return rectangle.getNorthBorder();
    }

    public int getSouthBorder() {
        return rectangle.getSouthBorder();
    }

    public int getBorder(CardinalDirection direction) {
        switch (direction) {
        case NORTH: return getNorthBorder();
        case SOUTH: return getSouthBorder();
        case WEST: return getWestBorder();
        case EAST: return getEastBorder();
        default: return 0;
        }
    }

    public int getWidth() {
        return rectangle.getWidth();
    }

    public int getHeight() {
        return rectangle.getHeight();
    }

    public String getWorldName() {
        return world;
    }

    public boolean doBlocksCount() {
        if (hasSuperClaim()) return false;
        if (isAdminClaim()) return false;
        return true;
    }

    public boolean canSpawnEntity() {
        return !options.doesDenyEntitySpawn();
    }

    public boolean isExempt(Location loc) {
        if (options != null) {
            return options.isExempt(loc);
        }
        return false;
    }

    public void editOptions(String... tokens) {
        try {
            options.edit(tokens);
        } catch (CommandException ce) {
            throw new CommandException(ce);
        } catch (Exception e) {
            throw new CommandException("Syntax error");
        }
    }

    public boolean hasSuperClaim() {
        return superClaim != null;
    }

    public boolean hasSubclaims() {
        return !subclaims.isEmpty();
    }

    public Claim getSubclaimAt(Location location) {
        for (Claim subclaim : subclaims) {
            if (subclaim.contains(location)) {
                return subclaim.getSubclaimAt(location);
            }
        }
        return this;
    }

    public Claim getTopLevelClaim() {
        Claim result = this;
        while (result.hasSuperClaim()) result = result.getSuperClaim();
        return result;
    }

    public List<Claim> getAllSubclaims() {
        if (!hasSubclaims()) return Collections.<Claim>emptyList();
        List<Claim> result = new ArrayList<>();
        result.addAll(subclaims);
        for (Claim subclaim : subclaims) {
            if (subclaim.hasSubclaims()) {
                result.addAll(subclaim.getAllSubclaims());
            }
        }
        return result;
    }

    public Set<CardinalDirection> cornersOfLocation(Location loc) {
        Set<CardinalDirection> result = EnumSet.noneOf(CardinalDirection.class);
        if (loc.getX() == getWestBorder()) result.add(CardinalDirection.WEST);
        if (loc.getX() == getEastBorder()) result.add(CardinalDirection.EAST);
        if (loc.getZ() == getNorthBorder()) result.add(CardinalDirection.NORTH);
        if (loc.getZ() == getSouthBorder()) result.add(CardinalDirection.SOUTH);
        return result;
    }

    public String humanClaimHierarchyType() {
        if (hasSuperClaim()) return "subclaim";
        return "claim";
    }

    public void highlightFull(Player player) {
        Claim top = getTopLevelClaim();
        player.info().highlightClaim(top);
        for (Claim sub : top.getAllSubclaims()) player.info().highlightClaim(sub);
    }

    public List<Location> getBorderBlocks() {
        List<Location> result = new ArrayList<>();
        int north = getNorthBorder();
        int south = getSouthBorder();
        int west = getWestBorder();
        int east = getEastBorder();
        for (int x = west; x <= east; ++x) {
            result.add(new Location(world, x, 65, north));
            result.add(new Location(world, x, 65, south));
        }
        for (int z = north; z <= south; ++z) {
            result.add(new Location(world, west, 65, z));
            result.add(new Location(world, east, 65, z));
        }
        return result;
    }

    public boolean shouldDenyCreeperDamage() {
        String option = getOptions().getOption("creeperDamage");
        return option == null || option.equals("false");
    }

    public boolean shouldDenyTNTDamage() {
        String option = getOptions().getOption("tntDamage");
        if (option == null || option.equals("false")) {
            return true;
        } else {
            return false;
        }
    }
}
