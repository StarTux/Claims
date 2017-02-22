package com.winthier.claims;

/**
 * The different types of trust one can give to other players
 * within one's claim.
 */
public enum TrustType {
    BUILD,
    CONTAINER,
    ACCESS,
    PERMISSION;

    public final String human;

    TrustType() {
        this.human = name().toLowerCase();
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }

    public static TrustType forString(String string) {
        return valueOf(string.toUpperCase());
    }
}
