package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.infantry.ApproachBound;
import com.dillon.starsectormarines.battle.infantry.LaneSidestep;
import com.dillon.starsectormarines.battle.infantry.PatrolMotion;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.mech.MechCombatantBehavior;
import com.dillon.starsectormarines.battle.mech.MechRouteIntent;
import com.dillon.starsectormarines.battle.mech.MechTargeting;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.FrontageLine;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * Holds a player-authored circle while reacting to the squad's own contact
 * picture. Members form a threat-facing line in quiet belief, improve into
 * directional cover when available, and engage only from firing positions
 * inside the ordered area.
 */
public final class DefendArea implements Action {

    private static final int PREPARED_POSITION_SEARCH_RADIUS = 5;
    private static final float PREPARED_LINE_REARWARD_OFFSET = 3f;
    private static final float PREPARED_LINE_SPACING = 2.5f;
    /** Target gap, in cells, between neighbours standing in a quiet circle. */
    private static final float QUIET_SPACING = 4f;
    /** A sunflower of N points spaced S apart has radius about S*sqrt(N)/this. */
    private static final float QUIET_DENSITY_DIVISOR = 1.77f;
    /** Quiet members stay well inside the edge so a flank is not the first thing outside the circle. */
    private static final float QUIET_MAX_RADIUS_SHARE = 0.7f;
    private static final double GOLDEN_ANGLE = Math.PI * (3d - Math.sqrt(5d));
    private static final int STAND_QUIET = 0;
    private static final int STAND_PREPARED = 1;

    private final int centerX;
    private final int centerY;
    private final int radius;

    // The squad's stands as last settled by squadStand, and what they were settled for; guarded by this.
    private int[] standCells;
    private int standTick;
    private int standKind;
    private int standThreatX;
    private int standThreatY;
    private List<Long> standMembers = List.of();

    public DefendArea(int centerX, int centerY, int radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
    }

    @Override public String name() { return "DefendArea"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState state, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() != AssignmentKind.DEFEND_AREA
                || assignment.targetCellX() != centerX
                || assignment.targetCellY() != centerY
                || assignment.targetRadiusCells() != radius) {
            sim.clearPath(member);
            return ActionStatus.FAILURE;
        }

        boolean mech = sim.world().hasMechLoadout(member);
        long target;
        if (mech) {
            // A mech's weapons are triggered by the mech fire pass, not by the
            // fire intent a marine's shot is queued through, so the order has
            // to pull that trigger itself or the chassis holds its circle and
            // watches the enemy walk up to it.
            target = MechTargeting.refreshTarget(member, sim);
            sim.world().setTargetId(member, target);
            if (target != 0L) fireMechWeapons(member, target, sim);
        } else {
            target = sim.targetOf(member);
            if (target == 0L
                    || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
                target = sim.getTacticalScoring().findBestTarget(member);
            }
            if (target != 0L && !inReach(member, target, sim)) {
                target = fromItsGround(target, sim.getTacticalScoring().findBestTargetInReach(member));
            }
            sim.world().setTargetId(member, target);
        }
        if (target != 0L) return engageInsideArea(member, target, squad, sim);

        SquadContactPicture picture = squad.contactPicture;
        int[] destination = picture.hasContacts()
                && picture.primaryCellX() >= 0 && picture.primaryCellY() >= 0
                ? preparedPosition(member, picture.primaryCellX(),
                        picture.primaryCellY(), squad, sim)
                : quietPosition(member, squad, sim);
        moveToward(member, destination[0], destination[1], sim);
        return ActionStatus.RUNNING;
    }

    /**
     * A defender holds its ground: a target it would have to move to hit gives way to one it can hit from
     * where it stands ({@code inReach}, 0 when there is none). A guard watching a flier at the edge of its
     * sight while runners cross the yard is the failure this prevents.
     */
    static long fromItsGround(long target, long inReach) {
        return inReach != 0L ? inReach : target;
    }

    private static boolean inReach(long member, long target, BattleControl sim) {
        return TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member),
                sim.world().x(target), sim.world().y(target)) <= sim.world().attackRange(member)
                && sim.getTacticalScoring().hasClearShot(member, target);
    }

    /** Fires every mount that is in band on {@code target}, from wherever the mech stands. */
    private static void fireMechWeapons(long member, long target, BattleControl sim) {
        float distance = TacticalScoring.cellDistance(
                sim.world().x(member), sim.world().y(member),
                sim.world().x(target), sim.world().y(target));
        if (distance > sim.world().attackRange(member)) return;
        MechLoadoutComponent loadout = sim.world().mechLoadout(member);
        MechCombatantBehavior.tryFireMechWeapons(member, loadout, target, distance, sim,
                sim.getTacticalScoring().hasClearShot(member, target));
    }

    private ActionStatus engageInsideArea(long member, long target, Squad squad,
                                          BattleControl sim) {
        int[] claimed = claimedCell(member, sim);
        if (claimed != null) {
            // A marine holding a fighting-position cell fights from it. Hunting a better
            // firing cell elsewhere in the circle would walk him out of the position and
            // restart his dig-in, so one without a shot waits in the trench instead; the
            // claims move him when the believed threat turns the position's cover away.
            if (!sim.movement().atCell(member, claimed[0], claimed[1])) {
                moveToward(member, claimed[0], claimed[1], sim, true);
                return ActionStatus.RUNNING;
            }
            if (LaneSidestep.isStepping(member, sim)) {
                sim.advanceMovement(member);
            } else {
                PatrolMotion.hold(member, sim);
            }
            float held = TacticalScoring.cellDistance(
                    sim.world().x(member), sim.world().y(member),
                    sim.world().x(target), sim.world().y(target));
            if (held <= sim.world().attackRange(member)
                    && sim.getTacticalScoring().hasClearShot(member, target)) {
                sim.combat().setFireIntent(member, target, FireStance.STANCED, false);
            }
            return ActionStatus.RUNNING;
        }
        FrontageLine line = squad == null ? null : sim.frontageLineOf(squad.id);
        int[] open = line == null ? null : sim.fightingPositionClaims().openCellOf(member);
        if (open != null && sim.movement().atCell(member, open[0], open[1])) {
            // A marine on a drawn line fights from the cover the line gave him against its front
            // while it gives him a shot, rather than stepping out to a better angle on one runner.
            float held = TacticalScoring.cellDistance(
                    sim.world().x(member), sim.world().y(member),
                    sim.world().x(target), sim.world().y(target));
            if (held <= sim.world().attackRange(member)
                    && sim.getTacticalScoring().hasClearShot(member, target)) {
                if (LaneSidestep.isStepping(member, sim)) {
                    sim.advanceMovement(member);
                } else {
                    PatrolMotion.hold(member, sim);
                }
                sim.combat().setFireIntent(member, target, FireStance.STANCED, false);
                return ActionStatus.RUNNING;
            }
        }
        int[] firingPosition = onLine(sim.getTacticalScoring().findFiringPositionWithin(
                member, target, centerX, centerY, radius), line);
        if (firingPosition == null) {
            long alternative = sim.getTacticalScoring().findEngageableEnemyWithin(
                    member, centerX, centerY, radius);
            if (alternative != 0L && alternative != target) {
                sim.world().setTargetId(member, alternative);
                target = alternative;
                firingPosition = onLine(sim.getTacticalScoring().findFiringPositionWithin(
                        member, target, centerX, centerY, radius), line);
            }
        }
        if (firingPosition == null) {
            int[] prepared = preparedPosition(member,
                    sim.world().cellX(target), sim.world().cellY(target), squad, sim);
            moveToward(member, prepared[0], prepared[1], sim);
            return ActionStatus.RUNNING;
        }

        float distance = TacticalScoring.cellDistance(
                sim.world().x(member), sim.world().y(member),
                sim.world().x(target), sim.world().y(target));
        boolean canFireHere = inside(sim.world().cellX(member),
                sim.world().cellY(member))
                && distance <= sim.world().attackRange(member)
                && sim.getTacticalScoring().hasClearShot(member, target);

        if (canFireHere && sim.movement().atCell(member,
                firingPosition[0], firingPosition[1])) {
            // Planting is the ordinary answer, but a step-aside already under
            // way is walked instead of dropped: the marine is moving out of a
            // squadmate's lane, not off his firing position, and he shoots on
            // the same tick either way.
            if (LaneSidestep.isStepping(member, sim)) {
                sim.advanceMovement(member);
            } else {
                PatrolMotion.hold(member, sim);
            }
            // A mech has already fired through its own weapon pass.
            if (!sim.world().hasMechLoadout(member)) {
                sim.combat().setFireIntent(member, target, FireStance.STANCED, false);
            }
            return ActionStatus.RUNNING;
        }

        moveToward(member, firingPosition[0], firingPosition[1], sim, true);
        return ActionStatus.RUNNING;
    }

    /** A firing cell off a drawn line's band is no firing cell for a squad holding the line. */
    private static int[] onLine(int[] cell, FrontageLine line) {
        return cell == null || line == null || line.covers(cell[0], cell[1]) ? cell : null;
    }

    private int[] preparedPosition(long member, int threatX, int threatY,
                                   Squad squad, BattleView sim) {
        int[] claimed = claimedCell(member, sim);
        if (claimed != null) return claimed;
        FrontageLine line = squad == null ? null : sim.frontageLineOf(squad.id);
        if (line != null) return linePosition(member, line, squad, sim);
        if (threatX == centerX && threatY == centerY) return quietPosition(member, squad, sim);
        return squadStand(member, STAND_PREPARED, threatX, threatY, squad, sim);
    }

    /**
     * Where a member holds a drawn line when no prepared position was his: the open-ground cell
     * the claims settled for him, the best cover against the line's front near his share of it
     * ({@link LinePlacement#openCells}). Shares are spaced evenly along the whole line, so a
     * squad on open ground stands as a skirmish line across the ground the player drew. Before
     * the claims have run for a new order, his share itself.
     */
    private int[] linePosition(long member, FrontageLine line, Squad squad, BattleView sim) {
        int[] open = sim.fightingPositionClaims().openCellOf(member);
        if (open != null) return open;
        int ordinal = memberOrdinal(member, squad, sim);
        int count = Math.max(1, squad.autonomousMemberCount(sim));
        int[] share = LinePlacement.share(line, ordinal, count);
        return nearestWalkableInside(share[0], share[1], sim);
    }

    /**
     * The position this member holds under the squad's claims (a fighting-position cell, or a
     * building post on a drawn line), or {@code null}: overflow, no positions in the ground, or
     * fighting positions off on an order that is not a line. Claims are settled in the
     * serial phase before dispatch; this only reads them.
     */
    private static int[] claimedCell(long member, BattleView sim) {
        return sim.fightingPositionClaims().cellOf(member);
    }


    /** A member left without a position must not settle on one a squadmate holds. */
    private static boolean claimedByOther(int x, int y, long member, BattleView sim) {
        return sim.fightingPositionClaims()
                .isClaimedByOther(x, y, sim.getGrid().getWidth(), member);
    }

    private int[] quietPosition(long member, Squad squad, BattleView sim) {
        int[] claimed = claimedCell(member, sim);
        if (claimed != null) return claimed;
        FrontageLine line = squad == null ? null : sim.frontageLineOf(squad.id);
        if (line != null) return linePosition(member, line, squad, sim);
        return squadStand(member, STAND_QUIET, -1, -1, squad, sim);
    }

    /**
     * Where {@code member} stands in the circle while he holds no position of his own, with every
     * squadmate given a different cell. The squad's stands are settled together in member order
     * ({@link DistinctStands}), once per tick and kind of stand: each member's own rule, the best
     * cover near his slot, finds the same cover cell as his neighbours whenever their searches
     * overlap, and squadmates sent to one cell jostle round its centre without any of them ever
     * counting as arrived. A drawn line's open cells are settled the same way by the claims.
     */
    private int[] squadStand(long member, int kind, int threatX, int threatY,
                             Squad squad, BattleView sim) {
        if (squad == null) return standCell(kind, 0, 1, threatX, threatY, (x, y) -> false, sim);
        List<Long> members = FightingPositionClaims.availableMembers(squad, sim);
        int ordinal = members.indexOf(member);
        if (ordinal < 0) {
            return standCell(kind, 0, Math.max(1, members.size()), threatX, threatY,
                    (x, y) -> claimedByOther(x, y, member, sim), sim);
        }
        int width = sim.getGrid().getWidth();
        int[] cells;
        synchronized (this) {
            int tick = sim.getSimTickIndex();
            if (standCells == null || standTick != tick || standKind != kind
                    || standThreatX != threatX || standThreatY != threatY
                    || !standMembers.equals(members)) {
                int count = members.size();
                standCells = DistinctStands.assign(count, width, (i, taken) -> {
                    long candidate = members.get(i);
                    if (claimedCell(candidate, sim) != null) return null;
                    return standCell(kind, i, count, threatX, threatY,
                            (x, y) -> taken.test(x, y) || claimedByOther(x, y, candidate, sim), sim);
                });
                standTick = tick;
                standKind = kind;
                standThreatX = threatX;
                standThreatY = threatY;
                standMembers = members;
            }
            cells = standCells;
        }
        int cell = cells[ordinal];
        if (cell == DistinctStands.NONE) {
            return standCell(kind, ordinal, members.size(), threatX, threatY,
                    (x, y) -> claimedByOther(x, y, member, sim), sim);
        }
        return new int[]{cell % width, cell / width};
    }

    /**
     * Member {@code ordinal} of {@code count}'s stand, avoiding every cell {@code taken} accepts
     * while any other will do: in a quiet circle his point of the sunflower; facing a threat, his
     * slot of a line across the threat axis behind the centre, settled into the best cover near it.
     */
    private int[] standCell(int kind, int ordinal, int count, int threatX, int threatY,
                            BiPredicate<Integer, Integer> taken, BattleView sim) {
        if (kind == STAND_QUIET) {
            float[] offset = quietOffset(ordinal, count, radius);
            return nearestWalkableInside(Math.round(centerX + offset[0]),
                    Math.round(centerY + offset[1]), taken, sim);
        }
        float axisX = threatX - centerX;
        float axisY = threatY - centerY;
        float length = (float) Math.sqrt(axisX * axisX + axisY * axisY);
        axisX /= length;
        axisY /= length;
        float lateral = (ordinal - (count - 1) * 0.5f) * PREPARED_LINE_SPACING;
        int desiredX = Math.round(centerX - axisX * PREPARED_LINE_REARWARD_OFFSET
                - axisY * lateral);
        int desiredY = Math.round(centerY - axisY * PREPARED_LINE_REARWARD_OFFSET
                + axisX * lateral);
        int[] covered = sim.getTacticalScoring().bestCoverCell(
                threatX, threatY, desiredX, desiredY,
                PREPARED_POSITION_SEARCH_RADIUS, (x, y) -> !inside(x, y) || taken.test(x, y));
        if (covered != null) return covered;
        return nearestWalkableInside(desiredX, desiredY, taken, sim);
    }

    /**
     * Offset from the circle's centre for member {@code ordinal} of {@code count}
     * while no enemy is known: a sunflower spiral whose members sit about
     * {@link #QUIET_SPACING} cells apart, kept inside the ordered circle. A lone
     * member holds the centre; a larger squad fans out rather than packing a block.
     */
    static float[] quietOffset(int ordinal, int count, int radius) {
        if (count <= 1) return new float[]{0f, 0f};
        float reach = Math.min(radius * QUIET_MAX_RADIUS_SHARE,
                QUIET_SPACING * (float) Math.sqrt(Math.max(1, count)) / QUIET_DENSITY_DIVISOR);
        float distance = reach * (float) Math.sqrt((ordinal + 0.5f) / count);
        double angle = ordinal * GOLDEN_ANGLE;
        return new float[]{(float) (Math.cos(angle) * distance), (float) (Math.sin(angle) * distance)};
    }

    private int[] nearestWalkableInside(int wantedX, int wantedY, BattleView sim) {
        return nearestWalkableInside(wantedX, wantedY, (x, y) -> false, sim);
    }

    /**
     * {@code wanted} if it is walkable, inside and not {@code taken}, else the nearest such cell of
     * the circle; with every walkable cell of it taken, the nearest walkable one regardless.
     */
    private int[] nearestWalkableInside(int wantedX, int wantedY,
                                        BiPredicate<Integer, Integer> taken, BattleView sim) {
        if (inside(wantedX, wantedY) && sim.getGrid().inBounds(wantedX, wantedY)
                && sim.getGrid().isWalkable(wantedX, wantedY) && !taken.test(wantedX, wantedY)) {
            return new int[]{wantedX, wantedY};
        }
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        int[] shared = null;
        int sharedDistance = Integer.MAX_VALUE;
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                if (!inside(x, y) || !sim.getGrid().inBounds(x, y)
                        || !sim.getGrid().isWalkable(x, y)) continue;
                int dx = x - wantedX;
                int dy = y - wantedY;
                int distance = dx * dx + dy * dy;
                if (taken.test(x, y)) {
                    if (distance < sharedDistance) {
                        sharedDistance = distance;
                        shared = new int[]{x, y};
                    }
                } else if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new int[]{x, y};
                }
            }
        }
        if (best != null) return best;
        return shared != null ? shared : new int[]{centerX, centerY};
    }

    private void moveToward(long member, int x, int y, BattleControl sim) {
        moveToward(member, x, y, sim, false);
    }

    /**
     * {@code boundDetour} refuses a route out of proportion to the straight
     * line it stands in for — see {@link ApproachBound}. Only the firing
     * position passes {@code true}: it is a cell chosen as an improvement
     * inside the area, so walking round the outside of a building to reach it
     * spends the defence of the area on getting somewhere marginally better.
     * A prepared or quiet position is where the member belongs, and it goes
     * there whichever way the ground allows.
     */
    private void moveToward(long member, int x, int y, BattleControl sim,
                            boolean boundDetour) {
        if (sim.world().hasMechLoadout(member)) {
            MechRouteIntent.forMember(member, DefendArea.class,
                    MechRouteIntent.cellKey(centerX, centerY), sim).moveToward(member, x, y, sim, boundDetour);
            return;
        }
        // Same exception, on the branch that re-paths rather than plants: a
        // path whose destination is not this member's own firing position is
        // normally stale and cleared, and a step-aside's is exactly that.
        if (LaneSidestep.isStepping(member, sim)) {
            sim.advanceMovement(member);
            return;
        }
        if (sim.movement().atCell(member, x, y)) {
            PatrolMotion.hold(member, sim);
            return;
        }
        int[] path = sim.world().path(member);
        if (!Paths.isEmpty(path)
                && (Paths.destX(path) != x || Paths.destY(path) != y)) {
            sim.clearPath(member);
            path = sim.world().path(member);
        }
        if (sim.movement().mayRepath(member)
                && sim.world().pathIdx(member) >= Paths.cellCount(path)) {
            int fromX = sim.world().cellX(member);
            int fromY = sim.world().cellY(member);
            int[] found = GridPathfinder.findPath(sim.getGrid(), fromX, fromY,
                    x, y, sim.getOccupancyMap());
            if (!ApproachBound.worthWalkingTo(fromX, fromY, x, y, found, boundDetour)) {
                PatrolMotion.hold(member, sim);
                return;
            }
            sim.setPath(member, found);
        }
        sim.advanceMovement(member);
    }

    private boolean inside(int x, int y) {
        int dx = x - centerX;
        int dy = y - centerY;
        return dx * dx + dy * dy <= radius * radius;
    }

    private static int memberOrdinal(long member, Squad squad, BattleView sim) {
        if (squad == null) return 0;
        int ordinal = 0;
        for (int i = 0, count = sim.squadMemberCount(squad.id); i < count; i++) {
            long candidate = sim.squadMemberAt(squad.id, i);
            if (!squad.availableToPlan(candidate, sim)) continue;
            if (candidate == member) return ordinal;
            ordinal++;
        }
        return 0;
    }

    @Override
    public List<int[]> highlightCells(Squad squad, BattleView sim) {
        return List.of(new int[]{centerX, centerY});
    }

    public int centerX() { return centerX; }
    public int centerY() { return centerY; }
    public int radius() { return radius; }
}
