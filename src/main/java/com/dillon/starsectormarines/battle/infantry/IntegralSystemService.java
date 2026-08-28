package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.MitigationService;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.marine.BreacherAssistSpec;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.PerceptionSweepSpec;

/**
 * Owns the live state of the capability a marine's armour pattern carries
 * ({@code integral-armor-systems.md}).
 *
 * <p>Presence of the {@code INTEGRAL_SYSTEM} component is the capability, in
 * the {@code SECONDARY_WEAPON} precedent: a unit whose suit declares nothing
 * has no component and every question here answers "no" for it.
 *
 * <p><b>The effect is recomputed, never accumulated.</b> Movement speed is
 * derived each time as {@code base * multiplier} from
 * {@code MOVEMENT_BASE_MOVE_SPEED}, so activating twice cannot compound and an
 * expiry cannot leave a remainder behind. That matters more than it sounds:
 * scale-on-activate/unscale-on-expire drifts with float rounding and breaks
 * outright the first time two effects overlap.
 */
public final class IntegralSystemService {

    private final EntityWorld entityWorld;
    private final BattleComponents components;
    private final MitigationService mitigations;

    public IntegralSystemService(EntityWorld entityWorld, BattleComponents components,
                                 MitigationService mitigations) {
        this.entityWorld = entityWorld;
        this.components = components;
        this.mitigations = mitigations;
    }

    /** Whether this unit's suit carries a capability at all. Most do not. */
    public boolean has(long id) {
        return entityWorld.has(id, components.INTEGRAL_SYSTEM);
    }

    /** The authored definition, or null when the suit declares none. */
    public IntegralSystemDef spec(long id) {
        if (!has(id)) return null;
        Object value = entityWorld.getObject(id, components.INTEGRAL_SYSTEM,
                BattleComponents.INTEGRAL_SYSTEM_SPEC);
        return value instanceof IntegralSystemDef def ? def : null;
    }

    /** Sim-seconds left in the current activation; {@code 0} when not running. */
    public float activeRemaining(long id) {
        return has(id) ? Math.max(0f, entityWorld.getFloat(id, components.INTEGRAL_SYSTEM,
                BattleComponents.INTEGRAL_SYSTEM_ACTIVE_TIMER)) : 0f;
    }

    /** Sim-seconds before the system may be spent again; {@code 0} when ready. */
    public float cooldownRemaining(long id) {
        return has(id) ? Math.max(0f, entityWorld.getFloat(id, components.INTEGRAL_SYSTEM,
                BattleComponents.INTEGRAL_SYSTEM_COOLDOWN_TIMER)) : 0f;
    }

    /** Uses left on an ammunition-gated system. */
    public int ammo(long id) {
        return has(id) ? entityWorld.getInt(id, components.INTEGRAL_SYSTEM,
                BattleComponents.INTEGRAL_SYSTEM_AMMO) : 0;
    }

    public boolean isActive(long id) {
        return activeRemaining(id) > 0f;
    }

    /**
     * Whether the system could be spent right now: it exists, it is not already
     * running, its cooldown has drained, and an ammunition-gated system still
     * has a use left.
     */
    public boolean canActivate(long id) {
        IntegralSystemDef def = spec(id);
        if (def == null) return false;
        if (isActive(id) || cooldownRemaining(id) > 0f) return false;
        return !def.usesAmmunition() || ammo(id) > 0;
    }

    /**
     * Spends the system. Returns false and changes nothing when it was not
     * available, so a caller may ask by trying.
     *
     * <p>The cooldown starts now rather than at expiry — the authored
     * cooldown is validated to exceed the duration, so the system is
     * unavailable for the whole run plus the remainder either way, and
     * starting it here means one clock to reason about instead of two.
     */
    public boolean activate(long id) {
        IntegralSystemDef def = spec(id);
        if (!canActivate(id)) return false;
        entityWorld.setFloat(id, components.INTEGRAL_SYSTEM,
                BattleComponents.INTEGRAL_SYSTEM_ACTIVE_TIMER, def.durationSeconds());
        entityWorld.setFloat(id, components.INTEGRAL_SYSTEM,
                BattleComponents.INTEGRAL_SYSTEM_COOLDOWN_TIMER, def.cooldownSeconds());
        if (def.usesAmmunition()) {
            entityWorld.setInt(id, components.INTEGRAL_SYSTEM,
                    BattleComponents.INTEGRAL_SYSTEM_AMMO, Math.max(0, ammo(id) - 1));
        }
        applyEffect(id, def);
        return true;
    }

    /**
     * Drains this unit's timers by one tick and drops the effect the moment the
     * activation expires. Idempotent at zero, and safe to call on a unit whose
     * suit carries nothing.
     */
    public void tick(long id, float dt) {
        IntegralSystemDef def = spec(id);
        if (def == null) return;

        float cooldown = cooldownRemaining(id);
        if (cooldown > 0f) {
            entityWorld.setFloat(id, components.INTEGRAL_SYSTEM,
                    BattleComponents.INTEGRAL_SYSTEM_COOLDOWN_TIMER, Math.max(0f, cooldown - dt));
        }

        float active = activeRemaining(id);
        if (active <= 0f) return;
        float remaining = active - dt;
        entityWorld.setFloat(id, components.INTEGRAL_SYSTEM,
                BattleComponents.INTEGRAL_SYSTEM_ACTIVE_TIMER, Math.max(0f, remaining));
        if (remaining <= 0f) clearEffect(id);
    }

    /**
     * The movement multiplier this unit's running system contributes, or
     * {@code 1} when nothing is running. Public so presentation and tests can
     * read the same number the mover does without recomputing the rules.
     */
    public float moveSpeedMultiplier(long id) {
        IntegralSystemDef def = spec(id);
        if (def == null || !isActive(id)) return 1f;
        BreacherAssistSpec breacher = def.breacherAssist();
        return breacher != null ? breacher.moveSpeedMult() : 1f;
    }

    /**
     * The sweep this unit is running right now, or {@code null} when it carries
     * no sweep or is not running one.
     *
     * <p>A sweep keeps <b>no</b> state of its own here, deliberately. What it
     * does is contribute a temporary observer to the player's reveal for as
     * long as this returns non-null, and the projection is rebuilt from
     * scratch on every tick ({@code IntegralSystemSystem}). There is therefore
     * nothing to grant on activation and nothing to unwind on expiry: the
     * standing "no residue" rule holds because there is no residue to leave.
     */
    public PerceptionSweepSpec activeSweep(long id) {
        IntegralSystemDef def = spec(id);
        return def != null && isActive(id) ? def.perceptionSweep() : null;
    }

    private void applyEffect(long id, IntegralSystemDef def) {
        BreacherAssistSpec breacher = def.breacherAssist();
        if (breacher == null) return;
        setMoveSpeedFromBase(id, breacher.moveSpeedMult());
        // The screen is mitigation, owned by combat-durability rather than by
        // this service: the authored pair is handed over and the durability
        // side owns the arc, the clock, and the expiry from there.
        mitigations.grant(id, breacher.frontalResistance(),
                breacher.shieldedArcDegrees(), def.durationSeconds());
    }

    /**
     * The screen's own clock expires with the system's, so dropping it here is
     * belt and braces rather than the mechanism — but it is what guarantees the
     * standing "no residue" rule holds even if the two ever disagree by a tick.
     */
    private void clearEffect(long id) {
        setMoveSpeedFromBase(id, 1f);
        mitigations.clear(id);
    }

    /**
     * Recomputes the live movement speed from the untouched base. A unit with
     * no MOVEMENT component (a turret wearing nothing that moves) is simply
     * skipped rather than fabricating a column.
     */
    private void setMoveSpeedFromBase(long id, float multiplier) {
        if (!entityWorld.has(id, components.MOVEMENT)) return;
        float base = entityWorld.getFloat(id, components.MOVEMENT,
                BattleComponents.MOVEMENT_BASE_MOVE_SPEED);
        if (base <= 0f) return;
        entityWorld.setFloat(id, components.MOVEMENT,
                BattleComponents.MOVEMENT_MOVE_SPEED, base * Math.max(0f, multiplier));
    }
}
