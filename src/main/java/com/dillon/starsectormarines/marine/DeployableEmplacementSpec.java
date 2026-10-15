package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/**
 * Authored behaviour of one placed emplacement, limited to what the
 * <em>carried item</em> legitimately decides: which platform it sets down, how
 * long the carrier is committed to setting it up, and how long the placed thing
 * runs before it burns out.
 *
 * <p>Everything else about the emplacement belongs to the three-way emplacement
 * authority split and is read from the referenced structure rather than
 * restated here — durability, collision geometry, and force value from the
 * {@code battle.turret.StructureDef}; magazine from its mount; engagement reach
 * and rate from the mount's weapon. Duplicating a gun's range onto the backpack
 * that carried it would give one emplacement two different answers to the same
 * question.
 *
 * <p>A crew-served emplacement ({@link SpecialAiPolicy#CREW_SERVED_FIRE_SUPPORT})
 * is the one that can be picked up again, so it also states how long the pack-up
 * takes and how long the carrier waits before setting it down a second time.
 * Both are zero for an expendable pod, which is never recovered.
 *
 * @param structureId      turret-catalog structure the placement mints
 * @param deployDuration   sim-seconds the carrier is committed to the placement
 *                         channel, movement frozen
 * @param lifetimeSeconds  sim-seconds the emplacement runs before burning out
 * @param packDuration     sim-seconds the carrier is committed to lifting a
 *                         crew-served emplacement again; zero when it is not recoverable
 * @param redeployCooldown sim-seconds after packing before the carrier may set
 *                         the emplacement down again; zero when it is not recoverable
 * @param carried          the mount stays on the carrier's back rather than being set
 *                         down: it switches on without halting them, covers ground around
 *                         wherever they are, and goes with them when they fall
 */
public record DeployableEmplacementSpec(
        String structureId,
        float deployDuration,
        float lifetimeSeconds,
        float packDuration,
        float redeployCooldown,
        boolean carried) implements Serializable {

    public DeployableEmplacementSpec(String structureId, float deployDuration,
                                     float lifetimeSeconds, float packDuration,
                                     float redeployCooldown) {
        this(structureId, deployDuration, lifetimeSeconds, packDuration, redeployCooldown, false);
    }

    /** An expendable pod: placed once, never recovered. */
    public DeployableEmplacementSpec(String structureId, float deployDuration,
                                     float lifetimeSeconds) {
        this(structureId, deployDuration, lifetimeSeconds, 0f, 0f);
    }

    /** Whether the carrier can lift this emplacement again after setting it down. */
    public boolean recoverable() {
        return packDuration > 0f;
    }
}
