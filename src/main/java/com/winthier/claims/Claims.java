package com.winthier.claims;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.Getter;
import org.yaml.snakeyaml.Yaml;

@Getter
public final class Claims {
    @Getter private static Claims instance;
    private final ClaimCommand claimCommand = new ClaimCommand(this);
    private final AdminCommand adminCommand = new AdminCommand(this);
    private final ClaimAction actions = new ClaimAction(this);
    private final List<Claim> allClaims = new LinkedList<>();
    private final Map<UUID, PlayerInfo> players = new HashMap<>();
    private Map<Object, Object> playersCache;
    private Set<String> worldBlacklist = new HashSet<>();
    private Map<String, String> worldAliases = new HashMap<>();
    private File dataFolder;
    private PluginDelegate delegate;
    private int ticks = 0;
    private boolean playersNeedSaving = false;
    private boolean claimsNeedSaving = false;
    private final ConcurrentLinkedQueue<Runnable> asyncTasks = new ConcurrentLinkedQueue<>();
    private boolean allowBuyClaimBlocks = true;

    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

    public Claims() {
        this.instance = this;
    }

    /* Initializing setters* * * * * * * * * * * * * * * * * * */

    public void setDelegate(PluginDelegate delegate) {
        this.delegate = delegate;
    }

    /* Public event handlers * * * * * * * * * * * * * * * * * */

    public void onEnable() {
        loadConfig();
        loadClaims();
        loadPlayers();
    }

    public void onDisable() {
        storeClaims();
        storePlayers();
        while (runAsyncTask());
    }

    public void onTick() {
        if (ticks % 20 == 0) {
            if (claimsNeedSaving) {
                claimsNeedSaving = false;
                storeClaims();
            }
            if (playersNeedSaving) {
                playersNeedSaving = false;
                storePlayers();
            }
            for (PlayerInfo info : this.players.values()) {
                info.updateHighlights();
            }
        }
        List<Player> playerList = getDelegate().getOnlinePlayers();
        // Update claims
        // if (!playerList.isEmpty()) {
        //     Player player = playerList.get(ticks % playerList.size());
        //     PlayerInfo info = getPlayerInfo(player.getUuid());
        //     info.updateCurrentClaim(player);
        // }
        // Give Claim Points
        if (!playerList.isEmpty() && ticks % (60 * 20) == 0) {
            for (Player player : playerList) {
                UUID uuid = player.getUuid();
                PlayerInfo info = player.info();
                info.onLivedOneMinute();
            }
            savePlayers();
        }
        ticks += 1;
    }

    public void asyncLoop() {
        if (!runAsyncTask()) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException ie) { }
        }
    }

    private synchronized boolean runAsyncTask() {
        Runnable run = asyncTasks.poll();
        if (run == null) return false;
        try {
            run.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    public void onPlayerJoin(Player player) {
        getPlayerInfo(player).setCurrentClaim(getClaimAt(player.getLocation()));
    }

    public void onPlayerQuit(Player player) {
        players.remove(player.getUuid());
    }

    // /* Debug * * * * * * * * * * * * * * * * * * * * * * * * * */

    // public void test(Player player) {
    //     Location location = player.getLocation();
    //     int cx = location.getX();
    //     int cy = location.getZ();
    //     Rectangle rect = new Rectangle(cx - 16, cy - 16, 32, 32);
    //     List<UUID> list = new ArrayList<>();
    //     Claim claim = new Claim(rect, location.getWorldName(), player.getUuid(), list);
    //     claim.setValid(true);
    //     claims.add(claim);
    // }

    public void sendDebugMessage(Player player, String message, Object... args) {
        PlayerInfo info = getPlayerInfo(player.getUuid());
        if (!info.isDebugMode()) return;
        player.sendMessage(message, args);
    }

    /* Private getters * * * * * * * * * * * * * * * * * * * * */

    File getClaimsFile() {
        return new File(delegate.getDataFolder(), "claims.yml");
    }

    File getPlayersFile() {
        return new File(delegate.getDataFolder(), "players.yml");
    }

    /* Data storage  * * * * * * * * * * * * * * * * * * * * * */

    @SuppressWarnings("unchecked")
    void loadConfig() {
        worldBlacklist.clear();
        worldAliases.clear();
        File file = new File(getDelegate().getDataFolder(), "config.yml");
        Map<String, Object> config;
        try {
            config = (Map<String, Object>)new Yaml().load(new FileReader(file));
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
            return;
        }
        if (config.containsKey("WorldBlacklist")) {
            worldBlacklist.addAll((List<String>)config.get("WorldBlacklist"));
        }
        if (config.containsKey("WorldAliases")) {
            for (Map.Entry<String, String> entry : ((Map<String, String>)config.get("WorldAliases")).entrySet()) {
                worldAliases.put(entry.getKey(), entry.getValue());
            }
        }
        if (config.containsKey("AllowBuyClaimBlocks")) {
            allowBuyClaimBlocks = (config.get("AllowBuyClaimBlocks") == Boolean.TRUE);
        }
    }

    void loadClaims() {
        allClaims.clear();
        File file = getClaimsFile();
        if (file.exists()) {
            try {
                FileReader reader = new FileReader(file);
                Yaml yaml = new Yaml();
                List list = (List)yaml.load(reader);
                for (Object o : list) {
                    @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>)o;
                    Claim claim = Claim.deserialize(map);
                    // We may not use addClaim() here as it would
                    // try to fix the hierarchy.
                    allClaims.add(claim);
                    claim.setValid(true);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    void loadPlayers() {
        players.clear();
        File file = getPlayersFile();
        if (file.exists()) {
            try {
                FileReader reader = new FileReader(file);
                Yaml yaml = new Yaml();
                Map map = (Map)yaml.load(reader);
                this.playersCache = map;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            playersCache = new LinkedHashMap<>();
        }
    }

    private void storeClaims() {
        final List<Object> storage = new ArrayList<>();
        for (Claim claim : allClaims) {
            if (!claim.hasSuperClaim()) {
                // Subclaims will be serialized by their
                // superclaims.
                storage.add(claim.serialize());
            }
        }
        final File file = getClaimsFile();
        Runnable run = new Runnable() {
            @Override public void run() {
                String output = new Yaml().dump(storage);
                try {
                    PrintWriter writer = new PrintWriter(file);
                    writer.println(output);
                    writer.flush();
                    writer.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        };
        asyncTasks.add(run);
    }

    private void storePlayers() {
        if (playersCache == null) return;
        final File file = getPlayersFile();
        Runnable run = new Runnable() {
            @Override public void run() {
                String output = null;
                synchronized (playersCache) {
                    output = new Yaml().dump(playersCache);
                }
                try {
                    PrintWriter writer = new PrintWriter(file);
                    writer.println(output);
                    writer.flush();
                    writer.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        };
        asyncTasks.add(run);
    }

    public void saveClaims() {
        claimsNeedSaving = true;
    }

    public void savePlayers() {
        playersNeedSaving = true;
    }

    @SuppressWarnings("unchecked")
    PlayerInfo fetchPlayerInfo(UUID uuid) {
        if (playersCache == null) loadPlayers();
        PlayerInfo result;
        Object o = null;
        o = playersCache.get(uuid.toString());
        if (o == null) {
            result = new PlayerInfo(uuid);
        } else {
            result = PlayerInfo.deserialize(uuid, (Map<String, Object>)o);
        }
        return result;
    }

    void storePlayerInfo(UUID uuid, PlayerInfo info) {
        if (playersCache == null) loadPlayers();
        Object o = info.serialize();
        synchronized (playersCache) {
            playersCache.put(uuid.toString(), o);
        }
    }

    public PlayerInfo getPlayerInfo(UUID uuid) {
        PlayerInfo result = players.get(uuid);
        if (result == null) {
            result = fetchPlayerInfo(uuid);
            players.put(uuid, result);
        }
        return result;
    }

    public PlayerInfo getPlayerInfo(Player player) {
        return getPlayerInfo(player.getUuid());
    }

    // TODO refactor and remove
    public PluginDelegate getDelegate() {
        return delegate;
    }

    /* Public interface* * * * * * * * * * * * * * * * * * * * */

    public void addClaim(Claim claim) {
        claim.insertIntoHierarchy();
        allClaims.add(claim);
        claim.setValid(true);
    }

    public void removeClaim(Claim claim) {
        for (Claim subclaim : new ArrayList<Claim>(claim.getSubclaims())) removeClaim(subclaim);
        claim.removeFromHierarchy();
        allClaims.remove(claim);
        claim.setValid(false);
    }

    /**
     * Find all top level claims
     */
    public List<Claim> getPlayerClaims(UUID uuid) {
        List<Claim> result = new ArrayList<>();
        for (Claim claim : allClaims) {
            if (claim.isAdminClaim()) continue;
            if (claim.hasSuperClaim()) continue;
            if (!claim.isOwner(uuid)) continue;
            result.add(claim);
        }
        return result;
    }

    public int countPlayerClaims(UUID uuid, List<Claim> claims) {
        int result = 0;
        for (Claim claim : allClaims) {
            if (claim.isOwner(uuid) && claim.doBlocksCount()) {
                result += 1;
            }
        }
        return result;
    }

    public int countPlayerClaims(UUID uuid) {
        return countPlayerClaims(uuid, allClaims);
    }

    /**
     * Find the claim at the given location, if any. If there are
     * any subclaims, find the most accurate subclaim at the
     * deepest level of the hierarchy.
     */
    public Claim getClaimAt(Location location) {
        for (Claim claim : allClaims) {
            if (claim.contains(location)) {
                return claim.getSubclaimAt(location);
            }
        }
        return null;
    }

    public Collection<Claim> findClaimsNear(Location location, int distance) {
        List<Claim> result = new ArrayList<>();
        for (Claim claim : allClaims) {
            if (claim.isNear(location, distance)) {
                result.add(claim);
            }
        }
        return result;
    }

    public Claim getClaimAt(String worldName, int x, int y, int z) {
        Location location = new Location(worldName, x, y, z);
        return getClaimAt(location);
    }

    public boolean canBuild(UUID player, String worldName, int x, int y, int z) {
        Claim claim = getClaimAt(worldName, x, y, z);
        if (claim == null) return true;
        return claim.checkTrust(player, TrustType.BUILD);
    }

    @Deprecated
    public boolean autoCheckAction(Player player, Location location) {
        return autoCheckAction(player, location, Action.BUILD);
    }

    public boolean autoCheckAction(Player player, Location location, Action action) {
        Claim claim = getClaimAt(location);
        if (claim == null) {
            return true;
        } else {
            return claim.autoCheckAction(player, location, action);
        }
    }

    public boolean checkEntitySpawn(Location location) {
        Claim claim = getClaimAt(location);
        if (claim != null) {
            if (!claim.canSpawnEntity()) return false;
        }
        return true;
    }

    /**
     * Check whether a claim collides with other claims. Another
     * claim can be exempted in case this is a resize
     * operation. Leave exemptedClaim null to ignore.
     */
    public boolean doesClaimCollide(Claim claim, Claim exemptedClaim) {
        List<Claim> claimList = claim.hasSuperClaim() ? claim.getSuperClaim().getSubclaims() : allClaims;
        if (claim.hasSuperClaim()) {
            for (Claim neighbor : claim.getSuperClaim().getSubclaims()) {
                if (neighbor == exemptedClaim) continue;
                if (neighbor == claim) continue;
                if (neighbor.collidesWith(claim)) return true;
            }
        } else {
            for (Claim neighbor : allClaims) {
                if (neighbor.hasSuperClaim()) continue;
                if (neighbor == exemptedClaim) continue;
                if (neighbor == claim) continue;
                if (neighbor.collidesWith(claim)) return true;
            }
        }
        return false;
    }

    public boolean doesClaimCollide(Claim claim) {
        return doesClaimCollide(claim, null);
    }

    /**
     * Return true if the claim is a subclaim and is not within
     * its super claim.
     */
    public boolean doesClaimExceedBounds(Claim claim) {
        if (!claim.hasSuperClaim()) return false;
        return (!claim.getSuperClaim().contains(claim));
    }

    public boolean doesClaimExcludeSubs(Claim claim) {
        if (!claim.hasSubclaims()) return false;
        for (Claim subclaim : claim.getSubclaims()) {
            if (!claim.contains(subclaim)) return true;
        }
        return false;
    }

    public boolean isClaimTooSmall(Claim claim) {
        if (claim.getWidth() < 1 || claim.getHeight() < 1) return true;
        if (claim.hasSuperClaim()) return false;
        return claim.getWidth() < 8 || claim.getHeight() < 8;
    }

    public boolean isClaimInBlacklistedWorld(Claim claim) {
        return worldBlacklist.contains(claim.getWorld());
    }

    public String getWorldAlias(String world) {
        String result = worldAliases.get(world);
        if (result == null) return world;
        return result;
    }

    public String format(String string, Object... args) {
        return delegate.format(string, args);
    }
}
