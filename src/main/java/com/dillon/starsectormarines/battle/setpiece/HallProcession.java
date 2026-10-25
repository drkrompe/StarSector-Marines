package com.dillon.starsectormarines.battle.setpiece;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.optional.ObjectivePressure;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.objective.optional.PressuredObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MainStreet;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The hall emptied down the main street ({@code set-piece-blocks.md}; the main street district): the colony's
 * people sheltering in the civic hall come out a party at a time and walk the street to the plaza at its far
 * end, and the company keeps the street open while they do. The swarm comes in across the street from the side
 * streets and the gaps between the shopfronts.
 *
 * <p>Done when everyone left alive has walked out and reached the plaza, and that is at least half of them; lost
 * the moment fewer than half are alive. Once done they go their own ways.
 */
public final class HallProcession implements PressuredObjective.Task {

    /** People sheltering in the hall, at most and fewest: a small hall holds fewer. */
    static final int PEOPLE = 8, FEWEST = 6;
    /** People in each party sent out together. */
    static final int PARTY = 2;
    /** Seconds before the first party comes out, and between parties. */
    static final float FIRST_SECONDS = 15f, EVERY_SECONDS = 12f;
    /** A person this near their place in the plaza has reached it. */
    static final float ARRIVED = 2.5f;

    private final MainStreet street;
    private final Gathering people;
    private final List<int[]> plaza;
    private final boolean[] arrived;
    private int startTick = -1;
    private int released;
    private Status status = Status.ACTIVE;

    private HallProcession(MainStreet street, Gathering people, List<int[]> plaza) {
        this.street = street;
        this.people = people;
        this.plaza = plaza;
        this.arrived = new boolean[people.size()];
    }

    /**
     * The hall's people waiting inside its door; null where the street has no hall, or the hall or the plaza has
     * no room for them.
     */
    public static HallProcession install(BattleSimulation sim, List<PointOfInterest> buildings, MainStreet street) {
        if (street == null || street.landmarkDoorX() < 0) return null;
        NavigationGrid grid = sim.getGrid();
        PointOfInterest hall = null;
        for (PointOfInterest b : buildings) {
            if (street.landmarkDoorX() >= b.left && street.landmarkDoorX() <= b.right
                    && street.landmarkDoorY() >= b.top && street.landmarkDoorY() <= b.bottom) hall = b;
        }
        if (hall == null) return null;
        List<int[]> inside = placesInside(grid, hall, street.landmarkDoorX(), street.landmarkDoorY(), PEOPLE);
        int[] middle = plazaMiddle(street);
        List<int[]> plaza = Gathering.placesAround(grid, sim.getTopology(), middle[0], middle[1], PEOPLE);
        int count = Math.min(inside.size(), plaza.size());
        if (count < FEWEST) return null;
        Gathering people = Gathering.spawn(sim, "Hall", inside.subList(0, count), street.landmarkDoorX() + .5f,
                street.landmarkDoorY() + .5f);
        return new HallProcession(street, people, plaza);
    }

    @Override
    public Status advance(BattleSimulation sim, int tick) {
        if (status.isFinal()) return status;
        if (startTick < 0) startTick = tick;
        float elapsed = (tick - startTick) * BattleSimulation.TICK_DT;
        int due = elapsed < FIRST_SECONDS ? 0
                : Math.min(people.size(), PARTY * (1 + (int) ((elapsed - FIRST_SECONDS) / EVERY_SECONDS)));
        int[] middle = plazaMiddle(street);
        for (; released < due; released++) {
            int[] place = plaza.get(released);
            people.send(sim, released, place[0], place[1], middle[0] + .5f, middle[1] + .5f);
        }
        int alive = people.alive(sim), walking = 0;
        if (alive * 2 < people.size()) return status = Status.FAILED;
        for (int i = 0; i < released; i++) {
            if (!arrived[i]) arrived[i] = people.at(sim, i, plaza.get(i)[0], plaza.get(i)[1], ARRIVED);
            if (!arrived[i] && people.alive(sim, i)) walking++;
        }
        if (released >= people.size() && walking == 0) {
            people.disperse(sim);
            return status = Status.SUCCEEDED;
        }
        return status;
    }

    /** Half the parties out is half way; the last party out brings everything the pressure owns onto the street. */
    @Override
    public ObjectivePressure.Beat beat() {
        float progress = released / (float) people.size();
        return released >= people.size()
                ? new ObjectivePressure.Beat(progress, true, spineMiddle(street))
                : ObjectivePressure.Beat.underWay(progress);
    }

    /** People sent out of the hall so far. */
    public int released() { return released; }

    /** People who have reached the plaza. */
    public int arrived() {
        int n = 0;
        for (boolean a : arrived) if (a) n++;
        return n;
    }

    /** People left alive. */
    public int alive(BattleSimulation sim) { return people.alive(sim); }

    /** The street walked. */
    public MainStreet street() { return street; }

    /** The middle of the street's spine, hall to plaza. */
    public static int[] spineMiddle(MainStreet street) {
        return new int[]{(int) Math.floor((street.startX() + street.endX()) / 2f),
                (int) Math.floor((street.startY() + street.endY()) / 2f)};
    }

    private static int[] plazaMiddle(MainStreet street) {
        return new int[]{(street.plazaLeft() + street.plazaRight()) / 2, (street.plazaTop() + street.plazaBottom()) / 2};
    }

    /** Up to {@code count} places on the hall's floor nearest its door, never a doorway: a crowd waiting to go. */
    static List<int[]> placesInside(NavigationGrid grid, PointOfInterest hall, int doorX, int doorY, int count) {
        List<int[]> places = new ArrayList<>();
        int w = grid.getWidth(), h = grid.getHeight();
        boolean[] seen = new boolean[w * h];
        ArrayDeque<int[]> open = new ArrayDeque<>();
        open.add(new int[]{doorX, doorY});
        seen[doorY * w + doorX] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty() && places.size() < count) {
            int[] at = open.poll();
            if (!grid.isDoorway(at[0], at[1])) places.add(at);
            for (int[] d : dirs) {
                int nx = at[0] + d[0], ny = at[1] + d[1];
                if (nx <= hall.left || ny <= hall.top || nx >= hall.right || ny >= hall.bottom) continue;
                if (seen[ny * w + nx] || !grid.isWalkable(nx, ny)) continue;
                seen[ny * w + nx] = true;
                open.add(new int[]{nx, ny});
            }
        }
        return places;
    }
}
