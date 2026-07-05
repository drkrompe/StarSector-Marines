package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code IDENTITY} component — by-id access to an entity's
 * immutable identity in the archetype {@link EntityWorld}: {@code type},
 * {@code faction}, and the human-readable {@code name}.
 *
 * <p>A <b>Service</b> (data owner) in the sense described on {@link CombatService}:
 * consumers reach it via {@code sim.identity()} / {@code roster.identity()} and
 * call {@code identity.name(id)} directly — no {@link World} hop. IDENTITY persists
 * alive→dead (it rides the death transmute), so the name is readable on a corpse
 * too.
 *
 * <p>Today this exposes only {@link #name(long)} — the greppable string an entity
 * spawns with (e.g. {@code "m0"}, {@code "drone-dh1-5"}), seeded from the ctor
 * String id and read by debug dumps, logs, and tests. The stable <em>machine</em>
 * identity is the {@code long} entityId, not this string. Type/faction are still
 * read off the {@code Entity} handle
 * fields today; those reads move here (via {@code type(id)}/{@code faction(id)}
 * accessors) when the handle collapses to a bare {@code long}
 * (identity-collapse Phase D). Serial-only.
 */
public final class IdentityService {

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    public IdentityService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** Presence check — true iff {@code id} carries IDENTITY. Every ground unit + corpse does; air/vehicle carry their own AIR_IDENTITY/GROUND_IDENTITY instead. */
    public boolean has(long id) { return entityWorld.has(id, components.IDENTITY); }

    /** The entity's human-readable greppable name (seeded from the ctor String id). Fail-loud on an entity with no IDENTITY (an air craft / convoy vehicle); gate on {@link #has} if the caller isn't sure it's a ground unit. */
    public String name(long id) { return (String) entityWorld.getObject(id, components.IDENTITY, BattleComponents.IDENTITY_NAME); }

    /**
     * The entity's immutable {@link UnitType} archetype (drives sprite + base stat
     * block). The by-id replacement for the {@code Entity.type} field read as the
     * handle collapses to a bare {@code long} (identity-collapse Phase D). IDENTITY
     * rides the death transmute, so this is readable on a corpse too. Fail-loud on an
     * entity with no IDENTITY (an air craft / convoy vehicle carries its own
     * AIR_IDENTITY / GROUND_IDENTITY type instead).
     */
    public UnitType type(long id) { return (UnitType) entityWorld.getObject(id, components.IDENTITY, BattleComponents.IDENTITY_TYPE); }

    /**
     * The entity's immutable {@link Faction}. The by-id replacement for the
     * {@code Entity.faction} field read as the handle collapses to a bare {@code long}
     * (identity-collapse Phase D). Readable on a corpse (IDENTITY rides the death
     * transmute). Fail-loud on an entity with no IDENTITY — an air craft reads
     * {@code World.airFaction(id)}, a convoy vehicle {@code ConvoyService.faction(id)}.
     */
    public Faction faction(long id) { return (Faction) entityWorld.getObject(id, components.IDENTITY, BattleComponents.IDENTITY_FACTION); }
}
