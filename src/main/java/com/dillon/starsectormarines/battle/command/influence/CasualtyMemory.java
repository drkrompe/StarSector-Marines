package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.nav.RouteCostField;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.EnumMap;
import java.util.Map;

/**
 * Where a faction has recently lost people, at influence-block resolution, so a
 * commander can decline to send the next squad up the lane the last one died
 * in.
 *
 * <p><b>This is a side's memory of its own casualties, not intelligence about
 * the enemy.</b> That is what makes it legal to read: a faction knows who it
 * lost and roughly where, the same class of own-force fact the friendly
 * influence field already carries. It says nothing about who did the killing or
 * where they are now — a lane can stay expensive long after the ambush has
 * moved on, which is the honest shape of the knowledge rather than a defect.
 *
 * <p>Weight decays with a half-life rather than expiring, because ground does
 * not become safe on a deadline. A place bought with a squad stays discouraging
 * for a while, then stops mattering, and a second squad lost in the same place
 * pushes it back up.
 *
 * <p>Only combatant losses count. A civilian caught in the open says something
 * about the map but nothing about whether a route is survivable for a fire
 * team, and letting it weigh would make evacuation corridors read as killing
 * grounds.
 *
 * <p>The memory is also published as a {@link RouteCostField} per side, which
 * is how it reaches the movers rather than only the commander: a squad ordered
 * somewhere prefers to get there without walking over its own dead. That
 * publication is on a fixed cadence and driven from the tick loop, not from
 * whoever happens to read a snapshot, because a costing consulted by every
 * repath cannot be built lazily by its readers.
 */
public final class CasualtyMemory {

    /**
     * Ticks for a remembered loss to decay to half weight - thirty seconds at
     * the simulation's thirty ticks a second. Expressed in ticks rather than
     * seconds so this package stays tick-native like the refresh interval
     * beside it, and does not have to reach into the sim for a frame duration.
     */
    public static final float HALF_LIFE_TICKS = 900f;

    /**
     * How much dearer the worst-remembered ground can get. One means a cell
     * where a side has been slaughtered costs at most twice an untouched one,
     * which buys a detour of roughly its own length and no more. A route
     * around must stay a route: ground bought with a squad is discouraging,
     * never impassable, or an objective defended well enough would simply stop
     * being approached.
     */
    public static final float MAX_ROUTE_PENALTY = 1f;

    /**
     * Losses in one block at which half the maximum penalty is reached. Six is
     * a squad, so a wipe roughly halves the remaining headroom rather than
     * saturating - which matters because a hot block on a long front
     * accumulates dozens of dead, and a linear scale with a cap would flatten
     * the whole front to one indistinguishable wall of maximum cost.
     */
    public static final float HALF_PENALTY_LOSSES = 6f;

    /**
     * Ticks between republications. Matches the influence field's cadence
     * beside it - a costing every mover reads is rebuilt on a clock, not per
     * death, and a fresher one would buy nothing a half-life of nine hundred
     * ticks can express.
     */
    public static final int PUBLISH_INTERVAL_TICKS = 15;

    /** Remembered weight below which a block is left at baseline cost. */
    private static final float NEGLIGIBLE_WEIGHT = 0.05f;

    /**
     * {@code -Dbattle.pathfinding.casualtyRouteCost=false} publishes no costing
     * at all, leaving every route exactly where it was before this layer
     * existed. It is here because the only honest control for a routing change
     * is the same battle with the routing change absent, and reaching that by
     * checking out an older commit measures every other difference between the
     * two trees at the same time. The memory itself keeps running: what is
     * switched off is whether the movers hear about it.
     */
    public static final String ROUTE_COST_PROPERTY =
            "battle.pathfinding.casualtyRouteCost";

    private static final boolean ROUTE_COST_ENABLED = Boolean.parseBoolean(
            System.getProperty(ROUTE_COST_PROPERTY, "true"));

    private final UnitRosterService roster;
    private final int blockSize;
    private final int cellWidth;
    private final int cellHeight;
    private final int blockWidth;
    private final int blockHeight;
    private final Map<Faction, float[]> byFaction = new EnumMap<>(Faction.class);
    /**
     * Replaced wholesale rather than mutated, and volatile, because it is
     * written on the serial tick and read from the parallel unit dispatch. A
     * costing is a frozen publication like the influence snapshots beside it,
     * not a structure anybody edits in place.
     */
    private volatile Map<Faction, RouteCostField> routeCosts = Map.of();
    private int lastPublishTick = Integer.MIN_VALUE;

    public CasualtyMemory(UnitRosterService roster, int blockSize,
                          int cellWidth, int cellHeight) {
        this.roster = roster;
        this.blockSize = blockSize;
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.blockWidth = (cellWidth + blockSize - 1) / blockSize;
        this.blockHeight = (cellHeight + blockSize - 1) / blockSize;
    }

    public int blockWidth() { return blockWidth; }

    public int blockHeight() { return blockHeight; }

    /**
     * Records one loss against the faction that suffered it. Subscribed to the
     * death dispatcher, so it sees every death exactly once and never has to
     * scan the roster for corpses.
     */
    public void onDeath(DeathEvent event) {
        long unit = event.unitId();
        if (!roster.identity().has(unit)) return;
        if (!roster.identity().type(unit).combatant) return;
        Faction faction = roster.identity().faction(unit);
        if (faction == null) return;
        // floorDiv, not /: integer division truncates toward zero, so a death
        // one cell off the west edge divides to block 0 and is recorded as
        // having happened in the corner of the map.
        int bx = Math.floorDiv(event.cellX(), blockSize);
        int by = Math.floorDiv(event.cellY(), blockSize);
        if (bx < 0 || by < 0 || bx >= blockWidth || by >= blockHeight) return;
        field(faction)[by * blockWidth + bx] += 1f;
    }

    /**
     * Serial tick-loop driver: decays on the publication cadence and
     * republishes each side's route costing.
     *
     * <p>Driven from the tick rather than from a reader because the influence
     * snapshot beside it is built lazily - nobody looks, nothing is built - and
     * a costing the pathfinder consults has no such reader to trigger it. A
     * battle with no diagnostic overlay would otherwise carry a loss memory
     * that never decayed and a costing that never existed.
     */
    public void advance(int simTick) {
        if (lastPublishTick != Integer.MIN_VALUE
                && simTick - lastPublishTick < PUBLISH_INTERVAL_TICKS) {
            return;
        }
        decay(lastPublishTick == Integer.MIN_VALUE ? 0
                : simTick - lastPublishTick);
        lastPublishTick = simTick;
        Map<Faction, RouteCostField> published = new EnumMap<>(Faction.class);
        for (Faction faction : byFaction.keySet()) {
            RouteCostField cost = buildRouteCost(faction);
            if (cost != null) published.put(faction, cost);
        }
        routeCosts = published;
    }

    /**
     * What this side's own recent losses make routes cost, or {@code null}
     * while it has lost nobody worth routing around. Null rather than an
     * all-baseline field so a caller passes nothing at all to the pathfinder in
     * the ordinary case, and the shared reverse trees keep their untouched
     * identity.
     */
    public RouteCostField routeCost(Faction faction) {
        return routeCosts.get(faction);
    }

    /**
     * Expands the block memory into the per-cell multiplier the pathfinders
     * read. The step at a block boundary is not smoothed: the block is the
     * resolution at which this side actually knows where it lost people, and
     * interpolating would draw a confidence the knowledge does not have.
     */
    private RouteCostField buildRouteCost(Faction faction) {
        float[] blocks = byFaction.get(faction);
        if (blocks == null || !ROUTE_COST_ENABLED) return null;
        boolean any = false;
        for (float weight : blocks) {
            if (weight > NEGLIGIBLE_WEIGHT) { any = true; break; }
        }
        if (!any) return null;
        float[] cells = new float[cellWidth * cellHeight];
        for (int y = 0; y < cellHeight; y++) {
            int blockRow = (y / blockSize) * blockWidth;
            int row = y * cellWidth;
            for (int x = 0; x < cellWidth; x++) {
                cells[row + x] = multiplierFor(blocks[blockRow + x / blockSize]);
            }
        }
        return new RouteCostField(cells, RouteCostField.nextRevision());
    }

    /** Saturating penalty on remembered weight; see {@link #HALF_PENALTY_LOSSES}. */
    static float multiplierFor(float weight) {
        if (weight <= NEGLIGIBLE_WEIGHT) return 1f;
        return 1f + MAX_ROUTE_PENALTY * weight / (weight + HALF_PENALTY_LOSSES);
    }

    /**
     * Decays every faction's memory toward nothing. Sized from the interval
     * actually elapsed, so the half-life is a property of simulated time rather
     * than of how often this happens to be called.
     */
    public void decay(int elapsedTicks) {
        if (elapsedTicks <= 0) return;
        float factor = (float) Math.pow(0.5, elapsedTicks / HALF_LIFE_TICKS);
        for (float[] field : byFaction.values()) {
            for (int i = 0; i < field.length; i++) {
                float value = field[i] * factor;
                field[i] = value < 0.01f ? 0f : value;
            }
        }
    }

    /** An immutable copy for the frozen snapshot a commander plans against. */
    public float[] copyFor(Faction faction) {
        float[] field = byFaction.get(faction);
        return field == null ? new float[blockWidth * blockHeight] : field.clone();
    }

    private float[] field(Faction faction) {
        return byFaction.computeIfAbsent(faction,
                f -> new float[blockWidth * blockHeight]);
    }
}
