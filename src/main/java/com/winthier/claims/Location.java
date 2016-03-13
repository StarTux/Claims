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

    public Location getRelative(int x, int y, int z) {
        return new Location(worldName, this.x + x, this.y + y, this.z + z);
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
}
