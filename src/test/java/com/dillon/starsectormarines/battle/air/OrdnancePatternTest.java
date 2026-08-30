package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a run's fire actually lands.
 *
 * <p>The lethality numbers say a run kills people, which a fault that dropped
 * every round on the aircraft's own position would say just as loudly. This
 * asks the shape instead, and asks it the way the battle does: a lattice of
 * marker infantry is laid across the ground, one strike is flown, and the
 * markers that lost health are the footprint. Nothing is instrumented — what is
 * measured is the damage the rest of the game would have taken.
 */
class OrdnancePatternTest {

    private static final int W = 90;
    private static final int H = 70;
    private static final int TARGET_X = 45;
    private static final int TARGET_Y = 35;

    /** Spacing of the marker lattice, in cells. */
    private static final int STEP = 2;
    /** Half-width of the lattice, in markers. */
    private static final int REACH = 11;

    /** One marker and whether the run hurt it. */
    private record Mark(int x, int y, boolean hit) {}

    /**
     * Flies one pass over a lattice of markers and reports which were hit.
     *
     * <p>The aircraft comes in from a fixed corner and makes exactly one pass,
     * so the footprint is one run's and not an accumulation of three.
     */
    private static List<Mark> footprintOfOneRun(FighterProfile hull) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);

        List<long[]> markers = new ArrayList<>();
        int n = 0;
        for (int gy = -REACH; gy <= REACH; gy++) {
            for (int gx = -REACH; gx <= REACH; gx++) {
                int mx = TARGET_X + gx * STEP;
                int my = TARGET_Y + gy * STEP;
                long id = sim.spawn(new EntitySpec("k" + (n++), Faction.MARINE,
                        UnitType.MARINE, mx, my));
                markers.add(new long[]{ id, mx, my });
            }
        }
        // Somebody of the other side, far off, so the battle keeps ticking.
        sim.spawn(new EntitySpec("d0", Faction.DEFENDER, UnitType.MARINE, 2, 2));

        float[] before = new float[markers.size()];
        for (int i = 0; i < markers.size(); i++) {
            before[i] = sim.world().hp(markers.get(i)[0]);
        }

        long craft = sim.spawnSortie(hull, Faction.DEFENDER,
                TARGET_X + 0.5f, TARGET_Y + 0.5f, 6f, 6f, 6f, 6f, 0f);
        ShuttleMission mission = sim.world().mission(craft);
        mission.strikeSortie = true;
        mission.passesLeft = 1;
        sim.world().kinematics(craft).teleport(6f, 6f, 0f);
        mission.state = ShuttleState.INCOMING;

        for (int t = 0; t < 3000 && mission.state != ShuttleState.DEPARTING
                && mission.state != ShuttleState.GONE
                && mission.state != ShuttleState.RETURNING; t++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        List<Mark> footprint = new ArrayList<>();
        for (int i = 0; i < markers.size(); i++) {
            long id = markers.get(i)[0];
            // A marker that died is gone from the roster, which is a hit.
            boolean hurt = !sim.world().isAlive(id) || sim.world().hp(id) < before[i] - 0.01f;
            footprint.add(new Mark((int) markers.get(i)[1], (int) markers.get(i)[2], hurt));
        }
        return footprint;
    }

    /** Prints the footprint so it can be looked at rather than only counted. */
    private static void draw(String title, List<Mark> footprint) {
        StringBuilder out = new StringBuilder();
        for (int gy = -REACH; gy <= REACH; gy++) {
            out.append('[').append(title).append("] ");
            for (int gx = -REACH; gx <= REACH; gx++) {
                int mx = TARGET_X + gx * STEP;
                int my = TARGET_Y + gy * STEP;
                Mark mark = find(footprint, mx, my);
                boolean centre = gx == 0 && gy == 0;
                out.append(mark != null && mark.hit() ? '#' : (centre ? 'T' : '.'));
            }
            out.append('\n');
        }
        System.out.print(out);
    }

    private static Mark find(List<Mark> footprint, int x, int y) {
        for (Mark mark : footprint) if (mark.x() == x && mark.y() == y) return mark;
        return null;
    }

    private static int hits(List<Mark> footprint) {
        int hit = 0;
        for (Mark mark : footprint) if (mark.hit()) hit++;
        return hit;
    }

    /**
     * How wide the pattern is <em>across</em> the run, in cells.
     *
     * <p>Measured perpendicular to the flight line rather than as a sprawl
     * about the centre, because every pattern is long — the aircraft is
     * moving. What tells a painted line from a spray of craters is how far the
     * hits sit off the axis, and a distance-from-centroid number is dominated
     * by the length of the run and barely moves between them.
     */
    private static float widthAcrossTheRun(List<Mark> footprint) {
        // The aircraft comes in from the corner at (6,6) toward the target.
        float axisX = TARGET_X - 6f;
        float axisY = TARGET_Y - 6f;
        float length = (float) Math.hypot(axisX, axisY);
        axisX /= length;
        axisY /= length;
        float total = 0f;
        int hit = 0;
        for (Mark mark : footprint) {
            if (!mark.hit()) continue;
            float dx = mark.x() - TARGET_X;
            float dy = mark.y() - TARGET_Y;
            // Component perpendicular to the axis.
            total += Math.abs(dx * axisY - dy * axisX);
            hit++;
        }
        return hit == 0 ? 0f : total / hit;
    }

    /**
     * A cannon run puts fire across a swathe of ground, over the target, and
     * not in one spot.
     */
    @Test
    void aCannonRunWalksItsFireAcrossTheGround() {
        List<Mark> footprint = footprintOfOneRun(FighterProfile.BROADSWORD);
        draw("cannon", footprint);
        System.out.println("[cannon] " + hits(footprint) + " markers hit, width across the run "
                + widthAcrossTheRun(footprint));

        assertTrue(hits(footprint) >= 6,
                "a cannon pass touched only " + hits(footprint) + " markers");
        assertTrue(widthAcrossTheRun(footprint) > 1.5f,
                "the fire landed in a " + widthAcrossTheRun(footprint) + "-cell-wide line, not a swathe");
        // And it went over the target rather than somewhere else entirely.
        float nearest = Float.MAX_VALUE;
        for (Mark mark : footprint) {
            if (!mark.hit()) continue;
            nearest = Math.min(nearest,
                    (float) Math.hypot(mark.x() - TARGET_X, mark.y() - TARGET_Y));
        }
        assertTrue(nearest < 8f, "the nearest hit was " + nearest + " cells from the target");
    }

    /**
     * A beam lands more tightly than a cannon. The presets claim one paints a
     * line and the other throws craters, so the two had better measure apart.
     */
    @Test
    void aBeamPaintsATighterPatternThanACannonThrows() {
        List<Mark> beam = footprintOfOneRun(FighterProfile.THUNDER);
        List<Mark> cannon = footprintOfOneRun(FighterProfile.BROADSWORD);
        draw("beam", beam);
        System.out.println("[spread] width across the run — beam " + widthAcrossTheRun(beam)
                + " over " + hits(beam) + " markers, cannon " + widthAcrossTheRun(cannon)
                + " over " + hits(cannon));
        assertTrue(widthAcrossTheRun(beam) < widthAcrossTheRun(cannon),
                "the beam sprawled as widely as the cannon");
    }

    /**
     * A bomber releases what it loaded and stops, however long it is over the
     * target.
     *
     * <p>The whole difference between a bomb bay and a gun, and the thing a
     * second kind of aircraft needs from this model. Checked on the count the
     * sortie actually carries rather than inferred from the footprint: five
     * heavy bombs cover more ground than twenty cannon shells do, so a hit
     * count cannot tell the two apart and pretending it could would be a test
     * that passes for the wrong reason.
     */
    @Test
    void aBomberDropsItsLoadAndIsDone() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        sim.spawn(new EntitySpec("m0", Faction.MARINE, UnitType.MARINE, TARGET_X, TARGET_Y));
        sim.spawn(new EntitySpec("d0", Faction.DEFENDER, UnitType.MARINE, 2, 2));

        long craft = sim.spawnSortie(FighterProfile.DAGGER, Faction.DEFENDER,
                TARGET_X + 0.5f, TARGET_Y + 0.5f, 6f, 6f, 6f, 6f, 0f);
        ShuttleMission mission = sim.world().mission(craft);
        mission.strikeSortie = true;
        mission.passesLeft = 1;
        sim.world().kinematics(craft).teleport(6f, 6f, 0f);
        mission.state = ShuttleState.INCOMING;

        int loadedWith = 0;
        boolean ranDry = false;
        for (int t = 0; t < 3000 && mission.state != ShuttleState.DEPARTING
                && mission.state != ShuttleState.GONE
                && mission.state != ShuttleState.RETURNING; t++) {
            sim.advance(BattleSimulation.TICK_DT);
            if (mission.state != ShuttleState.ATTACK_RUN) continue;
            loadedWith = Math.max(loadedWith, mission.roundsLeftThisPass);
            if (mission.roundsLeftThisPass == 0) ranDry = true;
        }

        assertTrue(loadedWith > 0, "the bomber rolled in carrying nothing countable");
        assertTrue(loadedWith <= AirOrdnance.BOMBS.roundsPerPass,
                "the bomber loaded " + loadedWith + " for a bay that holds "
                        + AirOrdnance.BOMBS.roundsPerPass);
        assertTrue(ranDry, "the bomber never ran out, so its bay is not finite");
    }

    /** A gun is the other case: nothing to count down, it fires while it can. */
    @Test
    void aGunFighterIsNotLimitedByALoad() {
        assertTrue(AirOrdnance.AUTOCANNON.firesContinuously(),
                "a rotary cannon should not be rationed by the pass");
        assertTrue(AirOrdnance.BEAM.firesContinuously(),
                "a beam should not be rationed by the pass");
        assertTrue(!AirOrdnance.BOMBS.firesContinuously(),
                "a bomb bay should be finite");
    }
}
