package com.winthier.claims;

import com.winthier.claims.util.JSON;
import com.winthier.claims.util.Strings;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Every PlayerInfo should contain an instance of this class
 */
public class ClaimTool {
    public static enum State {
        INIT,
        NEW,
        RESIZE,
        ;
    }

    private State state = State.INIT;
    private Location location = null;
    private Claim claim = null;
    private final Set<CardinalDirection> directions = EnumSet.noneOf(CardinalDirection.class);

    private void touchInit(Player player, Location location) {
        Claim claim = Claims.getInstance().getClaimAt(location);
        if (this.location != null && this.location.equals(location)) {
            state(State.NEW);
            Claim oldClaim = Claims.getInstance().getClaimAt(location);
            if (oldClaim == null || oldClaim.isOwner(player.getUuid())) {
                player.sendMessage("&3&lClaims&r&o Now click the other corner of your desired %s.", oldClaim == null ? "claim" : "subclaim");
                player.tellRaw(JSON.commandRunButton("&r[&cAbort&r]", "&cAbort claim creation", "/claim tool abort"));
            }
        } else if (claim == null) {
            player.sendMessage("&3&lClaims&r&o No claim here. Click the same block again to make a claim.");
        } else if (claim.isOwner(player.getUuid()) || PlayerInfo.forPlayer(player).doesIgnoreClaims()) {
            Set<CardinalDirection> dirs = claim.cornersOfLocation(location);
            if (player.info().isClaimHighlighted(claim) && !dirs.isEmpty()) {
                // A claim border was clicked. Engage resize mode.
                state(State.RESIZE);
                this.location = location;
                this.claim = claim;
                this.directions.addAll(dirs);
                player.info().highlightClaim(claim);
                List<Object> message = new ArrayList<>();
                message.add(Strings.format("&3&lClaims&r&o Resizing this " + claim.humanClaimHierarchyType() + ". Right-click a block or click "));
                message.add(JSON.commandRunButton("&4[abort]", "&4Click to abort\n&4resizing this\n&4" + claim.humanClaimHierarchyType() + ".", "/claim tool abort"));
                message.add(Strings.format("&o."));
                player.tellRaw(message);
            }
        } else {
            Claims.getInstance().getActions().info(player, claim);
            if (claim.checkTrust(player.getUuid(), TrustType.PERMISSION)) {
                player.sendMessage("&3&lClaims&r&o Click the same block again to make a subclaim.");
            }
        }
        this.location = location;
    }

    private void touchNewClaim(Player player, Location location) {
        Location locA = this.location;
        Location locB = location;
        if (!locA.getWorldName().equals(locB.getWorldName())) {
            state(State.INIT);
            return;
        }
        Claim newClaim = Claim.newClaim(Rectangle.forCorners(locA, locB), locA.getWorldName(), player.getUuid());
        Claim superClaim = Claims.getInstance().getClaimAt(locA);
        if (superClaim != null && !superClaim.isOwner(player.getUuid())) {
            player.sendMessage("&3&lClaims&c&o This claim does not belong to you.");
            state(State.INIT);
            return;
        }
        if (superClaim != null) newClaim.setSuperClaim(superClaim);
        if (player.info().getTotalClaimBlocks() < newClaim.getArea()) {
            player.sendMessage("&3&lClaims&c&o You don't have enough claim blocks!");
            state(State.INIT);
            return;
        }
        try {
            Claims.getInstance().getActions().throwOnClaimConflict(newClaim, null);
        } catch (ClaimEditException cee) {
            player.sendMessage("&3&lClaims&c&o Could not create claim because %s.", cee.getMessage());
            state(State.INIT);
            return;
        }
        newClaim.getOptions().setComment(String.format("%s created %s", player.getName(), Strings.formatDateNow()));
        Claims.getInstance().addClaim(newClaim);
        Claims.getInstance().saveClaims();
        player.sendMessage("&3&lClaims&r&o Claim created. Cost %d claim blocks.", newClaim.getArea());
        player.showTitle("", "&aClaim created");
        player.info().highlightClaim(newClaim);
        state(State.INIT);
    }

    private void touchSubclaim(Player player, Location location) {
    }

    private void touchResize(Player player, Location location) {
        Rectangle rect = this.claim.getRectangle();
        int north = rect.getNorthBorder();
        int east = rect.getEastBorder();
        int south = rect.getSouthBorder();
        int west = rect.getWestBorder();
        if (this.directions.contains(CardinalDirection.NORTH)) north = location.getZ();
        if (this.directions.contains(CardinalDirection.EAST)) east = location.getX();
        if (this.directions.contains(CardinalDirection.SOUTH)) south = location.getZ();
        if (this.directions.contains(CardinalDirection.WEST)) west = location.getX();
        rect = Rectangle.forNorthEastSouthWest(north, east, south, west);
        try {
            claim = Claims.getInstance().getActions().resizeClaim(player, claim, rect);
        } catch (ClaimEditException cee) {
            player.sendMessage("&cCould not resize claim because %s.", cee.getMessage());
            return;
        }
        player.sendMessage("&3&lClaims&r&o %s resized.", Strings.upperCaseWord(claim.humanClaimHierarchyType()));
        player.showTitle("", "&aClaim resized");
        player.info().highlightClaim(claim);
        reset();
    }

    private void state(State state) {
        reset();
        this.state = state;
    }
    
    // Public interface

    /**
     * Call this when the player uses the tool on a block. This
     * plugin comes with a builtin tool, but you can make other
     * tools with another plugin.
     */
    public void onTouch(Player player, Location location) {
        if (claim != null && !claim.isValid()) {
            reset();
            return;
        }
        switch (state) {
        case INIT: touchInit(player, location); break;
        case NEW: touchNewClaim(player, location); break;
        case RESIZE: touchResize(player, location); break;
        }
    }

    public boolean reset() {
        boolean result = state != State.INIT;
        state = State.INIT;
        location = null;
        location = null;
        directions.clear();
        claim = null;
        return result;
    }
}
