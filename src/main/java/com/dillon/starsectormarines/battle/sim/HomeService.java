package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code HOME} component — typed by-id access to a garrison
 * unit's idle-post cell in the archetype {@link EntityWorld}.
 *
 * <p>A <b>Service</b> in this codebase's sense (see
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}): it <em>owns</em>
 * a component's data and exposes the methods to read/modify it. A consumer reaches it
 * via {@code sim.home()} / {@code roster.home()} and calls {@code home.hasHome(id)} /
 * {@code home.homeCellX(id)} directly — no {@link World} hop.
 *
 * <p>{@code HOME} is OPTIONAL — <b>presence IS "has a post"</b>: {@link #hasHome} is the
 * presence check, and {@link #homeCellX}/{@link #homeCellY} are <b>fail-loud</b> on a
 * unit with no post (a roaming marine / patrol that never carried HOME, or a corpse once
 * the death drain transmuted it away). The old {@code -1} sentinel is gone as a stored
 * value — gate every cell read on {@link #hasHome}. Serial-only.
 */
public final class HomeService {

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    public HomeService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** Presence check — true iff {@code id} has a garrison post (carries HOME). Gate the cell reads on this. */
    public boolean hasHome(long id) { return entityWorld.has(id, components.HOME); }

    /** The unit's home-post cell x. Fail-loud on a unit with no post; gate on {@link #hasHome}. */
    public int homeCellX(long id) { return entityWorld.getInt(id, components.HOME, BattleComponents.HOME_CELL_X); }

    /** The unit's home-post cell y. Fail-loud on a unit with no post; gate on {@link #hasHome}. */
    public int homeCellY(long id) { return entityWorld.getInt(id, components.HOME, BattleComponents.HOME_CELL_Y); }

    /**
     * Sets {@code id}'s home-post cell — the runtime reassignment seam
     * ({@code SquadFallbackSystem} redistributes posts when a garrison squad retreats to
     * a new node). Adds the HOME component if the unit had none (an archetype row-move)
     * then writes the cell, so it works whether or not the unit already had a post. The
     * spawn path seeds the post at {@code allocate} from {@code Entity.seedHomeCell*}
     * instead. Serial-only — never mid-{@code Query} walk; mirrors
     * {@code SquadService.assignSquad}.
     */
    public void setHome(long id, int cellX, int cellY) {
        if (!entityWorld.has(id, components.HOME)) entityWorld.addComponent(id, components.HOME);
        entityWorld.setInt(id, components.HOME, BattleComponents.HOME_CELL_X, cellX);
        entityWorld.setInt(id, components.HOME, BattleComponents.HOME_CELL_Y, cellY);
    }
}
