package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Cell;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleLeg;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.List;

/**
 * Sable's rocket truck once the alarm is up: it works the corners. Its rockets reach no farther than a mech's own
 * rack and a Hound's chaingun reaches farther, so in the open it dies first; behind a wall it waits for armor to come
 * round to it. Every so often it looks at the nearest enemy armor and drives, down the yard's lane, to the stand that
 * armor cannot see that lies nearest it ({@link #choose}); with armor in reach and in sight it holds and fires. Men it
 * leaves to the crews: with no armor on the field it stays where it is. The Dust Ledger fields it in Saltwell Cross's
 * east yard and Blind Watch on the Sounding's uplink knoll; it reads only its stands and the grid.
 */
final class DustLedgerTechnical {
    /** Ticks between looks at the field: two seconds. */
    static final int LOOK_EVERY_TICKS = 60;
    /** How much nearer a new hidden stand must be before the truck leaves a hidden one, so it does not shuttle. */
    static final float SWITCH_MARGIN = 6f;
    /** Within this of its stand the truck counts as there. */
    static final float AT_STAND = 1.5f;

    private final long id;
    private final List<Cell> stands;
    private final VehicleClearance lane;
    private final float reach;
    private int stand;
    private int goal = -1;

    DustLedgerTechnical(BattleSimulation sim, long id, List<Cell> stands) {
        if (stands.isEmpty()) throw new IllegalArgumentException("the truck needs a stand");
        this.id = id;
        this.stands = List.copyOf(stands);
        VehicleType type = sim.convoy().vehicleType(id);
        this.lane = VehicleClearance.erode(sim.getGrid(), VehicleClearance.radiusForWidth(type.visualWidthCells));
        this.reach = type.turretStructure().mount.weapon.range;
    }

    long id() { return id; }
    /** The stand it holds or is driving to. */
    Cell stand() { return stands.get(goal >= 0 ? goal : stand); }

    /** One look at the field after the alarm: hold and fire, stay, or drive to a better corner. */
    void tick(BattleSimulation sim) {
        if (sim.getSimTickIndex() % LOOK_EVERY_TICKS != 0) return;
        VehicleMission mission = sim.convoy().mission(id);
        if (mission == null || mission.state != VehicleState.DEPLOYED) return;
        GroundBody body = sim.convoy().body(id);
        if (goal >= 0 && mission.authoredRouteX == null) {
            stand = goal;
            goal = -1;
        }
        float[] threat = nearestArmor(sim, body.x, body.y);
        if (threat == null) return;
        NavigationGrid grid = sim.getGrid();
        GroundTurret turret = sim.convoy().turret(id);
        boolean engaged = turret != null && turret.targetId != 0L
                || Math.hypot(threat[0] - body.x, threat[1] - body.y) <= reach && !hidden(grid, threat[0], threat[1], body.x, body.y);
        if (engaged) {
            // In a fight it stops where it is and shoots; it does not drive off with the armor in its sights.
            if (mission.authoredRouteX != null) mission.authoredRoutePaused = true;
            return;
        }
        int current = goal >= 0 ? goal : stand;
        boolean[] hiddenFrom = new boolean[stands.size()];
        float[] xs = new float[stands.size()], ys = new float[stands.size()];
        for (int i = 0; i < stands.size(); i++) {
            xs[i] = stands.get(i).x() + .5f;
            ys[i] = stands.get(i).y() + .5f;
            hiddenFrom[i] = hidden(grid, threat[0], threat[1], xs[i], ys[i]);
        }
        int next = choose(xs, ys, hiddenFrom, threat[0], threat[1], current);
        boolean there = Math.hypot(xs[next] - body.x, ys[next] - body.y) <= AT_STAND;
        if (next == current && (there || mission.authoredRouteX != null)) {
            mission.authoredRoutePaused = false;
            return;
        }
        drive(sim, mission, body, next);
    }

    /**
     * Which stand to make for: of those the threat cannot see, the nearest it; the one held while it stays hidden
     * unless another is {@link #SWITCH_MARGIN} nearer; and the one held when none is hidden, since a truck caught in
     * the open is no better off crossing more of it. Pure.
     */
    static int choose(float[] xs, float[] ys, boolean[] hiddenFrom, float threatX, float threatY, int current) {
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            if (!hiddenFrom[i]) continue;
            float d = (float) Math.hypot(xs[i] - threatX, ys[i] - threatY);
            if (d < bestDistance) { bestDistance = d; best = i; }
        }
        if (best < 0) return current;
        if (current >= 0 && hiddenFrom[current]
                && bestDistance > Math.hypot(xs[current] - threatX, ys[current] - threatY) - SWITCH_MARGIN) return current;
        return best;
    }

    /** Whether a wall stands between the two points: what the truck's corners are for. */
    static boolean hidden(NavigationGrid grid, float ax, float ay, float bx, float by) {
        return grid.firstWallOnLine(ax, ay, bx, by) != -1L;
    }

    private void drive(BattleSimulation sim, VehicleMission mission, GroundBody body, int next) {
        Cell to = stands.get(next);
        int[] path = GridPathfinder.findPathOnDemand(sim.getGrid(), (int) body.x, (int) body.y, to.x(), to.y(),
                index -> 1f, lane::isPassableAt);
        if (path.length < 4) return;
        float[] routeX = new float[path.length / 2], routeY = new float[path.length / 2];
        for (int i = 0; i < routeX.length; i++) { routeX[i] = path[i * 2] + .5f; routeY[i] = path[i * 2 + 1] + .5f; }
        mission.authoredRouteX = routeX;
        mission.authoredRouteY = routeY;
        mission.authoredLeg = VehicleLeg.MOVE_ORDER;
        mission.authoredRoutePaused = false;
        goal = next;
    }

    /** The nearest enemy machine, a mech or a live hull, as {x, y}; {@code null} when the company has none down. */
    private static float[] nearestArmor(BattleSimulation sim, float x, float y) {
        float[] best = null;
        double bestDistance = Double.MAX_VALUE;
        List<float[]> armor = new ArrayList<>();
        for (int i = 0, live = sim.liveUnitCount(); i < live; i++) {
            long unit = sim.liveUnitAt(i);
            if (!Faction.DEFENDER.hostileTo(sim.identity().faction(unit)) || !sim.world().hasMechLoadout(unit)
                    || !sim.world().hasPosition(unit) || sim.isRiding(unit)) continue;
            armor.add(new float[] {sim.world().x(unit), sim.world().y(unit)});
        }
        for (long hull : sim.convoy().entityIds()) {
            VehicleMission mission = sim.convoy().mission(hull);
            if (!Faction.DEFENDER.hostileTo(sim.convoy().faction(hull)) || mission == null
                    || mission.state != VehicleState.DEPLOYED || !sim.convoy().isTargetable(hull)) continue;
            armor.add(new float[] {sim.convoy().body(hull).x, sim.convoy().body(hull).y});
        }
        for (float[] at : armor) {
            double d = Math.hypot(at[0] - x, at[1] - y);
            if (d < bestDistance) { bestDistance = d; best = at; }
        }
        return best;
    }
}
