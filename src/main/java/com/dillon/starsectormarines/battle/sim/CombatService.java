package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code COMBAT} component — typed by-id access (read + mutate)
 * to a combatant's primary-weapon state in the archetype {@link EntityWorld}.
 *
 * <p>A <b>Service</b> in this codebase's sense (see
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}): it <em>owns</em>
 * a component's data and exposes the methods to read/modify it — distinct from a
 * per-tick <b>System</b>, which processes every entity matching an aspect by
 * column-walking. A consumer that needs combat state is constructor-injected with
 * this Service (or reaches it via {@code sim.combat()} / {@code roster.combat()})
 * and calls {@code combat.attackCooldown(id)} directly — no {@link World} hop. This
 * is the random-access / held-ref path; per-tick bulk systems column-walk the
 * COMBAT table instead.
 *
 * <p>{@code COMBAT} is OPTIONAL (combatant-narrowed): {@link #has} is the presence
 * check; every field accessor is <b>fail-loud</b> on a unit that lacks it (a
 * non-combatant, or a corpse once the death drain transmuted it away). Gate on
 * {@link #has} (or {@code u.type.combatant}) before any field read.
 *
 * <p>Part of the {@link World} decomposition: World now delegates its COMBAT
 * accessors here, so the flat facade is no longer the data owner. Serial-only.
 */
public final class CombatService {

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    public CombatService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** Presence check — true iff {@code id} carries COMBAT (is a live combatant). Gate field reads on this. */
    public boolean has(long id) { return entityWorld.has(id, components.COMBAT); }

    public float attackDamage(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_DAMAGE); }
    public void setAttackDamage(long id, float v) { entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_DAMAGE, v); }

    public float attackRange(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_RANGE); }
    public void setAttackRange(long id, float v) { entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_RANGE, v); }

    public float accuracy(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_ACCURACY); }
    public void setAccuracy(long id, float v) { entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ACCURACY, v); }

    public float cooldownTimer(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_COOLDOWN_TIMER); }
    public void setCooldownTimer(long id, float v) { entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_COOLDOWN_TIMER, v); }

    /** Per-unit primary cooldown reset value (seed-only stat); {@code setCooldownTimer(id, attackCooldown(id))} on a fire. */
    public float attackCooldown(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_COOLDOWN); }

    /** The primary handheld weapon flyweight, or {@code null} for a combatant with no per-weapon profile (militia/aliens/turrets fire off the baked attack stats). Seeded at allocate; assigned at deboard via {@link #setPrimaryWeapon}. */
    public MarineWeapon primaryWeapon(long id) { return (MarineWeapon) entityWorld.getObject(id, components.COMBAT, BattleComponents.COMBAT_PRIMARY_WEAPON); }
    public void setPrimaryWeapon(long id, MarineWeapon w) { entityWorld.setObject(id, components.COMBAT, BattleComponents.COMBAT_PRIMARY_WEAPON, w); }

    public long targetId(long id) { return entityWorld.getLong(id, components.COMBAT, BattleComponents.COMBAT_TARGET_ID); }
    public void setTargetId(long id, long v) { entityWorld.setLong(id, components.COMBAT, BattleComponents.COMBAT_TARGET_ID, v); }

    public int burstRemaining(long id) { return entityWorld.getInt(id, components.COMBAT, BattleComponents.COMBAT_BURST_REMAINING); }
    public void setBurstRemaining(long id, int v) { entityWorld.setInt(id, components.COMBAT, BattleComponents.COMBAT_BURST_REMAINING, v); }

    public float burstTimer(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_BURST_TIMER); }
    public void setBurstTimer(long id, float v) { entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_BURST_TIMER, v); }

    public long burstTargetId(long id) { return entityWorld.getLong(id, components.COMBAT, BattleComponents.COMBAT_BURST_TARGET_ID); }
    public void setBurstTargetId(long id, long v) { entityWorld.setLong(id, components.COMBAT, BattleComponents.COMBAT_BURST_TARGET_ID, v); }
}
