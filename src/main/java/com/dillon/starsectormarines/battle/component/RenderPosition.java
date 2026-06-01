package com.dillon.starsectormarines.battle.component;

/**
 * Smooth sub-cell render position ({@code x,y} in cell units) as a composable
 * component — the shared "where do I draw" capability any entity can have,
 * rather than a column redefined per entity category. Mutated in place by the
 * movement systems (no re-add to the store on each step).
 *
 * <p>Stored in a {@link ComponentStore} keyed by entity id, so it survives a
 * unit's release from the live dense registry: a corpse is an entity that still
 * <em>has</em> a {@code RenderPosition} (frozen at the spot it fell) but no
 * longer has health / AI. Wrapped by
 * {@code battle.unit.RenderPositionService} for a primitive float-typed API.
 */
public final class RenderPosition {
    public float x;
    public float y;

    public RenderPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }
}
