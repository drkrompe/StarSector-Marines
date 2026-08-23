package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.unit.LongBucket;

/**
 * LR Support doctrine: angle through medium/long-range firing lanes toward the
 * squad's known threat axis. Candidate cells prefer a same-faction combatant
 * between the mech and the threat, but another Sirocco never counts as the
 * screen. The picked cell is cached on
 * {@link MechLoadoutComponent#overwatchCellX}; it refreshes when the threat or
 * screen changes and periodically while unscreened.
 *
 * <p>Per-member execution branches on role. An LR_SUPPORT member runs the
 * overwatch body; any other member in the squad (e.g. an ARMORED_SUPPORT
 * mech in a mixed-role squad — possible with round-robin spawn assignment)
 * falls through to the parity {@link EngageAtCurrentBand} body so it still
 * fights instead of idling. Per-member goal assignment (Story F) is the
 * Stage 2 path that gives each member its own goal cleanly; for Stage 1
 * the squad-goal-wins-once model with action-side branching handles mixed
 * squads acceptably.
 *
 * <p>The "withhold SRM" piece is doctrine-as-positioning: the mech holds
 * in the medium/long band. A Sirocco can take a heavy-cannon opportunity near
 * the inner edge and uses LRMs outside the cannon band. SRM is never called
 * from this action regardless. Once every LRM rack is empty, the cached long
 * perch is invalidated and the mech closes into the outer edge of its installed
 * arms range instead of remaining unable to fire. It withholds replenished
 * LRMs in that fallback posture until every installed rack is full, then
 * returns to the normal medium/long band as one deliberate rearm cycle instead
 * of oscillating for each restored trigger. A future morale-driven pressured
 * override can unlock SRM as a pressure-release valve; see `14-mech-stage1.md`.
 *
 * <p>Always returns {@link ActionStatus#RUNNING} — same lifecycle as
 * {@link EngageAtCurrentBand}; replan handles posture changes.
 */
public final class OverwatchKillZone implements Action {

    public static final OverwatchKillZone INSTANCE = new OverwatchKillZone();

    /** Inner brawling edge. It overlaps the Sirocco's 26-cell heavy cannon. */
    static final float OVERWATCH_MIN_DIST = 24f;
    /** Outer edge. It stays comfortably inside the 40-cell LRM envelope. */
    static final float OVERWATCH_MAX_DIST = 36f;
    /** Depth of the outer direct-fire band used after all LRM pressure is spent. */
    static final float DIRECT_FALLBACK_BAND_DEPTH = 2f;
    /** Cover-bonus weight when scoring candidate overwatch cells. Higher = strong preference for high-cover cells over short-walk cells. */
    private static final float OVERWATCH_COVER_WEIGHT = 5f;
    /** Strong but non-mandatory preference for a lane screened by a useful ally. */
    private static final float SCREENED_POSITION_BONUS = 24f;
    /** Maximum lateral distance from the firing axis for an ally to count as the screen. */
    static final float SCREEN_AXIS_HALF_WIDTH = 3f;
    /** Keeps an ally meaningfully between the shooter and threat rather than touching either endpoint. */
    private static final float SCREEN_ENDPOINT_CLEARANCE = 3f;
    /** Two-second discovery cadence for an unscreened cached perch. */
    private static final int UNSCREENED_RECHECK_TICKS = 60;

    private static final WorldState PRE = WorldState.EMPTY;
    private static final WorldState EFF = WorldState.EMPTY
            .with(Predicate.KILL_ZONE_COVERED, true);

    private OverwatchKillZone() {}

    @Override public String name() { return "OverwatchKillZone"; }
    @Override public WorldState preconditions() { return PRE; }
    @Override public WorldState effects() { return EFF; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        // Per-member role branching. Non-LR_SUPPORT members (and non-mechs that
        // can't happen here but stay defensive) fall through to parity
        // engagement so mixed-role squads have every member doing something
        // sensible. Loadout reached by id (zero-alloc direct lookup).
        MechLoadoutComponent m = sim.world().mechLoadout(member);
        if (m == null || m.role != MechRole.LR_SUPPORT) {
            return EngageAtCurrentBand.INSTANCE.execute(member, squad, sim);
        }

        // No known contact → no kill corridor anchor. Drop back to parity
        // until the alert-update pass sets lastSeenEnemy.
        if (squad.lastSeenEnemyX < 0 || squad.lastSeenEnemyY < 0) {
            return EngageAtCurrentBand.INSTANCE.execute(member, squad, sim);
        }

        OverwatchBand band = overwatchBand(m);
        if (band == null) {
            return EngageAtCurrentBand.INSTANCE.execute(member, squad, sim);
        }

        // Refresh overwatch cell when threat axis shifts or we have no cached
        // pick yet. A transition between supplied LRM pressure and the
        // direct-fire fallback also invalidates the cache, even though the
        // two bands overlap at their inner edge. Pick is per-mech (each LR
        // member gets its own cell).
        boolean needsRepick = m.overwatchCellX < 0
                || m.overwatchAxisX != squad.lastSeenEnemyX
                || m.overwatchAxisY != squad.lastSeenEnemyY
                || m.overwatchLongRangeBand != band.longRange()
                || !insideBand(m.overwatchCellX, m.overwatchCellY,
                squad.lastSeenEnemyX, squad.lastSeenEnemyY, band);
        if (!needsRepick && m.overwatchScreenId != 0L) {
            needsRepick = !isValidScreen(m.overwatchScreenId,
                    m.overwatchCellX, m.overwatchCellY,
                    squad.lastSeenEnemyX, squad.lastSeenEnemyY,
                    squad, sim);
        }
        if (!needsRepick && m.overwatchScreenId == 0L) {
            needsRepick = Math.floorMod(sim.getSimTickIndex() + Long.hashCode(member),
                    UNSCREENED_RECHECK_TICKS) == 0;
        }
        if (needsRepick) {
            OverwatchPosition position = pickOverwatchCell(member, squad, band, sim);
            if (position == null) {
                m.overwatchCellX = -1;
                m.overwatchCellY = -1;
                m.overwatchScreenId = 0L;
                // No valid medium/LR firing cell — fall back to parity.
                return EngageAtCurrentBand.INSTANCE.execute(member, squad, sim);
            }
            m.overwatchCellX = position.x();
            m.overwatchCellY = position.y();
            m.overwatchAxisX = squad.lastSeenEnemyX;
            m.overwatchAxisY = squad.lastSeenEnemyY;
            m.overwatchScreenId = position.screenId();
            m.overwatchLongRangeBand = band.longRange();
        }

        // Path to the overwatch cell. Idempotent — only requests a new path
        // when the mech isn't already at the cell and isn't already moving.
        int[] path = sim.world().path(member);
        int pathIdx = sim.world().pathIdx(member);
        boolean stalePath = !Paths.isEmpty(path)
                && (Paths.destX(path) != m.overwatchCellX
                || Paths.destY(path) != m.overwatchCellY);
        if (stalePath) {
            sim.clearPath(member);
            path = sim.world().path(member);
            pathIdx = sim.world().pathIdx(member);
        }
        if (!sim.movement().atCell(member, m.overwatchCellX, m.overwatchCellY)
                && sim.movement().mayRepath(member)
                && pathIdx >= Paths.cellCount(path)) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member), sim.world().cellY(member),
                    m.overwatchCellX, m.overwatchCellY,
                    sim.getOccupancyMap()));
            path = sim.world().path(member);
            pathIdx = sim.world().pathIdx(member);
        }
        if (pathIdx < Paths.cellCount(path)) {
            sim.advanceMovement(member);
        }

        // Fire pass — withhold SRM, allow LRM in its long band and whichever
        // direct-fire weapon is installed on the arms track in its own band.
        // Re-pick whenever the cached target isn't currently shootable: an
        // LR mech parked at its overwatch cell can otherwise stay locked onto
        // an enemy that's slid behind cover while ignoring a fresh enemy now
        // standing in its kill lane.
        long target = MechTargeting.refreshTarget(member, sim);
        sim.world().setTargetId(member, target);
        if (target != 0L) {
            float dist = TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member),
                    sim.world().x(target), sim.world().y(target));
            boolean inRange = dist <= sim.world().attackRange(member);
            boolean visible = sim.getGrid().hasLineOfSight(sim.world().cellX(member), sim.world().cellY(member),
                    sim.world().cellX(target), sim.world().cellY(target));
            if (inRange) {
                if (band.longRange()) {
                    MechCombatantBehavior.tryFireLrm(member, m, target, dist, sim, visible);
                }
                MechCombatantBehavior.tryFireArms(member, m, target, dist, sim, visible);
                // SRM intentionally withheld — see class doc.
            }
        }
        return ActionStatus.RUNNING;
    }

    /**
     * Picks the best firing cell around {@code squad.lastSeenEnemy}. A supplied
     * LR loadout uses the normal medium/long band; one with spent racks uses
     * the outer edge of its installed arms range. Candidates require walkable
     * ground and LoS to the threat, score by walk distance and directional
     * cover, then strongly reward a non-Sirocco friendly combatant lying on the
     * candidate-to-threat axis. Returns {@code null} when no cell satisfies the
     * active band — caller falls back to parity engagement.
     */
    static OverwatchPosition pickOverwatchCell(long member, Squad squad, BattleView sim) {
        MechLoadoutComponent loadout = sim.world().mechLoadout(member);
        OverwatchBand band = loadout != null ? overwatchBand(loadout) : null;
        return band != null ? pickOverwatchCell(member, squad, band, sim) : null;
    }

    private static OverwatchPosition pickOverwatchCell(long member, Squad squad,
                                                       OverwatchBand band,
                                                       BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        int tx = squad.lastSeenEnemyX;
        int ty = squad.lastSeenEnemyY;
        int radius = (int) Math.ceil(band.maxDistance());
        ScreeningAllies allies = gatherScreeningAllies(member, squad, tx, ty, sim);

        OverwatchPosition best = null;
        float bestScore = Float.MAX_VALUE;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = tx + dx;
                int cy = ty + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                float distFromTarget = (float) Math.sqrt(dx * dx + dy * dy);
                if (distFromTarget < band.minDistance()
                        || distFromTarget > band.maxDistance()) continue;
                if (!grid.hasLineOfSight(cx, cy, tx, ty)) continue;
                // Cover lookup is directional against the threat axis (Story G
                // primitive). High-cover cells facing the threat win.
                int fdx = tx - cx;
                int fdy = ty - cy;
                int cover = grid.getCoverAt(cx, cy, fdx, fdy);
                int doodadCover = sim.getDoodadCoverAt(cx, cy, fdx, fdy);
                float walk = TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member), cx + 0.5f, cy + 0.5f);
                long screen = screeningAlly(cx, cy, tx, ty, allies);
                float score = walk
                        - OVERWATCH_COVER_WEIGHT * cover
                        - OVERWATCH_COVER_WEIGHT * doodadCover
                        - (screen != 0L ? SCREENED_POSITION_BONUS : 0f);
                if (score < bestScore) {
                    bestScore = score;
                    best = new OverwatchPosition(cx, cy, screen);
                }
            }
        }
        return best;
    }

    private static OverwatchBand overwatchBand(MechLoadoutComponent loadout) {
        if (hasLrmPressure(loadout)
                && (loadout.overwatchLongRangeBand || lrmRacksFull(loadout))) {
            return new OverwatchBand(OVERWATCH_MIN_DIST, OVERWATCH_MAX_DIST, true);
        }
        MechWeaponMount arms = loadout.mount(MechMountSlot.ARMS);
        if (arms == null || (!arms.hasAmmo() && arms.burstRemaining <= 0)) return null;
        float maxDistance = arms.weapon().range;
        float minDistance = Math.min(OVERWATCH_MIN_DIST,
                Math.max(0f, maxDistance - DIRECT_FALLBACK_BAND_DEPTH));
        return new OverwatchBand(minDistance, maxDistance, false);
    }

    private static boolean hasLrmPressure(MechLoadoutComponent loadout) {
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount != null && mount.weapon() == MechWeapon.LRM_ARTILLERY
                    && (mount.hasAmmo() || mount.burstRemaining > 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean lrmRacksFull(MechLoadoutComponent loadout) {
        boolean found = false;
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount == null || mount.weapon() != MechWeapon.LRM_ARTILLERY) continue;
            found = true;
            if (!mount.full()) return false;
        }
        return found;
    }

    private static boolean insideBand(int cellX, int cellY,
                                      int threatX, int threatY,
                                      OverwatchBand band) {
        float dx = cellX - threatX;
        float dy = cellY - threatY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        return distance >= band.minDistance() && distance <= band.maxDistance();
    }

    private static ScreeningAllies gatherScreeningAllies(long member, Squad squad,
                                                         int threatX, int threatY,
                                                         BattleView sim) {
        LongBucket gathered = new LongBucket();
        sim.getUnitIndex().gather(threatX + 0.5f, threatY + 0.5f,
                OVERWATCH_MAX_DIST, gathered);
        long[] ids = new long[gathered.size];
        float[] xs = new float[gathered.size];
        float[] ys = new float[gathered.size];
        int write = 0;
        for (int i = 0, n = gathered.size; i < n; i++) {
            long ally = gathered.ids[i];
            if (ally == member || sim.identity().faction(ally) != squad.faction
                    || !sim.identity().type(ally).combatant
                    || sim.identity().mechVariant(ally) == MechVariant.SIROCCO) continue;
            ids[write] = ally;
            xs[write] = sim.world().x(ally);
            ys[write] = sim.world().y(ally);
            write++;
        }
        return new ScreeningAllies(ids, xs, ys, write);
    }

    private static long screeningAlly(int candidateX, int candidateY,
                                      int threatX, int threatY,
                                      ScreeningAllies allies) {
        long best = 0L;
        float bestLateralSq = Float.MAX_VALUE;
        for (int i = 0; i < allies.size(); i++) {
            long ally = allies.ids()[i];
            float lateralSq = screenLateralDistanceSq(
                    allies.xs()[i], allies.ys()[i], candidateX, candidateY,
                    threatX, threatY);
            if (lateralSq < 0f) continue;
            if (lateralSq < bestLateralSq
                    || lateralSq == bestLateralSq && ally < best) {
                best = ally;
                bestLateralSq = lateralSq;
            }
        }
        return best;
    }

    private static boolean isValidScreen(long ally, int candidateX, int candidateY,
                                         int threatX, int threatY,
                                         Squad squad, BattleView sim) {
        return sim.resolveUnit(ally) != 0L
                && sim.identity().faction(ally) == squad.faction
                && sim.identity().type(ally).combatant
                && sim.identity().mechVariant(ally) != MechVariant.SIROCCO
                && screenLateralDistanceSq(sim.world().x(ally), sim.world().y(ally),
                candidateX, candidateY, threatX, threatY) >= 0f;
    }

    private static float screenLateralDistanceSq(float allyX, float allyY,
                                                 int candidateX, int candidateY,
                                                 int threatX, int threatY) {
        float startX = candidateX + 0.5f;
        float startY = candidateY + 0.5f;
        float dx = threatX + 0.5f - startX;
        float dy = threatY + 0.5f - startY;
        float lengthSq = dx * dx + dy * dy;
        if (lengthSq < 1e-4f) return -1f;
        float length = (float) Math.sqrt(lengthSq);
        float relX = allyX - startX;
        float relY = allyY - startY;
        float progress = (relX * dx + relY * dy) / length;
        if (progress < SCREEN_ENDPOINT_CLEARANCE
                || progress > length - SCREEN_ENDPOINT_CLEARANCE) return -1f;
        float lateral = Math.abs(relX * -dy + relY * dx) / length;
        return lateral <= SCREEN_AXIS_HALF_WIDTH ? lateral * lateral : -1f;
    }

    private record ScreeningAllies(long[] ids, float[] xs, float[] ys, int size) {}

    private record OverwatchBand(float minDistance, float maxDistance,
                                 boolean longRange) {}

    record OverwatchPosition(int x, int y, long screenId) {}
}
