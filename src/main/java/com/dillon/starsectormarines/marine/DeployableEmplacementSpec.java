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
 * @param structureId     turret-catalog structure the placement mints
 * @param deployDuration  sim-seconds the carrier is committed to the placement
 *                        channel, movement frozen
 * @param lifetimeSeconds sim-seconds the emplacement runs before burning out
 */
public record DeployableEmplacementSpec(
        String structureId,
        float deployDuration,
        float lifetimeSeconds) implements Serializable {
}
