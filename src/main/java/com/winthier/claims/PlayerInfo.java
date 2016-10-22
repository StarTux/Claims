package com.winthier.claims;

import com.winthier.claims.PlayerInfo;
import com.winthier.claims.util.Strings;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import lombok.Getter;
import lombok.Setter;

/**
 * Owner of a PlayerInfo may be offline.
 */
public class PlayerInfo {
    private static enum Config {
        NAME,
        MINUTES_PLAYED,
        CLAIM_BLOCKS,
        MAX_CLAIMS,
        CREATION_TIME,
        ;
        public final String key;
        Config() { this.key = name().toLowerCase(); }
    }

    private final UUID playerUuid;
    private String name;
    @Getter @Setter
    private int minutesPlayed = 0;
    @Getter @Setter
    private int claimBlocks = 1089;
    private Claim currentClaim = null;
    private Date creationTime = new Date();
    @Setter
    private boolean ignoreClaims = false;
    private boolean debug = false;
    private final Map<Claim, Highlight> highlights = new WeakHashMap<>();
    @Getter
    private final ClaimTool tool = new ClaimTool();
    // Buy Claim Blocks
    @Getter @Setter int buyClaimBlocksAmount = 0;
    @Getter @Setter UUID buyClaimBlocksId = null;
    // Info
    @Getter @Setter boolean claimToolReminded = false;
    @Getter @Setter int unclaimedBlocksPlaced = 0;
    @Getter @Setter Location lastUnclaimedBlockPlaced;
    // Silence Warning
    private long gotIt = 0;

    // Constructors

    PlayerInfo(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.name = Claims.getInstance().getDelegate().getPlayerName(playerUuid);
    }

    public static PlayerInfo forPlayer(UUID uuid) {
        return Claims.getInstance().getPlayerInfo(uuid);
    }

    public static PlayerInfo forPlayer(Player player) {
        PlayerInfo result = Claims.getInstance().getPlayerInfo(player);
        result.name = player.getName();
        return result;
    }

    // Info
    
    public UUID getUuid() {
        return playerUuid;
    }

    public String getName() {
        if (name == null) {
            name = Claims.getInstance().getDelegate().getPlayerName(playerUuid);
        }
        if (name == null) return "unknown";
        return name;
    }

    // Player Data
    
    public Claim getCurrentClaim() {
        if (currentClaim != null && !currentClaim.isValid()) {
            currentClaim = null;
        }
        return currentClaim;
    }
    public void setCurrentClaim(Claim currentClaim) { this.currentClaim = currentClaim; }

    public void addClaimBlocks(int claimBlocks) {
        this.claimBlocks += claimBlocks;
        if (this.claimBlocks < 0) this.claimBlocks = 0;
    }

    public int getTotalClaimBlocks() {
        int result = getClaimBlocks();
        for (Claim claim : Claims.getInstance().getPlayerClaims(playerUuid)) {
            if (claim.doBlocksCount()) {
                result -= claim.getArea();
            }
        }
        return Math.max(0, result);
    }

    // Storage
    
    public Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (name != null) result.put(Config.NAME.key, name);
        result.put(Config.MINUTES_PLAYED.key, minutesPlayed);
        result.put(Config.CLAIM_BLOCKS.key, claimBlocks);
        result.put(Config.CREATION_TIME.key, (Long)creationTime.getTime());
        return result;
    }

    public static PlayerInfo deserialize(UUID uuid, Map<String, Object> map) {
        PlayerInfo result = new PlayerInfo(uuid);
        result.claimBlocks = (Integer)map.get(Config.CLAIM_BLOCKS.key);
        if (!map.containsKey(Config.MINUTES_PLAYED.key)) {
            // legacy
            result.minutesPlayed = result.claimBlocks / 8;
        } else {
            result.minutesPlayed = (Integer)map.get(Config.MINUTES_PLAYED.key);
        }
        result.creationTime = new Date((Long)map.get(Config.CREATION_TIME.key));
        return result;
    }

    // Current Claim

    public void updateCurrentClaim(Player player) {
        this.name = player.getName();
        if (currentClaim == null) {
            Claim claim = Claims.getInstance().getClaimAt(player.getLocation());
            if (claim != null) {
                if (!claim.checkTrust(player.getUuid(), TrustType.BUILD)) {
                    if (claim.isAdminClaim()) {
                    //     player.sendMessage("&9Entering an administrator's claim.");
                    // } else if (claim.isOwner(player.getUuid())) {
                    //     player.sendMessage("&3Entering your claim. Welcome home.");
                    } else {
                        player.sendMessage("&cEntering %s claim.", Strings.genitive(claim.getOwnerName()));
                    }
                    // // Highlight
                    // if (claim.isAdminClaim()) {
                    //     highlightClaim(claim, Highlight.Type.ADMIN);
                    // } else {
                    //     highlightClaim(claim, Highlight.Type.ENEMY);
                    // }
                }
                currentClaim = claim;
            }
        } else {
            if (!currentClaim.isValid()) {
                currentClaim = null;
            } else if (!currentClaim.contains(player.getLocation())) {
                if (!currentClaim.checkTrust(player.getUuid(), TrustType.BUILD)) {
                    if (currentClaim.isAdminClaim()) {
                    //     player.sendMessage("&9Leaving an administrator's claim.");
                    // } else if (currentClaim.isOwner(player.getUuid())) {
                    //     player.sendMessage("&3Leaving your claim.");
                    } else {
                        player.sendMessage("&cLeaving %s claim.", Strings.genitive(currentClaim.getOwnerName()));
                    }
                }
                currentClaim = null;
            }
        }
    }

    // Admin settings

    public boolean doesIgnoreClaims() { return ignoreClaims; }
    public void toggleIgnoreClaims() { this.ignoreClaims = !this.ignoreClaims; }

    public boolean isDebugMode() { return debug; }
    public void toggleDebugMode() { this.debug = !this.debug; }

    // Claim Highlight

    void clearHighlights() {
        highlights.clear();
    }

    void removeHighlight(Claim claim) {
        Highlight highlight = highlights.remove(claim);
        if (highlight != null) {
            highlight.restore();
        }
    }

    void highlightClaim(Claim claim, Highlight.Type type) {
        removeHighlight(claim);
        Highlight highlight = Claims.getInstance().getDelegate().createHighlight(playerUuid, claim, type);
        if (highlight == null) return;
        highlights.put(claim, highlight);
        highlight.highlight();
        highlight.flash();
    }

    void highlightClaim(Claim claim) {
        highlightClaim(claim, Highlight.Type.forPlayer(playerUuid, claim));
    }

    void updateHighlights() {
        for (Claim claim : new ArrayList<Claim>(highlights.keySet())) {
            if (!claim.isValid()) {
                removeHighlight(claim);
            } else {
                Highlight highlight = highlights.get(claim);
                if (!highlight.secondTick()) removeHighlight(claim);
            }
        }
    }

    public boolean isClaimHighlighted(Claim claim) {
        return highlights.containsKey(claim);
    }

    void onLivedOneMinute() {
        minutesPlayed += 1;
        if (getTotalClaimBlocks() < 512 * 512) {
            claimBlocks += 8;
        }
        save();
    }

    public void save() {
        Claims.getInstance().storePlayerInfo(playerUuid, this);
    }

    public void setDidGetIt() {
        gotIt = System.currentTimeMillis() + 1000*60*60;
    }

    public boolean didGetIt() {
        return gotIt > System.currentTimeMillis();
    }
}
