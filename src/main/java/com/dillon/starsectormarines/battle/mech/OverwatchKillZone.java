package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
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
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

/**
 * LR Support doctrine: angle through medium/long-range firing lanes toward the
 * squad's active threat axis. Candidate cells categorically prefer a
 * same-faction combatant forming a broad front between the mech and the
 * threat, while keeping every friendly clear of a rear-oblique direct-fire
 * lane; another Sirocco never counts as the screen. The picked cell is cached on
 * {@link MechLoadoutComponent#overwatchCellX}; it refreshes when the threat or
 * screen changes and periodically while unscreened.
 *
 * <p>The shared doctrine dispatcher normally invokes this action only for an
 * LR_SUPPORT member. A direct call for another role still falls through to
 * {@link EngageAtCurrentBand} as a defensive compatibility guard.
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
 * override can unlock SRM as a pressure-release valve; see
 * {@code ai-nouns.md}.
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
    /** Broad lateral frontage in which an ally can credibly screen the threat axis. */
    static final float SCREEN_AXIS_HALF_WIDTH = 8f;
    /** Minimum ally clearance from the actual projectile ray. */
    static final float SCREEN_FIRE_LANE_CLEARANCE = 1.5f;
    /** Extra clearance around a friendly's authored physical profile. */
    private static final float SCREEN_FIRE_LANE_MARGIN = 0.5f;
    /** Sentinel returned when any friendly body obstructs the candidate's firing ray. */
    private static final long BLOCKED_FIRING_LANE = Long.MIN_VALUE;
    /** Keeps an ally meaningfully between the shooter and threat rather than touching either endpoint. */
    private static final float SCREEN_ENDPOINT_CLEARANCE = 3f;
    /** Two-second discovery cadence for an unscreened cached perch. */
    private static final int UNSCREENED_RECHECK_TICKS = 60;
    /** A contact inside this radius has penetrated the long-range posture. */
    static final float RUSHED_DISTANCE = 12f;
    /** One emergency move attempts to buy this many cells of separation. */
    private static final float RUSH_ESCAPE_DISTANCE = 8f;

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
        if (m == null || m.effectiveRole() != MechRole.LR_SUPPORT) {
            return EngageAtCurrentBand.INSTANCE.execute(member, squad, sim);
        }

        long immediateTarget = MechTargeting.refreshTarget(member, sim);
        sim.world().setTargetId(member, immediateTarget);
        if (immediateTarget != 0L && sim.resolveUnit(immediateTarget) != 0L) {
            float immediateDistance = TacticalScoring.cellDistance(
                    sim.world().x(member), sim.world().y(member),
                    sim.world().x(immediateTarget), sim.world().y(immediateTarget));
            if (immediateDistance < RUSHED_DISTANCE) {
                openDistance(member, squad, m, immediateTarget,
                        immediateDistance, sim);
                return ActionStatus.RUNNING;
            }
        }

        int threatX = squad.lastSeenEnemyX;
        int threatY = squad.lastSeenEnemyY;
        if (immediateTarget != 0L && sim.resolveUnit(immediateTarget) != 0L) {
            threatX = sim.world().cellX(immediateTarget);
            threatY = sim.world().cellY(immediateTarget);
        }

        // No known contact → no kill corridor anchor. Drop back to parity
        // until the alert-update pass publishes one.
        if (threatX < 0 || threatY < 0) {
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
                || m.overwatchAxisX != threatX
                || m.overwatchAxisY != threatY
                || m.overwatchLongRangeBand != band.longRange()
                || !insideBand(m.overwatchCellX, m.overwatchCellY,
                threatX, threatY, band)
                || !MechAssignmentBoundary.permitsOverwatchCell(member, squad,
                m.overwatchCellX, m.overwatchCellY, threatX, threatY, sim);
        if (!needsRepick) {
            ScreeningAllies currentAllies = gatherScreeningAllies(
                    member, squad, threatX, threatY, sim);
            needsRepick = screeningAlly(
                    m.overwatchCellX, m.overwatchCellY,
                    threatX, threatY, currentAllies) == BLOCKED_FIRING_LANE;
        }
        if (!needsRepick && m.overwatchScreenId != 0L) {
            needsRepick = !isValidScreen(m.overwatchScreenId,
                    m.overwatchCellX, m.overwatchCellY,
                    threatX, threatY,
                    squad, sim);
        }
        if (!needsRepick && m.overwatchScreenId == 0L) {
            needsRepick = Math.floorMod(sim.getSimTickIndex() + Long.hashCode(member),
                    UNSCREENED_RECHECK_TICKS) == 0;
        }
        if (needsRepick) {
            OverwatchPosition position = pickOverwatchCell(
                    member, squad, band, threatX, threatY, sim);
            if (position == null) {
                m.overwatchCellX = -1;
                m.overwatchCellY = -1;
                m.overwatchScreenId = 0L;
                m.overwatchAxisX = threatX;
                m.overwatchAxisY = threatY;
                m.overwatchLongRangeBand = band.longRange();
                // No valid medium/LR firing cell — fall back to parity.
                return EngageAtCurrentBand.INSTANCE.execute(member, squad, sim);
            }
            m.overwatchCellX = position.x();
            m.overwatchCellY = position.y();
            m.overwatchAxisX = threatX;
            m.overwatchAxisY = threatY;
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
            boolean visible = sim.getTacticalScoring().hasClearShot(member, target);
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

    private static void openDistance(long member, Squad squad,
                                     MechLoadoutComponent loadout,
                                     long target, float distance,
                                     BattleControl sim) {
        boolean visible = sim.getTacticalScoring().hasClearShot(member, target);
        if (distance <= sim.world().attackRange(member)) {
            // A rushed support mech uses every installed defensive weapon while
            // it creates room; normal overwatch resumes SRM withholding.
            MechCombatantBehavior.tryFireMechWeapons(
                    member, loadout, target, distance, sim, visible);
        }

        if (sim.movement().mayRepath(member)) {
            int[] escapePath = openingPath(member, squad, target, sim);
            if (escapePath != null) sim.setPath(member, escapePath);
            else if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
        }
        if (sim.world().pathIdx(member) < Paths.cellCount(sim.world().path(member))) {
            sim.advanceMovement(member);
        }
    }

    private static int[] openingPath(long member, Squad squad, long target,
                                     BattleControl sim) {
        float memberX = sim.world().x(member);
        float memberY = sim.world().y(member);
        float dx = memberX - sim.world().x(target);
        float dy = memberY - sim.world().y(target);
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-3f) return null;
        int idealX = (int) Math.floor(memberX + dx / length * RUSH_ESCAPE_DISTANCE);
        int idealY = (int) Math.floor(memberY + dy / length * RUSH_ESCAPE_DISTANCE);
        float currentDistanceSq = dx * dx + dy * dy;
        int threatX = sim.world().cellX(target);
        int threatY = sim.world().cellY(target);
        NavigationGrid grid = sim.getGrid();
        for (int radius = 0; radius <= 4; radius++) {
            for (int oy = -radius; oy <= radius; oy++) {
                for (int ox = -radius; ox <= radius; ox++) {
                    if (Math.max(Math.abs(ox), Math.abs(oy)) != radius) continue;
                    int x = idealX + ox;
                    int y = idealY + oy;
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                    if (!MechAssignmentBoundary.permitsOverwatchCell(
                            member, squad, x, y, threatX, threatY, sim)) continue;
                    float targetDx = x + 0.5f - sim.world().x(target);
                    float targetDy = y + 0.5f - sim.world().y(target);
                    if (targetDx * targetDx + targetDy * targetDy <= currentDistanceSq) continue;
                    if (!grid.hasLineOfFire(x + 0.5f, y + 0.5f,
                            sim.world().x(target), sim.world().y(target))) continue;
                    int[] path = GridPathfinder.findPath(grid,
                            sim.world().cellX(member), sim.world().cellY(member),
                            x, y, sim.getOccupancyMap());
                    if (!Paths.isEmpty(path)) return path;
                }
            }
        }
        return null;
    }

    /**
     * Picks the best firing cell around {@code squad.lastSeenEnemy} for direct
     * tests; production execution passes the current engageable target's cell.
     * A supplied LR loadout uses the normal medium/long band; one in a rearm
     * cycle uses the outer edge of its installed arms range. Candidates require
     * walkable ground and LoS to the threat. A credible broad allied screen is
     * categorical, then walk distance and directional cover rank cells within
     * the screened or unscreened class. Returns {@code null} when no cell
     * satisfies the active band — caller falls back to parity engagement.
     */
    static OverwatchPosition pickOverwatchCell(long member, Squad squad, BattleView sim) {
        MechLoadoutComponent loadout = sim.world().mechLoadout(member);
        OverwatchBand band = loadout != null ? overwatchBand(loadout) : null;
        return band != null ? pickOverwatchCell(member, squad, band,
                squad.lastSeenEnemyX, squad.lastSeenEnemyY, sim) : null;
    }

    private static OverwatchPosition pickOverwatchCell(long member, Squad squad,
                                                       OverwatchBand band,
                                                       int tx, int ty,
                                                       BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        if (tx < 0 || ty < 0) return null;
        int radius = (int) Math.ceil(band.maxDistance());
        int[] connected = GridPathfinder.labelConnectedComponents(grid);
        int memberX = sim.world().cellX(member);
        int memberY = sim.world().cellY(member);
        int memberComponent = grid.inBounds(memberX, memberY)
                ? connected[grid.index(memberX, memberY)] : -1;
        if (memberComponent < 0) return null;
        ScreeningAllies allies = gatherScreeningAllies(member, squad, tx, ty, sim);

        OverwatchPosition bestScreened = null;
        OverwatchPosition bestUnscreened = null;
        float bestScreenedScore = Float.MAX_VALUE;
        float bestUnscreenedScore = Float.MAX_VALUE;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = tx + dx;
                int cy = ty + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                if (!MechAssignmentBoundary.permitsOverwatchCell(
                        member, squad, cx, cy, tx, ty, sim)) continue;
                if (connected[grid.index(cx, cy)] != memberComponent) continue;
                float distFromTarget = (float) Math.sqrt(dx * dx + dy * dy);
                if (distFromTarget < band.minDistance()
                        || distFromTarget > band.maxDistance()) continue;
                if (!grid.hasLineOfFire(cx + 0.5f, cy + 0.5f,
                        tx + 0.5f, ty + 0.5f)) continue;
                // Cover lookup is directional against the threat axis (Story G
                // primitive). High-cover cells facing the threat win.
                int fdx = tx - cx;
                int fdy = ty - cy;
                int cover = grid.getCoverAt(cx, cy, fdx, fdy);
                int doodadCover = sim.getDoodadCoverAt(cx, cy, fdx, fdy);
                float walk = TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member), cx + 0.5f, cy + 0.5f);
                long screen = screeningAlly(cx, cy, tx, ty, allies);
                if (screen == BLOCKED_FIRING_LANE) continue;
                float score = walk
                        - OVERWATCH_COVER_WEIGHT * cover
                        - OVERWATCH_COVER_WEIGHT * doodadCover;
                if (screen != 0L) {
                    if (score < bestScreenedScore) {
                        bestScreenedScore = score;
                        bestScreened = new OverwatchPosition(cx, cy, screen);
                    }
                } else if (score < bestUnscreenedScore) {
                    bestUnscreenedScore = score;
                    bestUnscreened = new OverwatchPosition(cx, cy, 0L);
                }
            }
        }
        return bestScreened != null ? bestScreened : bestUnscreened;
    }

    private static OverwatchBand overwatchBand(MechLoadoutComponent loadout) {
        boolean hasLrmPressure = hasLrmPressure(loadout);
        if (!hasLrmPressure) {
            loadout.overwatchRearming = true;
        } else if (loadout.overwatchRearming && lrmRacksFull(loadout)) {
            loadout.overwatchRearming = false;
        }
        if (hasLrmPressure && !loadout.overwatchRearming) {
            return new OverwatchBand(OVERWATCH_MIN_DIST, OVERWATCH_MAX_DIST, true);
        }
        MechWeaponMount arms = loadout.mount(MechMountSlot.ARMS);
        if (arms == null || (!arms.hasAmmo() && arms.burstRemaining <= 0)) return null;
        float maxDistance = arms.weaponDef().range;
        float minDistance = Math.min(OVERWATCH_MIN_DIST,
                Math.max(0f, maxDistance - DIRECT_FALLBACK_BAND_DEPTH));
        return new OverwatchBand(minDistance, maxDistance, false);
    }

    private static boolean hasLrmPressure(MechLoadoutComponent loadout) {
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount != null
                    && WeaponRegistry.MECH_LRM_ARTILLERY_ID.equals(mount.weaponId())
                    && (mount.hasAmmo() || mount.burstRemaining > 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean lrmRacksFull(MechLoadoutComponent loadout) {
        boolean found = false;
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount == null
                    || !WeaponRegistry.MECH_LRM_ARTILLERY_ID.equals(mount.weaponId())) continue;
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
        sim.getUnitIndex().gatherFaction(
                threatX + 0.5f, threatY + 0.5f,
                OVERWATCH_MAX_DIST + SCREEN_AXIS_HALF_WIDTH,
                squad.faction, gathered);
        long[] vehicles = sim.getConvoyVehicleIds();
        int capacity = gathered.size + vehicles.length;
        long[] ids = new long[capacity];
        float[] xs = new float[capacity];
        float[] ys = new float[capacity];
        float[] radii = new float[capacity];
        boolean[] screenEligible = new boolean[capacity];
        int write = 0;
        for (int i = 0, n = gathered.size; i < n; i++) {
            long ally = gathered.ids[i];
            if (ally == member) continue;
            ids[write] = ally;
            xs[write] = sim.world().x(ally);
            ys[write] = sim.world().y(ally);
            MechVariant variant = sim.identity().mechVariant(ally);
            radii[write] = sim.physicalRadius(ally);
            screenEligible[write] = sim.identity().type(ally).combatant
                    && variant != MechVariant.SIROCCO;
            write++;
        }
        for (long ally : vehicles) {
            if (sim.resolveUnit(ally) == 0L
                    || sim.identity().faction(ally) != squad.faction) continue;
            ids[write] = ally;
            xs[write] = sim.world().x(ally);
            ys[write] = sim.world().y(ally);
            radii[write] = sim.physicalRadius(ally);
            screenEligible[write] = false;
            write++;
        }
        return new ScreeningAllies(
                ids, xs, ys, radii, screenEligible, write);
    }

    private static long screeningAlly(int candidateX, int candidateY,
                                      int threatX, int threatY,
                                      ScreeningAllies allies) {
        long best = 0L;
        float bestLateralSq = Float.MAX_VALUE;
        for (int i = 0; i < allies.size(); i++) {
            long ally = allies.ids()[i];
            SegmentGeometry geometry = segmentGeometry(
                    allies.xs()[i], allies.ys()[i], candidateX, candidateY,
                    threatX, threatY);
            if (geometry == null) continue;
            float laneClearance = Math.max(SCREEN_FIRE_LANE_CLEARANCE,
                    allies.radii()[i] + SCREEN_FIRE_LANE_MARGIN);
            if (geometry.progress()
                    > BallisticResolver.PROXIMITY_CATCH_ZERO_DISTANCE
                    && geometry.progress() < geometry.length()
                    && geometry.lateral() < laneClearance) {
                return BLOCKED_FIRING_LANE;
            }
            if (!allies.screenEligible()[i]
                    || geometry.progress() < SCREEN_ENDPOINT_CLEARANCE
                    || geometry.progress()
                    > geometry.length() - SCREEN_ENDPOINT_CLEARANCE
                    || geometry.lateral() > SCREEN_AXIS_HALF_WIDTH) {
                continue;
            }
            float lateralSq = geometry.lateral() * geometry.lateral();
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
        SegmentGeometry geometry = segmentGeometry(
                allyX, allyY, candidateX, candidateY, threatX, threatY);
        if (geometry == null
                || geometry.progress() < SCREEN_ENDPOINT_CLEARANCE
                || geometry.progress()
                > geometry.length() - SCREEN_ENDPOINT_CLEARANCE) {
            return -1f;
        }
        return geometry.lateral() >= SCREEN_FIRE_LANE_CLEARANCE
                && geometry.lateral() <= SCREEN_AXIS_HALF_WIDTH
                ? geometry.lateral() * geometry.lateral() : -1f;
    }

    private static SegmentGeometry segmentGeometry(
            float allyX, float allyY,
            int candidateX, int candidateY,
            int threatX, int threatY) {
        float startX = candidateX + 0.5f;
        float startY = candidateY + 0.5f;
        float dx = threatX + 0.5f - startX;
        float dy = threatY + 0.5f - startY;
        float lengthSq = dx * dx + dy * dy;
        if (lengthSq < 1e-4f) return null;
        float length = (float) Math.sqrt(lengthSq);
        float relX = allyX - startX;
        float relY = allyY - startY;
        float progress = (relX * dx + relY * dy) / length;
        if (progress <= 0f || progress >= length) return null;
        float lateral = Math.abs(relX * -dy + relY * dx) / length;
        return new SegmentGeometry(length, progress, lateral);
    }

    private record ScreeningAllies(long[] ids, float[] xs, float[] ys,
                                   float[] radii, boolean[] screenEligible,
                                   int size) {}

    private record SegmentGeometry(float length, float progress,
                                   float lateral) {}

    private record OverwatchBand(float minDistance, float maxDistance,
                                 boolean longRange) {}

    record OverwatchPosition(int x, int y, long screenId) {}
}
