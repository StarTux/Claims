package com.winthier.claims.util;

import com.winthier.claims.Claim;
import com.winthier.claims.ClaimEditException;
import com.winthier.claims.Claims;
import com.winthier.claims.Location;
import com.winthier.claims.PlayerInfo;
import com.winthier.claims.Rectangle;
import com.winthier.claims.TrustType;
import com.winthier.playercache.PlayerCache;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Class with the purpose to convert legacy GriefPrevention claims and players.
 */
public class Legacy {
    private final Pattern pattern2Coords = Pattern.compile("^[-\\w]+;[-\\d]+;[-\\d]+$");
    private final Pattern pattern3Coords = Pattern.compile("^([-\\w]+);([-\\d]+);[-\\d]+;([-\\d]+)$");
    private final Pattern patternChunk = Pattern.compile("^=+$");
    private String currentFile;
    private LineNumberReader in;
    private String line;
    @Getter
    private int claimCount;
    private int playerCount;
    private YamlConfiguration times = new YamlConfiguration();;

    public static void migrate() {
        Legacy legacy = new Legacy();
        try {
            legacy.times.load(new FileReader("plugins/Claims/times.yml"));
            System.out.println(String.format("%d times loaded", legacy.times.getKeys(false).size()));
            legacy.migrateClaims();
            legacy.migratePlayers();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(String.format("Migrated %d claims, %d players", legacy.claimCount, legacy.playerCount));
        Claims.getInstance().saveClaims();
        Claims.getInstance().savePlayers();
    }

    private UUID findUuid(String name) {
        UUID result = PlayerCache.uuidForName(name);
        if (result == null) System.err.println(String.format("UUID not found for name: '%s' in %s, line %d", name, currentFile, in.getLineNumber()));
        return result;
    }

    private void parseTrustLine(String trustLine, Claim claim, TrustType trust) {
        String[] tokens = trustLine.split(";");
        List<UUID> result = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            } else if (token.equals("public")) {
                claim.addPublicTrust(trust);
            } else {
                UUID uuid = findUuid(token);
                if (uuid != null) claim.addTrusted(uuid, trust);
            }
        }
    }

    private void migrateClaims() throws IOException {
        File dir = new File("plugins/GriefPreventionData/ClaimData");
        LinkedList<String> filenames = new LinkedList<>();
        for (String filename : dir.list()) {
            if (pattern3Coords.matcher(filename).matches()) {
                filenames.addFirst(filename);
            } else if (pattern2Coords.matcher(filename).matches()) {
                filenames.addLast(filename);
            } else {
                System.err.println("Claim file name does not match: " + filename);
            }
        }
    fileLoop:
        for (String filename : filenames) {
            File file = new File(dir, filename);
            this.currentFile = file.getPath();
            in = new LineNumberReader(new FileReader(file));
            // the readClaim routine adds the claim and all its
            // subclaims to allClaims by itself.
            try {
                readClaim(null);
            } catch (RuntimeException re) {
                System.err.println(re.getMessage());
            }
            in.close();
        }
    }

    private void migratePlayers() throws IOException {
        File dir = new File("plugins/GriefPreventionData/PlayerData");
        for (String filename : dir.list()) {
            File file = new File(dir, filename);
            currentFile = file.getPath();
            String playerName = filename;
            UUID playerUuid = findUuid(playerName);
            if (playerUuid == null) continue;
            in = new LineNumberReader(new FileReader(file));
            try {
                readPlayer(playerUuid);
            } catch (RuntimeException re) {
                System.err.println(re.getMessage());
            }
            in.close();
        }
    }

    private Claim readClaim(Claim superClaim) throws IOException {
        int firstLineNumber = in.getLineNumber();
        Matcher matcher;
        // Line #1 is the top left coordinate
        line = in.readLine();
        if (line == null) return null;
        matcher = pattern3Coords.matcher(line);
        if (!matcher.matches()) throw new RuntimeException(String.format("Unexpected line %d in %s, expected coordinate: %s", in.getLineNumber(), currentFile, line));
        Location loc1 = new Location(matcher.group(1), Integer.parseInt(matcher.group(2)), 64, Integer.parseInt(matcher.group(3)));
        // Line #2 is the bottom right coordinate
        line = in.readLine();
        matcher = pattern3Coords.matcher(line);
        if (!matcher.matches()) throw new RuntimeException(String.format("Unexpected line %d in %s, expected coordinate: %s", in.getLineNumber(), currentFile, line));
        Location loc2 = new Location(matcher.group(1), Integer.parseInt(matcher.group(2)), 64, Integer.parseInt(matcher.group(3)));
        // We can build the rectangle now
        Rectangle rectangle = Rectangle.forCorners(loc1, loc2);
        if (rectangle.getWidth() < 1 || rectangle.getHeight() < 1) {
            throw new RuntimeException(String.format("Bad rectangle dimensions in %s, line %d: %d,%d", currentFile, in.getLineNumber(), rectangle.getWidth(), rectangle.getHeight()));
        }
        // Line #3 contains the owner name or "--subdivision--"
        Claim claim;
        if (superClaim != null) {
            // Subclaim
            line = in.readLine();
            if (!line.equals("--subdivision--")) {
                throw new RuntimeException(String.format("Unexpected line %d in %s, expected '--subdivision--': %s", in.getLineNumber(), currentFile, line));
            }
            claim = Claim.newClaim(rectangle, loc1.getWorldName(), superClaim.getOwner());
            claim.setSuperClaim(superClaim);
        } else {
            // Top level claim
            line = in.readLine();
            String ownerName = line;
            if (ownerName.isEmpty()) {
                UUID owner = findUuid("StarTux");
                claim = Claim.newClaim(rectangle, loc1.getWorldName(), owner);
                claim.setAdminClaim(true);
            } else {
                UUID owner = findUuid(ownerName);
                claim = Claim.newClaim(rectangle, loc1.getWorldName(), owner);
            }
        }
        // Line #4 contains player names with build trust
        parseTrustLine(in.readLine(), claim, TrustType.BUILD);
        // Line #5 contains player names with container trust
        parseTrustLine(in.readLine(), claim, TrustType.CONTAINER);
        // Line #6 contains player names with access trust
        parseTrustLine(in.readLine(), claim, TrustType.ACCESS);
        // Line #7 contains player names with permission trust
        parseTrustLine(in.readLine(), claim, TrustType.PERMISSION);
        // Add the claim
        // Line #8 is a chunk line
        line = in.readLine();
        if (!patternChunk.matcher(line).matches()) throw new RuntimeException(String.format("Unexpected line %d in %s, expected chunk '=': %s", in.getLineNumber(), currentFile, line));
        // Time to check for conflicts.
        try {
            Claims.getInstance().getActions().throwOnClaimConflict(claim, null);
        } catch (ClaimEditException cee) {
            throw new RuntimeException(String.format("Claim conflict in line %s, line %d: %s", currentFile, firstLineNumber, cee.getMessage()));
        }
        // Save comment
        claim.getOptions().setComment(String.format("%s migrated %s", claim.getOwnerName(), Strings.formatDateNow()));
        Claims.getInstance().addClaim(claim);
        // The next lines will be subclaims
        if (superClaim == null) {
            while (true) {
                try {
                    if (null == readClaim(claim)) break;
                } catch (RuntimeException re) {
                    System.err.println(re.getMessage());
                }
            }
        }
        claimCount += 1;
        return claim;
    }

    private PlayerInfo readPlayer(UUID playerUuid) throws IOException {
        // Line #1 contains a date; ignore.
        line = in.readLine();
        // Line #2 are accrued claim blocks
        line = in.readLine();
        int accruedBlocks = Integer.parseInt(line);
        // Line #3 are bonus claim blocks
        line = in.readLine();
        int bonusBlocks = Integer.parseInt(line);
        // All other lines are ignored
        PlayerInfo info = PlayerInfo.forPlayer(playerUuid);
        info.setClaimBlocks(accruedBlocks + bonusBlocks);
        if (!times.isSet(playerUuid.toString())) {
            System.err.println(String.format("Using deduced times for %s (%s)", Claims.getInstance().getDelegate().getPlayerName(playerUuid), playerUuid.toString()));
            info.setMinutesPlayed(Math.max(0, (accruedBlocks - 10000) * 6 / 50));
        } else {
            int seconds = times.getInt(playerUuid.toString());
            int minutes = seconds / 60;
            info.setMinutesPlayed(minutes);
        }
        info.save();
        playerCount += 1;
        return info;
    }
}
