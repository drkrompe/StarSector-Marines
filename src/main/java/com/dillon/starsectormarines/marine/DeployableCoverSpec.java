package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/**
 * Authored behaviour of one placed cover screen, limited to what the
 * <em>carried item</em> legitimately decides: which boundary profile it sets
 * down, how long the carrier is committed to setting it up, and how long the
 * placed thing stands before it is taken back down.
 *
 * <p>The same authority split the emplacement spec obeys applies here, with a
 * different owner on the far side of it. A placed emplacement is an actor, so
 * its durability and geometry come from the turret catalog's structure. A
 * placed cover screen is not an actor at all — it is a property of a cell
 * boundary — so its cover level, its catch height, its structure, and
 * whether it is passable, transparent, and shoot-through all come from the
 * named {@code battle.nav.SharedEdgeBarrier.Kind} profile. Restating any of
 * them on the backpack would give one barricade two different answers to the
 * same question.
 *
 * @param barrierKind     name of the shared-edge profile the placement authors
 * @param deployDuration  sim-seconds the carrier is committed to the placement
 *                        channel, movement frozen
 * @param lifetimeSeconds sim-seconds the screen stands before it is retired
 */
public record DeployableCoverSpec(
        String barrierKind,
        float deployDuration,
        float lifetimeSeconds) implements Serializable {
}
