package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The mission-neutral producer (`allied-tools.md`, Code seam): a base's stock of each tool, drawn on by need through
 * {@link #request}. Each tool kind has a {@link Maker} the mission supplies (or one of {@link AlliedMakers}) that
 * brings one onto the field from the depot's cell and sends it on; the shed keeps the stock, the restock pace, the
 * air gate, and what is on its way where. A request while the depot cannot send, with none in stock, or behind a
 * closed gate is refused rather than sent from somewhere else.
 */
public final class AlliedToolShed implements AlliedProducer {

    /** Brings one tool on. */
    public interface Maker {
        /** Sets the tool down at {@code from} and sends it toward the request; {@code null} when it could not. */
        Sent make(BattleSimulation sim, int[] from, AlliedRequest request);
    }

    /** One tool on its way: done once it has arrived, been spent or been lost. */
    public interface Sent {
        boolean done(BattleSimulation sim);
    }

    /** A request granted, and what the shed knows of it. */
    public record Dispatch(AlliedRequest request, int tick, Sent sent) { }

    private static final class Line {
        final Maker maker;
        int stock;
        final int cap;
        final int restockTicks;
        int nextRestock;

        Line(Maker maker, int stock, int cap, int restockTicks) {
            this.maker = maker;
            this.stock = stock;
            this.cap = cap;
            this.restockTicks = restockTicks;
        }
    }

    private final AlliedDepot depot;
    private final Map<AlliedTool, Line> lines = new EnumMap<>(AlliedTool.class);
    private final Map<AlliedTool, Predicate<BattleSimulation>> gates = new EnumMap<>(AlliedTool.class);
    private final Map<AlliedTool, Predicate<BattleSimulation>> supplies = new EnumMap<>(AlliedTool.class);
    private final List<Dispatch> onRoad = new ArrayList<>();
    private final List<Dispatch> log = new ArrayList<>();
    /** Tools that bring fighting bodies on, the pool they count against and how many each brings ({@link AlliedCap}). */
    private final Map<AlliedTool, AlliedCap.Pool> capPools = new EnumMap<>(AlliedTool.class);
    private final Map<AlliedTool, Integer> capSizes = new EnumMap<>(AlliedTool.class);

    public AlliedToolShed(AlliedDepot depot) {
        this.depot = depot;
    }

    /**
     * Stocks a tool: {@code stock} ready now, one more every {@code restockTicks} up to {@code cap} ({@code 0} ticks:
     * never restocked).
     */
    public AlliedToolShed stock(AlliedTool tool, Maker maker, int stock, int cap, int restockTicks) {
        lines.put(tool, new Line(maker, stock, cap, restockTicks));
        return this;
    }

    /** A tool sent only while {@code gate} is open (a sortie behind the mission's air gate). */
    public AlliedToolShed gated(AlliedTool tool, Predicate<BattleSimulation> gate) {
        gates.put(tool, gate);
        return this;
    }

    /**
     * A tool whose fresh stock reaches the base only while {@code supplied} holds (a supply road the enemy can cut):
     * while it does not, the tool's restock clock stands still, and what is in stock can still be sent.
     */
    public AlliedToolShed suppliedWhile(AlliedTool tool, Predicate<BattleSimulation> supplied) {
        supplies.put(tool, supplied);
        return this;
    }

    /**
     * A tool that brings {@code bodies} fighting bodies of {@code pool} on: a request for it obeys the battle's allied
     * cap, waiting its turn in the cap's queue while the pool has no room ({@link AlliedCap#mayGo}), keyed by the tool and
     * the place it is for. The maker enlists what it brings.
     */
    public AlliedToolShed capped(AlliedTool tool, AlliedCap.Pool pool, int bodies) {
        capPools.put(tool, pool);
        capSizes.put(tool, bodies);
        return this;
    }

    @Override
    public int[] dispatchCell(BattleSimulation sim) {
        return depot.dispatchCell(sim);
    }

    /** Whether a request for {@code tool} would be granted now, short of the maker finding ground. */
    public boolean ready(BattleSimulation sim, AlliedTool tool) {
        return refusal(sim, tool) == null;
    }

    /** Why a request for {@code tool} would be refused now, or {@code null}: {@code none}, {@code empty}, {@code gate}, {@code depot}. */
    public String refusal(BattleSimulation sim, AlliedTool tool) {
        Line line = lines.get(tool);
        if (line == null) return "none";
        if (line.stock <= 0) return "empty";
        Predicate<BattleSimulation> gate = gates.get(tool);
        if (gate != null && !gate.test(sim)) return "gate";
        if (depot.dispatchCell(sim) == null) return "depot";
        return null;
    }

    @Override
    public boolean request(BattleSimulation sim, AlliedRequest request) {
        return request(sim, request, sim.getSimTickIndex());
    }

    boolean request(BattleSimulation sim, AlliedRequest request, int tick) {
        if (refusal(sim, request.tool()) != null) return false;
        AlliedCap.Pool pool = capPools.get(request.tool());
        if (pool != null && !sim.alliedCap().mayGo(sim, request.tool() + "@" + request.toPlace(), pool,
                capSizes.get(request.tool()))) return false;
        Line line = lines.get(request.tool());
        Sent sent = line.maker.make(sim, depot.dispatchCell(sim), request);
        if (sent == null) return false;
        if (line.stock == line.cap) line.nextRestock = tick + line.restockTicks;
        line.stock--;
        Dispatch dispatch = new Dispatch(request, tick, sent);
        onRoad.add(dispatch);
        log.add(dispatch);
        return true;
    }

    /** One tick: restocks on the pace, and forgets what has arrived or been lost. */
    public void advance(BattleSimulation sim) {
        advance(sim, sim.getSimTickIndex());
    }

    void advance(BattleSimulation sim, int tick) {
        for (Map.Entry<AlliedTool, Line> e : lines.entrySet()) {
            Line line = e.getValue();
            if (line.restockTicks <= 0 || line.stock >= line.cap) continue;
            if (line.nextRestock == 0) line.nextRestock = tick + line.restockTicks;
            Predicate<BattleSimulation> supplied = supplies.get(e.getKey());
            if (supplied != null && !supplied.test(sim)) {
                // Cut off: the clock stands still, so the restock lands as late as the cut lasted.
                line.nextRestock = Math.max(line.nextRestock, tick) + 1;
                continue;
            }
            if (tick >= line.nextRestock) {
                line.stock++;
                line.nextRestock = tick + line.restockTicks;
            }
        }
        onRoad.removeIf(d -> d.sent().done(sim));
    }

    @Override
    public int onRoad(AlliedTool tool, String toPlace) {
        int n = 0;
        for (Dispatch d : onRoad) if (d.request().tool() == tool && d.request().toPlace().equals(toPlace)) n++;
        return n;
    }

    /** Tools of this kind in stock now. */
    public int stock(AlliedTool tool) {
        Line line = lines.get(tool);
        return line == null ? 0 : line.stock;
    }

    /** Every request granted so far, in order, for evidence and the radio. */
    public List<Dispatch> granted() {
        return List.copyOf(log);
    }

    /** Requests granted and still on their way. */
    public List<Dispatch> pending() {
        return List.copyOf(onRoad);
    }
}
