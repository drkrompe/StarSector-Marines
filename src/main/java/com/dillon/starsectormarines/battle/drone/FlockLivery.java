package com.dillon.starsectormarines.battle.drone;

/**
 * Which derelict lineage a flock was built by (`derelict-lore-guide.md`, two lineages): Domain security in grey and
 * hazard yellow, or contingency security in Tri-Tachyon's white and cyan gone dull. Presentation only: the lineage
 * changes how a flock's drones and cradle are drawn, never how they fly or fight.
 */
public enum FlockLivery {

    DOMAIN(DroneLook.SWARM, "graphics/battle/drones/cradle-domain.png"),
    CONTINGENCY(DroneLook.SWARM_CONTINGENCY, "graphics/battle/drones/cradle-contingency.png");

    /** A cradle's drawn size, its long side in cells: a housing for fourteen drones, larger than a drone hub. */
    public static final float CRADLE_VISUAL_CELLS = 2.4f;

    private final DroneLook drone;
    private final String cradleSpritePath;

    FlockLivery(DroneLook drone, String cradleSpritePath) {
        this.drone = drone;
        this.cradleSpritePath = cradleSpritePath;
    }

    /** The look the flock's drones wear. */
    public DroneLook drone() { return drone; }

    /** The cradle's sprite, unrotated. */
    public String cradleSpritePath() { return cradleSpritePath; }
}
