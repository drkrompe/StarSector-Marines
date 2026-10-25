package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;
import com.dillon.starsectormarines.battle.command.allied.AlliedMakers;
import com.dillon.starsectormarines.battle.command.allied.AlliedRequest;
import com.dillon.starsectormarines.battle.command.allied.AlliedTool;
import com.dillon.starsectormarines.battle.command.allied.AlliedToolShed;
import com.dillon.starsectormarines.battle.command.allied.GunStock;
import com.dillon.starsectormarines.battle.command.allied.SupplyDelivery;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The allied line command's draw on its base ({@code cordon-allied-command.md}, The producer): the Cordon's
 * {@link AlliedToolShed} over {@link CordonLogistics} as the depot, asked for tools by need rather than every machine
 * coming on a timer. An uncovered sector asks for an armour pair (a Hound pair in a city); a redoubt whose guns and
 * tubes run low asks for an ammunition truck, which refills its {@link GunStock} on arrival. Each "when to ask" is a
 * pure rule in {@link CordonAlliedRules}; this freezes the line, asks, and keeps the readings evidence reports.
 *
 * <p>It also says the radio's two allied items the company hears ({@link #uncovered}, {@link #helpComing}) and hands
 * the armour the road hulls that want an escort (its own trucks, a beat's dozer).
 */
public final class CordonLineCommand {

    /** Who drives the ammunition trucks, as the command log names it. */
    public static final String SUPPLY_ISSUER = "cordon-supply";
    /** Machines in one request's armour pair or Hound lance. */
    static final int PAIR = 2;
    /** Ticks before a refused request for one place is asked again. */
    static final int RETRY_TICKS = Math.round(10f / BattleSimulation.TICK_DT);

    private final CordonSequence sequence;
    private final CordonArmour armour;
    private final CordonLogistics logistics;
    private final CordonPlan.Density density;
    private final CordonAlliedTable.Reserve reserve;
    private final AlliedCommand supply = new AlliedCommand(SUPPLY_ISSUER);
    private final AlliedToolShed shed;
    private final Map<CordonRedoubt, GunStock> stocks = new LinkedHashMap<>();
    private final Map<CordonRedoubt, int[]> truckCell = new HashMap<>();
    /**
     * Where a truck for each redoubt sets out: the base's end of its supply road where one was laid, else the base end
     * of its supply way through the city's streets ({@link CordonSupplyStreets}). A redoubt with neither has no truck.
     */
    private final Map<CordonRedoubt, int[]> truckStart = new HashMap<>();
    /** Each redoubt's supply way through the streets, where no supply road was laid and the streets give one. */
    private final Map<CordonRedoubt, List<int[]>> streetWay = new HashMap<>();
    private final Map<SupplyDelivery, CordonRedoubt> deliveries = new LinkedHashMap<>();
    /** The tick each place may be asked for again after a refusal, by tool and place. */
    private final Map<String, Integer> retryAt = new HashMap<>();
    private final Map<AlliedTool, int[]> asked = new EnumMap<>(AlliedTool.class);
    private final Map<String, Integer> refusals = new TreeMap<>();
    private long dryGunTicks;
    private final Map<Integer, Integer> dryPeak = new TreeMap<>();
    /** The picket nests' guns, each with the redoubt whose stock it draws on. */
    private final Map<Long, CordonRedoubt> picketGuns = new LinkedHashMap<>();
    /** Picket guns that ran dry, and those a truck filled again after they did. */
    private final Set<Long> picketsDry = new HashSet<>(), picketsRearmed = new HashSet<>();
    /** Picket guns destroyed: the nest overrun. */
    private final Set<Long> picketsLost = new HashSet<>();

    /**
     * @param pickets the picket nests' guns; each joins the stock of the redoubt it stands nearest
     * @param laid    the battle's layout, whose supply roads a truck drives to each redoubt's gate
     */
    public CordonLineCommand(BattleSimulation sim, CordonSequence sequence, CordonArmour armour,
                             CordonLogistics logistics, CordonPlan.Density density, CordonAlliedTable table,
                             List<Long> pickets, LaidLayout laid) {
        this.sequence = sequence;
        this.armour = armour;
        this.logistics = logistics;
        this.density = density;
        this.reserve = table.reserve();
        armour.setCoverFloor(CordonAlliedRules.coverFloor(density));
        armour.setRoadbound(CordonAlliedRules.roadbound(density, sequence.variant()));
        armour.setWalkingEscorts(CordonAlliedRules.walkingEscorts(density));
        List<CordonRedoubt> redoubts = sequence.redoubts();
        for (CordonRedoubt r : redoubts) {
            GunStock stock = new GunStock(place(r), reserve.roundsPerGun());
            for (long gun : r.guns()) stock.add(gun);
            stocks.put(r, stock);
            truckCell.put(r, truckCell(sim, r, laid));
            int[] start = roadStart(r, laid);
            if (start != null && !drivable(sim, start, truckCell.get(r))) {
                // A road laid in part, cut off by other ground short of the redoubt: its end is no place a truck
                // can reach, and one sent there stood where its way ran out. The streets to the rear gate instead.
                start = null;
                truckCell.put(r, armour.rearGate(sim, r));
            }
            if (start == null && logistics != null) start = streetStart(sim, r, logistics);
            if (start != null) truckStart.put(r, start);
        }
        for (long gun : pickets) {
            CordonRedoubt near = nearest(redoubts, sim.world().x(gun), sim.world().y(gun));
            if (near != null) {
                stocks.get(near).add(gun);
                picketGuns.put(gun, near);
            }
        }
        for (int squad : armour.mortarTeams()) {
            long leader = sim.getSquad(squad) == null ? 0L : sim.getSquad(squad).leaderId;
            if (leader == 0L || sim.resolveUnit(leader) == 0L) continue;
            CordonRedoubt near = nearest(redoubts, sim.world().x(leader), sim.world().y(leader));
            if (near != null) stocks.get(near).addMortarSquad(squad);
        }
        if (logistics == null) {
            shed = null;
        } else {
            shed = new AlliedToolShed(logistics);
            int restockCover = CordonSequence.ticks(reserve.coverRestockSeconds());
            if (CordonAlliedRules.coverTool(density) == AlliedTool.MECH_LANCE) {
                shed.stock(AlliedTool.MECH_LANCE, (s, from, request) -> {
                    CordonRedoubt to = redoubt(request.toPlace());
                    if (to == null) return null;
                    List<Long> mechs = armour.sendMechs(s, PAIR, from, to, "Redoubt " + to.number() + " relief Hound");
                    return mechs.isEmpty() ? null : arrived(mechs, to);
                }, reserve.cover(), reserve.cover(), restockCover);
            } else {
                shed.stock(AlliedTool.ARMOUR_PAIR, (s, from, request) -> {
                    CordonRedoubt to = redoubt(request.toPlace());
                    if (to == null) return null;
                    List<Long> hulls = armour.sendHulls(s, reserve.coverHull(), PAIR, from, to);
                    return hulls.isEmpty() ? null : arrived(hulls, to);
                }, reserve.cover(), reserve.cover(), restockCover);
            }
            AlliedToolShed.Maker truck = AlliedMakers.ammunition(supply, request -> {
                CordonRedoubt to = redoubt(request.toPlace());
                return to == null ? null : stocks.get(to).recipient(truckCell.get(to), to::manned);
            }, (at, hull) -> armour.escortOf(hull), delivery -> {
                CordonRedoubt to = redoubt(delivery.recipient().name());
                if (to != null) deliveries.put(delivery, to);
            });
            // Loaded at the dump, it comes out of the base's gate onto the redoubt's supply road, or in a city onto the
            // street way to it: from the shed's door a truck had to find its own way out of the yard.
            shed.stock(AlliedTool.AMMO_TRUCK, (s, from, request) -> {
                CordonRedoubt to = redoubt(request.toPlace());
                int[] start = to == null ? null : truckStart.get(to);
                return truck.make(s, start != null ? start : from, request);
            }, reserve.trucks(), reserve.trucks(), CordonSequence.ticks(reserve.truckRestockSeconds()));
        }
        armour.escortHulls(this::roadHulls);
    }

    /** The name a redoubt goes by in requests, stocks and deliveries. */
    static String place(CordonRedoubt r) {
        return "redoubt " + r.number();
    }

    /**
     * One tick: the guns spend their rounds, the trucks drive, the base restocks; at each look the line asks for what
     * it needs ({@link CordonAlliedRules#wantsCover}, {@link CordonAlliedRules#wantsAmmunition}).
     */
    public void advance(BattleSimulation sim) {
        for (Map.Entry<CordonRedoubt, GunStock> e : stocks.entrySet()) if (e.getKey().manned()) e.getValue().advance(sim);
        supply.advance(sim);
        if (shed != null) shed.advance(sim);
        int tick = sim.getSimTickIndex();
        if (tick % CordonArmour.REVIEW_TICKS != 0) return;
        for (Map.Entry<CordonRedoubt, GunStock> e : stocks.entrySet()) {
            if (!e.getKey().manned()) continue;
            int dry = e.getValue().dry();
            dryGunTicks += (long) dry * CordonArmour.REVIEW_TICKS;
            if (dry > 0) dryPeak.merge(e.getKey().number(), dry, Math::max);
        }
        for (Map.Entry<Long, CordonRedoubt> e : picketGuns.entrySet()) {
            long gun = e.getKey();
            if (picketsLost.contains(gun)) continue;
            if (sim.resolveUnit(gun) == 0L || !sim.world().isAlive(gun)) {
                picketsLost.add(gun);
                continue;
            }
            int left = stocks.get(e.getValue()).rounds(gun);
            if (left <= 0) picketsDry.add(gun);
            else if (picketsDry.contains(gun) && left >= reserve.roundsPerGun()) picketsRearmed.add(gun);
        }
        if (shed == null) return;
        AlliedTool cover = CordonAlliedRules.coverTool(density);
        for (CordonRedoubt r : sequence.redoubts()) {
            String place = place(r);
            int[] at = truckCell.get(r);
            GunStock stock = stocks.get(r);
            if (CordonAlliedRules.wantsAmmunition(r.manned(), supplied(r), stock.pooledShare(sim), reserve.lowShare(),
                    stock.dry(), shed.onRoad(AlliedTool.AMMO_TRUCK, place))) {
                ask(sim, tick, new AlliedRequest(AlliedTool.AMMO_TRUCK, place, at[0], at[1], "low"));
            }
            if (CordonAlliedRules.wantsCover(armour.uncovered(r), shed.onRoad(cover, place))) {
                ask(sim, tick, new AlliedRequest(cover, place, r.centreX(), r.centreY(), "uncovered"));
            }
        }
    }

    private void ask(BattleSimulation sim, int tick, AlliedRequest request) {
        String key = request.tool() + " " + request.toPlace();
        if (tick < retryAt.getOrDefault(key, 0)) return;
        int[] counts = asked.computeIfAbsent(request.tool(), t -> new int[2]);
        counts[0]++;
        String why = shed.refusal(sim, request.tool());
        if (why == null && shed.request(sim, request)) {
            counts[1]++;
            return;
        }
        refusals.merge(request.tool() + " " + (why == null ? "ground" : why), 1, Integer::sum);
        retryAt.put(key, tick + RETRY_TICKS);
    }

    /** The redoubt's ammunition: its gun towers, the picket nests nearest it, and its yard mortar team's tubes. */
    public GunStock stock(CordonRedoubt r) {
        return stocks.get(r);
    }

    /** The truck now on the road to a redoubt, or {@code 0}. */
    public long truckTo(CordonRedoubt r) {
        for (Map.Entry<SupplyDelivery, CordonRedoubt> e : deliveries.entrySet()) {
            if (e.getValue() == r && e.getKey().state() == SupplyDelivery.State.ON_ROAD) return e.getKey().truck();
        }
        return 0L;
    }

    /** Trucks to a redoubt that were lost on the road, or whose redoubt went first. */
    public int lostTo(CordonRedoubt r) {
        int n = 0;
        for (Map.Entry<SupplyDelivery, CordonRedoubt> e : deliveries.entrySet()) {
            if (e.getValue() == r && e.getKey().state() == SupplyDelivery.State.LOST) n++;
        }
        return n;
    }

    /** The radio's Uncovered: the sector had armour or mechs and has none left. */
    public boolean uncovered(CordonRedoubt r) {
        return armour.uncovered(r);
    }

    /** The radio's Help coming: a carrier with a squad bound for it, or an allied column marching to it. */
    public boolean helpComing(CordonRedoubt r) {
        boolean column = false;
        AlliedColumns columns = sequence.columns();
        if (columns != null) for (int[] dest : columns.marching()) column |= r.contains(dest[0], dest[1], 0);
        return CordonAlliedRules.helpComing(logistics != null && logistics.carryingTo(r), column);
    }

    /** The producer, or {@code null} on a line with no base. */
    public AlliedToolShed shed() {
        return shed;
    }

    /** The ammunition trucks' command. */
    public AlliedCommand supply() {
        return supply;
    }

    /** Every truck sent, and the redoubt it was sent to, in order. */
    public Map<SupplyDelivery, CordonRedoubt> deliveries() {
        return Map.copyOf(deliveries);
    }

    /** The road hulls that want an armed escort: trucks on the road, then the beats' hulls (a dozer). */
    private List<Long> roadHulls(BattleSimulation sim) {
        List<Long> out = new ArrayList<>();
        for (SupplyDelivery d : deliveries.keySet()) {
            if (d.truckCell(sim) != null && (d.pace() == null || !d.pace().alone())) out.add(d.truck());
        }
        if (sequence.beats() != null) out.addAll(sequence.beats().escortHulls(sim));
        return out;
    }

    /**
     * What the line command did, in a line for evidence: requests made, granted and refused by tool, trucks delivered
     * and lost, dry guns, escorts, and how long sectors stood uncovered.
     */
    public String readings() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<AlliedTool, int[]> e : asked.entrySet()) {
            out.append(e.getKey().name().toLowerCase(Locale.ROOT)).append(' ').append(e.getValue()[1]).append('/')
                    .append(e.getValue()[0]).append(" granted; ");
        }
        if (!refusals.isEmpty()) out.append("refused ").append(refusals).append("; ");
        int delivered = 0, lost = 0;
        for (SupplyDelivery d : deliveries.keySet()) {
            if (d.state() == SupplyDelivery.State.DELIVERED) delivered++;
            if (d.state() == SupplyDelivery.State.LOST) lost++;
        }
        out.append("trucks ").append(delivered).append(" delivered, ").append(lost).append(" lost");
        int onRoad = deliveries.size() - delivered - lost;
        if (onRoad > 0) out.append(", ").append(onRoad).append(" on the road");
        if (logistics != null) out.append("; company lifts ").append(logistics.liftReadings());
        out.append(" (ways");
        for (CordonRedoubt r : sequence.redoubts()) {
            out.append(" r").append(r.number()).append(' ')
                    .append(!supplied(r) ? "none" : streetWay.containsKey(r) ? "streets" : "road");
        }
        out.append("); ");
        out.append(String.format(Locale.ROOT, "dry gun-min %.1f", dryGunTicks * BattleSimulation.TICK_DT / 60f));
        if (!dryPeak.isEmpty()) out.append(" (most dry at once by redoubt ").append(dryPeak).append(')');
        if (!picketGuns.isEmpty()) {
            out.append("; picket nests ").append(picketGuns.size() - picketsLost.size()).append('/').append(picketGuns.size())
                    .append(" standing, ").append(picketsDry.size()).append(" ran dry, ")
                    .append(picketsRearmed.size()).append(" rearmed");
        }
        float[] escorts = armour.escortReadings();
        out.append(String.format(Locale.ROOT, "; road hulls escorted %.0f/%.0f, escort hull-min %.1f taken %.1f short"
                        + ", road-hull min %.1f alongside %.1f alone",
                escorts[1], escorts[0], escorts[2] / 60f, escorts[3] / 60f, escorts[4] / 60f, escorts[5] / 60f));
        Map<Integer, Float> uncovered = armour.uncoveredSeconds();
        if (!uncovered.isEmpty()) {
            out.append("; uncovered min");
            for (Map.Entry<Integer, Float> e : uncovered.entrySet()) {
                out.append(String.format(Locale.ROOT, " r%d %.1f", e.getKey(), e.getValue() / 60f));
            }
        }
        return out.toString();
    }

    /** Machines sent to a redoubt are on the road until each has reached its sector or is gone. */
    private AlliedToolShed.Sent arrived(List<Long> machines, CordonRedoubt to) {
        return s -> {
            for (long m : machines) if (!armour.arrivedOrGone(s, m, to)) return false;
            return true;
        };
    }

    private CordonRedoubt redoubt(String place) {
        for (CordonRedoubt r : sequence.redoubts()) if (place(r).equals(place)) return r;
        return null;
    }

    /**
     * Where a truck drives to reach a redoubt: the redoubt's end of its supply road where one was laid (the road runs
     * from the base's gate to the redoubt's rear gate), else outside its rear gate.
     */
    private int[] truckCell(BattleSimulation sim, CordonRedoubt r, LaidLayout laid) {
        Laid road = laid == null ? null : laid.find(CordonPlan.SUPPLY_ROAD + r.number());
        if (road != null && road.isLaid() && road.gaps().size() >= 2) {
            int[] a = road.gaps().get(0), b = road.gaps().get(1);
            return dist(a, r) <= dist(b, r) ? a : b;
        }
        return armour.rearGate(sim, r);
    }

    /**
     * The base end of a redoubt's supply way through the streets ({@link CordonSupplyStreets}): the way the router reads
     * from the base's dispatch cell to the redoubt's rear gate, streets first, and on it the first street cell outside
     * the base from which a truck can drive the rest ({@link CordonSupplyStreets#starts}). {@code null} where a truck
     * can drive it from none of them. Records the way from there.
     */
    private int[] streetStart(BattleSimulation sim, CordonRedoubt r, CordonLogistics logistics) {
        int[] door = logistics.dispatchCell(sim);
        if (door == null) return null;
        int[] gate = truckCell.get(r);
        float[][] route = truckRoute(sim, door, gate, false);
        if (route == null) return null;
        List<int[]> way = CordonSupplyStreets.cells(route[0], route[1]);
        CellTopology topology = sim.getTopology();
        for (int[] start : CordonSupplyStreets.starts(way,
                (x, y) -> topology.getGroundKind(x, y) == CellTopology.GroundKind.STREET, STREET_TRIES, STREET_PITCH)) {
            // Set down part-way, a truck has to make the way's next bend from a standing start, which the whole way
            // from the base did not ask of it: each start is proved as the truck will drive it.
            if (!drivable(sim, start, gate)) continue;
            int from = way.indexOf(start);
            streetWay.put(r, List.copyOf(way.subList(Math.max(0, from), way.size())));
            return start;
        }
        return null;
    }

    /** Street cells along a way tried as a truck's start, and the cells between them. */
    static final int STREET_TRIES = 6, STREET_PITCH = 6;

    /** Cells round a way's ends a truck's footprint is looked for. */
    static final int STREET_SNAP = 6;

    /**
     * The route between two cells for a truck's footprint, or {@code null} where it has none: {@code proved}, one whose
     * every bend a truck can make forward, as a move order is planned; else the router's way over the ground alone.
     */
    private float[][] truckRoute(BattleSimulation sim, int[] from, int[] to, boolean proved) {
        if (truckMask == null) {
            truckMask = VehicleClearance.erode(sim.getGrid(),
                    VehicleClearance.radiusForWidth(VehicleType.SUPPLY_TRUCK.visualWidthCells));
            truckCost = TerrainCostField.from(sim.getTopology());
        }
        int[] a = VehicleRoutePlanner.snapToMask(truckMask, from[0], from[1], STREET_SNAP);
        int[] b = VehicleRoutePlanner.snapToMask(truckMask, to[0], to[1], STREET_SNAP);
        if (a == null || b == null) return null;
        if (!proved) return VehicleRoutePlanner.route(a[0], a[1], b[0], b[1], sim.getGrid(), truckCost, truckMask);
        return VehicleRoutePlanner.routeDrivable(a[0], a[1], b[0], b[1], sim.getGrid(), truckCost, truckMask,
                VehicleType.SUPPLY_TRUCK);
    }

    /** Whether a truck can drive from one cell to the other. */
    private boolean drivable(BattleSimulation sim, int[] from, int[] to) {
        return truckRoute(sim, from, to, true) != null;
    }

    /** A truck's clearance and route prices, found at setup for the supply ways. */
    private VehicleClearance truckMask;
    private TerrainCostField truckCost;

    /** Whether a truck can be sent to a redoubt: it has a supply road, or a supply way through the streets. */
    public boolean supplied(CordonRedoubt r) {
        return truckStart.containsKey(r);
    }

    /** Where a truck for a redoubt sets out, or {@code null} where none can be sent. */
    public int[] truckStart(CordonRedoubt r) {
        int[] at = truckStart.get(r);
        return at == null ? null : at.clone();
    }

    /** A redoubt's supply way through the streets, or {@code null} where it has a laid road or none. */
    public List<int[]> streetWay(CordonRedoubt r) {
        List<int[]> way = streetWay.get(r);
        return way == null ? null : List.copyOf(way);
    }

    /** The base's end of a redoubt's supply road, or {@code null} where none was laid. */
    private static int[] roadStart(CordonRedoubt r, LaidLayout laid) {
        Laid road = laid == null ? null : laid.find(CordonPlan.SUPPLY_ROAD + r.number());
        if (road == null || !road.isLaid() || road.gaps().size() < 2) return null;
        int[] a = road.gaps().get(0), b = road.gaps().get(1);
        return dist(a, r) <= dist(b, r) ? b : a;
    }

    private static long dist(int[] c, CordonRedoubt r) {
        long dx = c[0] - r.centreX(), dy = c[1] - r.centreY();
        return dx * dx + dy * dy;
    }

    private static CordonRedoubt nearest(List<CordonRedoubt> redoubts, float x, float y) {
        CordonRedoubt best = null;
        double bestD = Double.MAX_VALUE;
        for (CordonRedoubt r : redoubts) {
            double d = Math.hypot(r.centreX() - x, r.centreY() - y);
            if (d < bestD) {
                bestD = d;
                best = r;
            }
        }
        return best;
    }
}
