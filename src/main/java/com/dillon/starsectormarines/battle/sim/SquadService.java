package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code SQUAD} component — typed by-id access to a unit's
 * squad membership in the archetype {@link EntityWorld}.
 *
 * <p>A <b>Service</b> in this codebase's sense (see
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}): it <em>owns</em>
 * a component's data and exposes the methods to read/modify it — distinct from a
 * per-tick <b>System</b>, which column-walks every entity matching an aspect. A
 * consumer reaches it via {@code sim.squad()} / {@code roster.squad()} and calls
 * {@code squad.hasSquad(id)} / {@code squad.squadId(id)} directly — no {@link World}
 * hop.
 *
 * <p><b>Distinct from the squad <em>objects</em>.</b> This owns the per-unit
 * membership <em>key</em> (the {@code squadId}); the {@link com.dillon.starsectormarines.battle.squad.Squad}
 * <em>registry</em> the key indexes into is owned by {@code UnitRosterService}
 * ({@code getSquad(int)} / {@code mintSquad}). Resolve a unit's squad object with
 * {@code sim.getSquad(sim.squad().squadId(id))} after a {@link #hasSquad} gate, or
 * the {@code BattleView.squadOf(id)} convenience.
 *
 * <p>{@code SQUAD} is OPTIONAL — <b>presence IS membership</b>: {@link #hasSquad} is
 * the presence check, and {@link #squadId} is <b>fail-loud</b> on a non-member (a
 * solo defender / civilian / unsquadded turret that never carried SQUAD, or a corpse
 * once the death drain transmuted it away). The old {@code Entity.NO_SQUAD} sentinel
 * is gone as a stored value — gate every {@link #squadId} read on {@link #hasSquad}.
 * Serial-only.
 */
public final class SquadService {

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    public SquadService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** Presence check — true iff {@code id} is in a squad (carries SQUAD). Gate {@link #squadId} reads on this. */
    public boolean hasSquad(long id) { return entityWorld.has(id, components.SQUAD); }

    /** The unit's squad id — a key into {@code UnitRosterService.getSquad}. Fail-loud on a non-member; gate on {@link #hasSquad}. */
    public int squadId(long id) { return entityWorld.getInt(id, components.SQUAD, BattleComponents.SQUAD_ID); }

    /**
     * Joins {@code id} to {@code squadId} — the post-spawn membership seam (the
     * deboard / setup / reinforcement paths seed it at {@code allocate} from
     * {@code Entity.seedSquadId} instead). Adds the SQUAD component if the unit had
     * none (an archetype row-move) then writes the key, so it works whether or not
     * the unit was already a member. Serial-only — never mid-{@code Query} walk;
     * mirrors {@code World.attachSecondaryWeapon}. {@code squadId} is a real squad
     * (leaving a squad isn't a modeled operation — membership is set once at spawn).
     */
    public void assignSquad(long id, int squadId) {
        if (!entityWorld.has(id, components.SQUAD)) entityWorld.addComponent(id, components.SQUAD);
        entityWorld.setInt(id, components.SQUAD, BattleComponents.SQUAD_ID, squadId);
    }
}
