package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.sim.World;

import java.util.List;

/**
 * Stateless tick consumer that owns squad + mech morale recovery, hysteresis,
 * and the near-miss drain pass. Hit / death drain live on
 * {@code damage.DamageResolver.resolve} (they fire from the damage callback
 * site, not the tick chain); the constants live here and the resolver reads
 * them through this class.
 *
 * <p>Recovery gates on "haven't been shot at recently"
 * ({@link Squad#timeSinceUnderFire} {@code >=} {@link #MORALE_RECOVER_AFTER_FIRE_SECONDS}),
 * not on raw LoS. A broken squad behind imperfect cover can see — and fire
 * opportunistically at — distant enemies and still compose itself once
 * incoming hits/near-misses lull. Pre-fix, any LoS kept
 * {@code _engagedThisTick=true} and locked the squad broken indefinitely once
 * BreakContact's picker landed them on a least-exposed-but-not-truly-hidden
 * cell. Capped by {@code aliveMembers / originalSize} so a squad that's lost
 * half its members can't climb back above 0.5 no matter how long they hide.
 *
 * <p>Hysteresis: {@link Squad#moraleBroken} flips true when morale dips below
 * {@link #MORALE_BROKEN_THRESHOLD}; flips false again once morale climbs
 * above the (higher) {@link #MORALE_CLEAR_THRESHOLD}. The gap prevents a
 * squad hovering near the threshold from flickering between SurviveContact
 * and EliminateEnemies on every replan.
 *
 * <p>Mech squads route through {@link #updateMechSquadMorale}, which uses
 * per-chassis morale + stricter thresholds + faster recovery + an
 * armor-gone hard cap. Squad-level {@link Squad#moraleBroken} is aggregated
 * from majority-broken members so the squad-level GOAP predicate has a
 * stable signal.
 *
 * <p>Sibling to other {@code *System} tick consumers — single {@link #tick}
 * entry point, all dependencies constructor-injected.
 */
public final class SquadMoraleSystem {

    // ---- Squad-level morale (infantry) ----

    /** Base squad morale drained per non-fatal hit on a squadmate, at full strength (cap = 1.0). At lower caps the per-hit drain is scaled by {@code 1 / cap} — a heavily mauled squad is more brittle per shot, so a lone survivor (cap = 0.25) takes a 0.20 drain per hit and folds on the first incoming. */
    public static final float MORALE_DROP_ON_HIT   = 0.05f;
    /** Additional morale drop when a hit kills the squadmate. Kept absolute (not cap-scaled) — it's a one-off event correlated with the cap reduction that the death itself triggers. */
    public static final float MORALE_DROP_ON_DEATH = 0.30f;
    /** Base morale recovered per sim-second while the squad isn't being shot at ({@link Squad#timeSinceUnderFire} {@code >=} {@link #MORALE_RECOVER_AFTER_FIRE_SECONDS}), at full strength. Effective rate is scaled by cap so a mauled squad takes proportionally longer to reach their (lower) ceiling — at base 0.20 this gives a constant ~2.5s recovery to the clear threshold and ~5s to full cap across all squad sizes. */
    public static final float MORALE_RECOVERY_RATE = 0.20f;
    /** Cooldown between morale-drain events on a single squad (sim seconds). A burst of incoming bullets in one tick still counts as one drain — prevents a hail of fire from insta-breaking a full squad. At 0.2s a full squad endures sustained fire for ~2.8s before breaking (5 hits/sec × 0.05/hit = 0.25/sec, 0.7 margin). Doesn't shield mauled squads: their per-hit drain (0.05/cap) is large enough that a single hit folds them on the first cooldown window. */
    public static final float MORALE_DRAIN_COOLDOWN = 0.2f;
    /** Near-miss morale drain — applied when a hostile shot's endpoint lands near a squad member but no damage is taken. Cap-scaled like hit drain. Suppressing fire that doesn't connect still rattles them, just less than a landed hit. */
    public static final float MORALE_DROP_ON_NEAR_MISS = 0.01f;
    /** Squared cell-distance from a shot's endpoint to a squad member that counts as a "near miss." 2.25 = 1.5 cells radius. */
    public static final float NEAR_MISS_RADIUS_SQ = 2.25f;
    /** Hysteresis broken threshold, as a <em>fraction of cap</em>. Squad flips to broken when {@code morale < MORALE_BROKEN_THRESHOLD * cap}. Scaling by cap keeps the model coherent for mauled squads: a lone survivor (cap = 0.25) breaks below 0.075 absolute morale, a fresh squad (cap = 1.0) breaks below 0.30. */
    public static final float MORALE_BROKEN_THRESHOLD = 0.30f;
    /** Hysteresis clear threshold, as a <em>fraction of cap</em>. Broken squad reverts once {@code morale > MORALE_CLEAR_THRESHOLD * cap}. Fixes the pre-scaling pathology where a solo survivor's cap (0.25) was below the absolute clear threshold (0.5) and they could never recover. */
    public static final float MORALE_CLEAR_THRESHOLD  = 0.50f;
    /** Sim seconds since the last hit/near-miss on a squadmate before morale recovery resumes. Decouples recovery from raw LoS — a broken squad in cover that can see distant enemies (and is firing back) but isn't actually being shot at composes itself; a pinned-down squad still taking incoming stays locked. Without this gate, a fallback that lands on a still-exposed cell (BreakContact's picker minimizes exposure but doesn't guarantee a true hide) keeps {@code _engagedThisTick=true} every tick via STANCED return fire and the squad never recovers. */
    public static final float MORALE_RECOVER_AFTER_FIRE_SECONDS = 2.0f;

    // ---- Mech morale (Stage 2) ----
    //
    // Per-roadmap/ai/14-mech-stage1.md "Mech survival" — mechs use a tougher
    // morale model than infantry: HP-threshold drain (not per-hit), stricter
    // broken/clear thresholds, faster recovery, hard cap once damaged. Read
    // by {@link #updateMechSquadMorale} + the HP-drain pass inside
    // {@code damage.DamageResolver.applyMechHpThresholdDrain}.

    /** Fraction-of-maxHp marks where a mech bleeds morale. Crossing each drops {@link #MECH_MORALE_DROP_PER_THRESHOLD} once (monotonic via {@link MechLoadoutComponent#hpThresholdsCrossed}). Descending order — first entry trips at 75% HP. */
    public static final float[] MECH_HP_DRAIN_THRESHOLDS = {0.75f, 0.50f, 0.25f, 0.10f};
    /** Per-threshold morale drop. Sized so all four thresholds drained drops a fresh mech (morale 1.0) to 0.0 — total wipe at 10% HP matches the "wounded mech withdraws" target. */
    public static final float MECH_MORALE_DROP_PER_THRESHOLD = 0.25f;
    /** Hysteresis broken threshold for mechs, as a fraction of cap. Tuned with the cap drop at {@link #MECH_MORALE_ARMOR_GONE_HP_FRAC}: with default drops the mech breaks just after the 25% HP threshold crosses (morale 0.25 < 0.60×0.5 = 0.30), leaving headroom to disengage before destruction. Earlier values (0.15) only tripped break at 10% HP — too late to survive the retreat. */
    public static final float MECH_MORALE_BROKEN_THRESHOLD = 0.60f;
    /** Hysteresis clear threshold for mechs, as a fraction of cap. At cap=0.5 (damaged), clear sits at 0.425 absolute — reachable from a broken mech (morale=0.25) in ~0.6s of recovery, so a successful disengage clears the flag and re-engages the planner. */
    public static final float MECH_MORALE_CLEAR_THRESHOLD = 0.85f;
    /** Multiplier on {@link #MORALE_RECOVERY_RATE} for mech-side recovery. 1.5× — a mech that broke recomposes faster than infantry once safe. */
    public static final float MECH_MORALE_RECOVERY_RATE_MULT = 1.5f;
    /** HP fraction below which a mech's morale cap drops to {@link #MECH_MORALE_ARMOR_GONE_CAP} — the "armor is gone, this thing can be rattled" gate. */
    public static final float MECH_MORALE_ARMOR_GONE_HP_FRAC = 0.50f;
    /** Hard cap on mech morale once HP drops below {@link #MECH_MORALE_ARMOR_GONE_HP_FRAC}. With the clear threshold at 0.85 × 0.50 = 0.425 absolute and broken at 0.60 × 0.50 = 0.30, a damaged mech that breaks (morale 0.25 after the 25% HP threshold) only needs to climb 0.175 to clear — fast enough that a successful disengage actually un-breaks. */
    public static final float MECH_MORALE_ARMOR_GONE_CAP = 0.50f;

    private final UnitRosterService roster;
    private final ShotService shots;

    public SquadMoraleSystem(UnitRosterService roster, ShotService shots) {
        this.roster = roster;
        this.shots = shots;
    }

    public void tick(float dt) {
        // Dense iteration over [0, liveCount()) implicitly filters out released
        // units — no .isAlive() guard needed inside the inner loops. The
        // registry reference is stable within this serial-phase tick (no
        // allocation happens between here and the next phase boundary), so a
        // once-per-tick capture of denseArray / cell arrays is safe. hp/maxHp
        // moved to the entity world's HEALTH columns (migration step 3) — the
        // mech pass reads them by id, a handful of probes per tick.
        Entity[] dense = roster.denseArray();
        int liveCount = roster.liveCount();

        // Near-miss drain pass: hostile shots that landed near a squadmate
        // but didn't connect still rattle the squad. Same cooldown gate as
        // hits — a hail of misses can't insta-break either. One drain event
        // per squad per tick max (the cooldown bails on the second pass).
        List<ShotEvent> shotsThisFrame = shots.getShotsThisFrame();
        if (!shotsThisFrame.isEmpty()) {
            for (ShotEvent shot : shotsThisFrame) {
                if (shot.hit) continue;
                Squad target = squadHitByMiss(shot, dense, roster, liveCount);
                if (target == null) continue;
                // Mech squads don't take near-miss morale — their drain model
                // is HP-threshold only (per roadmap/ai/14-mech-stage1.md). A
                // mech that didn't catch a round isn't rattled by air.
                if (target.isMechSquad()) continue;
                // Recovery gate: a near-miss always resets the "under fire"
                // timer, even when the drain cooldown blocks the morale drop.
                target.timeSinceUnderFire = 0f;
                if (target.moraleDrainCooldown > 0f) continue;
                float cap = (target.originalSize > 0 && target.aliveMembers > 0)
                        ? (float) target.aliveMembers / target.originalSize
                        : 1f;
                float base = (cap > 0f) ? MORALE_DROP_ON_NEAR_MISS / cap : MORALE_DROP_ON_NEAR_MISS;
                float drop = base * shot.moraleImpact;
                target.morale = Math.max(0f, target.morale - drop);
                target.moraleDrainCooldown = MORALE_DRAIN_COOLDOWN;
            }
        }

        for (Squad squad : roster.getSquads()) {
            if (squad.aliveMembers <= 0) continue;
            // Mech squads run a separate per-chassis morale pass — recovery,
            // hysteresis, hard cap, squad-level aggregation. The infantry
            // body below would otherwise drain {@link Squad#morale} on a flag
            // that isn't read for mech squads (predicate consults
            // {@link Squad#moraleBroken}, not raw morale).
            if (squad.isMechSquad()) {
                updateMechSquadMorale(squad, dense, roster, liveCount, dt);
                continue;
            }

            // Tick "time since under fire" before reading it. Saturate to
            // avoid overflow on long quiet stretches — the threshold check
            // only cares about >= MORALE_RECOVER_AFTER_FIRE_SECONDS.
            if (squad.timeSinceUnderFire < 1e9f) squad.timeSinceUnderFire += dt;

            float cap = (squad.originalSize > 0)
                    ? (float) squad.aliveMembers / squad.originalSize
                    : 1f;
            // Recovery gates on "haven't been shot at recently," not on raw
            // LoS. A broken squad that pulled back to cover with imperfect
            // hide (BreakContact's picker minimizes exposure but the
            // geometry doesn't always allow a true hide) can still see — and
            // fire back at — distant enemies. As long as no incoming
            // hits/near-misses arrive within MORALE_RECOVER_AFTER_FIRE_SECONDS,
            // they compose themselves. Pre-fix: any LoS kept _engagedThisTick
            // true forever, locking the squad in SurviveContact.
            if (squad.timeSinceUnderFire >= MORALE_RECOVER_AFTER_FIRE_SECONDS) {
                // Recovery rate scales with cap: a mauled squad recovers
                // proportionally slower toward their (lower) ceiling. With
                // base 0.20 this gives constant ~2.5s time-to-clear and
                // ~5s time-to-full-cap across all squad sizes. A solo
                // survivor's previous 1.25s full recovery was too fast to
                // read as "they're composing themselves."
                float rate = MORALE_RECOVERY_RATE * cap;
                squad.morale = Math.min(cap, squad.morale + rate * dt);
            } else {
                // Under fire — no recovery; also re-clamp in case the cap
                // dropped (member died this tick) below current morale.
                squad.morale = Math.min(cap, squad.morale);
            }

            // Tick down the drain cooldown so the next incoming hit /
            // near-miss can register.
            if (squad.moraleDrainCooldown > 0f) {
                squad.moraleDrainCooldown = Math.max(0f, squad.moraleDrainCooldown - dt);
            }

            // Thresholds scale with cap so the model stays coherent for
            // mauled squads. Solo cap = 0.25 → broken below 0.075, clears
            // above 0.125. Without scaling, the absolute clear threshold
            // (0.5) was above solo cap (0.25) and they could never recover.
            float brokenAt = MORALE_BROKEN_THRESHOLD * cap;
            float clearAt  = MORALE_CLEAR_THRESHOLD  * cap;
            if (squad.moraleBroken) {
                if (squad.morale > clearAt) squad.moraleBroken = false;
            } else {
                if (squad.morale < brokenAt) squad.moraleBroken = true;
            }
        }
    }

    /**
     * Per-mech morale tick + squad-level aggregation. Called from {@link #tick}
     * for each alive mech squad. For each member: tick the under-fire timer,
     * derive the cap (1.0 above the armor-gone HP fraction,
     * {@link #MECH_MORALE_ARMOR_GONE_CAP} below), recover passively when out
     * of fire, apply {@link MechLoadoutComponent#moraleBroken} hysteresis with
     * the mech thresholds. Then set {@link Squad#moraleBroken} from the
     * count of broken members — majority-broken trips the squad (one mech
     * cracking out of four isn't enough; two or more is).
     *
     * <p>Squad-level GOAP doesn't yet route only broken members to
     * BreakContact (the action just takes all members in one "any" slot);
     * once that lands, this aggregator could be relaxed to "any broken."
     * Today majority is what gives a stable squad-level signal.
     */
    private void updateMechSquadMorale(Squad squad, Entity[] dense,
                                       UnitRosterService roster, int liveCount, float dt) {
        World world = roster.world();
        int aliveMechs = 0;
        int brokenMechs = 0;
        for (int i = 0; i < liveCount; i++) {
            Entity u = dense[i];
            // Dense iteration excludes released units — no isAlive() needed.
            if (!roster.squad().hasSquad(u.entityId) || roster.squad().squadId(u.entityId) != squad.id) continue;
            // Capability-as-presence: a mech is an entity with a loadout
            // component (was the nullable u.mech field). Null-safe by-id read off
            // the MECH_LOADOUT world component.
            MechLoadoutComponent m = world.mechLoadout(u.entityId);
            if (m == null) continue;
            aliveMechs++;
            if (m.timeSinceUnderFire < 1e9f) m.timeSinceUnderFire += dt;

            // hp lives in the entity world's HEALTH columns — by-id reads via
            // the world facade. Mechs per squad are few, so the per-member
            // probe is cold.
            float uMaxHp = world.maxHp(u.entityId);
            float cap = (uMaxHp > 0f
                    && world.hp(u.entityId) < MECH_MORALE_ARMOR_GONE_HP_FRAC * uMaxHp)
                    ? MECH_MORALE_ARMOR_GONE_CAP
                    : 1.0f;
            if (m.timeSinceUnderFire >= MORALE_RECOVER_AFTER_FIRE_SECONDS) {
                float rate = MORALE_RECOVERY_RATE * MECH_MORALE_RECOVERY_RATE_MULT;
                m.morale = Math.min(cap, m.morale + rate * dt);
            } else {
                m.morale = Math.min(cap, m.morale);
            }

            float brokenAt = MECH_MORALE_BROKEN_THRESHOLD * cap;
            float clearAt  = MECH_MORALE_CLEAR_THRESHOLD  * cap;
            if (m.moraleBroken) {
                if (m.morale > clearAt) m.moraleBroken = false;
            } else {
                if (m.morale < brokenAt) m.moraleBroken = true;
            }
            if (m.moraleBroken) brokenMechs++;
        }
        squad.moraleBroken = aliveMechs > 0 && (brokenMechs * 2 >= aliveMechs);
    }

    /**
     * Returns the friendly squad most affected by {@code shot} as a near
     * miss — first squad whose member is within {@link #NEAR_MISS_RADIUS_SQ}
     * cells of the shot's endpoint. Returns null when the shot is a self-
     * faction shot, no squad member is in range, or the shooter's faction
     * matches the candidate. One assignment per shot — a stray that grazes
     * two squad members only rattles one of them (the first found), which
     * matches the "single drain event per shot" intent.
     */
    private Squad squadHitByMiss(ShotEvent shot, Entity[] dense, UnitRosterService roster, int liveCount) {
        World world = roster.world();
        for (Squad sq : roster.getSquads()) {
            if (sq.aliveMembers <= 0) continue;
            if (sq.faction == shot.shooterFaction) continue;
            for (int i = 0; i < liveCount; i++) {
                Entity member = dense[i];
                // Dense iteration excludes released units — no isAlive() needed.
                if (!roster.squad().hasSquad(member.entityId) || roster.squad().squadId(member.entityId) != sq.id) continue;
                float dx = shot.toX - (world.cellX(member.entityId) + 0.5f);
                float dy = shot.toY - (world.cellY(member.entityId) + 0.5f);
                if (dx * dx + dy * dy <= NEAR_MISS_RADIUS_SQ) return sq;
            }
        }
        return null;
    }
}
