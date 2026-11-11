package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.derelict.DerelictPower;
import com.dillon.starsectormarines.battle.derelict.FacilityWake;
import com.dillon.starsectormarines.battle.derelict.SecurityNetwork;
import com.dillon.starsectormarines.battle.derelict.SecurityZone;
import com.dillon.starsectormarines.battle.derelict.ServiceDucts;
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
 *
 * <p>At a derelict site the watch has lights of its own, on the standby it never lost ({@link DerelictPower}), and
 * they show its wake ({@code derelict-watch.md}, Waking): a zone's red alarm strips burn once it is alert or awake;
 * its vent hatches glow amber from {@link FacilityWake.Stage#STIRRING}, its reserve bay from
 * {@link FacilityWake.Stage#ROUSED}; the hunt sets every online zone alert and so lights them all, and the
 * contingency lights every zone with a live node. A zone powered down through its junction, or whose node is down,
 * goes dark.
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
    static final String WATCH = "watch-";
    static final String VENTS = "vents-";
    static final String BAY = "bay-";
    /** The watch's alarm strips stand this far apart: a sparse lattice over the plant's own lights. */
    static final int ALARM_SPACING = 10;

    private static final float[] ALARM_LIGHT = {1f, 0.12f, 0.08f};
    private static final float[] HATCH_LIGHT = {1f, 0.62f, 0.14f};

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

    /** The circuit of a security zone's alarm strips. */
    public static String watch(int zoneId) { return WATCH + zoneId; }

    /** The circuit of a security zone's vent hatches. */
    public static String vents(int zoneId) { return VENTS + zoneId; }

    /** The circuit of a security zone's reserve bay. */
    public static String bay(int zoneId) { return BAY + zoneId; }

    /**
     * The watch's circuits lit now: each online zone's alarm strips while it is alert or awake, or in the contingency;
     * its vent hatches once the ducts are up and its bay once the bays open.
     */
    public static Set<String> watchCircuits(SecurityNetwork network, FacilityWake.Stage stage) {
        Set<String> out = new TreeSet<>();
        for (SecurityZone zone : network.zones()) {
            if (!zone.online()) continue;
            if (zone.state() != SecurityZone.State.DORMANT || stage == FacilityWake.Stage.CONTINGENCY) {
                out.add(watch(zone.id));
            }
            if (stage.ventsOpen()) out.add(vents(zone.id));
            if (stage.baysOpen()) out.add(bay(zone.id));
        }
        return out;
    }

    /**
     * The watch's lamps over the plant inside {@code box}: an alarm strip at each zone's node and a sparse lattice of
     * them over the roofed floor it keeps, its reserve bay's lamp beside the node, and a hatch lamp at each vent.
     */
    static List<Lamp> watchLamps(CellTopology ground, SecurityNetwork network, ServiceDucts ducts, RuinsDelve.Area box) {
        List<Lamp> out = new ArrayList<>();
        for (SecurityZone zone : network.zones()) {
            add(out, ground, zone.nodeCellX, zone.nodeCellY, 3, 4f, ALARM_LIGHT, watch(zone.id), 0.8f, 4f);
            add(out, ground, zone.nodeCellX + 2, zone.nodeCellY, 3, 3f, HATCH_LIGHT, bay(zone.id), 0.7f, 3f);
        }
        List<int[]> placed = new ArrayList<>();
        for (int y = Math.max(0, box.bottom()); y <= Math.min(ground.getHeight() - 1, box.top()); y++) {
            for (int x = Math.max(0, box.left()); x <= Math.min(ground.getWidth() - 1, box.right()); x++) {
                if (!floor(ground, x, y) || nearAny(placed, x, y, ALARM_SPACING)) continue;
                SecurityZone zone = network.zoneAt(x, y);
                if (zone == null) continue;
                placed.add(new int[] {x, y});
                out.add(lamp(x, y, 4f, ALARM_LIGHT, watch(zone.id), 0.5f, 4f));
            }
        }
        if (ducts != null) {
            for (ServiceDucts.Vent vent : ducts.vents()) {
                out.add(lamp(vent.cellX(), vent.cellY(), 2.5f, HATCH_LIGHT, vents(vent.zoneId()), 0.6f, 3f));
            }
        }
        return out;
    }

    private static void add(List<Lamp> out, CellTopology ground, int x, int y, int reach, float radius, float[] c,
                            String circuit, float strength, float tint) {
        int[] at = nearestFloor(ground, x, y, reach);
        if (at != null) out.add(lamp(at[0], at[1], radius, c, circuit, strength, tint));
    }

    private static Lamp lamp(int x, int y, float radius, float[] c, String circuit, float strength, float tint) {
        return new Lamp(x + 0.5f, y + 0.5f, radius, c[0], c[1], c[2], circuit, NightLighting.Fixture.STRIP, 0f,
                strength, tint);
    }

    private static boolean nearAny(List<int[]> lamps, int x, int y, int spacing) {
        for (int[] lamp : lamps) {
            if (Math.max(Math.abs(lamp[0] - x), Math.abs(lamp[1] - y)) < spacing) return true;
        }
        return false;
    }

    /** Adds the watch's lamps to {@code ground}'s lighting, once its network stands; no lighting, no change. */
    public static void layWatch(CellTopology ground, RuinsDelve delve, SecurityNetwork network, ServiceDucts ducts) {
        NightLighting night = ground.nightLighting();
        if (night == null || delve.interior() == null) return;
        List<Lamp> lamps = new ArrayList<>(night.lamps());
        lamps.addAll(watchLamps(ground, network, ducts, delve.interior().footprint()));
        ground.setNightLighting(night.withLamps(lamps));
    }

    /**
     * Brings {@code ground}'s lighting to the works' power and the watch's {@code watch} circuits at battle second
     * {@code seconds}, from which the circuits newly on warm up; no change, no new lighting, so no rebake.
     */
    public static void apply(CellTopology ground, PowerGraph graph, Set<String> watch, float seconds) {
        NightLighting night = ground.nightLighting();
        if (night == null) return;
        Set<String> lit = new TreeSet<>(circuits(graph));
        lit.addAll(watch);
        NightLighting next = night.withPowered(lit, seconds);
        if (next != night) ground.setNightLighting(next);
    }

    /**
     * Brings {@code ground}'s lighting to the works' power at battle second {@code seconds}; no change, no new lighting,
     * so no rebake.
     */
    public static void apply(CellTopology ground, PowerGraph graph, float seconds) {
        apply(ground, graph, Set.of(), seconds);
    }
}
