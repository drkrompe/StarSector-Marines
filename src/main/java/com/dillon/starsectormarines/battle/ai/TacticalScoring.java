package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.drone.DroneHubUnit;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitDestinationSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure scoring helpers used by behaviors to pick targets and positions.
 * Stateless; each call takes the sim plus the acting unit and computes a
 * fresh answer. Pulled out of {@link BattleSimulation} so behavior code
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

    private final NavigationService nav;
    private final NavigationGrid grid;
    private final UnitRosterService roster;
    private final UnitRegistry registry;
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
        this.registry = roster.getRegistry();
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
     * {@link com.dillon.starsectormarines.battle.ai.goap.goals.BreachToEngage}:
     * once there's no acceptable in-zone target, the cross-zone enemy wins
     * by default and BreachToEngage's relevance flips on.
     */
    public static final float TARGET_ZONE_MISMATCH_COST = 8f;

    /**
     * Multiplier on the weapon-target affinity term in {@link #findBestTarget}.
     * A marine's score for a hardened target gets {@code WEIGHT * (1 - vsHardenedMult)}
     * added — well-suited weapons (rockets, mult 3.5) earn a ~20-point bonus
     * toward the hardened target, poorly-suited weapons (rifles, mult 0.3)
     * eat a ~5-point penalty. With one visible target in LOS this is a no-op
     * (one candidate wins regardless); with multiple, it tilts the AT marine
     * toward the mech and the SMG marine toward the infantry.
     */
    public static final float WEAPON_AFFINITY_WEIGHT = 8f;

    /**
     * Seconds of unit travel that govern the fall-back candidate scan radius.
     * Multiplied by {@link Unit#moveSpeed} (cells/sec) and clamped to
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


    /**
     * Picks the lowest-scored enemy where score = cell-distance + a per-engager
     * crowding penalty (heavier for squadmates than for general allies). Prefers
     * visible targets; falls back to nearest of any LOS so the unit pathfinds
     * toward them and visibility eventually opens.
     */
    public Unit findBestTarget(Unit self) {
        return findBestTarget(self.getCellX(), self.getCellY(), self.faction, self.squadId, self,
                self.airLosRadius);
    }

    /**
     * Stickiness gate for {@code self.target}: keeps the current target only
     * while it's still shootable from {@code self}'s current cell (alive,
     * within {@link Unit#attackRange}, and with line of sight). Anything
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
    public Unit refreshTargetIfNotShootable(Unit self) {
        Unit cur = registry.getOrNull(self.getTargetId());
        if (cur != null) {
            float dist = cellDistance(self.getCellX(), self.getCellY(), cur.getCellX(), cur.getCellY());
            if (dist <= self.getAttackRange()
                    && grid.hasLineOfSight(self.getCellX(), self.getCellY(), cur.getCellX(), cur.getCellY())) {
                return cur;
            }
        }
        return findBestTarget(self);
    }

    /**
     * Primitive-args overload — used by callers that aren't a {@link Unit}
     * themselves (today: shuttle-mounted turrets, which live as data on a
     * {@link com.dillon.starsectormarines.battle.air.Shuttle} rather than as
     * grid entities). The selection logic is identical; pass {@link Unit#NO_SQUAD}
     * for {@code squadId} and {@code null} for {@code excludeFromCrowding}
     * when the caller doesn't squad up and isn't itself in the unit list.
     */
    public Unit findBestTarget(int selfCellX, int selfCellY, Faction selfFaction,
                               int selfSquadId, Unit excludeFromCrowding) {
        return findBestTarget(selfCellX, selfCellY, selfFaction, selfSquadId, excludeFromCrowding,
                0f);
    }

    /**
     * Air-LoS aware overload — when {@code shooterAirRadius > 0}, walls within
     * that many cells of the shooter's position are treated as transparent
     * (shuttle-mounted turrets hovering above a building's footprint). When
     * the candidate target has its own {@link Unit#airLosRadius} > 0 (drones),
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
     * {@link Unit} (the marine's own callers pass {@code self} here), hardened
     * targets (turrets + heavy mechs) get a per-marine score adjustment based
     * on {@code primary.vsTurretMult} / {@code secondary.vsTurretMult}.
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
    public Unit findBestTarget(int selfCellX, int selfCellY, Faction selfFaction,
                               int selfSquadId, Unit excludeFromCrowding,
                               float shooterAirRadius) {
        return findBestTarget(selfCellX, selfCellY, selfFaction, selfSquadId,
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
    public Unit findBestTarget(int selfCellX, int selfCellY, Faction selfFaction,
                               int selfSquadId, Unit excludeFromCrowding,
                               float shooterAirRadius, boolean allowNoLos) {
        long _profT0 = System.nanoTime();
        try {
            return findBestTargetImpl(selfCellX, selfCellY, selfFaction, selfSquadId,
                    excludeFromCrowding, shooterAirRadius, allowNoLos);
        } finally {
            TickInnerProfile p = TickInnerProfile.current();
            if (p != null) p.record(TickInnerProfile.Bucket.TARGET_PICK, System.nanoTime() - _profT0);
        }
    }

    private Unit findBestTargetImpl(int selfCellX, int selfCellY, Faction selfFaction,
                                     int selfSquadId, Unit excludeFromCrowding,
                                     float shooterAirRadius, boolean allowNoLos) {
        // SoA consumer: dense iteration over [0, liveCount()) implicitly
        // excludes released slots (no isAlive() filter inside the loop), and
        // cellX/cellY reads stream from the int[] arrays in tandem.

        Unit[] dense = registry.denseArray();
        int[] cellX = registry.cellXArray();
        int[] cellY = registry.cellYArray();
        int liveCount = registry.liveCount();

        Unit best = null;
        float bestScore = Float.MAX_VALUE;
        Unit bestAny = null;
        float bestAnyDist = Float.MAX_VALUE;

        for (int i = 0; i < liveCount; i++) {
            Unit other = dense[i];
            if (other.faction == selfFaction) continue;
            // Civilians and other non-combatants don't draw fire — they're
            // bystanders. A separate "rules of engagement" toggle could relax
            // this for pirate atrocity scenarios later.
            if (!other.type.combatant) continue;

            int ox = cellX[i];
            int oy = cellY[i];
            float d = cellDistance(selfCellX, selfCellY, ox, oy);
            if (d < bestAnyDist) {
                bestAnyDist = d;
                bestAny = other;
            }
            boolean visible = canSeePair(grid, selfCellX, selfCellY, ox, oy,
                    shooterAirRadius, other.airLosRadius);
            if (!visible && !allowNoLos) continue;
            float crowding = scoreCrowding(selfFaction, selfSquadId, other, excludeFromCrowding);
            float density = scoreThreatDensity(other, selfFaction);
            float affinity = scoreWeaponAffinity(excludeFromCrowding, other);
            float zoneMismatch = scoreZoneMismatch(selfCellX, selfCellY, other);
            float score = d + crowding + density + affinity + zoneMismatch;
            if (!visible) score += TARGET_NO_LOS_COST;
            if (score < bestScore) {
                bestScore = score;
                best = other;
            }
        }
        return best != null ? best : bestAny;
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
     * weapon {@code vsTurretMult} treated as an affinity: rockets (3.5×) earn
     * a strong negative adjustment (bonus); rifles (0.3×) earn a positive one
     * (penalty). Soft targets are baseline (no adjustment).
     *
     * <p>{@code self} is nullable for non-Unit callers (shuttle / static
     * turrets) — they get no affinity term.
     */
    private static float scoreWeaponAffinity(Unit self, Unit target) {
        if (self == null) return 0f;
        if (!isHardened(target)) return 0f;
        float primary = self.primaryWeapon != null ? self.primaryWeapon.vsTurretMult : 0.3f;
        float secondary = (self.secondaryWeapon != null && self.secondaryAmmo > 0)
                ? self.secondaryWeapon.vsTurretMult : 0f;
        float bestMult = Math.max(primary, secondary);
        return WEAPON_AFFINITY_WEIGHT * (1f - bestMult);
    }

    /**
     * Returns {@link #TARGET_ZONE_MISMATCH_COST} when the candidate's cell is
     * in a different navigation zone from the shooter's cell, 0 otherwise.
     * Zones with id {@code < 0} (out-of-bounds / unwalkable, shouldn't happen
     * for live combatants) read as "no zone" — treated as matching so weird
     * edge cases don't accidentally amplify the bias.
     */
    private float scoreZoneMismatch(int selfCellX, int selfCellY, Unit candidate) {
        int selfZone = zoneGraph.zoneIdAt(selfCellX, selfCellY);
        int targetZone = zoneGraph.zoneIdAt(candidate.getCellX(), candidate.getCellY());
        if (selfZone < 0 || targetZone < 0) return 0f;
        return selfZone == targetZone ? 0f : TARGET_ZONE_MISMATCH_COST;
    }

    /**
     * Hardened target classification — counts static emplacements
     * ({@link MapTurret}, {@link DroneHubUnit}) and heavy mechs. Anything else
     * (infantry archetypes, aliens, militia) is soft. Drives the weapon-affinity
     * bias in {@link #findBestTarget} (rocketeers prefer hardened) and the
     * rocket-eligibility gates in {@link com.dillon.starsectormarines.battle.ai.InfantryUnitPrep#tryOpportunityRocket}
     * and {@link com.dillon.starsectormarines.battle.ai.goap.actions.EngagePosture} —
     * marines burn a rocket on anything that earns the {@code vsTurretMult}
     * (3.5×) bonus payoff.
     */
    public static boolean isHardened(Unit target) {
        if (target instanceof MapTurret) return true;
        if (target instanceof DroneHubUnit) return true;
        return target.type == UnitType.HEAVY_MECH;
    }

    /**
     * True when {@code shooter} carries a loaded rocket and {@code target} is
     * a hardened class ({@link MapTurret}, {@link DroneHubUnit}, heavy mech) —
     * the pairings where the rocket's {@code vsTurretMult} bonus damage pays
     * off. Centralizes the check used by {@link #effectiveAttackRange}.
     */
    public static boolean canRocketTarget(Unit shooter, Unit target) {
        return isHardened(target)
                && shooter.secondaryWeapon != null
                && shooter.secondaryAmmo > 0;
    }

    /**
     * Effective engagement range for {@code shooter} against {@code target} —
     * primary range, unless the shooter can rocket the target (hardened class
     * + loaded tube), in which case the rocket's longer range wins. Used by
     * {@link com.dillon.starsectormarines.battle.ai.goap.actions.EngagePosture}'s
     * act-here gate and the firing-position picker so a rocketeer doesn't have
     * to close to rifle range before firing.
     */
    public static float effectiveAttackRange(Unit shooter, Unit target) {
        if (canRocketTarget(shooter, target)) {
            return Math.max(shooter.getAttackRange(), shooter.secondaryWeapon.range);
        }
        return shooter.getAttackRange();
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
     * just the damage projection. Pass any {@link Unit}; the per-target HP
     * read works on the full unit hierarchy ({@code Unit.getHp()} is the
     * canonical SoA-routed accessor for both regular units and turrets).
     *
     * <p>{@code shooter} is excluded from the projection so the same marine
     * re-checking on a later tick (after his own cooldown) isn't blocked by
     * his own prior contribution.
     */
    public boolean shouldCommitRocket(Unit shooter, Unit target) {
        if (shooter.secondaryWeapon == null || shooter.secondaryAmmo <= 0) return false;
        if (target == null || !target.isAlive()) return false;
        return projectedRocketDamageOnTarget(shooter, target) < target.getHp();
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
    private float projectedRocketDamageOnTarget(Unit shooter, Unit target) {
        float total = 0f;
        if (shooter.squadId != Unit.NO_SQUAD) {
            for (Unit u : roster.getUnits()) {
                if (u == shooter) continue;
                if (u.squadId != shooter.squadId) continue;
                if (!u.isAlive()) continue;
                if (u.secondaryWeapon == null) continue;
                if (u.getSecondaryActionTimer() <= 0f) continue;
                if (u.getSecondaryAimTargetId() != target.entityId) continue;
                total += u.secondaryWeapon.damage * u.secondaryWeapon.vsTurretMult;
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
        float targetCx = target.getCellX() + 0.5f;
        float targetCy = target.getCellY() + 0.5f;
        for (Projectile p : shots.snapshotActiveProjectiles()) {
            if (p.shooterFaction != shooter.faction) continue;
            PendingDetonation det = p.onArrival;
            if (det == null) continue;
            float dx = targetCx - det.endpointX;
            float dy = targetCy - det.endpointY;
            if (dx * dx + dy * dy <= det.aoeRadius * det.aoeRadius) {
                total += det.damage * det.vsTurretMult;
            }
        }
        return total;
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
    private float scoreThreatDensity(Unit candidate, Faction selfFaction) {
        ArrayList<Unit> scratch = new ArrayList<>();
        unitIndex.gather(candidate.getCellX(), candidate.getCellY(), THREAT_DENSITY_RADIUS, scratch);
        int count = 0;
        for (int i = 0, n = scratch.size(); i < n; i++) {
            Unit other = scratch.get(i);
            if (other == candidate) continue;
            if (other.faction == selfFaction) continue;
            if (!other.type.combatant) continue;
            count++;
        }
        return count * TARGET_THREAT_DENSITY_COST;
    }

    /**
     * Score margin (in cell-distance) by which a different visible enemy must
     * beat the current target before the pursuit gate switches. Prevents
     * thrashing on marginal differences while still flipping promptly when a
     * close mech walks up next to a marine engaged on a distant turret.
     */
    public static final float RETARGET_DISTANCE_MARGIN = 5f;

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
     *   <li>LOS is broken to the current target <em>and</em> it now sits
     *       inside a non-trivial threat-density cluster — Story I bail-out
     *       to prevent chasing a fleer into their squad.</li>
     * </ul>
     */
    public boolean shouldKeepPursuing(Unit self, Unit currentTarget) {
        if (currentTarget == null || !currentTarget.isAlive()) return false;
        boolean visible = canSeePair(grid, self.getCellX(), self.getCellY(),
                currentTarget.getCellX(), currentTarget.getCellY(),
                self.airLosRadius, currentTarget.airLosRadius);

        // "Meaningfully closer visible enemy" check — runs whether or not the
        // current target is visible. If current is invisible and a visible
        // alternative exists, switch unconditionally. If current is visible,
        // switch only when the alternative is closer by at least
        // RETARGET_DISTANCE_MARGIN to dampen thrashing.
        Unit closerVisible = closestVisibleOtherEnemy(self, currentTarget);
        if (closerVisible != null) {
            if (!visible) return false;
            float currentDist = cellDistance(self.getCellX(), self.getCellY(),
                    currentTarget.getCellX(), currentTarget.getCellY());
            float candidateDist = cellDistance(self.getCellX(), self.getCellY(),
                    closerVisible.getCellX(), closerVisible.getCellY());
            if (candidateDist + RETARGET_DISTANCE_MARGIN < currentDist) return false;
        }

        if (visible) return true;

        // LOS lost — bail out if the target ducked into a cluster. Density
        // count > 1 means "at least 2 of their squadmates nearby" — chasing
        // into that costs more than the dropped target is worth.
        int r2 = THREAT_DENSITY_RADIUS * THREAT_DENSITY_RADIUS;
        int density = 0;

        Unit[] dense = registry.denseArray();
        int[] cellX = registry.cellXArray();
        int[] cellY = registry.cellYArray();
        int liveCount = registry.liveCount();
        int tx = currentTarget.getCellX();
        int ty = currentTarget.getCellY();
        for (int i = 0; i < liveCount; i++) {
            Unit other = dense[i];
            if (other == currentTarget) continue;
            if (other.faction == self.faction) continue;
            if (!other.type.combatant) continue;
            int dx = cellX[i] - tx;
            int dy = cellY[i] - ty;
            if (dx * dx + dy * dy <= r2) {
                density++;
                if (density > 1) return false;
            }
        }
        return true;
    }

    /**
     * Nearest visible enemy combatant to {@code self} that isn't
     * {@code exclude}, or null when none. Used by {@link #shouldKeepPursuing}'s
     * "closer visible target appeared" check. Linear scan; the caller pays
     * once per posture tick.
     */
    private Unit closestVisibleOtherEnemy(Unit self, Unit exclude) {
        Unit best = null;
        float bestDist = Float.MAX_VALUE;


        Unit[] dense = registry.denseArray();
        int[] cellX = registry.cellXArray();
        int[] cellY = registry.cellYArray();
        int liveCount = registry.liveCount();
        int sx = self.getCellX();
        int sy = self.getCellY();
        for (int i = 0; i < liveCount; i++) {
            Unit u = dense[i];
            if (u == exclude || u == self) continue;
            if (u.faction == self.faction || !u.type.combatant) continue;
            int ux = cellX[i];
            int uy = cellY[i];
            if (!canSeePair(grid, sx, sy, ux, uy,
                    self.airLosRadius, u.airLosRadius)) continue;
            float d = cellDistance(sx, sy, ux, uy);
            if (d < bestDist) {
                bestDist = d;
                best = u;
            }
        }
        return best;
    }

    /**
     * Adds {@link #TARGET_CROWDING_COST} for every ally targeting the same
     * enemy, plus an additional {@link #TARGET_SQUADMATE_EXTRA_COST} when the
     * ally is a squadmate. Reads the precomputed attackers-by-target index
     * from the sim ({@link BattleSimulation#getAttackersOf}) so this is O(L)
     * in the small attacker list rather than O(U) over every unit — total
     * target-selection cost drops from O(U³) to O(U² + U·L).
     */
    private float scoreCrowding(Faction selfFaction, int selfSquadId, Unit target,
                                Unit exclude) {
        java.util.ArrayList<Unit> attackers = attackerIndex.getAttackersOf(target);
        if (attackers == null) return 0f;
        float cost = 0f;
        for (int i = 0, n = attackers.size(); i < n; i++) {
            Unit u = attackers.get(i);
            if (u == exclude || !u.isAlive()) continue;
            if (u.faction != selfFaction) continue;
            cost += TARGET_CROWDING_COST;
            if (selfSquadId != Unit.NO_SQUAD && u.squadId == selfSquadId) {
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
     * from {@link BattleSimulation#getVantagePointsFor}. Vantages are walkable
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
    public int[] findFiringPosition(Unit self, Unit target) {
        return findFiringPosition(self, target, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    /**
     * Constrained firing-position search — like {@link #findFiringPosition} but
     * rejects any candidate whose cell-distance from ({@code anchorX},
     * {@code anchorY}) exceeds {@code maxDistFromAnchor}. Used by
     * {@link com.dillon.starsectormarines.battle.ai.goap.actions.HoldPost} to keep engaged defenders within a tight
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
     * {@link com.dillon.starsectormarines.battle.ai.goap.actions.HoldPost} to retarget when the unit's current target is
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
    public Unit findEngageableEnemyWithin(Unit self,
                                          int anchorX, int anchorY,
                                          float maxDistFromAnchor) {
        float maxWeaponReach = self.getAttackRange();
        if (self.secondaryWeapon != null && self.secondaryAmmo > 0) {
            maxWeaponReach = Math.max(maxWeaponReach, self.secondaryWeapon.range);
        }
        float gatherRadius = maxDistFromAnchor + maxWeaponReach;
        ArrayList<Unit> scratch = new ArrayList<>();
        unitIndex.gather(anchorX, anchorY, gatherRadius, scratch);
        Unit best = null;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0, n = scratch.size(); i < n; i++) {
            Unit enemy = scratch.get(i);
            if (!enemy.isAlive() || !enemy.type.combatant) continue;
            if (enemy.faction == self.faction) continue;
            int[] pos = findFiringPositionWithin(self, enemy, anchorX, anchorY, maxDistFromAnchor);
            if (pos == null) continue;
            float d = cellDistance(self.getCellX(), self.getCellY(), enemy.getCellX(), enemy.getCellY());
            if (d < bestDist) {
                bestDist = d;
                best = enemy;
            }
        }
        return best;
    }

    public int[] findFiringPositionWithin(Unit self, Unit target,
                                          int anchorX, int anchorY, float maxDistFromAnchor) {
        long _profT0 = System.nanoTime();
        try {
            return findFiringPositionWithinImpl(self, target, anchorX, anchorY, maxDistFromAnchor);
        } finally {
            TickInnerProfile p = TickInnerProfile.current();
            if (p != null) p.record(TickInnerProfile.Bucket.FIRING_POSITION, System.nanoTime() - _profT0);
        }
    }

    private int[] findFiringPositionWithinImpl(Unit self, Unit target,
                                                int anchorX, int anchorY, float maxDistFromAnchor) {

        // Rocketeer-vs-turret pairs search a ring sized to the rocket's range —
        // otherwise an out-of-rifle-range marine paths into rifle range before
        // ever firing the rocket. Inner range check uses the same effective
        // range so candidate cells are valid for whatever weapon will fire.
        float effectiveRange = effectiveAttackRange(self, target);
        int range = Math.max(1, (int) Math.floor(effectiveRange));
        int tx = target.getCellX();
        int ty = target.getCellY();

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
                if (!canSeePair(grid, cx, cy, tx, ty, self.airLosRadius, target.airLosRadius)) continue;
                if (cellDistance(anchorX, anchorY, cx, cy) > maxDistFromAnchor) continue;

                int occupants = occupantsExcludingSelf(self, cx, cy);
                int alliesNear = alliesNearForSpread(self, cx, cy);
                // Cover lookup is directional against the target (the
                // upcoming threat from this firing position) — Story G.
                int fdx = tx - cx;
                int fdy = ty - cy;
                int cover = grid.getCoverAt(cx, cy, fdx, fdy);
                int doodadCover = doodads.getDoodadCoverAt(cx, cy, fdx, fdy);
                float distFromSelf = cellDistance(self.getCellX(), self.getCellY(), cx, cy);
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

    public int[] findFiringPosition(Unit self, Unit target, int rejectX, int rejectY) {
        long _profT0 = System.nanoTime();
        try {
            return findFiringPositionImpl(self, target, rejectX, rejectY);
        } finally {
            TickInnerProfile p = TickInnerProfile.current();
            if (p != null) p.record(TickInnerProfile.Bucket.FIRING_POSITION, System.nanoTime() - _profT0);
        }
    }

    private int[] findFiringPositionImpl(Unit self, Unit target, int rejectX, int rejectY) {

        // See findFiringPositionWithin — rocketeer-vs-turret widens the ring.
        float effectiveRange = effectiveAttackRange(self, target);
        int range = Math.max(1, (int) Math.floor(effectiveRange));
        int tx = target.getCellX();
        int ty = target.getCellY();

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
                if (!canSeePair(grid, cx, cy, tx, ty, self.airLosRadius, target.airLosRadius)) continue;

                int occupants = occupantsExcludingSelf(self, cx, cy);
                int alliesNear = alliesNearForSpread(self, cx, cy);
                // Per-facing cover against the target (Story G).
                int fdx = tx - cx;
                int fdy = ty - cy;
                int cover = grid.getCoverAt(cx, cy, fdx, fdy);
                int doodadCover = doodads.getDoodadCoverAt(cx, cy, fdx, fdy);
                float distFromSelf = cellDistance(self.getCellX(), self.getCellY(), cx, cy);
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
    private int[] pickReachableVantage(Unit self, Unit target) {
        int[][] vantages = nav.getVantagePointsFor(target.getCellX(), target.getCellY());
        if (vantages.length == 0) return null;

        int n = vantages.length;
        // Sort by Euclidean distance from self. n is bounded by
        // (2 * MAX_VANTAGE_SEARCH_RADIUS + 1)^2 ≈ 1681 worst case but usually
        // <100 after the walkability + LOS filters in computeVantagePoints.
        // Indirect-sort to avoid mutating the cached array.
        int[] order = new int[n];
        float[] dist = new float[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
            int dx = vantages[i][0] - self.getCellX();
            int dy = vantages[i][1] - self.getCellY();
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
                    self.getCellX(), self.getCellY(), cell[0], cell[1], occupancyMap);
            if (path.length > 0) return cell;
        }
        return null;
    }

    /**
     * Computes the vantage-point set for cell ({@code tx}, {@code ty}) —
     * walkable cells within {@link #MAX_VANTAGE_SEARCH_RADIUS} that have
     * line of sight to ({@code tx}, {@code ty}). Pure function of the grid;
     * called by {@link BattleSimulation#getVantagePointsFor} on cache miss.
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
     * Cover-aware variant of {@link #findFiringPosition(Unit, Unit, int, int)} —
     * filters the candidate ring to cells whose combined (cell + doodad) cover
     * meets or exceeds the unit's current combined cover against the same
     * threat direction. When no candidate meets that threshold, returns
     * {@code null}: callers (Story G's {@link com.dillon.starsectormarines.battle.ai.goap.actions.RepositionToCover})
     * treat that as "hold position, current cover is best."
     *
     * <p>Compared with the unfiltered {@link #findFiringPosition}, this won't
     * downgrade — a marine in heavy cover only moves to a cell with at least
     * equal cover. That's the "MG-in-heavy-cover stays cozy" half of Story G,
     * paired with the cooldown gate inside RepositionToCover.
     */
    public int[] findFiringPositionCoverPreferred(Unit self, Unit target,
                                                   int rejectX, int rejectY) {
        long _profT0 = System.nanoTime();
        try {
            return findFiringPositionCoverPreferredImpl(self, target, rejectX, rejectY);
        } finally {
            TickInnerProfile p = TickInnerProfile.current();
            if (p != null) p.record(TickInnerProfile.Bucket.FIRING_POSITION, System.nanoTime() - _profT0);
        }
    }

    private int[] findFiringPositionCoverPreferredImpl(Unit self, Unit target,
                                                        int rejectX, int rejectY) {

        int range = Math.max(1, (int) Math.floor(self.getAttackRange()));
        int tx = target.getCellX();
        int ty = target.getCellY();
        // Self's current cover against the target — per-facing, so a
        // marine already in heavy cover from this threat direction won't
        // downgrade to a cell that lacks that specific facing.
        int selfFdx = tx - self.getCellX();
        int selfFdy = ty - self.getCellY();
        int selfCover = grid.getCoverAt(self.getCellX(), self.getCellY(), selfFdx, selfFdy)
                      + doodads.getDoodadCoverAt(self.getCellX(), self.getCellY(), selfFdx, selfFdy);

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
                if (distFromTarget > self.getAttackRange()) continue;
                if (distFromTarget < FIRING_MIN_DISTANCE) continue;
                if (!canSeePair(grid, cx, cy, tx, ty, self.airLosRadius, target.airLosRadius)) continue;

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

                int occupants = occupantsExcludingSelf(self, cx, cy);
                int alliesNear = alliesNearForSpread(self, cx, cy);
                float distFromSelf = cellDistance(self.getCellX(), self.getCellY(), cx, cy);
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
     * {@code self.moveSpeed * FALLBACK_SCAN_SECONDS} clamped to
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
    public int[] findFallbackPosition(Unit self) {
        long _profT0 = System.nanoTime();
        try {
            return findFallbackPositionImpl(self);
        } finally {
            TickInnerProfile p = TickInnerProfile.current();
            if (p != null) p.record(TickInnerProfile.Bucket.FALLBACK_POSITION, System.nanoTime() - _profT0);
        }
    }

    private int[] findFallbackPositionImpl(Unit self) {

        ZoneGraph zones = zoneGraph;
        int sx = self.getCellX();
        int sy = self.getCellY();
        int[] zoneControl = computeZoneControl(self);

        int[] threatRef = averageEnemyCell(self);
        if (threatRef == null) return new int[]{sx, sy};
        float selfDistFromThreat = cellDistance(sx, sy, threatRef[0], threatRef[1]);

        int scanRange = Math.max(FALLBACK_SCAN_RANGE_MIN,
                       Math.min(FALLBACK_SCAN_RANGE_MAX,
                                Math.round(self.moveSpeed * FALLBACK_SCAN_SECONDS)));

        // Pre-gather every enemy that could threaten any candidate cell, once.
        // The radius bound is "candidate-furthest-from-self" + "enemy with the
        // longest plausible attackRange" — anyone farther can't reach a cell
        // in the scan, so excluding them is exact, not approximate. Replaces
        // the O(scanRange² × totalUnits) inner loop with O(K) per candidate
        // where K is the nearby-enemy count.
        ArrayList<Unit> threats = new ArrayList<>();
        unitIndex.gather(sx, sy, scanRange + MAX_PLAUSIBLE_ATTACK_RANGE, threats);
        filterEnemyCombatants(threats, self.faction);

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

                int occupants = occupantsExcludingSelf(self, cx, cy);
                int fdx = threatRef[0] - cx;
                int fdy = threatRef[1] - cy;
                int gridCover   = grid.getCoverAt(cx, cy, fdx, fdy);
                int doodadCover = doodads.getDoodadCoverAt(cx, cy, fdx, fdy);
                int exposure = countEnemiesWithLos(cx, cy, threats, grid);
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
    private int[] averageEnemyCell(Unit self) {
        float sumX = 0f, sumY = 0f;
        int count = 0;

        Unit[] dense = registry.denseArray();
        int[] cellX = registry.cellXArray();
        int[] cellY = registry.cellYArray();
        int liveCount = registry.liveCount();
        for (int i = 0; i < liveCount; i++) {
            Unit u = dense[i];
            if (u.faction == self.faction) continue;
            if (!u.type.combatant) continue;
            sumX += cellX[i];
            sumY += cellY[i];
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
    private int[] computeZoneControl(Unit self) {
        ZoneGraph zones = zoneGraph;
        int[] control = new int[zones.getZones().size()];

        Unit[] dense = registry.denseArray();
        int[] cellX = registry.cellXArray();
        int[] cellY = registry.cellYArray();
        int liveCount = registry.liveCount();
        for (int i = 0; i < liveCount; i++) {
            Unit u = dense[i];
            int zid = zones.zoneIdAt(cellX[i], cellY[i]);
            if (zid < 0 || zid >= control.length) continue;
            control[zid] += (u.faction == self.faction) ? 1 : -1;
        }
        return control;
    }

    /**
     * Hidden iff no alive enemy combatant has effective LoS to {@code (cx, cy)}
     * — meaning a clear Bresenham line <em>and</em> the candidate cell within
     * that enemy's {@link Unit#attackRange}. A 40-cell-range mech threatens
     * cells the 18-cell-range militia can't, which is the gameplay axis we
     * want fall-back picking to respect (squads flee mech LoS even if militia
     * LoS reads the same cell as "open").
     *
     * <p>Routes through the spatial index — gathers only enemies within
     * {@link #MAX_PLAUSIBLE_ATTACK_RANGE} of ({@code cx}, {@code cy}). Anyone
     * farther can't threaten the cell by construction.
     */
    public boolean isHiddenFromAllEnemies(Unit self, int cx, int cy) {

        ArrayList<Unit> scratch = new ArrayList<>();
        unitIndex.gather(cx, cy, MAX_PLAUSIBLE_ATTACK_RANGE, scratch);
        for (int i = 0, n = scratch.size(); i < n; i++) {
            Unit other = scratch.get(i);
            if (other.faction == self.faction) continue;
            if (!other.type.combatant) continue;
            if (grid.hasLineOfSightWithin(cx, cy, other.getCellX(), other.getCellY(), other.getAttackRange())) return false;
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
     * {@link com.dillon.starsectormarines.battle.ai.goap.actions.BreakContact}
     * and {@link com.dillon.starsectormarines.battle.ai.goap.actions.BreakLOS}
     * — both stash the picker's result on {@link Unit#getFallbackCellX()}/
     * {@link Unit#getFallbackCellY()} and need the same "re-roll when stale"
     * invariant. The SQ-17 stuck-defender dump exposed BreakLOS lacking
     * this check: once the cached cell drifted into enemy LoS the unit
     * was glued to it.
     */
    public boolean fallbackDestinationNeedsRefresh(Unit member) {
        if (member.getFallbackCellX() < 0 || member.getFallbackCellY() < 0) return true;
        return !isHiddenFromAllEnemies(member, member.getFallbackCellX(), member.getFallbackCellY());
    }

    /**
     * Count of alive enemy combatants with effective LoS to {@code (cx, cy)} —
     * clear Bresenham line and within that enemy's {@link Unit#attackRange}.
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
    public int countEnemiesWithLos(Unit self, int cx, int cy) {
        ArrayList<Unit> scratch = new ArrayList<>();
        unitIndex.gather(cx, cy, MAX_PLAUSIBLE_ATTACK_RANGE, scratch);
        filterEnemyCombatants(scratch, self.faction);
        return countEnemiesWithLos(cx, cy, scratch, grid);
    }

    /**
     * Pre-filtered overload — caller has already gathered enemy combatants
     * and is asking "how many of these can see {@code (cx, cy)}." Used in
     * {@link #findFallbackPosition}'s per-cell loop so we don't re-query the
     * spatial index 1089 times per fallback decision.
     */
    public static int countEnemiesWithLos(int cx, int cy, List<Unit> threats, NavigationGrid grid) {
        int count = 0;
        for (int i = 0, n = threats.size(); i < n; i++) {
            Unit other = threats.get(i);
            if (grid.hasLineOfSightWithin(cx, cy, other.getCellX(), other.getCellY(), other.getAttackRange())) count++;
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
    public static void filterEnemyCombatants(ArrayList<Unit> units, Faction selfFaction) {
        int write = 0;
        for (int i = 0, n = units.size(); i < n; i++) {
            Unit u = units.get(i);
            if (u.faction == selfFaction) continue;
            if (!u.type.combatant) continue;
            units.set(write++, u);
        }
        // Trim the tail. ArrayList.subList(write, size).clear() is the
        // idiomatic in-place truncate.
        if (write < units.size()) units.subList(write, units.size()).clear();
    }

    /**
     * Counts same-faction allies (excluding {@code self}) whose <em>current
     * cell or path destination</em> sits within {@link #FIRING_AOE_SPREAD_RADIUS}
     * of {@code (cx, cy)}. Used by firing-position scorers to discourage
     * AoE-survivable clustering — an ally with a path destination near the
     * candidate counts as a future occupant (their claim is the path-dest
     * occupancy from {@link BattleSimulation#setPath}, which eagerly updates
     * the map). Counting both current AND dest captures both "they're already
     * here" and "they're coming here" states without double-counting allies
     * that are at-rest at their destination.
     *
     * <p>Both halves route through bucketed spatial indices — the current-cell
     * half through {@link BattleSimulation#getUnitIndex()}, the path-destination
     * half through {@link BattleSimulation#getDestIndex()}. A radius-2 window
     * touches one or two buckets per index, so per-call cost is constant in
     * total unit count. The 2026-05-21 JFR profile flagged the previous O(N)
     * destination walk as the single hottest sim-side leaf (~15% of sim CPU);
     * the dest index drops it to the same O(units-with-path-near-radius)
     * complexity as Pass 1.
     */
    public int alliesNearForSpread(Unit self, int cx, int cy) {
        int r2 = FIRING_AOE_SPREAD_RADIUS * FIRING_AOE_SPREAD_RADIUS;
        int count = 0;
        ArrayList<Unit> scratch = new ArrayList<>();
        // Pass 1 — units whose CURRENT cell is in the spread radius.
        unitIndex.gather(cx, cy, FIRING_AOE_SPREAD_RADIUS, scratch);
        for (int i = 0, n = scratch.size(); i < n; i++) {
            Unit u = scratch.get(i);
            if (u == self || u.faction != self.faction) continue;
            count++;
        }
        // Pass 2 — units whose path DESTINATION is in the spread radius.
        // Dest index excludes still units (dest == current) and pathless
        // units; the per-unit current-cell radius check below dedupes
        // against Pass 1 for moving units whose current happens to also
        // be near (cx, cy).
        destIndex.gather(cx, cy, FIRING_AOE_SPREAD_RADIUS, scratch);
        for (int i = 0, n = scratch.size(); i < n; i++) {
            Unit u = scratch.get(i);
            if (u == self || u.faction != self.faction) continue;
            int dx = u.getCellX() - cx;
            int dy = u.getCellY() - cy;
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
    public int occupantsExcludingSelf(Unit self, int cx, int cy) {

        if (!grid.inBounds(cx, cy)) return 0;
        int n = occupancyMap[cy * grid.getWidth() + cx] & 0xFF;
        if (cx == self.getCellX() && cy == self.getCellY()) n--;
        int cells = self.pathCellCount();
        if (cells > 0) {
            int destX = self.pathCellX(cells - 1);
            int destY = self.pathCellY(cells - 1);
            if (destX == cx && destY == cy && (destX != self.getCellX() || destY != self.getCellY())) {
                n--;
            }
        }
        return Math.max(0, n);
    }

    public static float cellDistance(int x0, int y0, int x1, int y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
