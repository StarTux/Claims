package com.winthier.claims;

public class ClaimEditException extends Exception {
    public static enum ErrorType {
        OUT_OF_CLAIM_BLOCKS,
        WORLD_BLACKLISTED,
        CLAIM_TOO_SMALL,
        CLAIM_COLLISION,
        SUBCLAIM_OUT_OF_BOUNDS,
        CLAIM_EXCLUDES_SUBCLAIMS,
        ;
        public ClaimEditException create() {
            return new ClaimEditException(this);
        }
    }

    private final ErrorType errorType;

    public ClaimEditException(ErrorType errorType) {
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public String getMessage() {
        switch (errorType) {
        case OUT_OF_CLAIM_BLOCKS:
            return "you don't have enough claim blocks";
        case WORLD_BLACKLISTED:
            return "you can't make claims in this world";
        case CLAIM_TOO_SMALL:
            return "it would be too small";
        case CLAIM_COLLISION:
            return "your claim would collide with an existing claim";
        case SUBCLAIM_OUT_OF_BOUNDS:
            return "your subclaim would not be contained within its super claim";
        case CLAIM_EXCLUDES_SUBCLAIMS:
            return "your claim would be too small for its subclaims";
        default:
            return "unknown reason: " + errorType.name();
        }
    }
}
