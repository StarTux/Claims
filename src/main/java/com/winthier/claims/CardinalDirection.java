package com.winthier.claims;

public enum CardinalDirection {
    NORTH,
    EAST,
    SOUTH,
    WEST;

    public static CardinalDirection fromString(String string) {
        string = string.toUpperCase();
        for (CardinalDirection cd : CardinalDirection.values()) {
            if (cd.name().startsWith(string)) return cd;
        }
        return null;
    }

    public String toString() {
        return name().toLowerCase();
    }
}
