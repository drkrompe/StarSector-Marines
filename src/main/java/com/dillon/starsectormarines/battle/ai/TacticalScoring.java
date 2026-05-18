package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.MapTurret;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;

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

    /** Per-engaging-ally penalty added to target selection — pushes the squad to spread fire instead of dogpiling. */
    public static final float TARGET_CROWDING_COST = 6f;
    /** Extra penalty when the engager is a squadmate. Real fireteams cover sectors, not the same enemy. */
    public static final float TARGET_SQUADMATE_EXTRA_COST = 6f;

    /** Per-ally-on-cell penalty added when picking a firing position around a target — pushes units into a spread ring. */
    public static final float FIRING_OCCUPANCY_COST = 4f;
    /** Minimum cell-distance from target when picking a firing position. Avoids picking the target's own cell. */
    public static final float FIRING_MIN_DISTANCE = 0.7f;
    /** Per-cover-level bonus subtracted from firing-position score. Pushes units to peek from corners and wall edges. */
    public static final float FIRING_COVER_BONUS = 3f;
    /** Per-cover-level bonus from doodad cover (crates, shelves) on the candidate cell. Lower weight than wall cover because doodads don't fully break LOS — they're concealment, not full intervening geometry. */
    public static final float FIRING_DOODAD_COVER_BONUS = 1.5f;
    /** Radius (cells) over which {@link #findBestTarget} counts neighboring enemies to penalize cluster targets. */
    public static final int THREAT_DENSITY_RADIUS = 4;
    /** Per-neighbor-enemy penalty added to a target's score. Pursuing one fleer into 3 squadmates costs roughly the same as walking ~20 extra cells. */
    public static final float TARGET_THREAT_DENSITY_COST = 5f;

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

    /** Cell radius searched for a fall-back position around the hit unit. */
    public static final int   FALLBACK_SCAN_RANGE = 8;
    /** Per-ally-on-cell penalty in fall-back scoring. */
    public static final float FALLBACK_OCCUPANCY_COST = 4f;
    /** Per-cover-level bonus in fall-back scoring. */
    public static final float FALLBACK_COVER_BONUS    = 2f;
    /** Bonus subtracted from fall-back score per net ally in the candidate cell's zone. Pulls retreating units toward where their squad lives rather than the nearest blind corner. */
    public static final float FALLBACK_FRIENDLY_ZONE_BONUS = 1.5f;

    private TacticalScoring() {}

    /**
     * Four-arg line-of-sight predicate so callers can supply a non-standard
     * LoS rule (today: shuttle-mounted "air" turrets that ignore walls
     * within a few cells of their flying origin). The {@link Unit} overload
     * and the primitive overload both default to {@code grid::hasLineOfSight}.
     */
    @FunctionalInterface
    public interface LosTest {
        boolean visible(int fromX, int fromY, int toX, int toY);
    }

    /**
     * Picks the lowest-scored enemy where score = cell-distance + a per-engager
     * crowding penalty (heavier for squadmates than for general allies). Prefers
     * visible targets; falls back to nearest of any LOS so the unit pathfinds
     * toward them and visibility eventually opens.
     */
    public static Unit findBestTarget(Unit self, BattleSimulation sim) {
        return findBestTarget(self.cellX, self.cellY, self.faction, self.squadId, self, sim);
    }

    /**
     * Primitive-args overload — used by callers that aren't a {@link Unit}
     * themselves (today: shuttle-mounted turrets, which live as data on a
     * {@link com.dillon.starsectormarines.battle.air.Shuttle} rather than as
     * grid entities). The selection logic is identical; pass {@link Unit#NO_SQUAD}
     * for {@code squadId} and {@code null} for {@code excludeFromCrowding}
     * when the caller doesn't squad up and isn't itself in the unit list.
     */
    public static Unit findBestTarget(int selfCellX, int selfCellY, Faction selfFaction,
                                      int selfSquadId, Unit excludeFromCrowding, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        return findBestTarget(selfCellX, selfCellY, selfFaction, selfSquadId, excludeFromCrowding,
                grid::hasLineOfSight, sim);
    }

    /**
     * LoS-injectable overload — air turrets pass an "ignore close walls"
     * predicate so they can acquire targets through the building they're
     * hovering over.
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
    public static Unit findBestTarget(int selfCellX, int selfCellY, Faction selfFaction,
                                      int selfSquadId, Unit excludeFromCrowding,
                                      LosTest los, BattleSimulation sim) {
        List<Unit> units = sim.getUnits();
        Unit bestVisible = null;
        float bestVisibleScore = Float.MAX_VALUE;
        Unit bestAny = null;
        float bestAnyDist = Float.MAX_VALUE;

        for (Unit other : units) {
            if (!other.isAlive()) continue;
            if (other.faction == selfFaction) continue;
            // Civilians and other non-combatants don't draw fire — they're
            // bystanders. A separate "rules of engagement" toggle could relax
            // this for pirate atrocity scenarios later.
            if (!other.type.combatant) continue;

            float d = cellDistance(selfCellX, selfCellY, other.cellX, other.cellY);
            if (d < bestAnyDist) {
                bestAnyDist = d;
                bestAny = other;
            }
            if (!los.visible(selfCellX, selfCellY, other.cellX, other.cellY)) continue;
            float crowding = scoreCrowding(selfFaction, selfSquadId, other, sim, excludeFromCrowding);
            float density = scoreThreatDensity(other, selfFaction, sim);
            float affinity = scoreWeaponAffinity(excludeFromCrowding, other);
            float score = d + crowding + density + affinity;
            if (score < bestVisibleScore) {
                bestVisibleScore = score;
                bestVisible = other;
            }
        }
        return bestVisible != null ? bestVisible : bestAny;
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

    /** Hardened target classification — counts emplacements and heavy mechs. Anything else (infantry archetypes, aliens, militia) is soft. */
    private static boolean isHardened(Unit target) {
        if (target instanceof MapTurret) return true;
        return target.type == UnitType.HEAVY_MECH;
    }

    /**
     * Counts other enemies within {@link #THREAT_DENSITY_RADIUS} cells of the
     * candidate's cell, then multiplies by {@link #TARGET_THREAT_DENSITY_COST}.
     * "Other enemies" = alive combatants of the candidate's faction, excluding
     * the candidate itself. The point is to model "stacking into a fire line" —
     * a lone wounded soldier is much cheaper to engage than one surrounded by
     * three buddies.
     */
    private static float scoreThreatDensity(Unit candidate, Faction selfFaction, BattleSimulation sim) {
        int r2 = THREAT_DENSITY_RADIUS * THREAT_DENSITY_RADIUS;
        int count = 0;
        for (Unit other : sim.getUnits()) {
            if (other == candidate) continue;
            if (!other.isAlive()) continue;
            if (other.faction == selfFaction) continue;
            if (!other.type.combatant) continue;
            int dx = other.cellX - candidate.cellX;
            int dy = other.cellY - candidate.cellY;
            if (dx * dx + dy * dy <= r2) count++;
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
    public static boolean shouldKeepPursuing(Unit self, Unit currentTarget, BattleSimulation sim) {
        if (currentTarget == null || !currentTarget.isAlive()) return false;
        boolean visible = sim.getGrid().hasLineOfSight(self.cellX, self.cellY,
                currentTarget.cellX, currentTarget.cellY);

        // "Meaningfully closer visible enemy" check — runs whether or not the
        // current target is visible. If current is invisible and a visible
        // alternative exists, switch unconditionally. If current is visible,
        // switch only when the alternative is closer by at least
        // RETARGET_DISTANCE_MARGIN to dampen thrashing.
        Unit closerVisible = closestVisibleOtherEnemy(self, currentTarget, sim);
        if (closerVisible != null) {
            if (!visible) return false;
            float currentDist = cellDistance(self.cellX, self.cellY,
                    currentTarget.cellX, currentTarget.cellY);
            float candidateDist = cellDistance(self.cellX, self.cellY,
                    closerVisible.cellX, closerVisible.cellY);
            if (candidateDist + RETARGET_DISTANCE_MARGIN < currentDist) return false;
        }

        if (visible) return true;

        // LOS lost — bail out if the target ducked into a cluster. Density
        // count > 1 means "at least 2 of their squadmates nearby" — chasing
        // into that costs more than the dropped target is worth.
        int r2 = THREAT_DENSITY_RADIUS * THREAT_DENSITY_RADIUS;
        int density = 0;
        for (Unit other : sim.getUnits()) {
            if (other == currentTarget) continue;
            if (!other.isAlive()) continue;
            if (other.faction == self.faction) continue;
            if (!other.type.combatant) continue;
            int dx = other.cellX - currentTarget.cellX;
            int dy = other.cellY - currentTarget.cellY;
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
    private static Unit closestVisibleOtherEnemy(Unit self, Unit exclude, BattleSimulation sim) {
        Unit best = null;
        float bestDist = Float.MAX_VALUE;
        for (Unit u : sim.getUnits()) {
            if (u == exclude || u == self) continue;
            if (!u.isAlive() || u.faction == self.faction || !u.type.combatant) continue;
            if (!sim.getGrid().hasLineOfSight(self.cellX, self.cellY, u.cellX, u.cellY)) continue;
            float d = cellDistance(self.cellX, self.cellY, u.cellX, u.cellY);
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
    private static float scoreCrowding(Faction selfFaction, int selfSquadId, Unit target,
                                       BattleSimulation sim, Unit exclude) {
        java.util.ArrayList<Unit> attackers = sim.getAttackersOf(target);
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
     * even at range. Returns the target's own cell as a fallback if no
     * candidate qualifies.
     */
    public static int[] findFiringPosition(Unit self, Unit target, BattleSimulation sim) {
        return findFiringPosition(self, target, sim, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    /**
     * Constrained firing-position search — like {@link #findFiringPosition} but
     * rejects any candidate whose cell-distance from ({@code anchorX},
     * {@code anchorY}) exceeds {@code maxDistFromAnchor}. Used by
     * {@link GarrisonBehavior} to keep engaged defenders within a tight
     * radius of their tactical-node anchor: they'll peek around corners and
     * grab better cover, but won't chase marines off the wall.
     *
     * <p>Returns {@code null} (not the target's cell) when no candidate
     * satisfies range + LOS + anchor-radius. The caller treats null as
     * "hold position" rather than "advance toward the target."
     */
    public static int[] findFiringPositionWithin(Unit self, Unit target, BattleSimulation sim,
                                                  int anchorX, int anchorY, float maxDistFromAnchor) {
        NavigationGrid grid = sim.getGrid();
        int range = Math.max(1, (int) Math.floor(self.attackRange));
        int tx = target.cellX;
        int ty = target.cellY;

        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                int cx = tx + dx;
                int cy = ty + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;

                float distFromTarget = (float) Math.sqrt(dx * dx + dy * dy);
                if (distFromTarget > self.attackRange) continue;
                if (distFromTarget < FIRING_MIN_DISTANCE) continue;
                if (!grid.hasLineOfSight(cx, cy, tx, ty)) continue;
                if (cellDistance(anchorX, anchorY, cx, cy) > maxDistFromAnchor) continue;

                int occupants = occupantsExcludingSelf(self, cx, cy, sim);
                int cover = grid.getCoverAt(cx, cy);
                int doodadCover = sim.getDoodadCoverAt(cx, cy);
                float distFromSelf = cellDistance(self.cellX, self.cellY, cx, cy);
                float score = distFromSelf
                        + FIRING_OCCUPANCY_COST * occupants
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

    public static int[] findFiringPosition(Unit self, Unit target, BattleSimulation sim, int rejectX, int rejectY) {
        NavigationGrid grid = sim.getGrid();
        int range = Math.max(1, (int) Math.floor(self.attackRange));
        int tx = target.cellX;
        int ty = target.cellY;

        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                int cx = tx + dx;
                int cy = ty + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                if (cx == rejectX && cy == rejectY) continue;

                float distFromTarget = (float) Math.sqrt(dx * dx + dy * dy);
                if (distFromTarget > self.attackRange) continue;
                if (distFromTarget < FIRING_MIN_DISTANCE) continue;
                if (!grid.hasLineOfSight(cx, cy, tx, ty)) continue;

                int occupants = occupantsExcludingSelf(self, cx, cy, sim);
                int cover = grid.getCoverAt(cx, cy);
                int doodadCover = sim.getDoodadCoverAt(cx, cy);
                float distFromSelf = cellDistance(self.cellX, self.cellY, cx, cy);
                float score = distFromSelf
                        + FIRING_OCCUPANCY_COST * occupants
                        - FIRING_COVER_BONUS * cover
                        - FIRING_DOODAD_COVER_BONUS * doodadCover;
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best != null ? best : new int[]{tx, ty};
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
    public static int[] bestCoverCell(int threatX, int threatY,
                                      int nearX, int nearY, int radius,
                                      BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
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
                int combined = grid.getCoverAt(cx, cy) + sim.getDoodadCoverAt(cx, cy);
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
     * Cover-aware variant of {@link #findFiringPosition(Unit, Unit, BattleSimulation, int, int)} —
     * filters the candidate ring to cells whose combined (cell + doodad) cover
     * meets or exceeds the unit's current combined cover. When no candidate
     * meets that threshold, falls through to the standard scorer.
     *
     * <p>Used by reposition rolls (Story G — "the sidestep between bursts
     * shouldn't make things worse"). Plain {@link #findFiringPosition} can
     * legally pick a slightly-worse-cover cell if its other terms (proximity,
     * spread) dominate; this variant refuses to downgrade.
     */
    public static int[] findFiringPositionCoverPreferred(Unit self, Unit target, BattleSimulation sim,
                                                          int rejectX, int rejectY) {
        NavigationGrid grid = sim.getGrid();
        int range = Math.max(1, (int) Math.floor(self.attackRange));
        int tx = target.cellX;
        int ty = target.cellY;
        int selfCover = grid.getCoverAt(self.cellX, self.cellY) + sim.getDoodadCoverAt(self.cellX, self.cellY);

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
                if (distFromTarget > self.attackRange) continue;
                if (distFromTarget < FIRING_MIN_DISTANCE) continue;
                if (!grid.hasLineOfSight(cx, cy, tx, ty)) continue;

                int cover = grid.getCoverAt(cx, cy);
                int doodadCover = sim.getDoodadCoverAt(cx, cy);
                int combined = cover + doodadCover;
                if (combined < selfCover) continue;

                int occupants = occupantsExcludingSelf(self, cx, cy, sim);
                float distFromSelf = cellDistance(self.cellX, self.cellY, cx, cy);
                float score = distFromSelf
                        + FIRING_OCCUPANCY_COST * occupants
                        - FIRING_COVER_BONUS * cover
                        - FIRING_DOODAD_COVER_BONUS * doodadCover;
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{cx, cy};
                    foundEqualOrBetter = true;
                }
            }
        }
        if (foundEqualOrBetter && best != null) return best;
        // Fall through to the unfiltered scorer if no cover-preserving move
        // exists — caller will get the regular best-effort firing position.
        return findFiringPosition(self, target, sim, rejectX, rejectY);
    }

    /**
     * Scans cells within {@link #FALLBACK_SCAN_RANGE} of {@code self} for a
     * walkable, out-of-LOS spot, scored by
     * {@code distFromSelf + occupancyPenalty - coverBonus - zoneControlBonus}.
     *
     * <p>The top-scored cell only helps if the unit can actually walk to it —
     * and the wall that hides a cell from enemies is exactly the kind of wall
     * that can also seal it off from us. Without a reachability check the
     * picker happily returns sealed pockets and the unit freezes for the
     * entire fall-back duration waiting on an empty path. We filter using
     * {@link ZoneGraph#areConnected} (BFS over the portal graph, cheaper than
     * per-cell A*) and walk the sorted list until a reachable candidate
     * shows up.
     *
     * <p>If the top-scored cell is unreachable we first try its four cardinal
     * neighbors before falling through to the next-best candidate. The dud is
     * almost always shadowed by a wall, and the cardinal on our side of that
     * wall inherits the same cover while staying in our zone. Cardinals are
     * tried in order of distance from the average enemy cell (farthest first)
     * so the consolation pick doesn't accidentally march us into the firing
     * lane. Falls through to {@code self}'s current cell only if nothing
     * qualifies — caller treats that as "don't enter fall-back."
     */
    public static int[] findFallbackPosition(Unit self, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        ZoneGraph zones = sim.getZoneGraph();
        int sx = self.cellX;
        int sy = self.cellY;
        int selfZone = zones.zoneIdAt(sx, sy);
        int[] zoneControl = computeZoneControl(self, sim);

        List<float[]> candidates = new ArrayList<>();
        for (int dy = -FALLBACK_SCAN_RANGE; dy <= FALLBACK_SCAN_RANGE; dy++) {
            for (int dx = -FALLBACK_SCAN_RANGE; dx <= FALLBACK_SCAN_RANGE; dx++) {
                int cx = sx + dx;
                int cy = sy + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                if (!isHiddenFromAllEnemies(self, cx, cy, sim)) continue;

                int occupants = occupantsExcludingSelf(self, cx, cy, sim);
                int cover = grid.getCoverAt(cx, cy) + sim.getDoodadCoverAt(cx, cy);
                int zoneId = zones.zoneIdAt(cx, cy);
                int control = (zoneId >= 0 && zoneId < zoneControl.length) ? zoneControl[zoneId] : 0;
                float distFromSelf = cellDistance(sx, sy, cx, cy);
                float score = distFromSelf
                        + FALLBACK_OCCUPANCY_COST * occupants
                        - FALLBACK_COVER_BONUS * cover
                        - FALLBACK_FRIENDLY_ZONE_BONUS * control;
                candidates.add(new float[]{score, cx, cy});
            }
        }
        candidates.sort((a, b) -> Float.compare(a[0], b[0]));

        int[] threatRef = averageEnemyCell(self, sim);
        for (float[] cand : candidates) {
            int cx = (int) cand[1];
            int cy = (int) cand[2];
            if (zones.areConnected(selfZone, zones.zoneIdAt(cx, cy))) {
                return new int[]{cx, cy};
            }
            int[] consolation = cardinalConsolation(grid, zones, selfZone, sx, sy, cx, cy, threatRef);
            if (consolation != null) return consolation;
        }
        return new int[]{sx, sy};
    }

    /**
     * Average alive-enemy cell from {@code self}'s perspective, or {@code
     * null} when there are no enemies. Used as the "don't march toward this"
     * reference for cardinal-neighbor ordering in fall-back consolation.
     */
    private static int[] averageEnemyCell(Unit self, BattleSimulation sim) {
        float sumX = 0f, sumY = 0f;
        int count = 0;
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive() || u.faction == self.faction) continue;
            if (!u.type.combatant) continue;
            sumX += u.cellX;
            sumY += u.cellY;
            count++;
        }
        if (count == 0) return null;
        return new int[]{Math.round(sumX / count), Math.round(sumY / count)};
    }

    /**
     * Picks a walkable cardinal neighbor of {@code (dudX, dudY)} in a zone
     * reachable from {@code selfZone}. Cardinals are tried farthest-from-
     * {@code threatRef} first so the consolation cell — taken when the
     * best-scored hide turns out to be sealed off — doesn't slide the unit
     * toward the threat. Returns {@code null} if no cardinal qualifies.
     */
    private static int[] cardinalConsolation(NavigationGrid grid, ZoneGraph zones,
                                             int selfZone, int sx, int sy,
                                             int dudX, int dudY, int[] threatRef) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] order = {0, 1, 2, 3};
        float[] threatDist = new float[4];
        for (int i = 0; i < 4; i++) {
            int nx = dudX + dirs[i][0];
            int ny = dudY + dirs[i][1];
            threatDist[i] = (threatRef == null)
                    ? 0f
                    : cellDistance(nx, ny, threatRef[0], threatRef[1]);
        }
        // Insertion sort by threatDist DESCENDING (farthest from threat first).
        for (int i = 1; i < 4; i++) {
            int slot = order[i];
            float d = threatDist[slot];
            int j = i;
            while (j > 0 && threatDist[order[j - 1]] < d) {
                order[j] = order[j - 1];
                j--;
            }
            order[j] = slot;
        }
        for (int oi : order) {
            int nx = dudX + dirs[oi][0];
            int ny = dudY + dirs[oi][1];
            if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny)) continue;
            if (nx == sx && ny == sy) continue;
            if (!zones.areConnected(selfZone, zones.zoneIdAt(nx, ny))) continue;
            return new int[]{nx, ny};
        }
        return null;
    }

    /**
     * Per-zone allies-minus-enemies from {@code self}'s perspective.
     * Indexed by zone id; positive = friendly-controlled, negative = hostile.
     * Computed once per {@link #findFallbackPosition} call so a 17×17 candidate
     * scan does at most O(zones + units) work instead of re-scanning units
     * per cell.
     */
    private static int[] computeZoneControl(Unit self, BattleSimulation sim) {
        ZoneGraph zones = sim.getZoneGraph();
        int[] control = new int[zones.getZones().size()];
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive()) continue;
            int zid = zones.zoneIdAt(u.cellX, u.cellY);
            if (zid < 0 || zid >= control.length) continue;
            control[zid] += (u.faction == self.faction) ? 1 : -1;
        }
        return control;
    }

    public static boolean isHiddenFromAllEnemies(Unit self, int cx, int cy, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        for (Unit other : sim.getUnits()) {
            if (!other.isAlive()) continue;
            if (other.faction == self.faction) continue;
            if (grid.hasLineOfSight(cx, cy, other.cellX, other.cellY)) return false;
        }
        return true;
    }

    /**
     * Occupancy count at cell (cx, cy), excluding self's own contributions
     * (current cell + path destination). Used so a unit doesn't penalize
     * itself when scoring its own current/intended position.
     */
    public static int occupantsExcludingSelf(Unit self, int cx, int cy, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        if (!grid.inBounds(cx, cy)) return 0;
        int n = sim.getOccupancyMap()[cy * grid.getWidth() + cx] & 0xFF;
        if (cx == self.cellX && cy == self.cellY) n--;
        int cells = self.pathCellCount();
        if (cells > 0) {
            int destX = self.pathCellX(cells - 1);
            int destY = self.pathCellY(cells - 1);
            if (destX == cx && destY == cy && (destX != self.cellX || destY != self.cellY)) {
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
