package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.ComponentStore;
import com.dillon.starsectormarines.battle.component.DeadBody;

/**
 * Records a {@link DeadBody} component for every unit that dies — the
 * death-event handler that builds the corpse home. Subscribed to the battle's
 * death dispatcher; on each {@link DeathEvent} it attaches a body keyed by the
 * dead unit's entity id, capturing the corpse-render identity ({@code type} +
 * {@code deathPoseIdx}). The body's location is the shared
 * {@link RenderPositionService} entry under the same id, which already survives
 * release.
 *
 * <p>Attaches for <em>every</em> death, not just units with a corpse sprite:
 * a body is a body regardless of whether it has render art (a future medic
 * cares about all of them), and keeping the filter out of this sim-tier handler
 * avoids coupling it to the render-tier {@code RenderAppearance}. The dead-sprite
 * render applies the "has a corpse sheet / valid pose" filter at draw time.
 *
 * <p>No per-tick lifecycle (unlike the drone crash) — a corpse is static once
 * recorded. The component survives registry release because it is keyed by
 * entity id, so the dead-sprite render finds it for the rest of the battle.
 */
public final class DeadBodySystem {

    private final ComponentStore<DeadBody> bodies;

    public DeadBodySystem(ComponentStore<DeadBody> bodies) {
        this.bodies = bodies;
    }

    /** Death-event handler: attach the corpse body for the unit that just died. */
    public void onDeath(DeathEvent event) {
        Entity u = event.unit();
        bodies.add(u.entityId, new DeadBody(u.type, u.faction, u.deathPoseIdx));
    }
}
