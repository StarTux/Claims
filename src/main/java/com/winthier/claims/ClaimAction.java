package com.winthier.claims;

import com.winthier.claims.util.JSON;
import com.winthier.claims.util.Players;
import com.winthier.claims.util.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Outsourced from Claims. All these are changes to claims on
 * behalf of a player. Some of them throw ClaimEditException on
 * failure, which has to be caught to retrieve the error type and
 * message.
 */
@RequiredArgsConstructor
public class ClaimAction {
    private final Claims claims;

    /*
     * /claim info
     */
    public boolean info(Player sender, Claim claim) {
        PlayerInfo info = PlayerInfo.forPlayer(sender);
        claim.highlightFull(sender);
        sender.sendMessage("");
        sender.sendMessage("&3&l%s&r&o Info", Strings.upperCaseWord(claim.humanClaimHierarchyType()));
        sender.sendMessage("&3Owner&b %s", claim.isAdminClaim() ? "an admin" : claim.getOwnerName());
        sender.sendMessage("&3Coordinates&b %d&3,&b%d&3 - &b%d&3,&b%d&3", claim.getWestBorder(), claim.getNorthBorder(), claim.getEastBorder(), claim.getSouthBorder());
        if (claim.checkTrust(sender.getUuid(), TrustType.PERMISSION)) {
            sender.sendMessage("&3Created&b %s", Strings.formatDate(claim.getCreationTime()));
            sender.sendMessage("&3Size&b %d&3x&b%d &3(&b%d blocks&3)", claim.getWidth(), claim.getHeight(), claim.getArea());
            // If the player has permission trust, show him permission info and options
            boolean trustedHeader = false;
            for (TrustType trust : TrustType.values()) {
                if (claim.isPublicTrusted(trust)) {
                    sender.sendMessage("&3&m &3 %s&b * &3(Public)", Strings.upperCaseWord(trust.human));
                } else {
                    List<String> names = claim.getTrustedNames(trust);
                    if (names.isEmpty()) continue;
                    if (!trustedHeader) {
                        sender.sendMessage("&3Trusted");
                        trustedHeader = true;
                    }
                    sender.sendMessage("&3&m &3 %s&b %s", Strings.upperCaseWord(trust.human), Strings.fold(names, ", "));
                }
            }
            List<Object> message = new ArrayList<>();
            message.add("");
            message.add(JSON.commandSuggestButton("&b[Trust]", "&3Click to trust\n&3someone to build\n&3in this " + claim.humanClaimHierarchyType() + ".", "/claim trust "));
            message.add(" ");
            message.add(JSON.commandSuggestButton("&b[Grow]", "&3Click to expand\n&3this " + claim.humanClaimHierarchyType() + ".", "/claim grow 8"));
            message.add(" ");
            message.add(JSON.commandSuggestButton("&b[Sub]", "&3Click to create\n&3a subclaim\n&3around yourself.", "/claim sub"));
            if (claim.isOwner(sender.getUuid())) {
                // Only owners can abandon
                message.add(" ");
                message.add(JSON.commandSuggestButton("&4[Abandon]", "&4Click to abandon\n&4this " + claim.humanClaimHierarchyType() + ".", "/claim abandon"));
            }
            sender.tellRaw(message);
        }
        sender.sendMessage("");
        return true;
    }

    /*
     * /claim trust <player>
     * /claim containertrust <player>
     * /claim accesstrust <player>
     * /claim permissiontrust <player>
     */
    public boolean trust(Player sender, TrustType trust, UUID trustee) {
        Claim claim = claims.getClaimAt(sender.getLocation());
        if (claim == null) throw new CommandException("Stand in your claim to give people trust in it");
        if (trust == TrustType.PERMISSION) {
            // Only owners can give permission trust
            if (!claim.isOwner(sender.getUuid())) throw new CommandException("This claim does not belong to you.");
        } else {
            // For any other trust type, you need permission trust and the trust type itself.
            if (!claim.checkTrust(sender.getUuid(), TrustType.PERMISSION)) throw new CommandException("This claim does not belong to you");
            if (!claim.checkTrust(sender.getUuid(), trust)) throw new CommandException("You must have " + trust.human + " trust yourself to do this");
        }
        if (!claim.addTrusted(trustee, trust)) throw new CommandException("Player already trusted: " + Players.getName(trustee));
        sender.sendMessage("&3&lClaims &r&oGave %s %s trust in this %s.", Players.getName(trustee), trust.human, claim.humanClaimHierarchyType());
        return true;
    }

    public boolean trustPublic(Player sender, TrustType trust) {
        Claim claim = claims.getClaimAt(sender.getLocation());
        if (claim == null) throw new CommandException("Stand in your claim to give people trust in it");
        if (trust == TrustType.PERMISSION) {
            // Only owners can give permission trust
            if (!claim.isOwner(sender.getUuid())) throw new CommandException("This claim does not belong to you.");
        } else {
            // For any other trust type, you need permission trust and the trust type itself.
            if (!claim.checkTrust(sender.getUuid(), TrustType.PERMISSION)) throw new CommandException("This claim does not belong to you");
            if (!claim.checkTrust(sender.getUuid(), trust)) throw new CommandException("You must have " + trust.human + " trust yourself to do this");
        }
        // Only owners can trust public
        if (!claim.isOwner(sender.getUuid())) throw new CommandException("This claim does not belong to you.");
        if (!claim.addPublicTrust(trust)) throw new CommandException("Public " + trust.human + " trust already granted");
        sender.sendMessage("&3&lClaims&r&o Gave %s trust to public in this %s", trust.human, claim.humanClaimHierarchyType());
        return true;
    }

    /*
     * /claim untrust <player>
     */
    public boolean untrust(Player sender, UUID trustee) {
        Claim claim = claims.getClaimAt(sender.getLocation());
        if (claim == null) throw new CommandException("Stand in your claim to untrust people in it");
        if (!claim.checkTrust(sender.getUuid(), TrustType.PERMISSION)) throw new CommandException("This claim does not belong to you");
        boolean wasTrusted = false;
        for (TrustType trust : TrustType.values()) {
            if (trust == TrustType.PERMISSION && !claim.isOwner(sender.getUuid())) continue;
            if (!claim.checkTrust(sender.getUuid(), trust)) continue;
            if (claim.removeTrusted(trustee, trust)) wasTrusted = true;
        }
        
        if (!wasTrusted) throw new CommandException("Player not trusted: " + Players.getName(trustee));        
        sender.sendMessage("&3&lClaims&r&o Removed %s permission in this claim.", Strings.genitive(Players.getName(trustee)));
        return true;
    }

    public boolean untrustPublic(Player sender) {
        Claim claim = claims.getClaimAt(sender.getLocation());
        if (claim == null) throw new CommandException("Stand in your claim to untrust people in it");
        if (!claim.checkTrust(sender.getUuid(), TrustType.PERMISSION)) throw new CommandException("This claim does not belong to you");
        // Only owners can trust public
        if (!claim.isOwner(sender.getUuid())) throw new CommandException("This claim does not belong to you.");
        boolean wasTrusted = false;
        for (TrustType trust : TrustType.values()) {
            if (claim.removePublicTrust(trust)) wasTrusted = true;
        }
        if (!wasTrusted) throw new CommandException("Public was not trusted");
        sender.sendMessage("&3&lClaims&r&o Removed public trust from this claim.");
        return true;
    }
    
    public void grow(Player sender, Claim oldClaim, CardinalDirection direction, int amount) {
        Rectangle rectangle = direction == null ? oldClaim.getRectangle().outset(amount) : oldClaim.getRectangle().expand(amount, direction);
        Claim newClaim = null;
        try {
            newClaim = resizeClaim(sender, oldClaim, rectangle);
        } catch (ClaimEditException cee) {
            sender.sendMessage("&3&lClaims&c&o Could not expand claim because %s.", cee.getMessage());
            return;
        }
        if (amount > 0) {
            sender.sendMessage("&3&lClaims&r&o Claim expanded %d blocks. Cost %d claim blocks.", amount, newClaim.getArea() - oldClaim.getArea());
        } else {
            sender.sendMessage("&3&lClaims&r&o Claim inset %d blocks. Refunded %d claim blocks.", amount, oldClaim.getArea() - newClaim.getArea());
        }
        sender.showTitle("", "&aClaim resized");
        PlayerInfo.forPlayer(sender).setCurrentClaim(newClaim); // ???
    }

    /**
     * Try resizing a claim to a new size. Throw a
     * ClaimEditException on failure. On success, return the new,
     * valid claim.
     */
    public Claim resizeClaim(Player player, Claim oldClaim, Rectangle rectangle) throws ClaimEditException {
        if (rectangle.getWidth() < 1 || rectangle.getHeight() < 1) {
            throw ClaimEditException.ErrorType.CLAIM_TOO_SMALL.create();
        }
        PlayerInfo info = PlayerInfo.forPlayer(player);
        Claim newClaim = oldClaim.copy();
        newClaim.setRectangle(rectangle);
        // Claim blocks
        if (info.getTotalClaimBlocks() + oldClaim.getArea() < newClaim.getArea()) {
            throw ClaimEditException.ErrorType.OUT_OF_CLAIM_BLOCKS.create();
        }
        // Claim collisions
        throwOnClaimConflict(newClaim, oldClaim);
        // Commit
        claims.removeClaim(oldClaim);
        claims.addClaim(newClaim);
        claims.saveClaims();
        return newClaim;
    }

    public void throwOnClaimConflict(Claim claim, Claim exemptedClaim) throws ClaimEditException {
        if (claims.isClaimTooSmall(claim)) throw ClaimEditException.ErrorType.CLAIM_TOO_SMALL.create();
        if (claims.isClaimInBlacklistedWorld(claim)) throw ClaimEditException.ErrorType.WORLD_BLACKLISTED.create();
        if (claims.doesClaimCollide(claim, exemptedClaim)) throw ClaimEditException.ErrorType.CLAIM_COLLISION.create();
        if (claims.doesClaimExceedBounds(claim)) throw ClaimEditException.ErrorType.SUBCLAIM_OUT_OF_BOUNDS.create();
        if (claims.doesClaimExcludeSubs(claim)) throw ClaimEditException.ErrorType.CLAIM_EXCLUDES_SUBCLAIMS.create();
    }

    public Claim createPlayerClaim(Player sender, Location location, int sizeX, int sizeY) throws ClaimEditException {
        Claim newClaim = Claim.newClaim(new Rectangle(location.getX(), location.getZ(), sizeX, sizeY), location.getWorldName(), sender.getUuid());
        // Claim blocks
        if (sender.info().getTotalClaimBlocks() < newClaim.getArea()) {
            throw ClaimEditException.ErrorType.OUT_OF_CLAIM_BLOCKS.create();
        }
        // Claim collisions
        throwOnClaimConflict(newClaim, null);
        //
        newClaim.getOptions().setComment(String.format("%s created %s", sender.getName(), Strings.formatDateNow()));
        claims.addClaim(newClaim);
        claims.saveClaims();
        return newClaim;
    }

    public Claim makeSubclaim(Player sender, Claim superClaim, Rectangle rectangle, String world) throws ClaimEditException {
        Claim newClaim = Claim.newClaim(rectangle, world, sender.getUuid());
        newClaim.setSuperClaim(superClaim);
        // Claim collisions
        throwOnClaimConflict(newClaim, null);
        // Commit
        newClaim.getOptions().setComment(String.format("%s created %s", sender.getName(), Strings.formatDateNow()));
        claims.addClaim(newClaim);
        claims.saveClaims();
        return newClaim;
    }
}
