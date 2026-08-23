package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code TELEMETRY} component — what each combatant
 * actually did this battle. Typed by-id access in the archetype
 * {@link EntityWorld}, the same shape as {@link HubStateService} and its
 * siblings; consumers reach it via {@code roster.telemetry()} /
 * {@code sim.telemetry()}.
 *
 * <p>Two products from one set of counters
 * ({@code s3-per-soldier-telemetry.md}): a reward
 * input, which the campaign converts to experience for the marines it
 * recognizes, and a balance artifact, which covers <em>every</em> entity —
 * defenders and employer militia included — so questions like "what is the
 * real landed-round rate at Milspec versus Service" are answerable from a
 * battle rather than from a stopwatch.
 *
 * <p><b>Lifecycle-stable.</b> {@code TELEMETRY} is not in the corpse-remove
 * mask, so every reader and writer here works unchanged on a dead entity: the
 * killing blow's {@code recordKill} / {@code recordDamageTaken} land during
 * {@code DamageResolver.resolve}, and the end-of-battle gather runs long after
 * {@code releaseFromRegistry} dropped the dense slot. That is deliberate — a
 * soldier's record matters most when they did not come home. Never gate a
 * telemetry read on liveness.
 *
 * <p><b>Every mutator is presence-tolerant.</b> Unlike the fail-loud readers
 * on the other optional-component services, {@code recordXxx} silently skips
 * an entity with no {@code TELEMETRY} row. The write sites are the damage and
 * firing pipelines, which are already reached by civilians, walls-only
 * detonations, air craft, and the {@link #NO_ATTACKER} sentinel; making them
 * each re-derive "is this a combatant" would put the same guard at seven call
 * sites to no benefit. The <em>readers</em> stay fail-loud, so a gather that
 * walks the wrong set still says so.
 *
 * <p><b>Threading.</b> Fire-side writes ({@link #recordRoundFired},
 * {@link #recordSecondaryUsed}) happen inside the parallel {@code UPDATE_UNITS}
 * dispatch, but each is a shooter writing its <em>own</em> row — one worker per
 * unit — so they are safe exactly as the fire-intent columns are. Damage-side
 * writes all run from {@code DamageResolver.resolve}, which is serial.
 */
public final class CombatTelemetryService {

    /**
     * Attacker id meaning "no entity is responsible" — scripted scenario
     * damage, flyby strafing routed through {@code applyExternalDamage}, and
     * the vanilla-combat bridge's mirrored hull damage. Damage credited to it
     * is dropped rather than attributed. Safe as a sentinel because
     * {@code UnitRosterService} mints ids from 1.
     */
    public static final long NO_ATTACKER = 0L;

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    public CombatTelemetryService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** Presence check — true iff {@code id}'s fighting is being recorded (spawned as a combatant). Gate the readers on this. */
    public boolean isRecorded(long id) {
        return id != NO_ATTACKER && entityWorld.has(id, components.TELEMETRY);
    }

    // ---- readers (fail-loud; gate on isRecorded) ----

    /** Primary rounds {@code id} has fired. */
    public int roundsFired(long id) { return getInt(id, BattleComponents.TELEMETRY_ROUNDS_FIRED); }

    /** Primary rounds {@code id} fired that reached a body — one per arriving round, never per damaged unit. */
    public int roundsHit(long id) { return getInt(id, BattleComponents.TELEMETRY_ROUNDS_HIT); }

    /** Post-mitigation HP {@code id} has taken off hostiles. Excludes friendly fire and overkill past zero. */
    public float damageDealt(long id) { return getFloat(id, BattleComponents.TELEMETRY_DAMAGE_DEALT); }

    /** Post-mitigation HP {@code id} has taken off its own side. Kept separate so it stays visible. */
    public float friendlyFireDamage(long id) { return getFloat(id, BattleComponents.TELEMETRY_FRIENDLY_FIRE_DAMAGE); }

    /** Post-mitigation HP {@code id} has absorbed, from any source. */
    public float damageTaken(long id) { return getFloat(id, BattleComponents.TELEMETRY_DAMAGE_TAKEN); }

    /** Hostiles {@code id} landed the killing blow on. */
    public int kills(long id) { return getInt(id, BattleComponents.TELEMETRY_KILLS); }

    /** Secondary-weapon rounds {@code id} has expended. */
    public int secondaryUsed(long id) { return getInt(id, BattleComponents.TELEMETRY_SECONDARY_USED); }

    /**
     * Landed fraction — {@link #roundsHit} over {@link #roundsFired}, or
     * {@code 0} before the first trigger pull. The headline accuracy number
     * for both the debug readout and the career record.
     */
    public float landedFraction(long id) {
        int fired = roundsFired(id);
        return fired > 0 ? (float) roundsHit(id) / fired : 0f;
    }

    // ---- mutators (presence-tolerant; see class doc) ----

    /** One primary round left the barrel. Called once per round, so a three-round burst counts three. */
    public void recordRoundFired(long shooterId) {
        bumpInt(shooterId, BattleComponents.TELEMETRY_ROUNDS_FIRED, 1);
    }

    /**
     * One fired round arrived on a body. Counted at the ballistic-impact
     * seam, not the damage seam, so a shell that detonates among six units is
     * still one landed round.
     */
    public void recordRoundHit(long shooterId) {
        bumpInt(shooterId, BattleComponents.TELEMETRY_ROUNDS_HIT, 1);
    }

    /**
     * Credits {@code attackerId} with {@code applied} HP of post-mitigation
     * damage. {@code friendly} routes it to the separate friendly-fire
     * counter instead of {@link #damageDealt}.
     */
    public void recordDamageDealt(long attackerId, float applied, boolean friendly) {
        bumpFloat(attackerId, friendly
                ? BattleComponents.TELEMETRY_FRIENDLY_FIRE_DAMAGE
                : BattleComponents.TELEMETRY_DAMAGE_DEALT, applied);
    }

    /** Records {@code applied} HP absorbed by {@code targetId}, from any source. */
    public void recordDamageTaken(long targetId, float applied) {
        bumpFloat(targetId, BattleComponents.TELEMETRY_DAMAGE_TAKEN, applied);
    }

    /** Credits {@code attackerId} with a kill. One detonation killing three counts three. */
    public void recordKill(long attackerId) {
        bumpInt(attackerId, BattleComponents.TELEMETRY_KILLS, 1);
    }

    /** One secondary-weapon round expended. */
    public void recordSecondaryUsed(long shooterId) {
        bumpInt(shooterId, BattleComponents.TELEMETRY_SECONDARY_USED, 1);
    }

    // ---- internals ----

    private int getInt(long id, int field) {
        return entityWorld.getInt(id, components.TELEMETRY, field);
    }

    private float getFloat(long id, int field) {
        return entityWorld.getFloat(id, components.TELEMETRY, field);
    }

    private void bumpInt(long id, int field, int delta) {
        if (!isRecorded(id)) return;
        entityWorld.setInt(id, components.TELEMETRY, field, getInt(id, field) + delta);
    }

    private void bumpFloat(long id, int field, float delta) {
        if (delta == 0f || !isRecorded(id)) return;
        entityWorld.setFloat(id, components.TELEMETRY, field, getFloat(id, field) + delta);
    }
}
