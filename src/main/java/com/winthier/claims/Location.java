package com.winthier.claims;

import lombok.Data;

@Data
public final class Location {
    private final String worldName;
    private final int x, y, z;

    public Location(String worldName, int x, int y, int z) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Location getRelative(int dx, int dy, int dz) {
        return new Location(worldName, x + dx, y + dy, z + dz);
    }

    public int getCoordinate(CardinalDirection direction) {
        switch (direction) {
        case NORTH:
        case SOUTH:
            return z;
        case EAST:
        case WEST:
            return x;
        default:
            return 0;
        }
    }

    public int distanceSquared(Location other) {
        int dx = other.x - x;
        int dy = other.y - y;
        int dz = other.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public int horizontalDistanceSquared(Location other) {
        int dx = other.x - x;
        int dz = other.z - z;
        return dx * dx + dz * dz;
    }
}
