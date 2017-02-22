package com.winthier.claims;

/**
 * Enum with different actions which we want to distinguish when
 * protecting a claim.
 */
public enum Action {
    NONE,
    BUILD,
    TRAMPLE,
    SWITCH_TRIGGER,
    SLEEP_BED,
    INTERACT_BLOCK,
    INTERACT_ENTITY,

    SWITCH_DOOR,
    OPEN_INVENTORY,
    SET_SPAWN,

    DAMAGE_ENTITY,
    MOUNT_ENTITY,
    RIDE_VEHICLE,
    LEASH_ENTITY,
    SHEAR_ENTITY,
    TRANSFORM_ENTITY, // Shear a mooshroom, use armor stand

    DAMAGE_FARM_ANIMAL;
}
