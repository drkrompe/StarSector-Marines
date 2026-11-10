package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.derelict.DerelictPower;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Consumer;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import com.dillon.starsectormarines.battle.world.model.NightLighting.Lamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A ruins site's light ({@code ruins-expedition.md}, Light): the plant is black under its roof when the company lands,
 * and its lights come back with its power. Daylight falls through the light wells and the broken roof; under the roof
 * only the residual emergency strips burn, at the section doors, the junction boxes and the hall's console, until the
 * company restarts the power hall. Then each junction lights what it feeds: its band's halls and corridors on its way
 * on, its stores on its store side, and the vault's suite with the vault's seal.
 *
 * <p>Light is presentation, never a consumer: it draws none of the hall's capacity and changes no sight
 * ({@link NightLighting}). An overloaded hall browns the plant out, all but the hall itself, so the load the player
 * must shed is seen. Each lamp's pool stops at its own building's walls, so rooms light as rooms.
 */
public final class RuinsLighting {

    /** Darkness under the plant's intact roof: nearly black, the floor still readable. */
    public static final float ROOFED_DARKNESS = 0.86f;
    /** Strip lamps stand this many cells apart in a building, and light this far: a lit hall reads evenly. */
    static final int SPACING = 5;
    static final float STRIP_REACH = 5f;
    /** An emergency strip lights only the cells round its door or box. */
    static final float EMERGENCY_REACH = 2.6f;
    /** Residual charge: an emergency strip lights its floor dimly, and red. */
    static final float EMERGENCY_STRENGTH = 0.45f, EMERGENCY_TINT = 4f;

    public static final String EMERGENCY = "emergency";
    public static final String HALL = "hall";
    public static final String VAULT = "vault";
    static final String BAND = "band-";
    static final String STORE = "store-";

    private static final float[] WORK_LIGHT = {0.86f, 0.92f, 1f};
    private static final float[] HALL_LIGHT = {1f, 0.86f, 0.62f};
    private static final float[] EMERGENCY_LIGHT = {1f, 0.24f, 0.16f};

    private RuinsLighting() {}

    /** The circuit lighting a layer's way on: its band of the plant. */
    public static String band(int junction) { return BAND + junction; }

    /** The circuit lighting a layer's stores. */
    public static String store(int junction) { return STORE + junction; }

    /**
     * The circuits lit now. The emergency strips always; the hall's own lights while it runs; each junction's band or
     * stores while the hall runs unoverloaded and the junction feeds that side; the vault while its seal is powered.
     */
    public static Set<String> circuits(PowerGraph graph) {
        Set<String> out = new TreeSet<>();
        out.add(EMERGENCY);
        if (!graph.running()) return out;
        out.add(HALL);
        if (graph.overloaded()) return out;
        for (int j = 0; j < graph.junctionCount(); j++) {
            out.add(graph.position(j) == Branch.ONWARD ? band(j) : store(j));
        }
        for (Consumer consumer : graph.consumers()) {
            if (consumer.id().equals(RuinsWorks.VAULT_SEAL) && graph.powered(consumer)) out.add(VAULT);
        }
        return out;
    }

    /**
     * The site's lighting as laid at setup, lit for {@code works}' power now, or null for a site with no roofed plant.
     * Open ground keeps the world's day; only the plant under its roof is dark.
     */
    public static NightLighting lay(CellTopology ground, RuinsDelve delve, RuinsWorks works) {
        if (delve.interior() == null) return null;
        Map<String, Integer> storeJunction = new HashMap<>();
        for (Consumer consumer : works.graph().consumers()) storeJunction.put(consumer.id(), consumer.junction());
        List<int[]> stores = new ArrayList<>();
        List<String> storeCircuits = new ArrayList<>();
        for (SealDoor door : works.doors()) {
            if (!door.store()) continue;
            stores.add(door.keep());
            storeCircuits.add(store(storeJunction.getOrDefault(door.id(), 0)));
        }
        RuinsDelve.Area hall = delve.powerHall() == null ? null : delve.powerHall().footprint();
        RuinsDelve.Area vault = delve.vault() == null ? null : delve.vault().footprint();
        Circuits circuitAt = (x, y) -> {
            if (vault != null && vault.contains(x, y)) return VAULT;
            for (int i = 0; i < stores.size(); i++) {
                int[] k = stores.get(i);
                if (x >= k[0] && x <= k[2] && y >= k[1] && y <= k[3]) return storeCircuits.get(i);
            }
            if (hall != null && hall.contains(x, y)) return HALL;
            return band(Math.max(0, DerelictPower.junctionOf(x, y, delve.layers())));
        };
        List<Lamp> lamps = new ArrayList<>(strips(ground, delve.interior().footprint(), circuitAt));
        RuinsDelve.Area box = delve.interior().footprint();
        for (RuinsDelve.LeafDoor door : delve.interior().doors()) {
            if (door.stance() == RuinsDelve.DoorStance.FORCED || door.stance() == RuinsDelve.DoorStance.TORN) continue;
            emergency(ground, lamps, door.span().centreX(), door.span().centreY());
        }
        for (SealDoor door : works.doors()) {
            int[] m = door.middle();
            if (box.contains(m[0], m[1])) emergency(ground, lamps, m[0], m[1]);
        }
        for (RuinsDelve.Junction junction : delve.junctions()) {
            if (box.contains(junction.stand().x(), junction.stand().y())) {
                emergency(ground, lamps, junction.stand().x(), junction.stand().y());
            }
        }
        if (delve.powerHall() != null) {
            emergency(ground, lamps, delve.powerHall().console().x(), delve.powerHall().console().y());
        }
        return new NightLighting(0f, ROOFED_DARKNESS, lamps, circuits(works.graph()));
    }

    /** What circuit lights a cell of the plant. */
    @FunctionalInterface
    interface Circuits {
        String at(int x, int y);
    }

    /**
     * Strip lamps over the plant's roofed floor inside {@code box}, building by building: a cell takes a lamp when no
     * lamp of its own building stands within {@link #SPACING} of it, so a hall takes a lattice and a corridor a row,
     * and every room at least one.
     */
    static List<Lamp> strips(CellTopology ground, RuinsDelve.Area box, Circuits circuitAt) {
        List<Lamp> out = new ArrayList<>();
        Map<Integer, List<int[]>> placed = new HashMap<>();
        for (int y = Math.max(0, box.bottom()); y <= Math.min(ground.getHeight() - 1, box.top()); y++) {
            for (int x = Math.max(0, box.left()); x <= Math.min(ground.getWidth() - 1, box.right()); x++) {
                if (!floor(ground, x, y)) continue;
                int building = ground.getBuildingId(x, y);
                List<int[]> own = placed.computeIfAbsent(building, b -> new ArrayList<>());
                if (near(own, x, y)) continue;
                own.add(new int[] {x, y});
                String circuit = circuitAt.at(x, y);
                float[] c = circuit.equals(HALL) ? HALL_LIGHT : WORK_LIGHT;
                out.add(new Lamp(x + 0.5f, y + 0.5f, STRIP_REACH, c[0], c[1], c[2], circuit));
            }
        }
        return out;
    }

    /** Whether a cell is the plant's floor under its roof, where a ceiling strip hangs. */
    static boolean floor(CellTopology ground, int x, int y) {
        return ground.isRoofIntact(x, y) && !ground.isWall(x, y)
                && ground.getGroundKind(x, y) != CellTopology.GroundKind.VOID;
    }

    private static boolean near(List<int[]> lamps, int x, int y) {
        for (int[] lamp : lamps) {
            if (Math.max(Math.abs(lamp[0] - x), Math.abs(lamp[1] - y)) < SPACING) return true;
        }
        return false;
    }

    /**
     * An emergency strip on the floor nearest {@code (x, y)}, within two cells: a door's span or a box on a wall line is
     * often wall, and a lamp lights only the building it stands in. None where no floor is that near.
     */
    private static void emergency(CellTopology ground, List<Lamp> lamps, int x, int y) {
        int[] at = nearestFloor(ground, x, y, 2);
        if (at == null) return;
        lamps.add(new Lamp(at[0] + 0.5f, at[1] + 0.5f, EMERGENCY_REACH, EMERGENCY_LIGHT[0], EMERGENCY_LIGHT[1],
                EMERGENCY_LIGHT[2], EMERGENCY, NightLighting.Fixture.STRIP, 0f, EMERGENCY_STRENGTH, EMERGENCY_TINT));
    }

    /** The roofed floor cell nearest {@code (x, y)} within {@code reach} cells, or null. */
    static int[] nearestFloor(CellTopology ground, int x, int y, int reach) {
        for (int r = 0; r <= reach; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (ground.inBounds(x + dx, y + dy) && floor(ground, x + dx, y + dy)) return new int[] {x + dx, y + dy};
                }
            }
        }
        return null;
    }

    /** Brings {@code ground}'s lighting to {@code works}' power now; no change, no new lighting, so no rebake. */
    public static void apply(CellTopology ground, PowerGraph graph) {
        NightLighting night = ground.nightLighting();
        if (night == null) return;
        NightLighting lit = night.withPowered(circuits(graph));
        if (lit != night) ground.setNightLighting(lit);
    }
}
