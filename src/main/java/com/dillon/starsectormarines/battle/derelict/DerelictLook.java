package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.drone.FlockLivery;
import com.dillon.starsectormarines.battle.turret.StructureLook;

/**
 * How a derelict security network's own units are drawn, by lineage (`derelict-security-network.md`, slice 9;
 * `derelict-lore-guide.md`, two lineages): Domain security in grey and hazard yellow, contingency security in
 * Tri-Tachyon's white gone dull. Its gun posts wear a folding emplacement of their own, and its nodes a relay
 * structure in place of the drone hub's. Presentation only: the lineage changes how the units are drawn, never how
 * they fight. The flocks' and cradles' looks are {@link FlockLivery}'s; the walker's is its own chassis's.
 */
public enum DerelictLook {

    DOMAIN("domain"),
    CONTINGENCY("contingency");

    /** A node's drawn size, its long side in cells: a relay structure set against a wall, a drone hub's size. */
    public static final float NODE_VISUAL_CELLS = 1.6f;
    private static final String ROOT = "graphics/battle/derelict/";

    private final String key;
    private final StructureLook postUnfolded;
    private final StructureLook postFolded;

    DerelictLook(String key) {
        this.key = key;
        this.postUnfolded = new StructureLook(ROOT + "post-base-" + key + ".png", ROOT + "post-head-" + key + ".png",
                null);
        this.postFolded = new StructureLook(ROOT + "post-base-" + key + ".png", ROOT + "post-folded-" + key + ".png",
                null);
    }

    /** The look of a network whose flocks wear {@code livery}; Domain security when none is given. */
    public static DerelictLook of(FlockLivery livery) {
        return livery == FlockLivery.CONTINGENCY ? CONTINGENCY : DOMAIN;
    }

    /** The node's sprite, unrotated. */
    public String nodeSpritePath() {
        return ROOT + "node-" + key + ".png";
    }

    /**
     * A gun post's look: its base in the paving and, unfolded, its gun head turning with the mount; folded (its zone
     * not awake, its power cut), the shut head in place of the gun. No recoil layer: the head carries its barrels.
     */
    public StructureLook postLook(boolean folded) {
        return folded ? postFolded : postUnfolded;
    }
}
