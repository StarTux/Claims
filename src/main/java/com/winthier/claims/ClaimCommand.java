package com.winthier.claims;

import com.winthier.claims.util.JSON;
import com.winthier.claims.util.Msg;
import com.winthier.claims.util.Players;
import com.winthier.claims.util.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.text.WordUtils;
import org.bukkit.ChatColor;

// TODO clean this up. This file should only parse player
// input. Any effective work should be done in ClaimActions.
@RequiredArgsConstructor
public class ClaimCommand {
    private final Claims claims;
    public final static String[] claimSubCommands = { "Me", "Info", "New", "Trust", "Grow", "Sub", "ContainerTrust", "AccessTrust", "PermissionTrust", "Untrust", "Abandon" };
    public final static int INITIAL_CLAIM_RADIUS = 8;
    public final static int INITIAL_SUBCLAIM_RADIUS = 8;

    private void msgAdd(List<Object> msg, String title, String description, String command) {
        msgAdd(msg, title, description, command, null, false);
    }
    
    private void msgAdd(List<Object> msg, String title, String description, String command, String syntax, boolean suggest) {
        msg.add(" ");
        description = WordUtils.wrap(description, 32);
        if (syntax == null) syntax = command;
        msg.add(JSON.commandButton("&r[&a" + title + "&r]", "&a/" + syntax + "\n&r" + description, "/" + command + (suggest ? " " : ""), !suggest));
    }

    private void msgSend(List<Object> msg, Player player) {
        player.tellRaw(msg);
        msg.clear();
    }

    private void usage(Player player) {
        player.sendMessage("");
        player.sendMessage("&3&lClaims&r&o Help");
        List<Object> msg = new ArrayList<>();
        msg.add(claims.format("&oInfo"));
        msgAdd(msg, "List", "View your claims summary", "Claim Me");
        msgAdd(msg, "Info", "View info about current claim", "Claim Info");
        msgSend(msg, player);
        msg.add(claims.format("&oCreate"));
        msgAdd(msg, "New", "Make a new claim", "Claim New");
        if (claims.allowBuyClaimBlocks) {
            msgAdd(msg, "Buy", "Buy more claim blocks", "buyclaimblocks", "BuyClaimBlocks <Amount>", true);
        }
        msgAdd(msg, "Grow", "Expand your claim", "Claim Grow", "Claim Grow <Blocks> [North|East|South|West]", true);
        msgAdd(msg, "Sub", "Create a subclaim", "Claim Sub");
        msgAdd(msg, "Abandon", "Abandon your current claim", "Claim Abandon");
        msgSend(msg, player);
        msg.add(claims.format("&oTrust"));
        msgAdd(msg, "Build", "Trust someone to build in your claim", "trust", "Trust <Player>", true);
        msgAdd(msg, "Container", "Trust someone to modify your containers", "containertrust", "ContainerTrust <Player>", true);
        msgAdd(msg, "Access", "Trust someone to use your doors and buttons", "accesstrust", "AccessTrust <Player>", true);
        msgAdd(msg, "Permission", "Trust someone to change permissions in your claim", "permissiontrust", "PermissionTrust <Player>", true);
        msgAdd(msg, "Untrust", "No longer trust someone someone", "Untrust", "Untrust <Player>", true);
        msgSend(msg, player);
        msg.add(claims.format("&oStuck"));
        msgAdd(msg, "Stuck", "Get help if you are stuck in a claim", "Stuck");
        msgSend(msg, player);
        player.sendMessage("");
    }

    public List<String> tabComplete(String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String startWith = args[0].toLowerCase();
            for (String string : claimSubCommands) {
                if (string.toLowerCase().startsWith(startWith)) result.add(string);
            }
        } else {
            String startWith = "";
            if (args.length > 0) startWith = args[args.length - 1].toLowerCase();
            for (Player online : claims.getDelegate().getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(startWith)) {
                    result.add(online.getName());
                }
            }
        }
        return result;
    }

    public void command(Player player, String[] args) {
        try {
            commandPrivate(player, args);
        } catch (CommandException ce) {
            player.sendMessage("&c%s", ce.getMessage());
        }
    }

    private void commandPrivate(Player sender, String[] args) {
        if (false) {
        } else if (args.length == 1 && args[0].equalsIgnoreCase("Info")) {
            Claim claim = claims.getClaimAt(sender.getLocation());
            if (claim == null) throw new CommandException("Stand in the claim you want to learn more about");
            claims.getActions().info(sender, claim);
        } else if (args.length == 1 && args[0].equalsIgnoreCase("New")) {
            Location location = sender.getLocation().getRelative(-INITIAL_CLAIM_RADIUS, 0, -INITIAL_CLAIM_RADIUS);
            int size = INITIAL_CLAIM_RADIUS + INITIAL_CLAIM_RADIUS + 1;
            try {
                Claim claim = claims.getActions().createPlayerClaim(sender, location, size, size);
                sender.sendMessage("&bClaim created. Cost %d claim blocks.", claim.getArea());
                sender.info().highlightClaim(claim, Highlight.Type.FRIENDLY);
                sender.showTitle("", "&aClaim created");
            } catch (ClaimEditException cee) {
                sender.sendMessage("&cCannot create claim because %s.", cee.getMessage());
            }
        } else if (args.length == 1 && args[0].equalsIgnoreCase("Sub")) {
            Location location = sender.getLocation();
            Claim superClaim = claims.getClaimAt(location);
            if (superClaim == null) throw new CommandException("Stand inside your claim to make a subclaim");
            Rectangle rectangle = new Rectangle(location.getX() - INITIAL_SUBCLAIM_RADIUS, location.getZ() - INITIAL_SUBCLAIM_RADIUS, INITIAL_SUBCLAIM_RADIUS * 2 + 1, INITIAL_SUBCLAIM_RADIUS * 2 + 1);
            try {
                Claim subclaim = claims.getActions().makeSubclaim(sender, superClaim, rectangle, location.getWorldName());
                sender.sendMessage("&bSubclaim created.");
                sender.info().highlightClaim(subclaim, Highlight.Type.SUBCLAIM);
            } catch (ClaimEditException cee) {
                sender.sendMessage("&cCannot create subclaim because %s.", cee.getMessage());
            }
        } else if (args.length >= 2 && args.length <= 3 && args[0].equalsIgnoreCase("Grow")) {
            Claim claim = claims.getClaimAt(sender.getLocation());
            if (claim == null) throw new CommandException("Stand inside your claim to expand it");
            if (!claim.isOwner(sender.getUuid())) throw new CommandException("You can only expand your own claims");
            String amountArg = args[1];
            String directionArg = (args.length >= 3) ? args[2] : null;
            int amount = 0;
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException nfe) {}
            if (amount == 0) throw new CommandException("Amount expected, got: " + amountArg);
            CardinalDirection direction = null;
            if (directionArg != null) {
                direction = CardinalDirection.fromString(directionArg);
                if (direction == null) throw new CommandException("Direction expected; got: " + directionArg);
            }
            claims.getActions().grow(sender, claim, direction, amount);
        } else if (args.length == 1 && args[0].equalsIgnoreCase("Abandon")) {
            Claim claim = claims.getClaimAt(sender.getLocation());
            if (claim == null) throw new CommandException("Stand in your claim to abandon it");
            boolean ignore = sender.info().doesIgnoreClaims();
            if (!claim.isOwner(sender.getUuid()) && !ignore) throw new CommandException("This claim does not belong to you");
            if (claim.isAdminClaim() && !ignore) throw new CommandException("You can't abandon admin claims");
            boolean isTop = !claim.hasSuperClaim();
            claims.removeClaim(claim);
            if (isTop) {
                sender.sendMessage("&bAbandoned your claim. Retrieved %d claim blocks.", claim.getArea());
            } else {
                sender.sendMessage("&bAbandoned this subclaim.");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("Trust")) {
            trust(sender, TrustType.BUILD, args[1]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("ContainerTrust")) {
            trust(sender, TrustType.CONTAINER, args[1]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("AccessTrust")) {
            trust(sender, TrustType.ACCESS, args[1]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("PermissionTrust")) {
            trust(sender, TrustType.PERMISSION, args[1]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("Untrust")) {
            untrust(sender, args[1]);
        } else if (args.length == 1 && args[0].equalsIgnoreCase("List")) {
            List<Claim> playerClaims = claims.getPlayerClaims(sender.getUuid());
            List<String> claimsList = new ArrayList<>(playerClaims.size());
            for (Claim claim : playerClaims) {
                if (!claim.isAdminClaim()) {
                    Location loc = claim.getCenterLocation();
                    claimsList.add(String.format("%s,%d,%d", claims.getWorldAlias(loc.getWorldName()), loc.getX(), loc.getZ()));
                }
            }
            sender.sendMessage("&bYour claims summary");
            sender.sendMessage(" &3Available claim blocks &b%d", sender.info().getTotalClaimBlocks());
            sender.sendMessage(" &3List&b %s", Strings.fold(claimsList, " "));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("Tool")) {
            if (args[1].equalsIgnoreCase("Abort")) {
                if (sender.info().getTool().reset()) {
                    sender.sendMessage("&3&lClaims&r&o Claim edit aborted.");
                } else {
                    sender.sendMessage("&3&lClaims&r&o No claim edit was active.");
                }
            } else {
                usage(sender);
            }
        } else if ((args.length == 1 || args.length == 3) && args[0].equalsIgnoreCase("Set")) {
            Claim claim = claims.getClaimAt(sender.getLocation());
            if (claim == null) throw new CommandException("Stand in the claim you would like to configure");
            claim = claim.getTopLevelClaim();
            if (!claim.isOwner(sender.getUuid())) throw new CommandException("The top level claim does not belong to you");
            if (args.length == 3) {
                String optionKey = args[1];
                String optionValue = args[2];
                if (claimOptionIsValid(optionKey, optionValue)) {
                    claim.getOptions().setOption(optionKey, optionValue);
                    claims.saveClaims();
                }
            }
            showClaimOptions(sender, claim);
        } else {
            usage(sender);
        }
    }

    boolean claimOptionIsValid(String key, String value) {
        for (ClaimOption claimOption: ClaimOptions.PUBLIC_OPTIONS) {
            if (claimOption.key.equals(key)) {
                for (ClaimOption.State state: claimOption.states) {
                    if (state.value.equals(value)) return true;
                }
            }
        }
        return false;
    }

    void showClaimOptions(Player sender, Claim claim) {
        sender.sendMessage("");
        sender.sendMessage("&3&lClaim&r Settings");
        for (ClaimOption claimOption: ClaimOptions.PUBLIC_OPTIONS) {
            List<Object> json = new ArrayList<>();
            json.add(" ");
            json.add(Msg.button("&o" + claimOption.displayName, claimOption.description, null));
            String currentValue = claim.getOptions().getOption(claimOption.key);
            if (currentValue == null) currentValue = claimOption.defaultValue;
            for (ClaimOption.State state: claimOption.states) {
                json.add(" ");
                if (currentValue.equals(state.value)) {
                    json.add(Msg.button(state.activeColor,
                                        "&r[" + state.activeColor + state.displayName + "&r]",
                                        state.description,
                                        null));
                } else {
                    json.add(Msg.button(state.color,
                                        state.displayName,
                                        state.description,
                                        "/claim set " + claimOption.key + " " + state.value));
                }
            }
            sender.tellRaw(json);
        }
        sender.sendMessage("");
    }

    public boolean trust(@NonNull Player sender, @NonNull TrustType trust, @NonNull String[] args) {
        if (args.length != 1) return false;
        try {
            return trust(sender, trust, args[0]);
        } catch (CommandException ce) {
            sender.sendMessage("&c%s", ce.getMessage());
            return true;
        }
    }

    public boolean untrust(@NonNull Player sender, @NonNull String[] args) {
        if (args.length != 1) return false;
        try {
            return untrust(sender, args[0]);
        } catch (CommandException ce) {
            sender.sendMessage("&c%s", ce.getMessage());
            return true;
        }
    }
    
    private boolean trust(@NonNull Player sender, @NonNull TrustType trust, @NonNull String playerArg) {
        if (playerArg.equals("*")) {
            return claims.getActions().trustPublic(sender, trust);
        } else {
            UUID playerUuid = Players.getUuid(playerArg);
            if (playerUuid == null) throw new CommandException("Player not found: " + playerArg);
            return claims.getActions().trust(sender, trust, playerUuid);
        }
    }

    private boolean untrust(@NonNull Player sender, @NonNull String playerArg) {
        if (playerArg.equals("*")) {
            return claims.getActions().untrustPublic(sender);
        } else {
            UUID playerUuid = Players.getUuid(playerArg);
            if (playerUuid == null) throw new CommandException("Player not found: " + playerArg);
            return claims.getActions().untrust(sender, playerUuid);
        }
    }
}
