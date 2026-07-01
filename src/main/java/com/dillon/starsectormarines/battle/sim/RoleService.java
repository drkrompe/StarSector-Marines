package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code ROLE} component — typed by-id access to a unit's
 * behavior-dispatch role in the archetype {@link EntityWorld}.
 *
 * <p>A <b>Service</b> in this codebase's sense (see
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}): it <em>owns</em>
 * a component's data and exposes the methods to read/modify it — distinct from a
 * per-tick <b>System</b>, which column-walks every entity matching an aspect. A
 * consumer reaches it via {@code sim.role()} / {@code roster.role()} and calls
 * {@code role.role(id)} directly — no {@link World} hop.
 *
 * <p>The role is stored as the {@link UnitRole#ordinal()} in a plain INT column
 * (not an OBJECT enum ref) so dispatch can later branch on / partition by the
 * ordinal without materializing the {@code Entity}; this Service hides the
 * ordinal↔enum round-trip behind {@link #role(long)} / {@link #setRole(long,
 * UnitRole)}. {@link #VALUES} caches {@code UnitRole.values()} so the reconstruction
 * allocates nothing per read (the getter is hit per-unit per-tick from the behavior
 * dispatch).
 *
 * <p>{@code ROLE} is <em>universal</em> among live units (every unit has a role),
 * so {@link #role(long)} is <b>fail-loud</b> only on a corpse (the death transmute
 * removed ROLE) or a released id — never on a live unit. Unlike SQUAD it is
 * <b>mutable on a live unit</b>: {@link #setRole} is the runtime-reassignment seam a
 * kit pickup drives (a marine promoted to {@code KIT_RETRIEVER}/{@code PLANTER}, then
 * reverted to {@code COMBATANT}); the spawn paths seed the role at {@code allocate}
 * from {@code Entity.seedRole} instead. Serial-only.
 */
public final class RoleService {

    private static final UnitRole[] VALUES = UnitRole.values();

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    public RoleService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** The unit's behavior-dispatch role. Fail-loud on a corpse / released id (ROLE is live-only). */
    public UnitRole role(long id) {
        return VALUES[entityWorld.getInt(id, components.ROLE, BattleComponents.ROLE_ORDINAL)];
    }

    /**
     * Reassigns {@code id}'s live role — the runtime seam (a kit pickup promotes a
     * marine to {@code KIT_RETRIEVER}/{@code PLANTER}; {@code KitRetrieverBehavior}
     * reverts it to {@code COMBATANT} once the drop is gone). The spawn paths seed the
     * role at {@code allocate} from {@code Entity.seedRole} instead. Fail-loud on a
     * corpse / released id. Serial-only — never mid-{@code Query} walk.
     */
    public void setRole(long id, UnitRole role) {
        entityWorld.setInt(id, components.ROLE, BattleComponents.ROLE_ORDINAL, role.ordinal());
    }
}
