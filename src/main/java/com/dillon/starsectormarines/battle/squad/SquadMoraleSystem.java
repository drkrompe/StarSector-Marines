package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.sim.SquadService;
import com.dillon.starsectormarines.battle.sim.World;

import java.util.List;

/**
 * Stateless tick consumer that owns fire-team and mech morale recovery,
 * hysteresis, and the near-miss drain pass. Hit / death drain live on
 * {@code damage.DamageResolver.resolve} (they fire from the damage callback
 * site, not the tick chain); the constants live here and the resolver reads
 * them through this class.
 *
 * <p><b>The fire team is the unit that breaks.</b> Infantry cohesion is held
 * per {@link FireTeamMorale}: a round that rattles one team leaves its
 * siblings composed, and a broken team peels to cover on its own while the
 * squad keeps executing its plan with everyone else. {@link Squad#morale},
 * {@link Squad#moraleBroken}, and {@link Squad#timeSinceUnderFire} are
 * readouts aggregated from the teams, not the state anything drains.
 *
 * <p>Recovery gates on "this team hasn't been shot at recently"
 * ({@link FireTeamMorale#timeSinceUnderFire} {@code >=}
 * {@link #MORALE_RECOVER_AFTER_FIRE_SECONDS}), not on raw LoS. A broken team
 * behind imperfect cover can see — and fire opportunistically at — distant
 * enemies and still compose itself once incoming hits/near-misses lull.
 * Pre-fix, any LoS kept {@code _engagedThisTick=true} and locked the squad
 * broken indefinitely once BreakContact's picker landed them on a
 * least-exposed-but-not-truly-hidden cell. Capped by the team's
 * {@code aliveMembers / originalSize} so a team that's lost half its marines
 * can't climb back above 0.5 no matter how long it hides.
 *
 * <p>Hysteresis: {@link FireTeamMorale#broken} flips true when that team's
 * morale dips below {@link #MORALE_BROKEN_THRESHOLD} of its cap; flips false
 * again once morale climbs above the (higher) {@link #MORALE_CLEAR_THRESHOLD}.
 * The gap prevents a team hovering near the threshold from flickering between
 * peeling and fighting on every replan.
 *
 * <p>Mech squads route through {@link #updateMechSquadMorale}, which uses
 * per-chassis morale + stricter thresholds + faster recovery + an
 * armor-gone hard cap. Mechs carry no fire-team organization, so their
 * {@link Squad#moraleBroken} is aggregated from majority-broken members
 * instead.
 *
 * <p>Sibling to other {@code *System} tick consumers — single {@link #tick}
 * entry point, all dependencies constructor-injected.
 */
public final class SquadMoraleSystem {

    // ---- Squad-level morale (infantry) ----

    /** Base fire-team morale drained per non-fatal hit on one of its marines, at full strength (cap = 1.0). At lower caps the per-hit drain is scaled by {@code 1 / cap} — a heavily mauled team is more brittle per shot, so a lone survivor of a four-marine team (cap = 0.25) takes a 0.20 drain per hit and folds on the first incoming. */
    public static final float MORALE_DROP_ON_HIT   = 0.05f;
    /** Additional morale drop when a hit kills the marine. Charged to the dead marine's own fire team. Kept absolute (not cap-scaled) — it's a one-off event correlated with the cap reduction that the death itself triggers. At the four-marine team size this is proportionally heavier than it was squad-wide, which is the point: losing a man is a team-sized event, not a squad-sized one. */
    public static final float MORALE_DROP_ON_DEATH = 0.30f;
    /** Base morale recovered per sim-second while the fire team isn't being shot at ({@link FireTeamMorale#timeSinceUnderFire} {@code >=} {@link #MORALE_RECOVER_AFTER_FIRE_SECONDS}), at full strength. Effective rate is scaled by cap so a mauled team takes proportionally longer to reach its (lower) ceiling — at base 0.20 this gives a constant ~2.5s recovery to the clear threshold and ~5s to full cap across all team strengths. */
    public static final float MORALE_RECOVERY_RATE = 0.20f;
    /** Cooldown between morale-drain events on a single fire team (sim seconds). A burst of incoming bullets in one tick still counts as one drain — prevents a hail of fire from insta-breaking an intact team. At 0.2s a full team endures sustained fire for ~2.8s before breaking (5 hits/sec × 0.05/hit = 0.25/sec, 0.7 margin). Per team rather than per squad, so fire concentrated on one team no longer buys its siblings a shared immunity window. Doesn't shield mauled teams: their per-hit drain (0.05/cap) is large enough that a single hit folds them on the first cooldown window. */
    public static final float MORALE_DRAIN_COOLDOWN = 0.2f;
    /** Near-miss morale drain — applied to the fire team of a marine whose position a hostile shot's flight path passed near without doing damage. Cap-scaled like hit drain. Suppressing fire that doesn't connect still rattles a team, just less than a landed hit. */
    public static final float MORALE_DROP_ON_NEAR_MISS = 0.01f;
    /** Squared cell-distance from a shot's flight path (point-to-segment, not endpoint — see {@link #memberHitByMiss}) to a marine that counts as a "near miss." 2.25 = 1.5 cells radius. */
    public static final float NEAR_MISS_RADIUS_SQ = 2.25f;
    /** Hysteresis broken threshold, as a <em>fraction of cap</em>. A fire team flips to broken when {@code morale < MORALE_BROKEN_THRESHOLD * cap}. Scaling by cap keeps the model coherent for mauled teams: a lone survivor (cap = 0.25) breaks below 0.075 absolute morale, a fresh team (cap = 1.0) breaks below 0.30. */
    public static final float MORALE_BROKEN_THRESHOLD = 0.30f;
    /** Hysteresis clear threshold, as a <em>fraction of cap</em>. A broken fire team reverts once {@code morale > MORALE_CLEAR_THRESHOLD * cap}. Fixes the pre-scaling pathology where a solo survivor's cap (0.25) was below the absolute clear threshold (0.5) and they could never recover. */
    public static final float MORALE_CLEAR_THRESHOLD  = 0.50f;
    /** Sim seconds since the last hit/near-miss on one of a team's marines before its morale recovery resumes. Decouples recovery from raw LoS — a broken team in cover that can see distant enemies (and is firing back) but isn't actually being shot at composes itself; a pinned-down team still taking incoming stays locked. Without this gate, a fallback that lands on a still-exposed cell (BreakContact's picker minimizes exposure but doesn't guarantee a true hide) keeps {@code _engagedThisTick=true} every tick via STANCED return fire and the team never recovers. */
    public static final float MORALE_RECOVER_AFTER_FIRE_SECONDS = 2.0f;

    // ---- Mech morale (Stage 2) ----
    //
    // Per the mech-survival boundary in ai-nouns.md, mechs use a tougher
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
        // once-per-tick capture of denseArray is safe. hp/maxHp live in the
        // entity world's HEALTH columns — the mech pass reads them by id.
        long[] dense = roster.denseArray();
        int liveCount = roster.liveCount();
        SquadService squads = roster.squad();
        for (Squad squad : roster.getSquads()) {
            squad._moraleBrokenChangedThisTick = false;
            for (FireTeamMorale team : squad.fireTeamMorale()) team.aliveMembers = 0;
        }

        // Fire-team census — one walk of the dense array rather than a member
        // walk per squad, and the only place team strength is established;
        // the drain and recovery passes below both read it. originalSize
        // tracks a running peak because a marine squad assembles across
        // lifts: a team with two of its four marines on the ground must not
        // read as half-strength while the rest are still inbound.
        for (int i = 0; i < liveCount; i++) {
            long member = dense[i];
            if (!squads.hasSquad(member)) continue;
            Squad squad = roster.getSquad(squads.squadId(member));
            if (squad == null || squad.isMechSquad()) continue;
            FireTeamMorale team = squad.fireTeamMorale(squads.fireTeamIndex(member));
            team.aliveMembers++;
            if (team.aliveMembers > team.originalSize) team.originalSize = team.aliveMembers;
        }

        // Near-miss drain pass: a hostile shot that landed near a marine but
        // did not connect still rattles that marine's fire team. Same
        // cooldown gate as hits — a hail of misses cannot insta-break a team
        // either.
        List<ShotEvent> shotsThisFrame = shots.getShotsThisFrame();
        if (!shotsThisFrame.isEmpty()) {
            for (ShotEvent shot : shotsThisFrame) {
                // shot.hit = connected with the locked target; shot.struckUnit =
                // connected with ANYONE (incidental contacts included). Either
                // means this round already drains morale through the hit path
                // (DamageResolver.resolve) at its (later) flight-time impact —
                // counting it here too would double-drain via the cooldown the
                // near-miss roll arms at the earlier fire tick.
                if (shot.hit || shot.struckUnit) continue;
                long grazed = memberHitByMiss(shot, dense, liveCount);
                if (grazed == 0L) continue;
                Squad target = roster.getSquad(squads.squadId(grazed));
                if (target == null) continue;
                // Mech squads take no near-miss morale — their drain model is
                // HP-threshold only (per ai-nouns.md). A mech that did not
                // catch a round is not rattled by air.
                if (target.isMechSquad()) continue;
                FireTeamMorale team = target.fireTeamMorale(squads.fireTeamIndex(grazed));
                // Recovery gate: a near-miss always resets the under-fire
                // timer, even when the drain cooldown blocks the morale drop.
                team.timeSinceUnderFire = 0f;
                if (team.drainCooldown > 0f) continue;
                float cap = team.cap();
                float base = (cap > 0f) ? MORALE_DROP_ON_NEAR_MISS / cap : MORALE_DROP_ON_NEAR_MISS;
                team.morale = Math.max(0f, team.morale - base * shot.moraleImpact);
                team.drainCooldown = MORALE_DRAIN_COOLDOWN;
            }
        }

        for (Squad squad : roster.getSquads()) {
            if (squad.aliveMembers <= 0) continue;
            // Mech squads run a separate per-chassis morale pass — recovery,
            // hysteresis, hard cap, squad-level aggregation. Mechs are not
            // organized into fire teams, so the per-team body has nothing to
            // walk for them.
            if (squad.isMechSquad()) {
                boolean wasMoraleBroken = squad.moraleBroken;
                updateMechSquadMorale(squad, roster, dt);
                squad._moraleBrokenChangedThisTick =
                        squad.moraleBroken != wasMoraleBroken;
                continue;
            }
            updateFireTeamMorale(squad, dt);
        }
    }

    /**
     * Per-fire-team morale tick, plus the squad-level readouts derived from
     * it. For each team holding at least one live marine: advance the
     * under-fire timer, recover while nothing has shot at <em>that team</em>
     * recently, tick the drain cooldown, and apply broken/clear hysteresis
     * against the team's own cap.
     *
     * <p>Recovery gates on "this team has not been shot at recently"
     * ({@link FireTeamMorale#timeSinceUnderFire} {@code >=}
     * {@link #MORALE_RECOVER_AFTER_FIRE_SECONDS}), not on raw LoS. A broken
     * team behind imperfect cover can see — and fire opportunistically at —
     * distant enemies and still compose itself once incoming hits and
     * near-misses lull. It is capped by the team's alive/original ratio, so a
     * team that has lost half its marines cannot climb back above 0.5 no
     * matter how long it hides.
     *
     * <p>Sets {@link Squad#_moraleBrokenChangedThisTick} when <em>any</em>
     * team flips, which is what makes the squad replan and redistribute its
     * role slots around the team that just peeled — or just rejoined.
     */
    private void updateFireTeamMorale(Squad squad, float dt) {
        int aliveTotal = 0;
        int brokenAlive = 0;
        float weightedMorale = 0f;
        float earliestUnderFire = Float.MAX_VALUE / 2f;
        boolean anyTeamFlipped = false;

        for (FireTeamMorale team : squad.fireTeamMorale()) {
            if (team.aliveMembers <= 0) continue;
            boolean wasBroken = team.broken;

            // Saturate to avoid overflow on long quiet stretches — the
            // threshold check only cares about >= the recovery delay.
            if (team.timeSinceUnderFire < 1e9f) team.timeSinceUnderFire += dt;

            float cap = team.cap();
            if (team.timeSinceUnderFire >= MORALE_RECOVER_AFTER_FIRE_SECONDS) {
                // Recovery rate scales with cap so a mauled team recovers
                // proportionally slower toward its lower ceiling — constant
                // time-to-clear across team strengths.
                team.morale = Math.min(cap, team.morale + MORALE_RECOVERY_RATE * cap * dt);
            } else {
                // Under fire — no recovery; also re-clamp in case the cap
                // dropped (a team member died this tick) below current morale.
                team.morale = Math.min(cap, team.morale);
            }

            if (team.drainCooldown > 0f) {
                team.drainCooldown = Math.max(0f, team.drainCooldown - dt);
            }

            // Thresholds scale with cap so the model stays coherent for a
            // mauled team: the lone survivor of a four-marine team (cap 0.25)
            // breaks below 0.075 and clears above 0.125, rather than sitting
            // permanently below an absolute clear line it could never reach.
            float brokenAt = MORALE_BROKEN_THRESHOLD * cap;
            float clearAt  = MORALE_CLEAR_THRESHOLD  * cap;
            if (team.broken) {
                if (team.morale > clearAt) team.broken = false;
            } else {
                if (team.morale < brokenAt) team.broken = true;
            }
            if (team.broken != wasBroken) anyTeamFlipped = true;

            aliveTotal += team.aliveMembers;
            weightedMorale += team.morale * team.aliveMembers;
            if (team.broken) brokenAlive += team.aliveMembers;
            earliestUnderFire = Math.min(earliestUnderFire, team.timeSinceUnderFire);
        }

        // The squad reads as broken only when every live team has broken.
        // Deliberately stricter than the mech path's majority rule: the peel
        // is per fire team, so the squad-level flag exists for the consumers
        // that ask whether the squad as a whole is finished — the mission-goal
        // carve-outs, the command tier, chatter. A squad with one wobbling
        // team is not finished; it is a squad with one team in cover.
        boolean wasSquadBroken = squad.moraleBroken;
        squad.morale = aliveTotal > 0 ? weightedMorale / aliveTotal : 1f;
        squad.moraleBroken = aliveTotal > 0 && brokenAlive == aliveTotal;
        squad.timeSinceUnderFire = aliveTotal > 0 ? earliestUnderFire : Float.MAX_VALUE / 2f;
        squad._moraleBrokenChangedThisTick =
                anyTeamFlipped || squad.moraleBroken != wasSquadBroken;
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
     * <p>Mechs have no fire-team organization to peel, so unlike infantry
     * the majority aggregate here is load-bearing: it is what
     * {@code MechSurviveContact} reads to pull the whole mech element out.
     */
    private void updateMechSquadMorale(Squad squad,
                                       UnitRosterService roster, float dt) {
        World world = roster.world();
        int aliveMechs = 0;
        int brokenMechs = 0;
        long[] members = roster.squadMemberArray(squad.id);
        for (int i = 0, n = roster.squadMemberCount(squad.id); i < n; i++) {
            long u = members[i];
            // Capability-as-presence: a mech is an entity with a loadout
            // component (was the nullable u.mech field). Null-safe by-id read off
            // the MECH_LOADOUT world component.
            MechLoadoutComponent m = world.mechLoadout(u);
            if (m == null) continue;
            aliveMechs++;
            if (m.timeSinceUnderFire < 1e9f) m.timeSinceUnderFire += dt;

            // hp lives in the entity world's HEALTH columns — by-id reads via
            // the world facade. Mechs per squad are few, so the per-member
            // probe is cold.
            float uMaxHp = world.maxHp(u);
            float cap = (uMaxHp > 0f
                    && world.hp(u) < MECH_MORALE_ARMOR_GONE_HP_FRAC * uMaxHp)
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
     * Returns the squadded member {@code shot} passed closest to as a near
     * miss, or {@code 0L} when none is in range. Uses point-to-segment
     * distance from {@code shot.fromX/fromY} to {@code shot.toX/toY} against
     * {@link #NEAR_MISS_RADIUS_SQ} — a missed ballistic round's endpoint
     * lands far downrange under the S1 resolver, so distance-to-endpoint
     * alone under-triggers, while the path it actually flew still passes
     * close by. Self-faction shots never count.
     *
     * <p>Returns the <em>closest</em> member rather than the first one found,
     * so a round rattles the fire team it actually flew past; a stray
     * threading two teams rattles the nearer. Walking the dense array once
     * rather than once per squad also drops the old squads x liveCount scan
     * to a single pass.
     */
    private long memberHitByMiss(ShotEvent shot, long[] dense, int liveCount) {
        World world = roster.world();
        SquadService squads = roster.squad();
        long best = 0L;
        float bestDistSq = NEAR_MISS_RADIUS_SQ;
        for (int i = 0; i < liveCount; i++) {
            long member = dense[i];
            // Dense iteration excludes released units — no isAlive() needed.
            if (!squads.hasSquad(member)) continue;
            Squad sq = roster.getSquad(squads.squadId(member));
            if (sq == null || sq.aliveMembers <= 0) continue;
            if (sq.faction == shot.shooterFaction) continue;
            float distSq = distanceToSegmentSq(world.x(member), world.y(member),
                    shot.fromX, shot.fromY, shot.toX, shot.toY);
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                best = member;
            }
        }
        return best;
    }

    /** Squared Euclidean distance from ({@code px}, {@code py}) to the closest point on segment ({@code x0}, {@code y0})–({@code x1}, {@code y1}), clamped to the segment's endpoints. */
    private static float distanceToSegmentSq(float px, float py, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len2 = dx * dx + dy * dy;
        float t = len2 > 0f ? ((px - x0) * dx + (py - y0) * dy) / len2 : 0f;
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        float ex = px - (x0 + dx * t);
        float ey = py - (y0 + dy * t);
        return ex * ex + ey * ey;
    }
}
