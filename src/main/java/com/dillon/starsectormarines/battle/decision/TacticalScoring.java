package com.dillon.starsectormarines.battle.decision;
import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.turret.TurretAim;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.BeliefSource;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.ContactInitiative;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.ForceBalance;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Motion;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Posture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Sector;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import com.dillon.starsectormarines.battle.unit.UnitDestinationSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.sim.VisionService;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure scoring helpers used by behaviors to pick targets and positions.
 * Stateless; each call takes the sim plus the acting unit and computes a
 * fresh answer. Pulled out of {@code BattleSimulation} so behavior code
 * stays thin and the math is reusable / testable in isolation.
 *
 * <p>Conventions:
 * <ul>
 *   <li><b>Cost-based</b> — lower score is better. Penalties add; bonuses subtract.</li>
 *   <li><b>Squad-aware</b> — {@link #findBestTarget} adds a heavier crowding
 *       penalty for squadmates already aiming at a target than for arbitrary
 *       allies, so a 4-man squad naturally spreads its fire across the front
 *       rather than collapsing onto a single enemy.</li>
 *   <li><b>Cover-aware</b> — firing-position and fall-back scoring read
 *       per-cell cover from the grid, biasing units toward wall-adjacent
 *       cells they can peek from.</li>
 * </ul>
 */
public final class TacticalScoring {

    /** Radius around the squad centroid represented by its local contact picture. */
    public static final float CONTACT_PICTURE_RADIUS = 36f;
    /**
     * Time after losing direct LOS that an advancing squad may still plant on
     * a doctrine-only HOLD. The belief remains useful for facing and target
     * guidance after this window, but cannot immobilize the objective advance
     * until the full fourteen-second belief lifetime expires.
     */
    public static final float HOLD_AFTER_LOS_SECONDS = 1f;
    public static final int HOLD_AFTER_LOS_TICKS =
            Math.round(HOLD_AFTER_LOS_SECONDS / BattleSimulation.TICK_DT);
    private static final float SECTOR_FRONT_COS = 0.70710677f;
    private static final float MOTION_RADIAL_THRESHOLD = 0.25f;

    private final NavigationService nav;
    private final NavigationGrid grid;
    private final UnitRosterService roster;
    private final UnitSpatialIndex unitIndex;
    private final UnitDestinationSpatialIndex destIndex;
    private final ZoneGraph zoneGraph;
    private final byte[] occupancyMap;
    private final AttackerIndexService attackerIndex;
    private final ShotService shots;
    private final DoodadService doodads;

    public TacticalScoring(NavigationService nav, UnitRosterService roster,
                           AttackerIndexService attackerIndex, ShotService shots,
                           DoodadService doodads) {
        this.nav = nav;
        this.grid = nav.getGrid();
        this.roster = roster;
        this.unitIndex = nav.getUnitIndex();
        this.destIndex = nav.getDestIndex();
        this.zoneGraph = nav.getZoneGraph();
        this.occupancyMap = nav.getOccupancyMap();
        this.attackerIndex = attackerIndex;
        this.shots = shots;
        this.doodads = doodads;
    }

    /** Per-engaging-ally penalty added to target selection — pushes the squad to spread fire instead of dogpiling. */
    public static final float TARGET_CROWDING_COST = 6f;
    /** Extra penalty when the engager is a squadmate. Real fireteams cover sectors, not the same enemy. */
    public static final float TARGET_SQUADMATE_EXTRA_COST = 6f;

    /**
     * Per-ally-on-cell penalty added when picking a firing position. Pushes
     * units off cells already claimed by squadmates. Tuned against the
     * post-Slice-3 directional cover scale: a single occupant should turn a
     * max-cover cell ({@code -3*FIRING_COVER_BONUS = -9}) into net positive
     * vs. an empty cover-1 cell ({@code -3}), so the second marine takes the
     * next-best cover instead of doubling up.
     */
    public static final float FIRING_OCCUPANCY_COST = 8f;

    /**
     * AoE-survival spread cost — penalty for each same-faction ally whose
     * current cell <em>or</em> path destination sits within
     * {@link #FIRING_AOE_SPREAD_RADIUS} cells of the candidate. Prevents the
     * "everyone bunches on the one cover doodad in the field, then dies to
     * one rocket" failure mode. Pairs with {@link #FIRING_OCCUPANCY_COST}:
     * occupancy handles literal cell-sharing, spread handles same-AoE-radius
     * clustering.
     */
    public static final float FIRING_AOE_SPREAD_COST = 4f;
    /**
     * Cell-radius for the AoE-spread penalty. {@code 2} matches typical AoE
     * weapon radii (marine rocket 1.5, mech SRM 1.3, mech LRM 2.0) — a
     * marine outside this radius from squadmates survives the rocket that
     * kills the cluster.
     */
    public static final int FIRING_AOE_SPREAD_RADIUS = 2;
    /** Minimum cell-distance from target when picking a firing position. Avoids picking the target's own cell. */
    public static final float FIRING_MIN_DISTANCE = 0.7f;
    /** Per-cover-level bonus subtracted from firing-position score. Pushes units to peek from corners and wall edges. */
    public static final float FIRING_COVER_BONUS = 3f;
    /** Per-cover-level bonus from doodad cover (crates, shelves) on the candidate cell. Lower weight than wall cover because doodads don't fully break LOS — they're concealment, not full intervening geometry. */
    public static final float FIRING_DOODAD_COVER_BONUS = 1.5f;

    /**
     * Cell-radius around a target searched by
     * {@link #computeVantagePoints} when populating the vantage-point cache —
     * the stage-2 fallback used by {@link #findFiringPosition} when no
     * in-range LOS-bearing firing cell exists. Sized at 20 so a marine
     * (attackRange ~5) can walk up to 4× its weapon range to find a vantage,
     * which covers the typical "turret around the corner" geometry without
     * letting the picker propose a marathon trek across the map. Rocketeer
     * approaches (attackRange ~12) get less headroom but still ~1.6×.
     */
    public static final int MAX_VANTAGE_SEARCH_RADIUS = 20;
    /**
     * Cap on pathfind attempts when picking among vantage candidates ordered
     * by Euclidean distance from self. With cached vantages, the first attempt
     * usually succeeds (the closest cell is also the closest path); the cap
     * exists to bound the worst case where multiple closer Euclidean
     * candidates lie on the unreachable side of a wall, so the picker has to
     * skip past them to find one on the unit's side.
     */
    public static final int MAX_VANTAGE_PATHFIND_ATTEMPTS = 6;
    /** Radius (cells) over which {@link #findBestTarget} counts neighboring enemies to penalize cluster targets. */
    public static final int THREAT_DENSITY_RADIUS = 4;
    /** Per-neighbor-enemy penalty added to a target's score. Pursuing one fleer into 3 squadmates costs roughly the same as walking ~20 extra cells. */
    public static final float TARGET_THREAT_DENSITY_COST = 5f;
    /** Two nearby hostile combatants turn a candidate into a formation rather than an isolated pursuit target. */
    public static final int HIGH_THREAT_DENSITY_COUNT = 2;
    /** Maximum cells an exposed engagement hold may shift to claim nearby cover. */
    public static final int ENGAGEMENT_HOLD_COVER_RADIUS = 3;

    /** How far ahead of the squad centroid the advance-leash threat read looks. Longer routes are clipped to this local window. */
    public static final float ADVANCE_THREAT_LOOKAHEAD = 36f;
    /** Contacts inside this distance of the advance axis contribute their full force to the threat score. */
    public static final float ADVANCE_THREAT_ROUTE_INNER = 4f;
    /** Contacts at or beyond this distance from the advance axis do not pull the squad off mission. */
    public static final float ADVANCE_THREAT_ROUTE_OUTER = 12f;
    /** A contact whose active path ends farther from the squad by this margin is treated as retreating. */
    public static final float ADVANCE_THREAT_RETREAT_MARGIN = 2f;
    /** Force contribution retained by a retreating contact — enough to avoid treating it as vanished, too little to stop a healthy fireteam alone. */
    public static final float ADVANCE_THREAT_RETREAT_MULT = 0.2f;
    /** Enemy force equal to half the nearby friendly force saturates the advance threat score at 1. */
    public static final float ADVANCE_THREAT_PARITY_FRACTION = 0.5f;
    /** Radius around a bound's forward stride point searched for covered firing cells. */
    public static final int BOUNDING_POSITION_SEARCH_RADIUS = 4;
    /** Minimum objective-axis progress a bounder must make; rejects oscillation and same-line role swaps. */
    public static final float BOUNDING_MIN_FORWARD_PROGRESS = 2f;

    /**
     * Penalty added to a no-LoS target's score when {@code allowNoLos} is set
     * on the {@link #findBestTarget} call (indirect-fire callers — artillery
     * turrets). Sized at ~10 cells so a visible target wins over a blind target
     * at the same distance, but a meaningfully closer blind target still wins
     * over a far visible one. Without this, an artillery battery would acquire
     * targets blindly regardless of whether a visible candidate was available.
     */
    public static final float TARGET_NO_LOS_COST = 10f;

    /**
     * Penalty added when a candidate target is in a different navigation zone
     * from the shooter — Slice 3.5 soft gate on cross-zone target selection.
     * Equivalent to ~8 cells of extra walking distance, so a close in-zone
     * enemy beats a slightly-further across-zone enemy without preventing
     * a meaningfully closer across-zone threat from winning. Pairs with
     * {@link com.dillon.starsectormarines.battle.infantry.BreachToEngage}:
     * once there's no acceptable in-zone target, the cross-zone enemy wins
     * by default and BreachToEngage's relevance flips on.
     */
    public static final float TARGET_ZONE_MISMATCH_COST = 8f;

    /**
     * Multiplier on the weapon-target affinity term in {@link #findBestTarget}.
     * A marine's score for a hardened target gets a weapon-penetration affinity
     * added — well-suited weapons (rockets, mult 3.5) earn a ~20-point bonus
     * toward the hardened target, poorly-suited weapons (rifles, mult 0.3)
     * eat a ~5-point penalty. With one visible target in LOS this is a no-op
     * (one candidate wins regardless); with multiple, it tilts the AT marine
     * toward the mech and the SMG marine toward the infantry.
     */
    public static final float WEAPON_AFFINITY_WEIGHT = 8f;
    /** Caps hardened-target preference so suitability cannot overwhelm engagement range. */
    public static final float MAX_WEAPON_AFFINITY_RELATIVE = 3.5f;

    /**
     * Seconds of unit travel that govern the fall-back candidate scan radius.
     * Multiplied by {@code world.moveSpeed(id)} (cells/sec) and clamped to
     * [{@link #FALLBACK_SCAN_RANGE_MIN}, {@link #FALLBACK_SCAN_RANGE_MAX}].
     * Fast units sprint farther for cover; slow units (mechs) stay short.
     * A baseline 2.0-speed marine lands at 10, slightly farther than the
     * legacy uniform 8.
     */
    public static final float FALLBACK_SCAN_SECONDS = 5.0f;
    /** Floor so slow units still get a usable search radius. */
    public static final int   FALLBACK_SCAN_RANGE_MIN = 6;
    /** Cap so future fast units don't blow up the O(R²) candidate scan. */
    public static final int   FALLBACK_SCAN_RANGE_MAX = 16;
    /** Per-ally-on-cell penalty in fall-back scoring. */
    public static final float FALLBACK_OCCUPANCY_COST = 4f;
    /**
     * Per-cover-level bonus on grid cover (walls/rubble) when scoring a fall-back
     * cell. Read against the dominant threat facing — only cover that actually
     * blocks the incoming firing lane counts. Mirrors the firing-picker split at
     * {@link #FIRING_COVER_BONUS}.
     */
    public static final float FALLBACK_GRID_COVER_BONUS   = 2.5f;
    /**
     * Per-cover-level bonus on doodad cover (crates/debris) when scoring a fall-back
     * cell. Lower than grid because doodads conceal but don't block LoS.
     */
    public static final float FALLBACK_DOODAD_COVER_BONUS = 1.5f;
    /** Bonus subtracted from fall-back score per net ally in the candidate cell's zone. Pulls retreating units toward where their squad lives rather than the nearest blind corner. */
    public static final float FALLBACK_FRIENDLY_ZONE_BONUS = 1.5f;
    /**
     * Per-enemy-with-LoS penalty added to fall-back score. Sized large enough
     * that any hidden cell (exposure 0) outranks any exposed cell (exposure
     * ≥ 1) across the full possible swing of the other score terms — so the
     * picker still prefers a hide when one exists, but degrades gracefully to
     * "least-exposed reachable cell" when no hide is available (open-field
     * fire). Without this, the picker fell through to the unit's own cell and
     * the unit visibly did nothing under sustained fire.
     */
    public static final float FALLBACK_EXPOSURE_PENALTY = 100f;

    /**
     * Mild bonus per cell of additional distance from the threat centroid
     * versus the unit's current cell. Pulls the picker toward away-side hides
     * when multiple are reachable; only meaningful as a tiebreaker because
     * the dominant signal is {@link #FALLBACK_TOWARD_THREAT_PENALTY}.
     */
    public static final float FALLBACK_AWAY_FROM_THREAT_BONUS = 2f;

    /**
     * Heavy per-cell penalty when a candidate is <em>closer</em> to the
     * threat centroid than the unit's current cell. Asymmetric on purpose:
     * we want a strong refusal to charge through enemies (the user-visible
     * "broken marines run through 100 enemies into a wall pocket" failure
     * mode) without forcing units into the map edge when a small forward
     * adjustment would be tactically fine.
     *
     * <p>Sized so even a deeply-hidden cell (exposure 0) ten cells into the
     * threat's half-space (penalty +300) loses to a moderately-exposed cell
     * (3 enemies × 100 = +300) on the away side. The previous symmetric ~3
     * weight was nowhere near the exposure floor and got overwhelmed by any
     * wall-shadowed cell behind the enemy line.
     */
    public static final float FALLBACK_TOWARD_THREAT_PENALTY = 30f;

    /**
     * Safety budget for the spatial pre-gather in {@link #findFallbackPosition}.
     * Any enemy more than {@code scanRange + this} cells from the unit can't
     * possibly hold LoS to a candidate cell — their LoS would have to be
     * longer than their attack range. Sized as a conservative max over all
     * unit-type ranges (mech LRMs cap at ~40, mech main guns ~30, infantry
     * ~24, turrets vary up to 36); 60 absorbs all of them with margin. A
     * future unit with longer effective range would need this bumped — or
     * better, swap to a per-unit max queried off {@link UnitType}.
     */
    public static final float MAX_PLAUSIBLE_ATTACK_RANGE = 60f;
    /** Per-worker scratch for local force tallies published by the serial contact-picture pass. */
    private static final ThreadLocal<LongBucket> LOCAL_FORCE_CANDIDATES =
            ThreadLocal.withInitial(LongBucket::new);
    /** Per-worker scratch for fallback visibility checks; may run inside parallel unit updates. */
    private static final ThreadLocal<LongBucket> HIDDEN_ENEMY_CANDIDATES =
            ThreadLocal.withInitial(LongBucket::new);


    /**
     * Picks the lowest-scored enemy where score = cell-distance + a per-engager
     * crowding penalty (heavier for squadmates than for general allies). Prefers
     * visible targets; falls back to nearest of any LOS so the unit pathfinds
     * toward them and visibility eventually opens.
     */
    public long findBestTarget(long self) {
        World world = roster.world();
        return findBestTarget(world.x(self), world.y(self),
                roster.identity().faction(self),
                roster.squad().hasSquad(self) ? roster.squad().squadId(self) : Squad.NO_SQUAD,
                self, roster.vision().airLosRadius(self));
    }

    /**
     * Stickiness gate for {@code self.target}: keeps the current target only
     * while it's still shootable from {@code self}'s current cell (alive,
     * within {@code world.attackRange(id)}, and with line of sight). Anything
     * outside that — dead, out of range, behind cover — drops the cached pick
     * and re-runs {@link #findBestTarget}, so a closer visible enemy that
     * stepped into LoS while we were locked onto someone now-unshootable wins
     * the next tick. Without this, every mech combat action (overwatch,
     * parity engage, the legacy behavior loop) hyper-fixates: their gate was
     * only "is the cached target alive?", which let a target hide behind a
     * wall forever while the mech ignored opportunities in its own kill lane.
     *
     * <p>Range check uses {@code self.getAttackRange()} because for mechs it's set
     * to the LRM range (40 cells, matching {@link UnitType#HEAVY_MECH}) — the
     * longest weapon's reach, which is the right "could this mech ever shoot
     * this target from here" bound. Indirect-fire (no LoS) still works on the
     * returned target because the per-weapon fire gate handles that downstream;
     * we only ask "is the current pick clearly the wrong choice right now?".
     */
    public long refreshTargetIfNotShootable(long self) {
        World world = roster.world();
        int sx = world.cellX(self);
        int sy = world.cellY(self);
        long cur = world.targetId(self);
        if (roster.isLive(cur)) {
            int cx = world.cellX(cur);
            int cy = world.cellY(cur);
            if (cellDistance(world.x(self), world.y(self), world.x(cur), world.y(cur)) <= world.attackRange(self)
                    && grid.hasLineOfSight(sx, sy, cx, cy)) {
                return cur;
            }
        }
        return findBestTarget(self);
    }

    /**
     * Primitive-args overload — used by callers that aren't a {@code Entity}
     * themselves (today: shuttle-mounted turrets, which live as data on an air
     * craft's {@code AIR_TURRETS} component rather than as grid entities). The
     * selection logic is identical; pass {@link Squad#NO_SQUAD}
     * for {@code squadId} and {@code null} for {@code excludeFromCrowding}
     * when the caller doesn't squad up and isn't itself in the unit list.
     * {@code selfX/selfY} are a TRUE continuous position (a cell's center is
     * {@code cx + 0.5f}), not a cell index.
     */
    public long findBestTarget(float selfX, float selfY, Faction selfFaction,
                               int selfSquadId, long excludeFromCrowding) {
        return findBestTarget(selfX, selfY, selfFaction, selfSquadId, excludeFromCrowding,
                0f);
    }

    /**
     * Air-LoS aware overload — when {@code shooterAirRadius > 0}, walls within
     * that many cells of the shooter's position are treated as transparent
     * (shuttle-mounted turrets hovering above a building's footprint). When
     * the candidate target has its own {@code airLosRadius} > 0 (drones),
     * walls within that radius of the target are also transparent — making
     * the LoS rule symmetric so a marine standing under a drone can fire up
     * at it through the same close-wall band that the drone fires down through.
     *
     * <p>Score per visible candidate:
     * <pre>
     *   distance + crowding + threat_density_at_target_cell + weapon_affinity
     * </pre>
     *
     * <p><b>Threat density</b> — count of <em>other</em> enemies of the same
     * faction within {@link #THREAT_DENSITY_RADIUS} cells of the candidate,
     * weighted by {@link #TARGET_THREAT_DENSITY_COST}. Story I: a wounded
     * fleer running into 3 of their squadmates is no longer the lowest-cost
     * target — the cluster makes pursuing them prohibitively expensive, and
     * the picker drops them in favor of an isolated enemy or no-target.
     *
     * <p><b>Weapon affinity</b> — when {@code excludeFromCrowding} is a
     * {@code Entity} (the marine's own callers pass {@code self} here), hardened
     * targets (turrets + heavy mechs) get a per-marine score adjustment based
     * on primary and secondary penetration.
     * Rocketeers prefer mechs; rifle/SMG marines prefer infantry. With one
     * visible target the term doesn't matter (single candidate wins); with
     * multiple, it tilts the choice without overriding distance for nearby
     * threats.
     *
     * <p>The any-distance fallback bucket still exists so the unit pathfinds
     * toward the nearest visible-eventually enemy when LOS is fully broken.
     * When no enemy combatants remain anywhere the method returns null —
     * caller treats null as "hold position, no target."
     */
    public long findBestTarget(float selfX, float selfY, Faction selfFaction,
                               int selfSquadId, long excludeFromCrowding,
                               float shooterAirRadius) {
        return findBestTarget(selfX, selfY, selfFaction, selfSquadId,
                excludeFromCrowding, shooterAirRadius, /*allowNoLos*/ false);
    }

    /**
     * Indirect-fire-aware overload — when {@code allowNoLos} is {@code true},
     * non-visible candidates are scored too (with {@link #TARGET_NO_LOS_COST}
     * added), so an artillery battery can pick a blind target when no visible
     * one exists or when the blind one is significantly closer. When
     * {@code false}, the existing behavior runs: only visible candidates are
     * scored; the any-distance bucket still tracks the nearest non-visible
     * enemy for the path-toward-them fallback.
     */
    public long findBestTarget(float selfX, float selfY, Faction selfFaction,
                               int selfSquadId, long excludeFromCrowding,
                               float shooterAirRadius, boolean allowNoLos) {
        return findBestTargetWithinRange(selfX, selfY, selfFaction, selfSquadId,
                excludeFromCrowding, shooterAirRadius, allowNoLos,
                0f, Float.POSITIVE_INFINITY);
    }

    /**
     * Mount acquisition variant that scores only targets the mount can engage.
     * Static weapons cannot path toward an otherwise attractive candidate, so
     * allowing out-of-range actors into their preference pass can starve a
     * valid in-range target indefinitely.
     */
    public long findBestTargetWithinRange(float selfX, float selfY, Faction selfFaction,
                                          int selfSquadId, long excludeFromCrowding,
                                          float shooterAirRadius, boolean allowNoLos,
                                          float minRange, float maxRange) {
        long _profT0 = System.nanoTime();
        try {
            return findBestTargetImpl(selfX, selfY, selfFaction, selfSquadId,
                    excludeFromCrowding, shooterAirRadius, allowNoLos,
                    minRange, maxRange);
        } finally {
            TickInnerProfile p = TickInnerProfile.current();
            if (p != null) p.record(TickInnerProfile.Bucket.TARGET_PICK, System.nanoTime() - _profT0);
        }
    }

    private long findBestTargetImpl(float selfX, float selfY, Faction selfFaction,
                                    int selfSquadId, long excludeFromCrowding,
                                    float shooterAirRadius, boolean allowNoLos,
                                    float minRange, float maxRange) {
        // SoA consumer: dense iteration over [0, liveCount()) implicitly
        // excludes released slots (no isAlive() filter inside the loop).

        World world = roster.world();
        VisionService vision = roster.vision();
        long[] dense = roster.denseArray();
        int liveCount = roster.liveCount();

        // Grid cell of the shooter, for the LoS + zone lookups only — the
        // distance scoring below uses the true position.
        int selfCellX = (int) Math.floor(selfX);
        int selfCellY = (int) Math.floor(selfY);

        long best = 0L;
        float bestScore = Float.MAX_VALUE;
        long bestAny = 0L;
        float bestAnyDist = Float.MAX_VALUE;

        for (int i = 0; i < liveCount; i++) {
            long other = dense[i];
            if (roster.identity().faction(other) == selfFaction) continue;
            // Civilians and other non-combatants don't draw fire — they're
            // bystanders. A separate "rules of engagement" toggle could relax
            // this for pirate atrocity scenarios later.
            if (!roster.identity().type(other).combatant) continue;

            int ox = world.cellX(other);
            int oy = world.cellY(other);
            float d = cellDistance(selfX, selfY, world.x(other), world.y(other));
            if (d < minRange || d > maxRange) continue;
            if (d < bestAnyDist) {
                bestAnyDist = d;
                bestAny = other;
            }
            boolean visible = canSeePair(grid, selfCellX, selfCellY, ox, oy,
                    shooterAirRadius, vision.airLosRadius(other));
            if (!visible && !allowNoLos) continue;
            float crowding = scoreCrowding(selfFaction, selfSquadId, other, excludeFromCrowding);
            float density = scoreThreatDensity(other, world.x(other), world.y(other), selfFaction);
            float affinity = scoreWeaponAffinity(excludeFromCrowding, other);
            float zoneMismatch = scoreZoneMismatch(selfCellX, selfCellY, ox, oy);
            float score = d + crowding + density + affinity + zoneMismatch;
            if (!visible) score += TARGET_NO_LOS_COST;
            if (score < bestScore) {
                bestScore = score;
                best = other;
            }
        }
        return best != 0L ? best : bestAny;
    }

    /**
     * Symmetric air-LoS check: returns true when ({@code sx, sy}) can see
     * ({@code tx, ty}) on the grid, with walls within {@code shooterAirR}
     * cells of the shooter and walls within {@code targetAirR} cells of the
     * target treated as transparent. Fast-paths to plain
     * {@link NavigationGrid#hasLineOfSight} when both radii are zero, so the
     * 99% of ground-vs-ground LoS checks stay on the cheaper path.
     */
    public static boolean canSeePair(NavigationGrid grid, int sx, int sy, int tx, int ty,
                                     float shooterAirR, float targetAirR) {
        if (shooterAirR <= 0f && targetAirR <= 0f) {
            return grid.hasLineOfSight(sx, sy, tx, ty);
        }
        return TurretAim.airLosVisible(grid, sx, sy, tx, ty, shooterAirR, targetAirR);
    }

    /**
     * Score adjustment for how well {@code self}'s loadout matches
     * {@code target}. Hardened targets (turrets + heavy mechs) get the per-
     * weapon penetration treated as a bounded affinity. This remains a
     * temporary type-based preference until D2 moves the decision onto a full
     * expected-damage comparison over current armor state.
     *
     * <p>{@code self} is {@code 0L} for anonymous mounts. Static turrets carry
     * a real entity id, so their transitional catalog penetration participates
     * in the same bounded preference until D2 replaces this type-based seam
     * with live armor-state evaluation.
     */
    private float scoreWeaponAffinity(long self, long target) {
        if (self == 0L) return 0f;
        if (!isHardened(roster.identity().type(target))) return 0f;
        World world = roster.world();
        // self is the scoring combatant (non-combatant callers pass 0L above), so its
        // COMBAT primary-weapon read is safe by id; null = no per-weapon profile.
        WeaponDef primaryWeapon = roster.combat().primaryWeaponDef(self);
        float primary;
        if (primaryWeapon != null) {
            primary = primaryWeapon.penetration;
        } else if (roster.identity().type(self).isTurret()) {
            primary = roster.turretState().kind(self).targetAffinityPenetration();
        } else {
            primary = 0f;
        }
        MarineSecondary special = world.hasSecondaryWeapon(self)
                ? world.secondaryWeapon(self) : null;
        float secondary = special != null && special.isDirectFireWeapon()
                && world.secondaryAmmo(self) > 0 ? special.penetration() : 0f;
        float relativeToServiceRifle = Math.min(
                MAX_WEAPON_AFFINITY_RELATIVE, Math.max(primary, secondary) / 5f);
        return WEAPON_AFFINITY_WEIGHT * (1f - relativeToServiceRifle);
    }

    /**
     * Returns {@link #TARGET_ZONE_MISMATCH_COST} when the candidate's cell is
     * in a different navigation zone from the shooter's cell, 0 otherwise.
     * Zones with id {@code < 0} (out-of-bounds / unwalkable, shouldn't happen
     * for live combatants) read as "no zone" — treated as matching so weird
     * edge cases don't accidentally amplify the bias.
     */
    private float scoreZoneMismatch(int selfCellX, int selfCellY, int candCellX, int candCellY) {
        int selfZone = zoneGraph.zoneIdAt(selfCellX, selfCellY);
        int targetZone = zoneGraph.zoneIdAt(candCellX, candCellY);
        if (selfZone < 0 || targetZone < 0) return 0f;
        return selfZone == targetZone ? 0f : TARGET_ZONE_MISMATCH_COST;
    }

    /**
     * Hardened target classification — counts static emplacements
     * (turrets — {@code UnitType.isTurret()}; drone hubs — {@code UnitType.isDroneHub()})
     * and heavy mechs. Anything else (infantry archetypes, aliens, militia) is soft.
     * Drives the weapon-affinity bias in {@link #findBestTarget} (rocketeers
     * prefer hardened) and the rocket-eligibility gates in
     * {@link com.dillon.starsectormarines.battle.infantry.InfantryUnitPrep#tryOpportunityRocket}
     * and {@link com.dillon.starsectormarines.battle.infantry.EngagePosture} —
     * marines burn a rocket on the legacy hardened target classes.
     */
    public static boolean isHardened(UnitType type) {
        if (type.isTurret()) return true;
        if (type.isDroneHub()) return true;
        return type == UnitType.HEAVY_MECH;
    }

    /**
     * True when {@code shooter} carries a loaded rocket and {@code target} is
     * a hardened class (a turret, a drone hub, heavy mech) —
     * the pairings where dedicated penetration is useful. Centralizes the
     * check used by {@link #effectiveAttackRange}.
     */
    public boolean canSpecialTarget(long shooter, long target) {
        World world = roster.world();
        if (!isHardened(roster.identity().type(target))
                || !world.hasSecondaryWeapon(shooter)) return false;
        MarineSecondary special = world.secondaryWeapon(shooter);
        return special.isDirectFireWeapon()
                && world.secondaryAmmo(shooter) > 0;
    }

    /**
     * Effective engagement range for {@code shooter} against {@code target} —
     * primary range, unless the shooter can rocket the target (hardened class
     * + loaded tube), in which case the rocket's longer range wins. Used by
     * {@link com.dillon.starsectormarines.battle.infantry.EngagePosture}'s
     * act-here gate and the firing-position picker so a rocketeer doesn't have
     * to close to rifle range before firing.
     */
    public float effectiveAttackRange(long shooter, long target, float shooterAttackRange) {
        if (canSpecialTarget(shooter, target)) {
            World world = roster.world();
            return Math.max(shooterAttackRange, world.secondaryWeapon(shooter).range());
        }
        return shooterAttackRange;
    }

    /**
     * Squad-coordination gate for committing a new rocket on {@code target}.
     * Returns {@code false} when squadmates already have enough rocket damage
     * locked in (mid-aim + inflight from the same faction) to flatten the
     * target — prevents the volley failure mode where a 4-man squad all fires
     * on one Vulcan (or one mech, or one drone hub) in a single tick.
     *
     * <p>Caller is responsible for {@code target} being a sensible rocket
     * target ({@link #isHardened}) — the gate doesn't re-check eligibility,
     * just the damage projection. Pass any {@code Entity}; the per-target HP
     * read ({@code world.hp}, the world HEALTH column) works on the
     * full unit hierarchy — regular units and turrets alike.
     *
     * <p>{@code shooter} is excluded from the projection so the same marine
     * re-checking on a later tick (after his own cooldown) isn't blocked by
     * his own prior contribution.
     */
    public boolean shouldCommitSpecial(long shooter, long target) {
        World world = roster.world();
        if (!world.hasSecondaryWeapon(shooter) || world.secondaryAmmo(shooter) <= 0) return false;
        if (!world.secondaryWeapon(shooter).isDirectFireWeapon()) return false;
        if (target == 0L || !roster.isAliveById(target)) return false;
        float remainingDurability = world.hp(target)
                + (world.hasArmor(target) ? world.armor(target) : 0f);
        return projectedSpecialDamageOnTarget(shooter, target) < remainingDurability;
    }

    /** Compatibility name retained for focused rocket tests and older callers. */
    public boolean shouldCommitRocket(long shooter, long target) {
        return shouldCommitSpecial(shooter, target);
    }

    /**
     * Sums committed rocket damage already inbound to {@code target} from
     * sources other than {@code shooter}: squadmates currently in the rocket
     * aim window, plus inflight {@link Projectile}s from the same faction
     * whose endpoint sits within AoE of the target cell. Used by
     * {@link #shouldCommitRocket}.
     *
     * <p>Iterates {@code shots.snapshotActiveProjectiles()} for the inflight
     * half. Every HE rocket-class weapon in the codebase rides the Projectile
     * entity model (locust + grenade-launcher turrets, marine handheld rocket,
     * mech SRM_POD + LRM_ARTILLERY) — each in-flight round is a real entity
     * owning its own {@link PendingDetonation} arrival payload. Sibling squads
     * + cross-class fires from the same faction (a same-faction mech and a
     * marine rocketeer both targeting one turret) are all counted here via
     * the faction match. The squad-aim-window pre-fire half above remains
     * squadId-gated so a sibling squad's pre-launch aim isn't double-counted.
     */
    private float projectedSpecialDamageOnTarget(long shooter, long target) {
        World world = roster.world();
        float total = 0f;
        if (roster.squad().hasSquad(shooter)) {
            int shooterSquadId = roster.squad().squadId(shooter);
            for (int i = 0, n = roster.liveCount(); i < n; i++) {
                long u = roster.get(i);
                if (u == shooter) continue;
                if (!roster.squad().hasSquad(u) || roster.squad().squadId(u) != shooterSquadId) continue;
                if (!world.hasSecondaryWeapon(u)) continue;
                if (world.secondaryActionTimer(u) <= 0f) continue;
                if (world.secondaryAimTargetId(u) != target) continue;
                MarineSecondary sw = world.secondaryWeapon(u);
                if (!sw.isDirectFireWeapon()) continue;
                total += projectedResolvedDamage(target, sw.damage(), sw.penetration());
            }
        }
        // Inflight rocket entities owned by the sim. The Projectile carries
        // its arrival payload directly — read damage / endpoint / AoE off
        // {@link Projectile#onArrival}. Same in-AoE-of-target-cell filter
        // as the legacy snapshot path.
        //
        // Snapshot — runs during parallel UPDATE_UNITS, can't iterate the
        // live projectile list while another worker may queueProjectile.
        // Mirrors the legacy snapshotInflightDetonations path.
        float targetCx = world.x(target);
        float targetCy = world.y(target);
        Faction shooterFaction = roster.identity().faction(shooter);
        for (Projectile p : shots.snapshotActiveProjectiles()) {
            if (p.shooterFaction != shooterFaction) continue;
            PendingDetonation det = p.onArrival;
            if (det == null) continue;
            float dx = targetCx - det.endpointX;
            float dy = targetCy - det.endpointY;
            if (dx * dx + dy * dy <= det.aoeRadius * det.aoeRadius) {
                total += projectedResolvedDamage(target, det.damage, det.penetration);
            }
        }
        for (ShotService.PendingImpact impact : shots.snapshotActiveImpacts()) {
            if (impact.marineSecondary == null || impact.victimId != target) continue;
            if (!roster.isAliveById(impact.shooterId)) continue;
            if (roster.identity().faction(impact.shooterId) != shooterFaction) continue;
            total += projectedResolvedDamage(target, impact.damage, impact.penetration);
        }
        return total;
    }

    /** Shared-model projection for the temporary D1 committed-fire gate. */
    private float projectedResolvedDamage(long target, float damage, float penetration) {
        World world = roster.world();
        float armor = world.hasArmor(target) ? world.armor(target) : 0f;
        float rating = world.hasArmor(target) ? world.armorRating(target) : 0f;
        DurabilityModel.Resolution result = new DurabilityModel.Resolution();
        DurabilityModel.resolveInto(damage, penetration, armor, rating, world.hp(target), result);
        return result.armorDamage() + result.structureDamage();
    }

    /**
     * Counts other enemies within {@link #THREAT_DENSITY_RADIUS} cells of the
     * candidate's cell, then multiplies by {@link #TARGET_THREAT_DENSITY_COST}.
     * "Other enemies" = alive combatants of the candidate's faction, excluding
     * the candidate itself. The point is to model "stacking into a fire line" —
     * a lone wounded soldier is much cheaper to engage than one surrounded by
     * three buddies.
     *
     * <p>Spatial-indexed: gathers the small bucket window around the
     * candidate instead of walking the full unit list per call. Called once
     * per visible target inside {@link #findBestTarget}, so the savings
     * compound when squads pick targets each tick.
     */
    private float scoreThreatDensity(long candidate, float candX, float candY, Faction selfFaction) {
        return threatDensityAt(candidate, candX, candY, selfFaction) * TARGET_THREAT_DENSITY_COST;
    }

    /**
     * Number of other hostile combatants within the Story-I density radius of
     * {@code candidate}. This ground-truth query is the explicit future swap
     * site for a per-squad believed-contact map.
     */
    public int threatDensityAt(long candidate, Faction selfFaction) {
        if (!roster.isAliveById(candidate)) return 0;
        World world = roster.world();
        return threatDensityAt(candidate, world.x(candidate), world.y(candidate), selfFaction);
    }

    /**
     * Number of other contacts the observing squad remembers near the
     * candidate's last-seen cell. Unknown hostiles are deliberately absent.
     */
    public int threatDensityAt(long candidate, Squad observer) {
        BelievedContact center = observer.believedContact(candidate);
        if (center == null) return 0;
        float radiusSquared = THREAT_DENSITY_RADIUS * THREAT_DENSITY_RADIUS;
        int count = 0;
        for (BelievedContact contact : observer.believedContacts()) {
            if (contact.unitId() == candidate) continue;
            if (center.distanceSquaredTo(contact) <= radiusSquared) count++;
        }
        return count;
    }

    private int threatDensityAt(long candidate, float candX, float candY, Faction selfFaction) {
        return unitIndex.countOtherFactionCombatants(candX, candY,
                THREAT_DENSITY_RADIUS, selfFaction, candidate);
    }

    /** Result of re-evaluating a cached infantry pursuit target. */
    public enum PursuitDecision {
        KEEP,
        RETARGET,
        HOLD
    }

    /**
     * Score margin (in cell-distance) by which a different visible enemy must
     * beat the current target before the pursuit gate switches. Prevents
     * thrashing on marginal differences while still flipping promptly when a
     * close mech walks up next to a marine engaged on a distant turret.
     */
    public static final float RETARGET_DISTANCE_MARGIN = 5f;
    /**
     * The unit index stores tick-start positions while UPDATE_UNITS may have
     * already advanced a candidate before another worker assesses pursuit.
     * One cell covers more than a tick of every current ground mover; the
     * live-distance check below remains the exact retarget authority.
     */
    private static final float RETARGET_QUERY_PADDING = 1f;
    /** Per-worker output for the parallel pursuit-assessment path. */
    private static final ThreadLocal<LongBucket> RETARGET_CANDIDATES =
            ThreadLocal.withInitial(LongBucket::new);
    /** Per-worker output for shootable opportunity targets near the actor. */
    private static final ThreadLocal<LongBucket> OPPORTUNITY_CANDIDATES =
            ThreadLocal.withInitial(LongBucket::new);
    /** Smaller hysteresis for a shot of opportunity that does not change pursuit. */
    public static final float OPPORTUNITY_RETARGET_DISTANCE_MARGIN = 2f;

    /**
     * Pursuit gate: returns true when {@code currentTarget} is still a sensible
     * target to keep firing on, false when the caller should re-pick.
     *
     * <p>Returns false in any of:
     * <ul>
     *   <li>{@code currentTarget} is dead or null.</li>
     *   <li>A meaningfully closer visible enemy exists than the current target
     *       — the user-visible case is a mech walking up to a squad engaged on
     *       a distant turret; ignoring the mech and continuing to fire past
     *       it is the failure mode.</li>
     *   <li>The target requires movement and now sits inside a non-trivial
     *       threat-density cluster — Story I bail-out to prevent chasing a
     *       fleer into their squad.</li>
     * </ul>
     */
    public boolean shouldKeepPursuing(long self, long currentTarget) {
        return assessPursuit(self, currentTarget) == PursuitDecision.KEEP;
    }

    /**
     * Story-I pursuit assessment. A visible target already inside effective
     * weapon range remains legal because no advance is required. Any target
     * that requires movement and has at least two nearby hostile combatants
     * produces {@link PursuitDecision#HOLD} instead of a blind reacquire.
     */
    public PursuitDecision assessPursuit(long self, long currentTarget) {
        return assessPursuit(self, currentTarget, null);
    }

    /** Story-25 assessment using the acting squad's remembered density. */
    public PursuitDecision assessPursuit(long self, long currentTarget, Squad observer) {
        World world = roster.world();
        if (currentTarget == 0L || !roster.isAliveById(currentTarget)) {
            return PursuitDecision.RETARGET;
        }
        VisionService vision = roster.vision();
        Faction selfFaction = roster.identity().faction(self);
        int sx = world.cellX(self);
        int sy = world.cellY(self);
        int tx = world.cellX(currentTarget);
        int ty = world.cellY(currentTarget);
        float selfAir = vision.airLosRadius(self);
        boolean visible = canSeePair(grid, sx, sy, tx, ty,
                selfAir, vision.airLosRadius(currentTarget));

        float selfX = world.x(self);
        float selfY = world.y(self);
        float currentDist = cellDistance(selfX, selfY,
                world.x(currentTarget), world.y(currentTarget));
        float effectiveRange = effectiveAttackRange(self, currentTarget,
                world.attackRange(self));
        int density = observer != null
                ? threatDensityAt(currentTarget, observer)
                : threatDensityAt(currentTarget, selfFaction);
        if (!(visible && currentDist <= effectiveRange)
                && density >= HIGH_THREAT_DENSITY_COUNT) {
            return PursuitDecision.HOLD;
        }

        // "Meaningfully closer visible enemy" check — runs whether or not the
        // current target is visible. If current is invisible and a visible
        // alternative exists, switch unconditionally. If current is visible,
        // switch only when the alternative is closer by at least
        // RETARGET_DISTANCE_MARGIN to dampen thrashing.
        if (hasRetargetingVisibleEnemy(self, currentTarget, selfFaction,
                selfX, selfY, sx, sy, selfAir, visible, currentDist)) {
            return PursuitDecision.RETARGET;
        }

        return PursuitDecision.KEEP;
    }

    /**
     * Best visible candidate whose nearby-hostile count remains below the
     * engagement-discipline gate, excluding {@code excludedTarget}. Returns
     * {@code 0L} when holding is safer than advancing on any visible contact.
     */
    public long findBestVisibleLowDensityTarget(long self, long excludedTarget) {
        return findBestVisibleLowDensityTarget(self, excludedTarget, null);
    }

    /** Story-25 candidate search using the acting squad's remembered density. */
    public long findBestVisibleLowDensityTarget(long self, long excludedTarget,
                                                Squad observer) {
        World world = roster.world();
        VisionService vision = roster.vision();
        Faction selfFaction = roster.identity().faction(self);
        int selfSquadId = roster.squad().hasSquad(self)
                ? roster.squad().squadId(self) : Squad.NO_SQUAD;
        int sx = world.cellX(self);
        int sy = world.cellY(self);
        float selfAir = vision.airLosRadius(self);
        long best = 0L;
        float bestScore = Float.MAX_VALUE;

        long[] dense = roster.denseArray();
        int liveCount = roster.liveCount();
        for (int i = 0; i < liveCount; i++) {
            long candidate = dense[i];
            if (candidate == excludedTarget || candidate == self) continue;
            if (roster.identity().faction(candidate) == selfFaction
                    || !roster.identity().type(candidate).combatant) continue;
            int cx = world.cellX(candidate);
            int cy = world.cellY(candidate);
            if (!canSeePair(grid, sx, sy, cx, cy,
                    selfAir, vision.airLosRadius(candidate))) continue;
            int density = observer != null
                    ? threatDensityAt(candidate, observer)
                    : threatDensityAt(candidate, world.x(candidate), world.y(candidate), selfFaction);
            if (density >= HIGH_THREAT_DENSITY_COUNT) continue;
            float score = cellDistance(world.x(self), world.y(self),
                    world.x(candidate), world.y(candidate))
                    + scoreCrowding(selfFaction, selfSquadId, candidate, self)
                    + density * TARGET_THREAT_DENSITY_COST
                    + scoreWeaponAffinity(self, candidate)
                    + scoreZoneMismatch(sx, sy, cx, cy);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * True when pursuit should yield to another visible enemy. For a visible
     * current target, only an enemy more than
     * {@link #RETARGET_DISTANCE_MARGIN} cells closer can change the answer, so
     * a faction-filtered spatial query prunes the global roster before the
     * exact live-position and LoS checks. For an invisible current target the
     * legacy rule is "any visible alternative"; that unbounded case retains a
     * dense scan but returns on its first qualifying candidate instead of
     * computing a global nearest id the caller never used.
     */
    private boolean hasRetargetingVisibleEnemy(
            long self, long exclude, Faction selfFaction,
            float selfX, float selfY, int selfCellX, int selfCellY,
            float selfAir, boolean currentVisible, float currentDistance) {
        if (!currentVisible) {
            return hasVisibleOtherEnemyDense(self, exclude, selfFaction,
                    selfX, selfY, selfCellX, selfCellY, selfAir,
                    Float.POSITIVE_INFINITY);
        }

        float closerThan = currentDistance - RETARGET_DISTANCE_MARGIN;
        if (!(closerThan > 0f)) return false;
        Faction enemyFaction = selfFaction == Faction.MARINE
                ? Faction.DEFENDER
                : selfFaction == Faction.DEFENDER ? Faction.MARINE : null;
        if (enemyFaction == null) {
            return hasVisibleOtherEnemyDense(self, exclude, selfFaction,
                    selfX, selfY, selfCellX, selfCellY, selfAir,
                    currentDistance);
        }

        LongBucket candidates = RETARGET_CANDIDATES.get();
        unitIndex.gatherFaction(selfX, selfY,
                closerThan + RETARGET_QUERY_PADDING, enemyFaction, candidates);
        World world = roster.world();
        VisionService vision = roster.vision();
        for (int i = 0, n = candidates.size; i < n; i++) {
            long u = candidates.ids[i];
            if (u == exclude || u == self) continue;
            if (!roster.identity().type(u).combatant) continue;
            float distance = cellDistance(selfX, selfY,
                    world.x(u), world.y(u));
            if (!(distance + RETARGET_DISTANCE_MARGIN < currentDistance)) continue;
            int ux = world.cellX(u);
            int uy = world.cellY(u);
            if (canSeePair(grid, selfCellX, selfCellY, ux, uy,
                    selfAir, vision.airLosRadius(u))) return true;
        }
        return false;
    }

    /** Unbounded legacy fallback, optionally constrained by the exact margin. */
    private boolean hasVisibleOtherEnemyDense(
            long self, long exclude, Faction selfFaction,
            float selfX, float selfY, int selfCellX, int selfCellY,
            float selfAir, float currentDistance) {
        World world = roster.world();
        VisionService vision = roster.vision();
        long[] dense = roster.denseArray();
        int liveCount = roster.liveCount();
        for (int i = 0; i < liveCount; i++) {
            long u = dense[i];
            if (u == exclude || u == self) continue;
            if (roster.identity().faction(u) == selfFaction
                    || !roster.identity().type(u).combatant) continue;
            if (Float.isFinite(currentDistance)
                    && !(cellDistance(selfX, selfY, world.x(u), world.y(u))
                    + RETARGET_DISTANCE_MARGIN < currentDistance)) continue;
            int ux = world.cellX(u);
            int uy = world.cellY(u);
            if (canSeePair(grid, selfCellX, selfCellY, ux, uy,
                    selfAir, vision.airLosRadius(u))) return true;
        }
        return false;
    }

    /**
     * Nearest enemy combatant that {@code self} can shoot <em>right now</em> —
     * within its attack range and in line of sight. Drives opportunistic
     * return fire while a unit advances toward a different, out-of-range
     * pursuit target: the assigned target governs movement, this governs the
     * trigger, so a marine crossing open ground shoots whatever it can
     * actually hit instead of marching on eating shots. Returns null when
     * nothing is in range and LoS.
     *
     * <p>Distance-only pick — no crowding/affinity scoring. An opportunistic
     * shot is a free trigger pull with the unit already committed to its
     * march, so "closest thing I can hit" is the right call and keeps this
     * off the heavier {@link #findBestTarget} scoring path. Cheap distance and
     * range tests gate the per-candidate LoS raycast.
     */
    public long closestEnemyInAttackRange(long self) {
        return closestEnemyInAttackRange(self, 0L, 0f);
    }

    /**
     * Nearest currently shootable enemy with acquisition hysteresis. A live,
     * shootable {@code preferred} target remains selected unless another
     * candidate is closer by more than {@code switchMargin} cells. This keeps
     * near-equal threats around an encircled marine from alternating every
     * tick and repeatedly restarting reflex registration.
     */
    public long closestEnemyInAttackRange(long self, long preferred,
                                          float switchMargin) {
        World world = roster.world();
        Faction selfFaction = roster.identity().faction(self);
        float selfX = world.x(self);
        float selfY = world.y(self);
        int sx = (int) Math.floor(selfX);
        int sy = (int) Math.floor(selfY);
        float range = world.attackRange(self);
        VisionService vision = roster.vision();
        float selfAir = vision.airLosRadius(self);

        LongBucket candidates = OPPORTUNITY_CANDIDATES.get();
        unitIndex.gatherOtherFactionCombatants(selfX, selfY,
                range + RETARGET_QUERY_PADDING, selfFaction, candidates);

        long best = 0L;
        float bestDist = Float.MAX_VALUE;
        float preferredDist = Float.MAX_VALUE;
        for (int i = 0, n = candidates.size; i < n; i++) {
            long other = candidates.ids[i];
            if (other == self) continue;
            if (!roster.isAliveById(other)) continue;
            float otherX = world.x(other);
            float otherY = world.y(other);
            int ox = (int) Math.floor(otherX);
            int oy = (int) Math.floor(otherY);
            float d = cellDistance(selfX, selfY, otherX, otherY);
            if (d > range) continue;
            if (!canSeePair(grid, sx, sy, ox, oy, selfAir, vision.airLosRadius(other))) continue;
            if (other == preferred) preferredDist = d;
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return preferredDist < Float.MAX_VALUE
                && !(bestDist + Math.max(0f, switchMargin) < preferredDist)
                ? preferred : best;
    }

    /**
     * Adds {@link #TARGET_CROWDING_COST} for every ally targeting the same
     * enemy, plus an additional {@link #TARGET_SQUADMATE_EXTRA_COST} when the
     * ally is a squadmate. Reads the precomputed attackers-by-target index
     * from the sim ({@code getAttackersOf}) so this is O(L)
     * in the small attacker list rather than O(U) over every unit — total
     * target-selection cost drops from O(U³) to O(U² + U·L).
     */
    private float scoreCrowding(Faction selfFaction, int selfSquadId, long target,
                                long exclude) {
        LongArrayList attackers = attackerIndex.getAttackersOf(target);
        if (attackers == null) return 0f;
        float cost = 0f;
        for (int i = 0, n = attackers.size(); i < n; i++) {
            long u = attackers.getLong(i);
            if (u == exclude || !roster.isAliveById(u)) continue;
            if (roster.identity().faction(u) != selfFaction) continue;
            cost += TARGET_CROWDING_COST;
            if (selfSquadId != Squad.NO_SQUAD && roster.squad().hasSquad(u)
                    && roster.squad().squadId(u) == selfSquadId) {
                cost += TARGET_SQUADMATE_EXTRA_COST;
            }
        }
        return cost;
    }

    /**
     * Picks a walkable cell at attack range from the target, minimizing
     * {@code distFromSelf + occupancy_penalty - cover_bonus}. Candidates must
     * have LOS to the target — a cell on the far side of a wall is useless
     * even at range.
     *
     * <p>Stage-2 fallback: when no candidate in the attack-range ring has LOS
     * (typical "turret around the corner" case — the unit's whole approach
     * ring is wall-blocked), falls back to picking a reachable vantage point
     * from {@link NavigationService#getVantagePointsFor}. Vantages are walkable
     * cells with LOS to the target anywhere within
     * {@link #MAX_VANTAGE_SEARCH_RADIUS}; the picker sorts them by Euclidean
     * distance from {@code self} and pathfinds in order, taking the first
     * reachable hit. Walking to a vantage gains LOS; once LOS exists,
     * subsequent ticks return real stage-1 candidates and the unit closes to
     * a proper firing position. Engagement (LOS + range) still gates SUCCESS.
     *
     * <p>Returns {@code null} when both stage 1 and stage 2 find nothing —
     * the target is geometrically unreachable from anywhere the unit can
     * walk to. Callers treat null as "drop the target and re-acquire," not
     * "stand still."
     */
    public int[] findFiringPosition(long self, long target) {
        return findFiringPosition(self, target, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    /**
     * True iff {@code self} can actually take up a firing position against
     * {@code target} — i.e. some walkable cell with LOS + weapon range to the
     * target is <em>reachable from {@code self} by the pathfinder</em>.
     *
     * <p>This is the reachability check {@link #findFiringPosition} deliberately
     * skips: its stage-1 result is LOS-and-range only (a per-candidate pathfind
     * would tank the hot reposition path), so it can hand back a firing cell
     * that sits on the far side of a wall from {@code self}. A caller that paths
     * to such a cell gets an empty path and freezes. So here we verify the
     * stage-1 cell is pathable, and when it isn't, fall back to the
     * reachable-vantage probe — the authoritative "can I even approach?".
     *
     * <p>The gate {@link com.dillon.starsectormarines.battle.infantry.GarrisonPatrol}
     * uses so a garrison doesn't wedge itself re-clearing an enemy it can never
     * close on — e.g. a surviving turret whose only LOS cells lie across a wall
     * the zone graph floods past but the pathfinder honors
     * ([[zone_graph_ignores_edges]]). Cost: one {@link #findFiringPosition}
     * plus at most a couple of pathfinds; invoke only when a cheaper "is anyone
     * even here" gate (e.g. zone-clear) has already passed.
     */
    public boolean hasReachableFiringSpot(long self, long target) {
        return findReachableFiringPosition(self, target) != null;
    }

    /**
     * Returns a firing or vantage cell that the unit can actually path to.
     * The ordinary hot-path picker may return an LOS-bearing cell across a
     * structural wall; coordinated maneuver needs the stronger guarantee so
     * a fixing element does not replace passive waiting with an empty path.
     */
    public int[] findReachableFiringPosition(long self, long target) {
        int[] spot = findFiringPosition(self, target);
        if (spot == null) return null;
        // A stage-2 vantage is already reachability-checked, so this pathfind
        // only ever fails when findFiringPosition returned a stage-1 (LOS+range)
        // cell that's walled off from self — in which case the vantage probe is
        // the real verdict on whether an approach exists at all.
        World world = roster.world();
        int[] path = GridPathfinder.findPath(grid, world.cellX(self), world.cellY(self), spot[0], spot[1]);
        if (path.length > 0) return spot;
        return pickReachableVantage(self, target);
    }

    /**
     * Constrained firing-position search — like {@link #findFiringPosition} but
     * rejects any candidate whose cell-distance from ({@code anchorX},
     * {@code anchorY}) exceeds {@code maxDistFromAnchor}. Used by
     * {@link com.dillon.starsectormarines.battle.infantry.HoldPost} to keep engaged defenders within a tight
     * radius of their tactical-node anchor: they'll peek around corners and
     * grab better cover, but won't chase marines off the wall.
     *
     * <p>Returns {@code null} (not the target's cell) when no candidate
     * satisfies range + LOS + anchor-radius. The caller treats null as
     * "hold position" rather than "advance toward the target."
     */
    /**
     * Picks an enemy combatant {@code self} can engage from within
     * {@code maxDistFromAnchor} cells of the anchor — i.e. an enemy with at
     * least one reachable firing position inside the hold radius. Used by
     * {@link com.dillon.starsectormarines.battle.infantry.HoldPost} to retarget when the unit's current target is
     * blocked by walls from every cell within the hold radius: rather than
     * idling on a fixated unreachable target, switch to one we can actually
     * engage from the post.
     *
     * <p>Selection: closest enemy to {@code self} that has a firing position
     * inside the ring. Closest is a cheap proxy for "easiest threat to
     * service from this post" and matches {@link #findBestTarget}'s default
     * distance bias.
     *
     * <p>Cost is O(enemies-near-anchor × firing-position-search). Only
     * invoke this in the fallback path — when {@link #findFiringPositionWithin}
     * for the current target has already returned null.
     */
    public long findEngageableEnemyWithin(long self,
                                          int anchorX, int anchorY,
                                          float maxDistFromAnchor) {
        World world = roster.world();
        Faction selfFaction = roster.identity().faction(self);
        float maxWeaponReach = world.attackRange(self);
        if (world.hasSecondaryWeapon(self) && world.secondaryAmmo(self) > 0) {
            MarineSecondary special = world.secondaryWeapon(self);
            if (special.isDirectFireWeapon()) {
                maxWeaponReach = Math.max(maxWeaponReach, special.range());
            }
        }
        float gatherRadius = maxDistFromAnchor + maxWeaponReach;
        LongBucket scratch = new LongBucket();
        unitIndex.gather(anchorX + 0.5f, anchorY + 0.5f, gatherRadius, scratch);
        long best = 0L;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0, n = scratch.size; i < n; i++) {
            long enemy = scratch.ids[i];
            if (!roster.isAliveById(enemy) || !roster.identity().type(enemy).combatant) continue;
            if (roster.identity().faction(enemy) == selfFaction) continue;
            int[] pos = findFiringPositionWithin(self, enemy, anchorX, anchorY, maxDistFromAnchor);
            if (pos == null) continue;
            float d = cellDistance(world.x(self), world.y(self), world.x(enemy), world.y(enemy));
            if (d < bestDist) {
                bestDist = d;
                best = enemy;
            }
        }
        return best;
    }

    /**
     * Count of alive combatants of {@code faction} within {@code radius} cells
     * of {@code (cx, cy)} — a cheap local force tally for odds-aware tactics
     * (e.g. a guard post tightening its engage leash when outnumbered). Goes
     * through the spatial index, not a full unit scan. Live turrets and drones
     * count (they're combatants); civilians don't.
     */
    public int countCombatantsWithin(Faction faction, int cx, int cy, float radius) {
        LongBucket scratch = LOCAL_FORCE_CANDIDATES.get();
        unitIndex.gather(cx + 0.5f, cy + 0.5f, radius, scratch);
        int count = 0;
        for (int i = 0, n = scratch.size; i < n; i++) {
            long u = scratch.ids[i];
            if (roster.identity().faction(u) == faction && roster.isAliveById(u) && roster.identity().type(u).combatant) count++;
        }
        return count;
    }

    /**
     * Confidence-weighted hostile presence remembered by {@code squad} within
     * {@code radius} of a cell. Geometry comes exclusively from believed
     * contact cells; the live roster is consulted only to discard dead or
     * non-combatant identities, never for a hidden current position.
     */
    public float believedHostilePresenceWithin(Squad squad, int cx, int cy,
                                                float radius) {
        float presence = 0f;
        for (BelievedContact contact : squad.believedContacts()) {
            long id = contact.unitId();
            if (!roster.isAliveById(id)) continue;
            if (roster.identity().faction(id) == squad.faction) continue;
            if (!roster.identity().type(id).combatant) continue;
            if (cellDistance(cx, cy, contact.lastSeenCellX(),
                    contact.lastSeenCellY()) <= radius) {
                presence += contact.confidence();
            }
        }
        return presence;
    }

    /**
     * Publishes one immutable belief-derived contact picture per live squad.
     * Called serially after {@code SquadAlertSystem} has published contact
     * memory and before the parallel replan/read phase.
     */
    public void updateContactPictures(int currentTick) {
        for (Squad squad : roster.getSquads()) {
            Doctrine previous = squad.contactPicture.doctrine();
            SquadContactPicture picture = assessContactPicture(squad, currentTick);
            squad.contactPicture = picture;
            squad._contactDoctrineChangedThisTick = picture.doctrine() != previous;
        }
    }

    /** Builds the local tactical picture without reading a hostile's hidden position. */
    public SquadContactPicture assessContactPicture(Squad squad, int currentTick) {
        if (squad.aliveMembers <= 0) {
            return new SquadContactPicture(currentTick, postureOf(squad), 0f, 0f,
                    0, 0, 0f, 0, ForceBalance.NONE, Sector.NONE,
                    Motion.UNKNOWN, 0L, -1, -1, 0f, Doctrine.ADVANCE,
                    0, 0, 0, 0, ContactInitiative.NONE);
        }

        if (squad.believedContacts().isEmpty()) {
            FiringLineCoverage coverage = firingLineCoverage(squad, null);
            return new SquadContactPicture(currentTick, postureOf(squad), 0f, 0f,
                    0, 0, 0f, 0, ForceBalance.NONE, Sector.NONE,
                    Motion.UNKNOWN, 0L, -1, -1, 0f, Doctrine.ADVANCE,
                    0, coverage.liveMembers(), 0, coverage.liveFireTeams(),
                    ContactInitiative.NONE);
        }

        Posture posture = postureOf(squad);
        float[] axis = tacticalAxis(squad);
        float[] sectorStrength = new float[Sector.values().length];
        float hostileStrength = 0f;
        int contactCount = 0;
        int directCount = 0;
        BelievedContact primary = null;
        float primaryDistance = Float.MAX_VALUE;

        for (BelievedContact contact : squad.believedContacts()) {
            long id = contact.unitId();
            if (!roster.isAliveById(id)
                    || roster.identity().faction(id) == squad.faction
                    || !roster.identity().type(id).combatant) continue;
            float dx = contact.lastSeenCellX() + 0.5f - squad.centroidX;
            float dy = contact.lastSeenCellY() + 0.5f - squad.centroidY;
            float distance = distanceToSquadFootprint(squad, contact);
            if (distance > CONTACT_PICTURE_RADIUS) continue;

            Sector sector = classifySector(axis[0], axis[1], dx, dy);
            sectorStrength[sector.ordinal()] += contact.confidence();
            hostileStrength += contact.confidence();
            contactCount++;
            if (contact.source() == BeliefSource.DIRECT
                    && contact.observedOnTick(currentTick)) directCount++;
            if (primary == null
                    || contact.confidence() > primary.confidence()
                    || (contact.confidence() == primary.confidence()
                    && distance < primaryDistance)) {
                primary = contact;
                primaryDistance = distance;
            }
        }

        if (primary == null) {
            FiringLineCoverage coverage = firingLineCoverage(squad, null);
            return new SquadContactPicture(currentTick, posture, axis[0], axis[1],
                    0, 0, 0f, 0, ForceBalance.NONE, Sector.NONE,
                    Motion.UNKNOWN, 0L, -1, -1, 0f, Doctrine.ADVANCE,
                    0, coverage.liveMembers(), 0, coverage.liveFireTeams(),
                    ContactInitiative.NONE);
        }

        Sector dominant = dominantSector(sectorStrength);
        int friends = countCombatantsWithin(squad.faction,
                primary.lastSeenCellX(), primary.lastSeenCellY(),
                CONTACT_PICTURE_RADIUS);
        ForceBalance balance = forceBalance(hostileStrength, friends);
        Motion motion = contactMotion(primary, squad, currentTick);
        boolean holdContactFresh = contactHoldIsFresh(
                primary, directCount, currentTick);
        Doctrine doctrine = selectDoctrine(posture, balance, dominant, motion,
                mustHold(squad), squad.contactPicture.doctrine(), true,
                holdContactFresh);
        FiringLineCoverage coverage = firingLineCoverage(squad, primary);
        boolean primaryDirect = primary.source() == BeliefSource.DIRECT
                && primary.observedOnTick(currentTick);
        ContactInitiative initiative = selectContactInitiative(doctrine,
                posture, balance, motion, mustHold(squad), primaryDirect,
                coverage.engageableMembers(), coverage.liveMembers(),
                coverage.engageableFireTeams(), coverage.liveFireTeams());
        return new SquadContactPicture(currentTick, posture, axis[0], axis[1],
                contactCount, directCount, hostileStrength, friends, balance,
                dominant, motion, primary.unitId(), primary.lastSeenCellX(),
                primary.lastSeenCellY(), primary.confidence(), doctrine,
                coverage.engageableMembers(), coverage.liveMembers(),
                coverage.engageableFireTeams(), coverage.liveFireTeams(),
                initiative);
    }

    private FiringLineCoverage firingLineCoverage(Squad squad,
                                                   BelievedContact primary) {
        int liveMembers = 0;
        int engageableMembers = 0;
        int liveTeamsMask = 0;
        int engageableTeamsMask = 0;
        long[] members = roster.squadMemberArray(squad.id);
        for (int i = 0, n = roster.squadMemberCount(squad.id); i < n; i++) {
            long member = members[i];
            if (!roster.isAliveById(member) || !roster.world().hasCombat(member)) continue;
            liveMembers++;
            int team = roster.squad().fireTeamIndex(member);
            int teamBit = 1 << Math.min(30, Math.max(0, team));
            liveTeamsMask |= teamBit;
            boolean engageable = false;
            if (primary != null) {
                float distance = cellDistance(roster.world().x(member),
                        roster.world().y(member), primary.lastSeenCellX() + 0.5f,
                        primary.lastSeenCellY() + 0.5f);
                engageable = distance <= roster.world().attackRange(member)
                        && grid.hasLineOfSight(roster.world().cellX(member),
                        roster.world().cellY(member), primary.lastSeenCellX(),
                        primary.lastSeenCellY());
            }
            if (engageable) {
                engageableMembers++;
                engageableTeamsMask |= teamBit;
            }
        }
        return new FiringLineCoverage(liveMembers, engageableMembers,
                Integer.bitCount(liveTeamsMask),
                Integer.bitCount(engageableTeamsMask));
    }

    /**
     * A contact is local when it lies near any live squad member. Fireteams
     * are allowed to spread far enough that the whole-squad centroid no longer
     * represents the element actually observing and engaging the contact.
     */
    private float distanceToSquadFootprint(Squad squad,
                                           BelievedContact contact) {
        float contactX = contact.lastSeenCellX() + 0.5f;
        float contactY = contact.lastSeenCellY() + 0.5f;
        float nearest = Float.MAX_VALUE;
        long[] members = roster.squadMemberArray(squad.id);
        for (int i = 0, n = roster.squadMemberCount(squad.id); i < n; i++) {
            long member = members[i];
            if (!roster.isAliveById(member)) continue;
            nearest = Math.min(nearest,
                    cellDistance(roster.world().x(member), roster.world().y(member),
                            contactX, contactY));
        }
        return nearest;
    }

    private record FiringLineCoverage(int liveMembers, int engageableMembers,
                                      int liveFireTeams,
                                      int engageableFireTeams) { }

    static ContactInitiative selectContactInitiative(
            Doctrine doctrine, Posture posture, ForceBalance balance,
            Motion motion, boolean mustHold, boolean primaryDirect,
            int engageableMembers, int liveMembers,
            int engageableFireTeams, int liveFireTeams) {
        if (doctrine != Doctrine.HOLD || !primaryDirect) {
            return ContactInitiative.NONE;
        }
        int usefulMemberLine = Math.max(1, (liveMembers + 1) / 2);
        int usefulTeamLine = Math.min(2, Math.max(1, liveFireTeams));
        boolean usefulFiringLine = engageableMembers >= usefulMemberLine
                && engageableFireTeams >= usefulTeamLine;
        if (posture == Posture.DEFENDING || mustHold
                || balance == ForceBalance.UNFAVORABLE
                || motion == Motion.APPROACHING || motion == Motion.UNKNOWN
                || usefulFiringLine) {
            return ContactInitiative.RECEIVE;
        }
        return ContactInitiative.PROSECUTE;
    }

    static Sector classifySector(float axisX, float axisY, float dx, float dy) {
        float axisLength = (float) Math.sqrt(axisX * axisX + axisY * axisY);
        float contactLength = (float) Math.sqrt(dx * dx + dy * dy);
        if (axisLength < 1e-4f || contactLength < 1e-4f) return Sector.UNKNOWN;
        float nx = axisX / axisLength;
        float ny = axisY / axisLength;
        float dot = (nx * dx + ny * dy) / contactLength;
        if (dot >= SECTOR_FRONT_COS) return Sector.FRONT;
        if (dot <= -SECTOR_FRONT_COS) return Sector.REAR;
        float cross = nx * dy - ny * dx;
        return cross < 0f ? Sector.LEFT_FLANK : Sector.RIGHT_FLANK;
    }

    static ForceBalance forceBalance(float hostileStrength, int friends) {
        if (hostileStrength <= 0f) return ForceBalance.NONE;
        float ratio = hostileStrength / Math.max(1, friends);
        if (ratio <= 0.65f) return ForceBalance.FAVORABLE;
        if (ratio <= 1.25f) return ForceBalance.EVEN;
        return ForceBalance.UNFAVORABLE;
    }

    /** Pure doctrine selector; the prior doctrine supplies enter/release hysteresis. */
    static Doctrine selectDoctrine(Posture posture, ForceBalance balance,
                                    Sector sector, Motion motion,
                                    boolean mustHold, Doctrine previous,
                                    boolean hasContacts) {
        return selectDoctrine(posture, balance, sector, motion, mustHold,
                previous, hasContacts, true);
    }

    static Doctrine selectDoctrine(Posture posture, ForceBalance balance,
                                    Sector sector, Motion motion,
                                    boolean mustHold, Doctrine previous,
                                    boolean hasContacts,
                                    boolean holdContactFresh) {
        if (!hasContacts || balance == ForceBalance.NONE) return Doctrine.ADVANCE;
        if (mustHold) return Doctrine.HOLD;

        int risk = switch (balance) {
            case FAVORABLE -> -1;
            case EVEN, NONE -> 0;
            case UNFAVORABLE -> 2;
        };
        if (sector == Sector.LEFT_FLANK || sector == Sector.RIGHT_FLANK) risk++;
        else if (sector == Sector.REAR) risk += 2;
        if (motion == Motion.APPROACHING) risk++;
        else if (motion == Motion.WITHDRAWING) risk--;

        if (posture == Posture.DEFENDING) {
            if (previous == Doctrine.DISENGAGE && risk > 1) return Doctrine.DISENGAGE;
            return risk >= 3 ? Doctrine.DISENGAGE : Doctrine.HOLD;
        }
        if (previous == Doctrine.DISENGAGE) {
            return risk > 1 ? Doctrine.DISENGAGE
                    : (risk <= -1 ? Doctrine.ADVANCE : Doctrine.HOLD);
        }
        if (previous == Doctrine.HOLD) {
            if (risk >= 3) return Doctrine.DISENGAGE;
            if (posture == Posture.ADVANCING && !holdContactFresh) {
                return Doctrine.ADVANCE;
            }
            return risk <= -2 ? Doctrine.ADVANCE : Doctrine.HOLD;
        }
        if (risk >= 3) return Doctrine.DISENGAGE;
        return risk >= 0 ? Doctrine.HOLD : Doctrine.ADVANCE;
    }

    /**
     * Whether the contact evidence is fresh enough to hard-stop an advancing
     * squad for doctrine HOLD. This deliberately expires before the belief:
     * stale evidence still informs aim and awareness, but not indefinite path
     * clearing.
     */
    public static boolean contactHoldIsFresh(Squad squad,
                                             SquadContactPicture picture,
                                             int currentTick) {
        if (picture == null || !picture.hasContacts()) return false;
        BelievedContact primary = squad.believedContact(
                picture.primaryContactId());
        return contactHoldIsFresh(primary, picture.directContactCount(),
                currentTick);
    }

    /** True when HOLD means plant the whole advancing squad and receive contact. */
    public static boolean shouldHardHoldAdvance(Squad squad,
                                                SquadContactPicture picture,
                                                int currentTick) {
        return picture != null && picture.posture() == Posture.ADVANCING
                && picture.doctrine() == Doctrine.HOLD
                && picture.contactInitiative() == ContactInitiative.RECEIVE
                && contactHoldIsFresh(squad, picture, currentTick);
    }

    private static boolean contactHoldIsFresh(BelievedContact primary,
                                              int directContactCount,
                                              int currentTick) {
        if (directContactCount > 0) return true;
        if (primary == null) return false;
        return Math.max(0, currentTick - primary.lastSeenTick())
                <= HOLD_AFTER_LOS_TICKS;
    }

    private Posture postureOf(Squad squad) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment != null) {
            return assignment.kind() == AssignmentKind.HOLD_NODE
                    ? Posture.DEFENDING : Posture.ADVANCING;
        }
        if (squad.holdsFireUntilKillZone || squad.defensePost != null
                || squad.assignedNode != null) return Posture.DEFENDING;
        return Posture.UNCOMMITTED;
    }

    private float[] tacticalAxis(Squad squad) {
        float targetX = Float.NaN;
        float targetY = Float.NaN;
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment != null) {
            if (assignment.targetCellX() >= 0 && assignment.targetCellY() >= 0) {
                targetX = assignment.targetCellX() + 0.5f;
                targetY = assignment.targetCellY() + 0.5f;
            } else if (assignment.targetNode() != null) {
                targetX = assignment.targetNode().anchorX + 0.5f;
                targetY = assignment.targetNode().anchorY + 0.5f;
            } else if (assignment.targetZoneId() >= 0) {
                var zone = zoneGraph.zoneById(assignment.targetZoneId());
                if (zone != null && zone.getCellIndices().length > 0) {
                    int cell = zone.getCellIndices()[zone.getCellIndices().length / 2];
                    targetX = cell % grid.getWidth() + 0.5f;
                    targetY = cell / grid.getWidth() + 0.5f;
                }
            }
        }
        if (Float.isNaN(targetX) && squad.patrolWaypointX >= 0) {
            targetX = squad.patrolWaypointX + 0.5f;
            targetY = squad.patrolWaypointY + 0.5f;
        }
        float dx = targetX - squad.centroidX;
        float dy = targetY - squad.centroidY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length >= 1e-4f) return new float[]{dx / length, dy / length};
        SquadContactPicture old = squad.contactPicture;
        if (old.axisX() != 0f || old.axisY() != 0f) {
            return new float[]{old.axisX(), old.axisY()};
        }
        // Stationary squads retain a deterministic map-north reference until
        // movement or an assignment supplies a more meaningful axis.
        return new float[]{0f, -1f};
    }

    private static Sector dominantSector(float[] strength) {
        Sector best = Sector.NONE;
        float bestStrength = 0f;
        for (Sector sector : Sector.values()) {
            if (sector == Sector.NONE) continue;
            float candidate = strength[sector.ordinal()];
            if (candidate > bestStrength) {
                best = sector;
                bestStrength = candidate;
            }
        }
        return best;
    }

    private static Motion contactMotion(BelievedContact contact, Squad squad,
                                        int currentTick) {
        if (!contact.hasFreshMotionSample(currentTick)) return Motion.UNKNOWN;
        float radialX = contact.previousDirectCellX() + 0.5f - squad.centroidX;
        float radialY = contact.previousDirectCellY() + 0.5f - squad.centroidY;
        float radialLength = (float) Math.sqrt(radialX * radialX + radialY * radialY);
        if (radialLength < 1e-4f) return Motion.UNKNOWN;
        float moveX = contact.lastSeenCellX() - contact.previousDirectCellX();
        float moveY = contact.lastSeenCellY() - contact.previousDirectCellY();
        float radialMotion = (moveX * radialX + moveY * radialY) / radialLength;
        if (radialMotion >= MOTION_RADIAL_THRESHOLD) return Motion.WITHDRAWING;
        if (radialMotion <= -MOTION_RADIAL_THRESHOLD) return Motion.APPROACHING;
        return Motion.LATERAL;
    }

    private static boolean mustHold(Squad squad) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment != null && assignment.targetNode() != null) {
            return assignment.targetNode().mustHold;
        }
        return squad.assignedNode != null && squad.assignedNode.mustHold;
    }

    /**
     * Cheap, local commit-vs-press read for a squad advancing toward
     * {@code (destX, destY)}. Enemy combatants contribute according to their
     * distance from the near-term advance segment; a contact on the route is
     * full weight, a flank contact fades to zero by
     * {@link #ADVANCE_THREAT_ROUTE_OUTER}. A freshly direct-observed contact
     * whose current path ends materially farther from the squad is discounted
     * as retreating; stale and audio contacts never expose hidden posture.
     * Weighted hostile force is normalized against nearby friendly combatants,
     * saturating when enemies reach half the friendly force.
     *
     * <p>Enemy geometry is belief-authoritative: confidence scales each
     * contribution and unremembered enemies contribute nothing. Friendly
     * positions are legitimate faction knowledge and remain exact.
     *
     * <p>The returned primary threat is the highest-contributing contact, with
     * nearer contacts winning ties. {@code axisAnchorX/Y} is the closest point
     * on the advance segment to that contact; the zone action centers its
     * off-axis firing-position leash there.
     */
    public AdvanceThreat assessAdvanceThreat(Squad squad, int destX, int destY,
                                              int currentTick) {
        float startX = squad.centroidX;
        float startY = squad.centroidY;
        float fullDx = destX + 0.5f - startX;
        float fullDy = destY + 0.5f - startY;
        float fullLen = (float) Math.sqrt(fullDx * fullDx + fullDy * fullDy);
        if (fullLen < 1e-4f) return AdvanceThreat.NONE;

        float segmentLen = Math.min(fullLen, ADVANCE_THREAT_LOOKAHEAD);
        float endX = startX + fullDx / fullLen * segmentLen;
        float endY = startY + fullDy / fullLen * segmentLen;

        World world = roster.world();
        float weightedFoes = 0f;
        int rawFoes = 0;
        long primary = 0L;
        float primaryContribution = 0f;
        float primaryDistance = Float.MAX_VALUE;
        float primaryAnchorX = startX;
        float primaryAnchorY = startY;
        boolean primaryRetreating = false;

        for (BelievedContact belief : squad.believedContacts()) {
            long contact = belief.unitId();
            if (!roster.isAliveById(contact)) continue;
            if (roster.identity().faction(contact) == squad.faction) continue;
            if (!roster.identity().type(contact).combatant) continue;

            float contactX = belief.lastSeenCellX() + 0.5f;
            float contactY = belief.lastSeenCellY() + 0.5f;
            SegmentProjection projection = projectOntoSegment(
                    contactX, contactY, startX, startY, endX, endY);
            if (projection.distance >= ADVANCE_THREAT_ROUTE_OUTER) continue;

            float routeWeight = projection.distance <= ADVANCE_THREAT_ROUTE_INNER
                    ? 1f
                    : 1f - (projection.distance - ADVANCE_THREAT_ROUTE_INNER)
                    / (ADVANCE_THREAT_ROUTE_OUTER - ADVANCE_THREAT_ROUTE_INNER);
            boolean retreating = belief.source() == BeliefSource.DIRECT
                    && belief.observedOnTick(currentTick)
                    && isRetreatingFrom(contact, startX, startY, world);
            float contribution = routeWeight * belief.confidence()
                    * (retreating ? ADVANCE_THREAT_RETREAT_MULT : 1f);
            weightedFoes += contribution;
            rawFoes++;

            float contactDistance = cellDistance(startX, startY, contactX, contactY);
            if (contribution > primaryContribution
                    || (contribution == primaryContribution && contactDistance < primaryDistance)) {
                primary = contact;
                primaryContribution = contribution;
                primaryDistance = contactDistance;
                primaryAnchorX = projection.x;
                primaryAnchorY = projection.y;
                primaryRetreating = retreating;
            }
        }

        if (primary == 0L || weightedFoes <= 0f) return AdvanceThreat.NONE;

        int friends = countCombatantsWithin(squad.faction,
                Math.round(startX - 0.5f), Math.round(startY - 0.5f),
                ADVANCE_THREAT_LOOKAHEAD);
        float parityForce = Math.max(1f, friends * ADVANCE_THREAT_PARITY_FRACTION);
        float weight = Math.min(1f, weightedFoes / parityForce);
        return new AdvanceThreat(weight, primary, rawFoes, friends,
                Math.round(primaryAnchorX - 0.5f), Math.round(primaryAnchorY - 0.5f),
                primaryRetreating);
    }

    private boolean isRetreatingFrom(long contact, float squadX, float squadY, World world) {
        if (!roster.movement().has(contact)) return false;
        int[] path = roster.movement().path(contact);
        if (Paths.isEmpty(path) || roster.movement().settled(contact)) return false;
        float currentDistance = cellDistance(squadX, squadY, world.x(contact), world.y(contact));
        float destinationDistance = cellDistance(squadX, squadY,
                Paths.destX(path) + 0.5f, Paths.destY(path) + 0.5f);
        return destinationDistance >= currentDistance + ADVANCE_THREAT_RETREAT_MARGIN;
    }

    private static SegmentProjection projectOntoSegment(float px, float py,
                                                         float x0, float y0,
                                                         float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len2 = dx * dx + dy * dy;
        float t = len2 > 0f ? ((px - x0) * dx + (py - y0) * dy) / len2 : 0f;
        t = Math.max(0f, Math.min(1f, t));
        float x = x0 + dx * t;
        float y = y0 + dy * t;
        return new SegmentProjection(x, y, cellDistance(px, py, x, y));
    }

    /** Value result of {@link #assessAdvanceThreat}; immutable and safe to share across the parallel unit-update readers. */
    public record AdvanceThreat(float weight, long primaryThreatId,
                                int foes, int friends,
                                int axisAnchorX, int axisAnchorY,
                                boolean primaryRetreating) {
        public static final AdvanceThreat NONE = new AdvanceThreat(0f, 0L, 0, 0, -1, -1, false);
    }

    private record SegmentProjection(float x, float y, float distance) {}

    public int[] findFiringPositionWithin(long self, long target,
                                          int anchorX, int anchorY, float maxDistFromAnchor) {
        long _profT0 = System.nanoTime();
        try {
            return findFiringPositionWithinImpl(self, target, anchorX, anchorY, maxDistFromAnchor);
        } finally {
            TickInnerProfile p = TickInnerProfile.current();
            if (p != null) p.record(TickInnerProfile.Bucket.FIRING_POSITION, System.nanoTime() - _profT0);
        }
    }

    private int[] findFiringPositionWithinImpl(long self, long target,
                                               int anchorX, int anchorY, float maxDistFromAnchor) {

        World world = roster.world();
        int tx = world.cellX(target);
        int ty = world.cellY(target);
        int sx = world.cellX(self);
        int sy = world.cellY(self);
        VisionService vision = roster.vision();
        float selfAir = vision.airLosRadius(self);
        float targetAir = vision.airLosRadius(target);

        // Rocketeer-vs-turret pairs search a ring sized to the rocket's range —
        // otherwise an out-of-rifle-range marine paths into rifle range before
        // ever firing the rocket. Inner range check uses the same effective
        // range so candidate cells are valid for whatever weapon will fire.
        float effectiveRange = effectiveAttackRange(self, target, world.attackRange(self));
        int range = Math.max(1, (int) Math.floor(effectiveRange));

        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                int cx = tx + dx;
                int cy = ty + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;

                float distFromTarget = (float) Math.sqrt(dx * dx + dy * dy);
                if (distFromTarget > effectiveRange) continue;
                if (distFromTarget < FIRING_MIN_DISTANCE) continue;
                if (!canSeePair(grid, cx, cy, tx, ty, selfAir, targetAir)) continue;
                if (cellDistance(anchorX, anchorY, cx, cy) > maxDistFromAnchor) continue;

                int occupants = occupantsExcludingSelf(self, sx, sy, cx, cy);
                int alliesNear = alliesNearForSpread(self, cx, cy);
                // Cover lookup is directional against the target (the
                // upcoming threat from this firing position) — Story G.
                int fdx = tx - cx;
                int fdy = ty - cy;
                int cover = grid.getCoverAt(cx, cy, fdx, fdy);
                int doodadCover = doodads.getDoodadCoverAt(cx, cy, fdx, fdy);
                float distFromSelf = cellDistance(sx, sy, cx, cy);
                float score = distFromSelf
                        + FIRING_OCCUPANCY_COST * occupants
                        + FIRING_AOE_SPREAD_COST * alliesNear
                        - FIRING_COVER_BONUS * cover
                        - FIRING_DOODAD_COVER_BONUS * doodadCover;
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best;
    }

    public int[] findFiringPosition(long self, long target, int rejectX, int rejectY) {
        long _profT0 = System.nanoTime();
        try {
            return findFiringPositionImpl(self, target, rejectX, rejectY);
        } finally {
            TickInnerProfile p = TickInnerProfile.current();
            if (p != null) p.record(TickInnerProfile.Bucket.FIRING_POSITION, System.nanoTime() - _profT0);
        }
    }

    private int[] findFiringPositionImpl(long self, long target, int rejectX, int rejectY) {

        World world = roster.world();
        int tx = world.cellX(target);
        int ty = world.cellY(target);
        int sx = world.cellX(self);
        int sy = world.cellY(self);
        VisionService vision = roster.vision();
        float selfAir = vision.airLosRadius(self);
        float targetAir = vision.airLosRadius(target);

        // See findFiringPositionWithin — rocketeer-vs-turret widens the ring.
        float effectiveRange = effectiveAttackRange(self, target, world.attackRange(self));
        int range = Math.max(1, (int) Math.floor(effectiveRange));

        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                int cx = tx + dx;
                int cy = ty + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                if (cx == rejectX && cy == rejectY) continue;

                float distFromTarget = (float) Math.sqrt(dx * dx + dy * dy);
                if (distFromTarget > effectiveRange) continue;
                if (distFromTarget < FIRING_MIN_DISTANCE) continue;
                if (!canSeePair(grid, cx, cy, tx, ty, selfAir, targetAir)) continue;

                int occupants = occupantsExcludingSelf(self, sx, sy, cx, cy);
                int alliesNear = alliesNearForSpread(self, cx, cy);
                // Per-facing cover against the target (Story G).
                int fdx = tx - cx;
                int fdy = ty - cy;
                int cover = grid.getCoverAt(cx, cy, fdx, fdy);
                int doodadCover = doodads.getDoodadCoverAt(cx, cy, fdx, fdy);
                float distFromSelf = cellDistance(sx, sy, cx, cy);
                float score = distFromSelf
                        + FIRING_OCCUPANCY_COST * occupants
                        + FIRING_AOE_SPREAD_COST * alliesNear
                        - FIRING_COVER_BONUS * cover
                        - FIRING_DOODAD_COVER_BONUS * doodadCover;
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{cx, cy};
                }
            }
        }
        if (best != null) return best;
        // Stage 2: no in-range LOS-bearing cell exists (turret behind a wall,
        // target tucked in a corner). Walk to a reachable vantage point —
        // any walkable cell with LOS to the target inside
        // MAX_VANTAGE_SEARCH_RADIUS. Once the unit arrives (or rounds a corner
        // mid-path), LOS opens and the next tick's stage-1 search returns a
        // real firing position. See class doc + BattleSimulation.getVantagePointsFor.
        return pickReachableVantage(self, target);
    }

    /**
     * Picks a vantage point from the cached set for {@code target}'s cell —
     * the closest one to {@code self} (by Euclidean cell distance) that the
     * pathfinder can actually reach. Skips up to
     * {@link #MAX_VANTAGE_PATHFIND_ATTEMPTS} candidates before giving up.
     *
     * <p>Returns {@code null} when no vantage is reachable from {@code self}
     * within the attempt cap, or when the target's vantage set is empty.
     * Caller treats null as "no engageable cell from here," typically by
     * dropping the target.
     */
    private int[] pickReachableVantage(long self, long target) {
        World world = roster.world();
        int[][] vantages = nav.getVantagePointsFor(world.cellX(target), world.cellY(target));
        if (vantages.length == 0) return null;

        int selfCellX = world.cellX(self);
        int selfCellY = world.cellY(self);
        int n = vantages.length;
        // Sort by Euclidean distance from self. n is bounded by
        // (2 * MAX_VANTAGE_SEARCH_RADIUS + 1)^2 ≈ 1681 worst case but usually
        // <100 after the walkability + LOS filters in computeVantagePoints.
        // Indirect-sort to avoid mutating the cached array.
        int[] order = new int[n];
        float[] dist = new float[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
            int dx = vantages[i][0] - selfCellX;
            int dy = vantages[i][1] - selfCellY;
            dist[i] = dx * dx + dy * dy;
        }
        // Simple insertion sort — n is small and almost-sorted in practice
        // (cached order is row-major), so an O(n²) insertion sort is fine
        // and avoids the boxing of a Comparator-based Arrays.sort over an
        // int[] index array.
        for (int i = 1; i < n; i++) {
            int oi = order[i];
            float di = dist[i];
            int j = i - 1;
            while (j >= 0 && dist[j] > di) {
                order[j + 1] = order[j];
                dist[j + 1] = dist[j];
                j--;
            }
            order[j + 1] = oi;
            dist[j + 1] = di;
        }

        int attempts = Math.min(n, MAX_VANTAGE_PATHFIND_ATTEMPTS);
        for (int k = 0; k < attempts; k++) {
            int[] cell = vantages[order[k]];
            int[] path = GridPathfinder.findPath(grid,
                    selfCellX, selfCellY, cell[0], cell[1], occupancyMap);
            if (path.length > 0) return cell;
        }
        return null;
    }

    /**
     * Computes the vantage-point set for cell ({@code tx}, {@code ty}) —
     * walkable cells within {@link #MAX_VANTAGE_SEARCH_RADIUS} that have
     * line of sight to ({@code tx}, {@code ty}). Pure function of the grid;
     * called by {@link NavigationService#getVantagePointsFor} on cache miss.
     *
     * <p>Excludes the target's own cell — walking onto your target is
     * never the right destination (and for turrets the cell isn't even
     * walkable). Uses {@link NavigationGrid#hasLineOfSight} directly (not
     * the air-LOS variant) since vantages are a property of the grid
     * geometry, not of any specific shooter/target air radius — a unit
     * with airLosRadius &gt; 0 gets a strict superset of these cells via
     * the air-LOS path at the per-tick check site.
     */
    public static int[][] computeVantagePoints(NavigationGrid grid, int tx, int ty) {
        ArrayList<int[]> hits = new ArrayList<>();
        int r = MAX_VANTAGE_SEARCH_RADIUS;
        int r2 = r * r;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx == 0 && dy == 0) continue;
                if (dx * dx + dy * dy > r2) continue;
                int cx = tx + dx;
                int cy = ty + dy;
                if (!grid.inBounds(cx, cy)) continue;
                if (!grid.isWalkable(cx, cy)) continue;
                if (!grid.hasLineOfSight(cx, cy, tx, ty)) continue;
                hits.add(new int[]{cx, cy});
            }
        }
        return hits.toArray(new int[hits.size()][]);
    }

    /**
     * Returns the highest-cover walkable cell within {@code radius} of
     * ({@code nearX}, {@code nearY}) that has LOS to the threat at
     * ({@code threatX}, {@code threatY}). "Cover" combines the cell-grid wall
     * count and any doodad cover stamped on the cell. Ties broken by smaller
     * distance to the anchor — closer cells win when cover quality is equal.
     *
     * <p>Pure scorer — used by the cover-aware reposition (Story G) and
     * overwatch-position picker (Story A). Returns {@code null} when no
     * candidate has LOS; callers treat that as "no better cover available,
     * hold current cell."
     *
     * <p>This is the cell-search counterpart to {@link #findFiringPosition} —
     * that method centers its search on the target cell and respects attack
     * range; this one centers on an arbitrary anchor (current cell, squad
     * centroid, doorway threshold) and respects an arbitrary radius. The two
     * search shapes are deliberately different: a reposition is a short
     * sidestep near the unit, not a fresh approach toward the target.
     */
    public int[] bestCoverCell(int threatX, int threatY,
                               int nearX, int nearY, int radius) {

        int[] best = null;
        int bestCover = -1;
        float bestDist = Float.MAX_VALUE;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = nearX + dx;
                int cy = nearY + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > radius) continue;
                if (!grid.hasLineOfSight(cx, cy, threatX, threatY)) continue;
                // Per-facing cover: cell-grid wall + doodad, each looked up
                // against the threat-direction snap. A cell with a wall east
                // of it reads as covered only when the threat is east — the
                // MG-in-corner case the user pinned. Score combines wall +
                // doodad in that single facing.
                int fdx = threatX - cx;
                int fdy = threatY - cy;
                int combined = grid.getCoverAt(cx, cy, fdx, fdy) + doodads.getDoodadCoverAt(cx, cy, fdx, fdy);
                if (combined > bestCover || (combined == bestCover && d < bestDist)) {
                    bestCover = combined;
                    bestDist = d;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best;
    }

    /**
     * Finds real wall/doodad cover for an exposed engagement-discipline hold
     * without stepping closer to the rejected cluster. Returns the current
     * cell when it is already covered, a reachable lateral/backward cover
     * cell within {@link #ENGAGEMENT_HOLD_COVER_RADIUS}, or the ordinary
     * away-biased fallback position when no local cover exists.
     */
    public int[] findEngagementHoldPosition(long self, long threat) {
        if (!roster.isAliveById(self) || !roster.isAliveById(threat)) return null;
        World world = roster.world();
        int sx = world.cellX(self);
        int sy = world.cellY(self);
        int tx = world.cellX(threat);
        int ty = world.cellY(threat);
        int threatDx = tx - sx;
        int threatDy = ty - sy;
        int currentCover = grid.getCoverAt(sx, sy, threatDx, threatDy)
                + doodads.getDoodadCoverAt(sx, sy, threatDx, threatDy);
        if (currentCover > 0) return new int[]{sx, sy};

        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        int radiusSquared = ENGAGEMENT_HOLD_COVER_RADIUS * ENGAGEMENT_HOLD_COVER_RADIUS;
        for (int dy = -ENGAGEMENT_HOLD_COVER_RADIUS;
             dy <= ENGAGEMENT_HOLD_COVER_RADIUS; dy++) {
            for (int dx = -ENGAGEMENT_HOLD_COVER_RADIUS;
                 dx <= ENGAGEMENT_HOLD_COVER_RADIUS; dx++) {
                if (dx == 0 && dy == 0 || dx * dx + dy * dy > radiusSquared) continue;
                // Positive dot means advancing toward the rejected cluster.
                if (dx * threatDx + dy * threatDy > 0) continue;
                int cx = sx + dx;
                int cy = sy + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                int coverDx = tx - cx;
                int coverDy = ty - cy;
                int gridCover = grid.getCoverAt(cx, cy, coverDx, coverDy);
                int doodadCover = doodads.getDoodadCoverAt(cx, cy, coverDx, coverDy);
                if (gridCover + doodadCover <= 0) continue;
                int[] path = GridPathfinder.findPath(grid, sx, sy, cx, cy, occupancyMap);
                if (Paths.isEmpty(path)
                        || pathAdvancesToward(path, sx, sy, threatDx, threatDy)) continue;
                float score = Paths.cellCount(path)
                        + FIRING_OCCUPANCY_COST * occupantsExcludingSelf(self, sx, sy, cx, cy)
                        - FIRING_COVER_BONUS * gridCover
                        - FIRING_DOODAD_COVER_BONUS * doodadCover;
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best != null ? best : findFallbackPosition(self);
    }

    private static boolean pathAdvancesToward(int[] path, int startX, int startY,
                                              int threatDx, int threatDy) {
        for (int i = 0, n = Paths.cellCount(path); i < n; i++) {
            int dx = Paths.cellX(path, i) - startX;
            int dy = Paths.cellY(path, i) - startY;
            if (dx * threatDx + dy * threatDy > 0) return true;
        }
        return false;
    }

    /**
     * Assigns one distinct, reachable forward firing cell to every member in
     * {@code bounders}. The search is centered on the phase's stride point,
     * but each candidate is validated against the individual member's weapon
     * range and current position. Returning an empty list is an all-or-nothing
     * failure: the caller keeps the squad on the ordinary committed-contact
     * path instead of moving only part of a bound with nobody able to take
     * over overwatch.
     *
     * <p>Cover dominates the rank, then proximity to the stride point, route
     * length, existing occupancy, and spacing from cells already reserved in
     * this phase. This is deliberately a small greedy assignment: one bound
     * contains one organizational fire team, normally four marines.
     */
    public List<BoundingPosition> findBoundingPositions(List<Long> bounders,
                                                        long threat,
                                                        int strideX, int strideY,
                                                        int destX, int destY) {
        if (bounders.isEmpty() || !roster.isAliveById(threat)) return List.of();

        World world = roster.world();
        float axisX = destX - strideX;
        float axisY = destY - strideY;
        float axisLen = (float) Math.sqrt(axisX * axisX + axisY * axisY);
        if (axisLen < 1e-4f) return List.of();
        axisX /= axisLen;
        axisY /= axisLen;

        int threatX = world.cellX(threat);
        int threatY = world.cellY(threat);
        List<BoundingPosition> assigned = new ArrayList<>(bounders.size());
        for (long member : bounders) {
            if (!roster.isAliveById(member)) continue;
            BoundingPosition best = null;
            float bestScore = Float.MAX_VALUE;
            int memberX = world.cellX(member);
            int memberY = world.cellY(member);

            for (int dy = -BOUNDING_POSITION_SEARCH_RADIUS; dy <= BOUNDING_POSITION_SEARCH_RADIUS; dy++) {
                for (int dx = -BOUNDING_POSITION_SEARCH_RADIUS; dx <= BOUNDING_POSITION_SEARCH_RADIUS; dx++) {
                    int x = strideX + dx;
                    int y = strideY + dy;
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                    if (isReserved(assigned, x, y)) continue;

                    float anchorDistance = cellDistance(x, y, strideX, strideY);
                    if (anchorDistance > BOUNDING_POSITION_SEARCH_RADIUS) continue;
                    float forward = (x - memberX) * axisX + (y - memberY) * axisY;
                    if (forward < BOUNDING_MIN_FORWARD_PROGRESS) continue;
                    float remaining = cellDistance(memberX, memberY, destX, destY);
                    if (forward > remaining + 0.5f) continue;
                    if (!grid.hasLineOfSight(x, y, threatX, threatY)) continue;
                    if (cellDistance(x + 0.5f, y + 0.5f,
                            world.x(threat), world.y(threat)) > world.attackRange(member)) continue;

                    int[] path = GridPathfinder.findPath(grid, memberX, memberY, x, y, occupancyMap);
                    if (Paths.isEmpty(path)) continue;

                    int threatDx = threatX - x;
                    int threatDy = threatY - y;
                    int cover = grid.getCoverAt(x, y, threatDx, threatDy)
                            + doodads.getDoodadCoverAt(x, y, threatDx, threatDy);
                    int occupancy = occupancyMap[grid.index(x, y)] & 0xFF;
                    float spacingPenalty = boundingSpacingPenalty(assigned, x, y);
                    float score = -cover * 100f
                            + anchorDistance * 5f
                            + Paths.cellCount(path)
                            + occupancy * 12f
                            + spacingPenalty;
                    if (score < bestScore) {
                        bestScore = score;
                        best = new BoundingPosition(member, x, y);
                    }
                }
            }

            if (best == null) return List.of();
            assigned.add(best);
        }
        return assigned.size() == bounders.size() ? assigned : List.of();
    }

    private static boolean isReserved(List<BoundingPosition> assigned, int x, int y) {
        for (BoundingPosition p : assigned) {
            if (p.x == x && p.y == y) return true;
        }
        return false;
    }

    private static float boundingSpacingPenalty(List<BoundingPosition> assigned, int x, int y) {
        float penalty = 0f;
        for (BoundingPosition p : assigned) {
            float distance = cellDistance(x, y, p.x, p.y);
            if (distance <= FIRING_AOE_SPREAD_RADIUS) {
                penalty += (FIRING_AOE_SPREAD_RADIUS + 1f - distance) * 20f;
            }
        }
        return penalty;
    }

    /** Immutable member-to-cell result from {@link #findBoundingPositions}. */
    public record BoundingPosition(long memberId, int x, int y) {}

    /**
     * Cover-aware variant of {@code findFiringPosition} —
     * filters the candidate ring to cells whose combined (cell + doodad) cover
     * meets or exceeds the unit's current combined cover against the same
     * threat direction. When no candidate meets that threshold, returns
     * {@code null}: callers (Story G's {@link com.dillon.starsectormarines.battle.infantry.RepositionToCover})
     * treat that as "hold position, current cover is best."
     *
     * <p>Compared with the unfiltered {@link #findFiringPosition}, this won't
     * downgrade — a marine in heavy cover only moves to a cell with at least
     * equal cover. That's the "MG-in-heavy-cover stays cozy" half of Story G,
     * paired with the cooldown gate inside RepositionToCover.
     */
    public int[] findFiringPositionCoverPreferred(long self, long target,
                                                  int rejectX, int rejectY) {
        long _profT0 = System.nanoTime();
        try {
            return findFiringPositionCoverPreferredImpl(self, target, rejectX, rejectY);
        } finally {
            TickInnerProfile p = TickInnerProfile.current();
            if (p != null) p.record(TickInnerProfile.Bucket.FIRING_POSITION, System.nanoTime() - _profT0);
        }
    }

    private int[] findFiringPositionCoverPreferredImpl(long self, long target,
                                                       int rejectX, int rejectY) {

        World world = roster.world();
        int tx = world.cellX(target);
        int ty = world.cellY(target);
        int sx = world.cellX(self);
        int sy = world.cellY(self);
        float selfRange = world.attackRange(self);
        int range = Math.max(1, (int) Math.floor(selfRange));
        VisionService vision = roster.vision();
        float selfAir = vision.airLosRadius(self);
        float targetAir = vision.airLosRadius(target);
        // Self's current cover against the target — per-facing, so a
        // marine already in heavy cover from this threat direction won't
        // downgrade to a cell that lacks that specific facing.
        int selfFdx = tx - sx;
        int selfFdy = ty - sy;
        int selfCover = grid.getCoverAt(sx, sy, selfFdx, selfFdy)
                      + doodads.getDoodadCoverAt(sx, sy, selfFdx, selfFdy);

        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        boolean foundEqualOrBetter = false;

        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                int cx = tx + dx;
                int cy = ty + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                if (cx == rejectX && cy == rejectY) continue;

                float distFromTarget = (float) Math.sqrt(dx * dx + dy * dy);
                if (distFromTarget > selfRange) continue;
                if (distFromTarget < FIRING_MIN_DISTANCE) continue;
                if (!canSeePair(grid, cx, cy, tx, ty, selfAir, targetAir)) continue;

                int fdx = tx - cx;
                int fdy = ty - cy;
                int cover = grid.getCoverAt(cx, cy, fdx, fdy);
                int doodadCover = doodads.getDoodadCoverAt(cx, cy, fdx, fdy);
                int combined = cover + doodadCover;
                // Strictly-better filter — equal cover means "same as current,"
                // and Story G's intent is "don't move if current is already
                // best." The cooldown gate inside RepositionToCover catches
                // the timing side; this filter catches the "no upgrade
                // available" side. Together they keep MGs cozy.
                if (combined <= selfCover) continue;

                int occupants = occupantsExcludingSelf(self, sx, sy, cx, cy);
                int alliesNear = alliesNearForSpread(self, cx, cy);
                float distFromSelf = cellDistance(sx, sy, cx, cy);
                float score = distFromSelf
                        + FIRING_OCCUPANCY_COST * occupants
                        + FIRING_AOE_SPREAD_COST * alliesNear
                        - FIRING_COVER_BONUS * cover
                        - FIRING_DOODAD_COVER_BONUS * doodadCover;
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{cx, cy};
                    foundEqualOrBetter = true;
                }
            }
        }
        return (foundEqualOrBetter && best != null) ? best : null;
    }

    /**
     * Scans cells around {@code self} for a walkable hide cell, scored by
     * {@code distFromSelf + occupancyPenalty - gridCoverBonus - doodadCoverBonus
     * - zoneControlBonus - threatAwayBonus + exposurePenalty}. Scan radius is
     * {@code world.moveSpeed(self) * FALLBACK_SCAN_SECONDS} clamped to
     * [{@link #FALLBACK_SCAN_RANGE_MIN}, {@link #FALLBACK_SCAN_RANGE_MAX}], so
     * fast units sprint farther.
     *
     * <p>Cover terms are read per-facing against the dominant threat direction
     * (average enemy cell): cells whose cover faces the wrong way score zero on
     * the cover term, so the picker prefers cells that actually block the
     * incoming fire.
     *
     * <p>Direction bias is folded in asymmetrically via
     * {@link #FALLBACK_AWAY_FROM_THREAT_BONUS} (mild tiebreaker for retreat
     * cells) and {@link #FALLBACK_TOWARD_THREAT_PENALTY} (heavy refusal of
     * cells closer to the threat centroid than self). The asymmetry is the
     * fix for "broken marines charge into a wall pocket past 100 enemies":
     * a symmetric small bias loses to the +100/enemy exposure floor when
     * the pocket is exposure-0, but a 30/cell toward-penalty puts a 10-cell
     * forward move at +300 — above any 3-enemy exposure cell behind self.
     *
     * <p>Exposure (count of alive enemies with LoS to the cell) is folded into
     * the score with a large weight rather than used as a hard filter. Hidden
     * cells (exposure 0) outrank every exposed cell by construction, so the
     * picker still prefers a hide when one exists — but in open-field fights
     * where no hide is reachable, it degrades gracefully to "least-exposed
     * reachable cell" instead of failing through to the unit's own cell (which
     * read visually as "AI gave up").
     * Bails to {@code self}'s own cell when no enemies are alive — the caller's
     * predicate gate makes that branch effectively unreachable, but it keeps
     * the threat-facing math well-defined.
     *
     * <p>The top-scored cell only helps if the unit can actually walk to it —
     * and the wall that hides a cell from enemies is exactly the kind of wall
     * that can also seal it off from us. Reachability is checked via a single
     * edge-honoring BFS from self, bounded to the scan window — so a cell
     * that's walkable per ZoneGraph (cell-flood only) but blocked off from
     * us by sealed edges is correctly excluded. Without this, the picker
     * returned sealed pockets and the unit froze waiting on an empty path
     * (the SQ-17 stuck-defender dump bug — ZoneGraph reported zones as
     * connected via portals, but {@link com.dillon.starsectormarines.battle.nav.GridPathfinder}
     * couldn't navigate the actual cell edges).
     *
     * <p>The flood replaces the prior {@code ZoneGraph.areConnected} +
     * cardinal-consolation fallback chain — only reachable cells are
     * scored, so the top-of-list pick is the answer with no post-hoc
     * salvage. Falls through to {@code self}'s current cell only when no
     * other reachable candidate exists — caller treats that as "don't
     * enter fall-back."
     */
    public int[] findFallbackPosition(long self) {
        long _profT0 = System.nanoTime();
        try {
            return findFallbackPositionImpl(self);
        } finally {
            TickInnerProfile p = TickInnerProfile.current();
            if (p != null) p.record(TickInnerProfile.Bucket.FALLBACK_POSITION, System.nanoTime() - _profT0);
        }
    }

    private int[] findFallbackPositionImpl(long self) {

        World world = roster.world();
        Faction selfFaction = roster.identity().faction(self);
        ZoneGraph zones = zoneGraph;
        int sx = world.cellX(self);
        int sy = world.cellY(self);
        int[] zoneControl = computeZoneControl(self);

        int[] threatRef = averageEnemyCell(self);
        if (threatRef == null) return new int[]{sx, sy};
        float selfDistFromThreat = cellDistance(sx, sy, threatRef[0], threatRef[1]);

        int scanRange = Math.max(FALLBACK_SCAN_RANGE_MIN,
                       Math.min(FALLBACK_SCAN_RANGE_MAX,
                                Math.round(roster.movement().moveSpeed(self) * FALLBACK_SCAN_SECONDS)));

        // Pre-gather every enemy that could threaten any candidate cell, once.
        // The radius bound is "candidate-furthest-from-self" + "enemy with the
        // longest plausible attackRange" — anyone farther can't reach a cell
        // in the scan, so excluding them is exact, not approximate. Replaces
        // the O(scanRange² × totalUnits) inner loop with O(K) per candidate
        // where K is the nearby-enemy count.
        LongBucket threats = new LongBucket();
        unitIndex.gather(world.x(self), world.y(self), scanRange + MAX_PLAUSIBLE_ATTACK_RANGE, threats);
        filterEnemyCombatants(threats, selfFaction);
        // Project the threat set into parallel SoA columns once, so the
        // per-candidate exposure check reads plain arrays — no registry probe
        // inside the ~1089-candidate scan (cache-locality guardrail).
        int threatCount = threats.size;
        int[] threatCellX = new int[threatCount];
        int[] threatCellY = new int[threatCount];
        float[] threatRange = new float[threatCount];
        resolveThreatColumns(threats, threatCount, threatCellX, threatCellY, threatRange);

        // Edge-honoring reachability flood from self. Bounded by Chebyshev
        // distance to the scan window so worst-case work is O(scanRange²),
        // matching the candidate scan. Eliminates the ZoneGraph mismatch.
        boolean[] reachable = floodReachableFromSelf(grid, sx, sy, scanRange);

        List<float[]> candidates = new ArrayList<>();
        for (int dy = -scanRange; dy <= scanRange; dy++) {
            for (int dx = -scanRange; dx <= scanRange; dx++) {
                int cx = sx + dx;
                int cy = sy + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                if (!reachable[grid.index(cx, cy)]) continue;

                int occupants = occupantsExcludingSelf(self, sx, sy, cx, cy);
                int fdx = threatRef[0] - cx;
                int fdy = threatRef[1] - cy;
                int gridCover   = grid.getCoverAt(cx, cy, fdx, fdy);
                int doodadCover = doodads.getDoodadCoverAt(cx, cy, fdx, fdy);
                int exposure = countEnemiesWithLos(cx, cy, threatCellX, threatCellY, threatRange, threatCount, grid);
                int zoneId = zones.zoneIdAt(cx, cy);
                int control = (zoneId >= 0 && zoneId < zoneControl.length) ? zoneControl[zoneId] : 0;
                float distFromSelf = cellDistance(sx, sy, cx, cy);
                float candDistFromThreat = cellDistance(cx, cy, threatRef[0], threatRef[1]);
                float threatGap = candDistFromThreat - selfDistFromThreat;
                // Asymmetric directional bias: cells closer to the threat
                // pay a heavy penalty (refusing the "charge into a wall
                // pocket past 100 enemies" pathology); cells farther earn a
                // mild bonus as a tiebreaker among away-side hides.
                float directionalScore = threatGap >= 0f
                        ? -FALLBACK_AWAY_FROM_THREAT_BONUS * threatGap
                        : -FALLBACK_TOWARD_THREAT_PENALTY * threatGap; // -negative = +positive
                float score = distFromSelf
                        + FALLBACK_OCCUPANCY_COST * occupants
                        - FALLBACK_GRID_COVER_BONUS   * gridCover
                        - FALLBACK_DOODAD_COVER_BONUS * doodadCover
                        - FALLBACK_FRIENDLY_ZONE_BONUS * control
                        + directionalScore
                        + FALLBACK_EXPOSURE_PENALTY * exposure;
                candidates.add(new float[]{score, cx, cy});
            }
        }
        if (candidates.isEmpty()) return new int[]{sx, sy};
        candidates.sort((a, b) -> Float.compare(a[0], b[0]));
        float[] best = candidates.get(0);
        return new int[]{(int) best[1], (int) best[2]};
    }

    /**
     * Cardinal BFS from {@code (sx, sy)} over walkable cells with passable
     * edges, bounded by Chebyshev distance {@code scanRange}. Returns a
     * cell→reachable mask sized to the grid. Cardinal-only is sufficient:
     * any diagonal-only connectivity decomposes into two cardinal moves
     * that this flood picks up either way.
     *
     * <p>Pairs with {@link #findFallbackPosition}'s candidate scan — both
     * are bounded to the same scan window so the flood can't grow beyond
     * what the candidate loop will consider. Edges are checked on both
     * sides (current cell's outgoing + neighbor's incoming), matching
     * {@link com.dillon.starsectormarines.battle.nav.GridPathfinder}'s
     * dual-side edge model.
     */
    private static boolean[] floodReachableFromSelf(NavigationGrid grid, int sx, int sy, int scanRange) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        boolean[] reachable = new boolean[w * h];
        if (!grid.inBounds(sx, sy) || !grid.isWalkable(sx, sy)) return reachable;
        int startIdx = grid.index(sx, sy);
        reachable[startIdx] = true;
        // Worst-case queue size = number of cells in the (2*scanRange+1)² window.
        int side = 2 * scanRange + 1;
        int[] queue = new int[side * side];
        int head = 0, tail = 0;
        queue[tail++] = startIdx;
        while (head < tail) {
            int idx = queue[head++];
            int cx = idx % w;
            int cy = idx / w;
            for (Direction dir : Direction.CARDINALS) {
                int nx = cx + dir.dx;
                int ny = cy + dir.dy;
                if (!grid.inBounds(nx, ny)) continue;
                if (Math.abs(nx - sx) > scanRange || Math.abs(ny - sy) > scanRange) continue;
                int nIdx = grid.index(nx, ny);
                if (reachable[nIdx]) continue;
                if (!grid.isWalkableAt(nIdx)) continue;
                if (!grid.isEdgePassable(cx, cy, dir)) continue;
                if (!grid.isEdgePassable(nx, ny, dir.opposite())) continue;
                reachable[nIdx] = true;
                queue[tail++] = nIdx;
            }
        }
        return reachable;
    }

    /**
     * Average alive-enemy cell from {@code self}'s perspective, or {@code
     * null} when there are no enemies. Used as the "don't march toward this"
     * reference for cardinal-neighbor ordering in fall-back consolation.
     */
    private int[] averageEnemyCell(long self) {
        float sumX = 0f, sumY = 0f;
        int count = 0;

        World world = roster.world();
        Faction selfFaction = roster.identity().faction(self);
        long[] dense = roster.denseArray();
        int liveCount = roster.liveCount();
        for (int i = 0; i < liveCount; i++) {
            long u = dense[i];
            if (roster.identity().faction(u) == selfFaction) continue;
            if (!roster.identity().type(u).combatant) continue;
            sumX += world.cellX(u);
            sumY += world.cellY(u);
            count++;
        }
        if (count == 0) return null;
        return new int[]{Math.round(sumX / count), Math.round(sumY / count)};
    }

    /**
     * Per-zone allies-minus-enemies from {@code self}'s perspective.
     * Indexed by zone id; positive = friendly-controlled, negative = hostile.
     * Computed once per {@link #findFallbackPosition} call so a 17×17 candidate
     * scan does at most O(zones + units) work instead of re-scanning units
     * per cell.
     */
    private int[] computeZoneControl(long self) {
        World world = roster.world();
        Faction selfFaction = roster.identity().faction(self);
        ZoneGraph zones = zoneGraph;
        int[] control = new int[zones.getZones().size()];

        long[] dense = roster.denseArray();
        int liveCount = roster.liveCount();
        for (int i = 0; i < liveCount; i++) {
            long u = dense[i];
            int zid = zones.zoneIdAt(world.cellX(u), world.cellY(u));
            if (zid < 0 || zid >= control.length) continue;
            control[zid] += (roster.identity().faction(u) == selfFaction) ? 1 : -1;
        }
        return control;
    }

    /**
     * Hidden iff no alive enemy combatant has effective LoS to {@code (cx, cy)}
     * — meaning a clear Bresenham line <em>and</em> the candidate cell within
     * that enemy's {@code world.attackRange(id)}. A 40-cell-range mech threatens
     * cells the 18-cell-range militia can't, which is the gameplay axis we
     * want fall-back picking to respect (squads flee mech LoS even if militia
     * LoS reads the same cell as "open").
     *
     * <p>Routes through the spatial index — gathers only enemies within
     * {@link #MAX_PLAUSIBLE_ATTACK_RANGE} of ({@code cx}, {@code cy}). Anyone
     * farther can't threaten the cell by construction.
     */
    public boolean isHiddenFromAllEnemies(long self, int cx, int cy) {

        World world = roster.world();
        Faction selfFaction = roster.identity().faction(self);
        LongBucket scratch = HIDDEN_ENEMY_CANDIDATES.get();
        unitIndex.gather(cx + 0.5f, cy + 0.5f, MAX_PLAUSIBLE_ATTACK_RANGE, scratch);
        for (int i = 0, n = scratch.size; i < n; i++) {
            long other = scratch.ids[i];
            if (roster.identity().faction(other) == selfFaction) continue;
            if (!roster.identity().type(other).combatant) continue;
            if (grid.hasLineOfSightWithin(cx, cy, world.cellX(other), world.cellY(other),
                    world.attackRange(other))) return false;
        }
        return true;
    }

    /**
     * True when {@code member}'s cached fall-back destination is unset or has
     * become visible to an enemy. Holds the cell while it's still hidden —
     * including after arrival — but a hide that gets exposed (threat
     * repositioned, picker landed on a borderline cell) re-rolls. The
     * picker's own {@code distFromSelf} bias absorbs the "don't scamper for
     * no reason" concern: if no neighbor scores meaningfully better, the
     * re-pick lands on the same cell.
     *
     * <p>Shared between
     * {@link com.dillon.starsectormarines.battle.decision.goap.action.BreakContact}
     * and {@link com.dillon.starsectormarines.battle.infantry.BreakLOS}
     * — both stash the picker's result on the AI_STATE fall-back cell
     * ({@code world.fallbackCellX(id)}/{@code world.fallbackCellY(id)}) and need
     * the same "re-roll when stale"
     * invariant. The SQ-17 stuck-defender dump exposed BreakLOS lacking
     * this check: once the cached cell drifted into enemy LoS the unit
     * was glued to it.
     */
    public boolean fallbackDestinationNeedsRefresh(long member) {
        World world = roster.world();
        int fx = world.fallbackCellX(member);
        int fy = world.fallbackCellY(member);
        if (fx < 0 || fy < 0) return true;
        return !isHiddenFromAllEnemies(member, fx, fy);
    }

    /**
     * Count of alive enemy combatants with effective LoS to {@code (cx, cy)} —
     * clear Bresenham line and within that enemy's {@code world.attackRange(id)}.
     * Zero means the cell is hidden from every effective threat
     * ({@link #isHiddenFromAllEnemies} returns true).
     *
     * <p>Used by {@link #findFallbackPosition} as the exposure term — folding
     * "how many <em>actually-threatening</em> guns see me" into the score is
     * what lets the picker pick the least-exposed cell when no hide exists,
     * instead of giving up.
     *
     * <p>The {@code BattleSimulation} overload allocates a scratch list per
     * call — fine for one-shot callers but the per-candidate hot path in
     * {@link #findFallbackPosition} uses the {@code threats}-list overload
     * to avoid re-gathering inside the cell loop.
     */
    public int countEnemiesWithLos(long self, int cx, int cy) {
        LongBucket scratch = new LongBucket();
        unitIndex.gather(cx + 0.5f, cy + 0.5f, MAX_PLAUSIBLE_ATTACK_RANGE, scratch);
        filterEnemyCombatants(scratch, roster.identity().faction(self));
        int n = scratch.size;
        int[] tcx = new int[n];
        int[] tcy = new int[n];
        float[] trange = new float[n];
        resolveThreatColumns(scratch, n, tcx, tcy, trange);
        return countEnemiesWithLos(cx, cy, tcx, tcy, trange, n, grid);
    }

    /**
     * Resolves each gathered enemy's cell + attack range into the caller's
     * parallel SoA scratch arrays via by-id world reads. Done once per fall-back
     * decision so the per-candidate exposure loop
     * ({@link #countEnemiesWithLos(int, int, int[], int[], float[], int, NavigationGrid)})
     * reads plain arrays with zero map probes — the cache-locality guardrail
     * for the {@code ~1089}-candidate scan. Threats come straight off a live
     * spatial gather, so every entry is registered.
     */
    private void resolveThreatColumns(LongBucket threats, int count,
                                      int[] outCellX, int[] outCellY, float[] outRange) {
        World world = roster.world();
        for (int i = 0; i < count; i++) {
            long t = threats.ids[i];
            outCellX[i] = world.cellX(t);
            outCellY[i] = world.cellY(t);
            outRange[i] = world.attackRange(t);
        }
    }

    /**
     * Pre-resolved overload — caller has gathered enemy combatants and
     * projected their cell + range into parallel arrays (via
     * {@link #resolveThreatColumns}). Counts how many can see {@code (cx, cy)}.
     * Used in {@link #findFallbackPosition}'s per-cell loop so we neither
     * re-query the spatial index nor probe the registry the {@code ~1089}
     * times per fall-back decision the cell scan would otherwise cost.
     */
    public static int countEnemiesWithLos(int cx, int cy,
                                          int[] threatCellX, int[] threatCellY, float[] threatRange,
                                          int threatCount, NavigationGrid grid) {
        int count = 0;
        for (int i = 0; i < threatCount; i++) {
            if (grid.hasLineOfSightWithin(cx, cy, threatCellX[i], threatCellY[i], threatRange[i])) count++;
        }
        return count;
    }

    /**
     * Drops every unit from {@code units} that isn't an alive combatant of a
     * different faction from {@code selfFaction}. Used after a spatial gather
     * to reduce the working set before tight LoS loops — the index returns
     * all units; this trims to "things that could threaten me." Compaction is
     * in-place to avoid a second allocation.
     */
    public void filterEnemyCombatants(LongBucket units, Faction selfFaction) {
        int write = 0;
        for (int i = 0, n = units.size; i < n; i++) {
            long u = units.ids[i];
            if (roster.identity().faction(u) == selfFaction) continue;
            if (!roster.identity().type(u).combatant) continue;
            units.ids[write++] = u;
        }
        // In-place truncate: LongBucket.size is the live count, so dropping it
        // to the write cursor discards the filtered-out tail (ids past size are
        // never read).
        units.size = write;
    }

    /**
     * Counts same-faction allies (excluding {@code self}) whose <em>current
     * cell or path destination</em> sits within {@link #FIRING_AOE_SPREAD_RADIUS}
     * of {@code (cx, cy)}. Used by firing-position scorers to discourage
     * AoE-survivable clustering — an ally with a path destination near the
     * candidate counts as a future occupant (their claim is the path-dest
     * occupancy from {@code setPath}, which eagerly updates
     * the map). Counting both current AND dest captures both "they're already
     * here" and "they're coming here" states without double-counting allies
     * that are at-rest at their destination.
     *
     * <p>Both halves route through bucketed spatial indices — the current-cell
     * half through {@code getUnitIndex()}, the path-destination
     * half through {@code getDestIndex()}. A radius-2 window
     * touches one or two buckets per index, so per-call cost is constant in
     * total unit count. The 2026-05-21 JFR profile flagged the previous O(N)
     * destination walk as the single hottest sim-side leaf (~15% of sim CPU);
     * the dest index drops it to the same O(units-with-path-near-radius)
     * complexity as Pass 1.
     */
    public int alliesNearForSpread(long self, int cx, int cy) {
        World world = roster.world();
        Faction selfFaction = roster.identity().faction(self);
        int r2 = FIRING_AOE_SPREAD_RADIUS * FIRING_AOE_SPREAD_RADIUS;
        int count = 0;
        LongBucket scratch = new LongBucket();
        // Pass 1 — units whose CURRENT cell is in the spread radius.
        unitIndex.gather(cx + 0.5f, cy + 0.5f, FIRING_AOE_SPREAD_RADIUS, scratch);
        for (int i = 0, n = scratch.size; i < n; i++) {
            long u = scratch.ids[i];
            if (u == self || roster.identity().faction(u) != selfFaction) continue;
            count++;
        }
        // Pass 2 — units whose path DESTINATION is in the spread radius.
        // Dest index excludes still units (dest == current) and pathless
        // units; the per-unit current-cell radius check below dedupes
        // against Pass 1 for moving units whose current happens to also
        // be near (cx, cy). The dest index is id-native — it gathers live
        // ids, and identity/position are read by id rather than off a handle.
        LongBucket destScratch = new LongBucket();
        destIndex.gather(roster, cx + 0.5f, cy + 0.5f, FIRING_AOE_SPREAD_RADIUS, destScratch);
        for (int i = 0, n = destScratch.size; i < n; i++) {
            long id = destScratch.ids[i];
            if (id == self || roster.identity().faction(id) != selfFaction) continue;
            // Dedupe against Pass 1 on the unit's CURRENT cell. Small gathered
            // set (path-dest within the spread radius), so the per-candidate
            // index resolve is decision-cadence, not a hot bulk loop.
            float dx = world.x(id) - (cx + 0.5f);
            float dy = world.y(id) - (cy + 0.5f);
            if (dx * dx + dy * dy <= r2) continue; // already counted via Pass 1
            count++;
        }
        return count;
    }

    /**
     * Occupancy count at cell (cx, cy), excluding self's own contributions
     * (current cell + path destination). Used so a unit doesn't penalize
     * itself when scoring its own current/intended position.
     */
    public int occupantsExcludingSelf(long self, int selfCellX, int selfCellY, int cx, int cy) {

        if (!grid.inBounds(cx, cy)) return 0;
        int n = occupancyMap[cy * grid.getWidth() + cx] & 0xFF;
        if (cx == selfCellX && cy == selfCellY) n--;
        World world = roster.world();
        int[] path = world.path(self);
        int cells = Paths.cellCount(path);
        if (cells > 0) {
            int destX = Paths.cellX(path, cells - 1);
            int destY = Paths.cellY(path, cells - 1);
            if (destX == cx && destY == cy && (destX != selfCellX || destY != selfCellY)) {
                n--;
            }
        }
        return Math.max(0, n);
    }

    /**
     * Euclidean distance in continuous cell space. Takes floats so callers can
     * pass true positions ({@code world.x/y}); int cell indices widen and keep
     * compiling — those sites carry a half-cell bias and are being swept to
     * true positions (phase 2c).
     */
    public static float cellDistance(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
