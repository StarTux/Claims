package com.winthier.claims;

import com.winthier.claims.util.Players;
import com.winthier.claims.util.Strings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class AdminCommand {
    private final Claims claims;

    public void command(Player player, String[] args) {
        try {
            if (args.length == 1 && args[0].equalsIgnoreCase("load")) {
                claims.loadConfig();
                claims.loadClaims();
                claims.loadPlayers();
                player.sendMessage("&eClaims file loaded");
            } else if (args.length == 1 && args[0].equalsIgnoreCase("save")) {
                claims.saveClaims();
                claims.savePlayers();
                player.sendMessage("&eClaims file saved");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
                String playerName = args[1];
                UUID uuid = claims.getDelegate().getPlayerUuid(playerName);
                if (uuid == null) throw new CommandException("Player not found: " + playerName);
                PlayerInfo info = claims.getPlayerInfo(uuid);
                List<Claim> playerClaims = claims.getPlayerClaims(uuid);
                List<String> claimsList = new ArrayList<>(playerClaims.size());
                for (Claim claim : playerClaims) {
                    Location loc = claim.getCenterLocation();
                    claimsList.add(String.format("%s,%d,%d", loc.getWorldName(), loc.getX(), loc.getZ()));
                }
                player.sendMessage("&3Player info of &b%s", playerName);
                player.sendMessage(" &3Play time&b &b%s", Strings.formatMinutes(info.getMinutesPlayed()));
                player.sendMessage(" &3Claim blocks&b %d&3/&b%d", info.getTotalClaimBlocks(), info.getClaimBlocks());
                player.sendMessage(" &3List&b %s", Strings.fold(claimsList, " "));
            } else if (args.length == 3 && args[0].equalsIgnoreCase("blocks")) {
                String playerName = args[1];
                String amountArg = args[2];
                UUID uuid = claims.getDelegate().getPlayerUuid(playerName);
                if (uuid == null) throw new CommandException("Player not found: " + playerName);
                PlayerInfo info = claims.getPlayerInfo(uuid);
                int amount = 0;
                try {
                    amount = Integer.parseInt(amountArg);
                } catch (NumberFormatException nfe) {
                    throw new CommandException("Expected amount, got: " + amountArg);
                }
                info.addClaimBlocks(amount);
                player.sendMessage("&bPlayer %s now has %d claim blocks.", playerName, info.getTotalClaimBlocks());
            } else if (args.length == 1 && args[0].equalsIgnoreCase("delete")) {
                Claim claim = claims.getClaimAt(player.getLocation());
                if (claim == null) throw new CommandException("There is no claim here");
                claims.removeClaim(claim);
                claims.saveClaims();
                player.sendMessage("&bDeleted %s claim.", Strings.genitive(claim.getOwnerName()));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("transfer")) {
                String playerArg = args[1];
                Claim claim = claims.getClaimAt(player.getLocation());
                if (claim == null) throw new CommandException("There is no claim here");
                UUID uuid = Players.getUuid(playerArg);
                if (uuid == null) throw new CommandException("Unknown player: " + playerArg);
                claim.setOwner(uuid);
                claims.saveClaims();
                player.sendMessage("&bClaim transfered to %s.", Players.getName(uuid));
            } else if (args.length == 1 && args[0].equalsIgnoreCase("ignore")) {
                PlayerInfo info = claims.getPlayerInfo(player.getUuid());
                info.toggleIgnoreClaims();
                if (info.doesIgnoreClaims()) {
                    player.sendMessage("&bNow ignoring claims.");
                } else {
                    player.sendMessage("&bNow respecting claims.");
                }
            } else if (args.length >= 1 && args[0].equalsIgnoreCase("debug")) {
                PlayerInfo info = claims.getPlayerInfo(player.getUuid());
                info.toggleDebugMode();
                if (info.isDebugMode()) {
                    player.sendMessage("&bDebug mode enabled.");
                } else {
                    player.sendMessage("&bDebug mode disabled.");
                }
                Claim claim = claims.getClaimAt(player.getLocation());
            } else if (args.length >= 2 && args[0].equalsIgnoreCase("options")) {
                Claim claim = claims.getClaimAt(player.getLocation());
                if (claim == null) throw new CommandException("There is no claim here");
                claim.editOptions(Arrays.copyOfRange(args, 1, args.length));
                claims.saveClaims();
                player.sendMessage("&bClaim options edited");
            } else if (args.length >= 1 && args[0].equalsIgnoreCase("import")) {
                player.sendMessage("Starting migration...");
                com.winthier.claims.util.Legacy.migrate();
                player.sendMessage("Finished migration...");
            } else if (args.length >= 1 && args[0].equals("near")) {
                int distance = 100;
                if (args.length >= 2) {
                    distance = Integer.parseInt(args[1]);
                }
                Collection<Claim> list = claims.findClaimsNear(player.getLocation(), distance);
                player.sendMessage("Found " + list.size() + " claims nearby (distance " + distance + " blocks):");
                for (Claim claim: list) {
                    player.sendMessage(" Owner: " + claim.getOwnerName());
                    player.info().highlightClaim(claim);
                }
            } else {
                player.sendMessage("&bClaims admin interface usage");
                player.sendMessage(" &b/ca player <name>&3 Display player info");
                player.sendMessage(" &b/ca blocks <name> <amount>&3 Adjust claim blocks");
                player.sendMessage(" &b/ca ignore&3 Ignore claim protection");
                player.sendMessage(" &b/ca delete&3 Delete current claim");
                player.sendMessage(" &b/ca transfer <name>&3 Transfer claim ownership");
                player.sendMessage(" &b/ca load&3 Reload save data");
                player.sendMessage(" &b/ca save&3 Save all data to disk");
                player.sendMessage(" &b/ca options <args...>&3 Edit claim options");
            }
        } catch (CommandException ce) {
            player.sendMessage("&c%s", ce.getMessage());
        }
    }

    public boolean ignore(@NonNull Player player, @NonNull String[] args) {
        PlayerInfo info = claims.getPlayerInfo(player.getUuid());
        if (args.length == 0) {
            info.toggleIgnoreClaims();
        } else if (args.length == 1) {
            String arg = args[0];
            if ("off".equalsIgnoreCase(arg)) {
                info.setIgnoreClaims(false);
            } else if ("on".equalsIgnoreCase(arg)) {
                info.setIgnoreClaims(true);
            } else {
                return false;
            }
        } else {
            return false;
        }
        if (info.doesIgnoreClaims()) {
            player.sendMessage("&bNow ignoring claims.");
        } else {
            player.sendMessage("&bNow respecting claims.");
        }
        return true;
    }
}
