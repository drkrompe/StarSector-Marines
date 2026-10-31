package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.List;

/**
 * The cut-off town's people ({@code cordon-nouns.md}, Cut-off town): civilians trapped in the town's staging area, who
 * stay there (they never walk off or flee on their own) until a convoy loads at the gate, then walk out to its trucks
 * and board them one at a time. A boarding takes the civilian off
 * the field and puts one more aboard that truck; a civilian the swarm kills before boarding is a townsperson lost.
 * They are walked, not commanded, as the Line's refugees are ({@link CordonRefugees}), and they are prey.
 *
 * <p>People the compound holds but who were never stood up (no room for them, or no field at all, as in a rules
 * test) are a count: they board straight from it at the same pace, so the convoys' tally never depends on whether
 * the town could be shown.
 */
final class CordonTownspeople {

    /** A truck at the gate with seats to fill. */
    interface Seat {
        /** Where the truck stands now: {x, y}. */
        float[] at(BattleSimulation sim);
        /** Seats still free on it. */
        int free();
        /** Whether it takes the next aboard at this tick (one at a time, at its convoy's pace). */
        boolean opens(int tick);
        /** One more aboard. */
        void board(int tick);
    }

    /** Cells into the compound past the gate the townspeople stand with no staging area given, behind the militia. */
    static final int DEPTH = 14;
    /** Cells from a truck's middle a walking townsperson climbs aboard. */
    static final float BOARD_REACH = 4f;

    private static final class Person {
        final long id;
        final int homeX, homeY;
        /** The truck it walks to, or {@code null} while it stands (or walks back) at home. */
        Seat seat;
        int destX = Integer.MIN_VALUE, destY = Integer.MIN_VALUE;
        /** The tick it was last given a path. */
        int pathTick = Integer.MIN_VALUE;

        Person(long id, int homeX, int homeY) {
            this.id = id;
            this.homeX = homeX;
            this.homeY = homeY;
        }
    }

    private final List<Person> people = new ArrayList<>();
    /** The compound's people who are a count, never stood up. */
    private int counted;
    private int lost, boarded;

    private CordonTownspeople(int counted) {
        this.counted = counted;
    }

    /** The town's people as a count only. */
    static CordonTownspeople counted(int people) {
        return new CordonTownspeople(Math.max(0, people));
    }

    /**
     * Stands the town's people up on {@code cells} (the staging area), nearest its middle first, on as many of them
     * as are open (walkable, no doorway, nobody standing there: stood after the militia, their cells are taken
     * first); the rest stay a count.
     */
    static CordonTownspeople stand(BattleSimulation sim, List<int[]> cells, int people) {
        CordonTownspeople town = new CordonTownspeople(Math.max(0, people));
        byte[] occupancy = sim.getOccupancyMap();
        List<int[]> open = new ArrayList<>();
        for (int[] c : cells) {
            if (sim.getGrid().inBounds(c[0], c[1]) && sim.getGrid().isWalkable(c[0], c[1])
                    && !sim.getGrid().isDoorway(c[0], c[1]) && (occupancy[sim.getGrid().index(c[0], c[1])] & 0xFF) == 0) {
                open.add(c);
            }
        }
        List<int[]> chosen = huddle(open, town.counted);
        for (int i = 0; i < chosen.size(); i++) {
            int[] cell = chosen.get(i);
            long id = sim.spawn(new EntitySpec("Townsperson " + (i + 1), Faction.CIVILIAN, UnitType.CIVILIAN,
                    cell[0], cell[1]).role(UnitRole.VIP));
            town.people.add(new Person(id, cell[0], cell[1]));
        }
        town.counted -= town.people.size();
        return town;
    }

    /**
     * Up to {@code count} of {@code cells}, nearest their middle first: where a crowd huddles in its staging area.
     */
    static List<int[]> huddle(List<int[]> cells, int count) {
        if (cells.isEmpty()) return List.of();
        float mx = 0, my = 0;
        for (int[] c : cells) {
            mx += c[0];
            my += c[1];
        }
        mx /= cells.size();
        my /= cells.size();
        List<float[]> at = new ArrayList<>();
        for (int[] c : cells) at.add(new float[]{c[0], c[1]});
        List<int[]> out = new ArrayList<>();
        for (int i : nearest(at, mx, my, count)) out.add(cells.get(i));
        return out;
    }

    /** One more stands on the field here, at home on {@code (homeX, homeY)}: someone brought out to the camp. */
    void admit(long id, int homeX, int homeY) {
        people.add(new Person(id, homeX, homeY));
    }

    /**
     * The town is given up: everyone still trapped dies, taken off the field (a walker included) and counted lost,
     * and the counted with them.
     */
    void abandon(BattleSimulation sim) {
        for (Person p : people) {
            if (sim.resolveUnit(p.id) != 0L && sim.world().isAlive(p.id)) {
                sim.clearPath(p.id);
                sim.takeOffTheField(p.id);
            }
            lost++;
        }
        people.clear();
        lost += counted;
        counted = 0;
    }

    /** People still trapped in the town (alive, not boarded): standing, walking to a truck, or a count. */
    int left() { return people.size() + counted; }
    /** Killed before they boarded. */
    int lost() { return lost; }
    /** Aboard a truck, all told. */
    int boarded() { return boarded; }
    /** Standing on the field (not a count), for evidence. */
    int onField() { return people.size(); }

    /**
     * One tick: drops the dead, sends people out to the trucks with seats free, walks everyone a step, and boards
     * whoever has reached their truck while it takes the next.
     */
    void step(BattleSimulation sim, int tick, List<Seat> seats) {
        for (int i = 0; i < people.size(); ) {
            long id = people.get(i).id;
            if (sim.resolveUnit(id) == 0L || !sim.world().isAlive(id)) {
                people.remove(i);
                lost++;
            } else {
                i++;
            }
        }
        if (people.isEmpty() && counted == 0) return;
        for (Person p : people) if (p.seat != null && !seats.contains(p.seat)) p.seat = null;
        for (Seat seat : seats) send(sim, seat);
        for (int i = 0; i < people.size(); ) {
            Person p = people.get(i);
            if (p.seat != null) {
                float[] at = p.seat.at(sim);
                float x = sim.world().x(p.id), y = sim.world().y(p.id);
                if (aboard(x, y, at[0], at[1]) && p.seat.opens(tick)) {
                    p.seat.board(tick);
                    boarded++;
                    sim.clearPath(p.id);
                    sim.takeOffTheField(p.id);
                    people.remove(i);
                    continue;
                }
                int[] dest = CordonGround.nearestWalkable(sim.getGrid(), (int) at[0], (int) at[1]);
                walk(sim, tick, p, dest == null ? (int) at[0] : dest[0], dest == null ? (int) at[1] : dest[1]);
            } else if (sim.world().cellX(p.id) != p.homeX || sim.world().cellY(p.id) != p.homeY) {
                // Trapped: one sent back from a truck that left or went, or jostled off its place, walks home.
                walk(sim, tick, p, p.homeX, p.homeY);
            }
            i++;
        }
    }

    /** Sends people out to a truck with seats nobody is yet walking to, the nearest first; a count boards at once. */
    private void send(BattleSimulation sim, Seat seat) {
        int walking = 0;
        for (Person p : people) if (p.seat == seat) walking++;
        int wanted = wanted(seat.free(), walking);
        if (wanted <= 0) return;
        float[] at = seat.at(sim);
        List<float[]> standing = new ArrayList<>();
        List<Person> idle = new ArrayList<>();
        for (Person p : people) {
            if (p.seat != null) continue;
            idle.add(p);
            standing.add(new float[]{sim.world().x(p.id), sim.world().y(p.id)});
        }
        for (int index : nearest(standing, at[0], at[1], wanted)) idle.get(index).seat = seat;
        // Nobody stood up left to send: the compound's counted people board straight from it.
        if (idle.isEmpty() && walking == 0 && counted > 0 && seat.opens(sim.getSimTickIndex())) {
            counted--;
            boarded++;
            seat.board(sim.getSimTickIndex());
        }
    }

    /** Ticks between paths given one person to the same place: a way it cannot finish is not looked for every tick. */
    static final int REPATH_TICKS = 30;

    private static void walk(BattleSimulation sim, int tick, Person p, int destX, int destY) {
        long id = p.id;
        int x = sim.world().cellX(id), y = sim.world().cellY(id);
        int[] path = sim.movement().path(id);
        boolean elsewhere = destX != p.destX || destY != p.destY;
        boolean spent = Paths.isEmpty(path) || sim.movement().pathIdx(id) >= Paths.cellCount(path);
        boolean due = elsewhere || spent && tick - p.pathTick >= REPATH_TICKS;
        if (due && (x != destX || y != destY) && sim.movement().mayRepath(id) && sim.getGrid().inBounds(x, y)) {
            sim.setPath(id, sim.findPath(x, y, destX, destY));
            p.destX = destX;
            p.destY = destY;
            p.pathTick = tick;
        }
        sim.advanceMovement(id);
    }

    /** How many more to send to a truck: its free seats less those already walking to it. */
    static int wanted(int free, int walking) {
        return Math.max(0, free - walking);
    }

    /** Whether a townsperson at {@code (x, y)} is close enough to the truck at {@code (tx, ty)} to climb aboard. */
    static boolean aboard(float x, float y, float tx, float ty) {
        return Math.abs(x - tx) <= BOARD_REACH && Math.abs(y - ty) <= BOARD_REACH;
    }

    /** The indices of up to {@code count} of {@code at}, nearest {@code (x, y)} first; ties keep their order. */
    static List<Integer> nearest(List<float[]> at, float x, float y, int count) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < at.size(); i++) order.add(i);
        order.sort((a, b) -> Float.compare(distance(at.get(a), x, y), distance(at.get(b), x, y)));
        return order.subList(0, Math.min(Math.max(0, count), order.size()));
    }

    private static float distance(float[] p, float x, float y) {
        float dx = p[0] - x, dy = p[1] - y;
        return dx * dx + dy * dy;
    }
}
