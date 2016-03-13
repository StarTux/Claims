package com.winthier.claims;

import java.util.UUID;

public interface Highlight {
    public void highlight();
    public void restore();
    public void flash();
    public boolean secondTick();

    public static enum Type {
        FRIENDLY,
        ENEMY,
        ADMIN,
        SUBCLAIM,
        ;
        public static Highlight.Type forPlayer(UUID uuid, Claim claim) {
            if (claim.hasSuperClaim()) return SUBCLAIM;
            if (claim.isAdminClaim()) return ADMIN;
            if (claim.checkTrust(uuid, TrustType.BUILD)) return FRIENDLY;
            return ENEMY;
        }
    }
}
