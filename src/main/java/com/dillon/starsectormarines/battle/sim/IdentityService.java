package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.mech.MechVariant;
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

    /**
     * Presence check — true iff {@code id} carries IDENTITY. Every ground unit
     * and corpse does, and so does every carried body: an air craft and a
     * convoy chassis carry IDENTITY <em>as well as</em> their own
     * AIR_IDENTITY / GROUND_IDENTITY, which is precisely what lets the ordinary
     * body paths read them. The domain component holds the domain type; this
     * one holds what every body has.
     */
    public boolean has(long id) { return entityWorld.has(id, components.IDENTITY); }

    /**
     * The entity's human-readable greppable name (seeded from the ctor String
     * id). Fail-loud on an entity with no IDENTITY; gate on {@link #has} if the
     * caller isn't sure. A carried body is not such an entity — it carries
     * IDENTITY too, and answers here.
     */
    public String name(long id) { return (String) entityWorld.getObject(id, components.IDENTITY, BattleComponents.IDENTITY_NAME); }

    /**
     * The entity's immutable {@link UnitType} archetype (drives sprite + base stat
     * block). The by-id replacement for the {@code Entity.type} field read as the
     * handle collapses to a bare {@code long} (identity-collapse Phase D). IDENTITY
     * rides the death transmute, so this is readable on a corpse too. Fail-loud on an
     * entity with no IDENTITY. A carried body carries IDENTITY alongside its own
     * AIR_IDENTITY / GROUND_IDENTITY and answers here with the archetype every
     * body has; its domain type is asked of the domain component.
     */
    public UnitType type(long id) { return (UnitType) entityWorld.getObject(id, components.IDENTITY, BattleComponents.IDENTITY_TYPE); }

    /** Persistent chassis profile, or null for a non-mech ground entity. */
    public MechVariant mechVariant(long id) {
        return (MechVariant) entityWorld.getObject(id, components.IDENTITY,
                BattleComponents.IDENTITY_MECH_VARIANT);
    }

    /**
     * The airframe a parked aircraft is a hull of, or null for anything else.
     *
     * <p>The berth is what has identity on a field, so this is deliberately not
     * a second record of where the aircraft is kept — it is the one fact about
     * the airframe that a body's shared accessors need, held where they can
     * reach it in a lookup rather than a walk over the field's hardstands.
     */
    public Airframe airframe(long id) {
        return (Airframe) entityWorld.getObject(id, components.IDENTITY,
                BattleComponents.IDENTITY_AIRFRAME);
    }

    /**
     * The entity's immutable {@link Faction}. The by-id replacement for the
     * {@code Entity.faction} field read as the handle collapses to a bare {@code long}
     * (identity-collapse Phase D). Readable on a corpse (IDENTITY rides the death
     * transmute). Convoy vehicles fall back to their {@code GROUND_IDENTITY}
     * faction so shared combat consumers can classify them. Fail-loud on an
     * entity with neither identity capability; air craft read
     * {@code World.airFaction(id)}.
     */
    public Faction faction(long id) {
        if (entityWorld.has(id, components.IDENTITY)) {
            return (Faction) entityWorld.getObject(id, components.IDENTITY,
                    BattleComponents.IDENTITY_FACTION);
        }
        return (Faction) entityWorld.getObject(id, components.GROUND_IDENTITY,
                BattleComponents.GROUND_IDENTITY_FACTION);
    }
}
