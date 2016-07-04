package com.winthier.claims;

import com.winthier.claims.util.Strings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

public class ClaimOptions {
    private static enum Config {
        ADMIN_CLAIM,
        DENY_ENTITY_SPAWN,
        EXEMPTIONS,
        COMMENT,
        ;
        public final String key;
        Config() { this.key = name().toLowerCase(); }
        public static Config fromString(String string) {
            for (Config config : Config.values()) {
                if (config.key.equalsIgnoreCase(string)) return config;
            }
            return null;
        }
    }

    final public static List<ClaimOption> PUBLIC_OPTIONS = Arrays.asList(
        ClaimOption.booleanOption("pvp", "PvP", "Player vs player combat", "false"),
        ClaimOption.booleanOption("creeperDamage", "Creeper Damage", "Creeper Explosions break blocks", "false"),
        ClaimOption.booleanOption("tntDamage", "TNT Damage", "Exploding TNT breaks blocks", "true"),
        ClaimOption.booleanOption("fireSpread", "Fire Spread", "Fire will spread and burn blocks", "false")
        );
    
    private Boolean adminClaim = null;
    private Boolean denyEntitySpawn = null;
    @Getter @Setter
    private String comment = null;
    private List<Cuboid> exemptions = new LinkedList<>();
    final Map<String, String> publicOptions = new HashMap<>();

    // Edit all these whenever you add a member

    public void copy(ClaimOptions copy) {
        this.adminClaim = copy.adminClaim;
        this.denyEntitySpawn = copy.denyEntitySpawn;
        this.comment = copy.comment;
        for (Cuboid cuboid : copy.exemptions) this.exemptions.add(cuboid);
        for (Map.Entry<String, String> entry: copy.publicOptions.entrySet()) this.publicOptions.put(entry.getKey(), entry.getValue());
    }

    public Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (adminClaim != null) result.put(Config.ADMIN_CLAIM.key, true);
        if (denyEntitySpawn != null) result.put(Config.DENY_ENTITY_SPAWN.key, true);
        if (comment != null) result.put(Config.COMMENT.key, comment);
        if (!exemptions.isEmpty()) {
            List<List<Integer>> list = new ArrayList<>();
            for (Cuboid cuboid : exemptions) list.add(cuboid.serialize());
            result.put(Config.EXEMPTIONS.key, list);
        }
        for (Map.Entry<String, String> entry: publicOptions.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * This method is not static because ClaimOptions is always
     * created with an empty state and then filled with
     * information.
     */
    @SuppressWarnings("unchecked")
    public void deserialize(Map<String, Object> map) {
        adminClaim = map.containsKey(Config.ADMIN_CLAIM.key) ? true : null;
        denyEntitySpawn = map.containsKey(Config.DENY_ENTITY_SPAWN.key) ? true : null;
        comment = map.containsKey(Config.COMMENT.key) ? (String)map.get(Config.COMMENT.key) : null;
        if (map.containsKey(Config.EXEMPTIONS.key)) {
            List<List<Integer>> list = (List<List<Integer>>)map.get(Config.EXEMPTIONS.key);
            for (List<Integer> item : list) exemptions.add(Cuboid.deserialize(item));
        }
        for (ClaimOption claimOption: PUBLIC_OPTIONS) {
            if (map.containsKey(claimOption.key)) {
                publicOptions.put(claimOption.key, map.get(claimOption.key).toString());
            }
        }
    }

    // Public interface

    public boolean isAdminClaim() {
        return adminClaim != null;
    }

    public void setAdminClaim(boolean val) {
        this.adminClaim = val ? true : null;
    }

    public boolean doesDenyEntitySpawn() {
        return denyEntitySpawn != null;
    }

    public void setDenyEntitySpawn(boolean val) {
        denyEntitySpawn = val ? true : null;
    }

    public boolean isExempt(Location loc) {
        if (exemptions != null) {
            for (Cuboid cuboid : exemptions) {
                if (cuboid.contains(loc)) return true;
            }
        }
        return false;
    }

    public void edit(String... args) {
        String key = args[0].toLowerCase();
        Config config = Config.fromString(key);
        if (config == null) {
            StringBuilder sb = new StringBuilder("Valid entries:");
            for (Config val : Config.values()) {
                sb.append(" ").append(val.key);
            }
            throw new CommandException(sb.toString());
        }
        switch (config) {
        case ADMIN_CLAIM: {
            if (args.length != 2) throw new CommandException("Syntax Error");
            this.adminClaim = Boolean.parseBoolean(args[1]) ? true : null;
        } break;
        case DENY_ENTITY_SPAWN: {
            if (args.length != 2) throw new CommandException("Syntax Error");
            this.denyEntitySpawn = Boolean.parseBoolean(args[1]) ? true : null;
        } break;
        case COMMENT: {
            if (args.length >= 2) {
                List<String> list = new ArrayList<>();
                for (int i = 1; i < args.length; ++i) list.add(args[i]);
                this.comment = Strings.fold(list, " ");
            } else {
                this.comment = null;
            }
        }
        case EXEMPTIONS: {
            if (args.length != 8) throw new CommandException("Syntax Error");
            if (args[1].equalsIgnoreCase("add")) {
                int x1 = Integer.parseInt(args[2]);
                int y1 = Integer.parseInt(args[3]);
                int z1 = Integer.parseInt(args[4]);
                int x2 = Integer.parseInt(args[5]);
                int y2 = Integer.parseInt(args[6]);
                int z2 = Integer.parseInt(args[7]);
                if (exemptions == null) exemptions = new ArrayList<>();
                exemptions.add(new Cuboid(x1, y1, z1, x2, y2, z2));
            } else {
                throw new CommandException("Syntax Error");
            }
        } break;
        default: throw new CommandException("Not implemented");
        }
    }

    public void setOption(String key, String value) {
        publicOptions.put(key, value);
    }

    public String getOption(String key) {
        return publicOptions.get(key);
    }
}

// For exemptions
class Coord {
    public final int x, y, z;
    public Coord(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}

// For exemptions
class Cuboid {
    public final Coord c1, c2;
    public Cuboid(Coord c1, Coord c2) {
        this.c1 = c1;
        this.c2 = c2;
    }
    public Cuboid(int x1, int y1, int z1, int x2, int y2, int z2) {
        this(new Coord(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2)),
             new Coord(Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)));
    }
    public List<Integer> serialize() {
        List<Integer> result = new ArrayList<>();
        result.add(c1.x);
        result.add(c1.y);
        result.add(c1.z);
        result.add(c2.x);
        result.add(c2.y);
        result.add(c2.z);
        return result;
    }
    public static Cuboid deserialize(List<Integer> list) {
        return new Cuboid(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4), list.get(5));
    }
    public boolean contains(int x, int y, int z) {
        if (x < c1.x || x > c2.x) return false;
        if (y < c1.y || y > c2.y) return false;
        if (z < c1.z || z > c2.z) return false;
        return true;
    }
    public boolean contains(Location loc) {
        return contains(loc.getX(), loc.getY(), loc.getZ());
    }
}
