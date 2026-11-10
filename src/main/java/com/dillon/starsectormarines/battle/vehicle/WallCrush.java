package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;

import java.util.ArrayList;
import java.util.List;

/**
 * What a wall-crushing hull ({@link VehicleType#crushesWalls()}) pushes through, how fast, and the view of the map its
 * route proof reads. A heavy or ultra-heavy hull crushes by its own weight on its nose: there is no blade on any hull.
 *
 * <p><b>The rule.</b> The hull pushes down what the ordinary destruction path brings down and what is built, not
 * grown: a blocking cell or a movement-blocking shared edge is <em>pushable</em> when its structure ({@link #structure})
 * is above zero and no more than the hull's ceiling ({@link #ceiling}: {@link #HEAVY_CEILING} and
 * {@link #ULTRA_HEAVY_CEILING} by weight). Built means a wall cell, a window cell in a wall, a window bay (the
 * {@link SharedEdgeBarrier.Kind#WINDOW} edge in a building's wall line, {@link #canPushEdge}), a sandbag, fence or crate
 * barricade, and registered equipment, furniture or a drum cluster (each with its damaged and destroyed art), so a hull
 * that opens a building's walls opens its window bays and the furniture behind them too. Ordinary city walls (100) and
 * gated-housing walls (80) fall to each; fortified and fortress walls (150 and up) do not, until fire has worn them
 * under the ceiling. Natural obstructions (rock, trees, summit terrain: {@link HeavyTracks} crushes undergrowth), a
 * fort's or an objective's works ({@link NavigationGrid.CellTag#WORKS}) and fixtures nothing can bring down (a
 * turret's mount, a prop without damage art) are never pushed. Low crash debris under the hull and its bearing strip
 * is graded flat: the cell keeps its terrain and loses the slowing tag.
 *
 * <p><b>The crush area.</b> A hull crushes where its whole footprint goes, whichever way it moves. Moving, it bears
 * on a strip just past the end it moves toward ({@link #push}): its nose driving forward, its tail reversing, the
 * hull's own width ({@link #bearingWidth}), so the gap it cuts is its own width. Every tick it moves or turns, it also
 * bears on every pushable cell and edge its footprint overlaps at the pose the move would reach ({@link #grind}): a
 * flank swung into a house wall as it turns, a corner as it pivots on the spot.
 *
 * <p><b>The rate.</b> The hull spends its rate ({@link #rate}: {@link #HEAVY_PER_SECOND} for a heavy hull,
 * {@link #ULTRA_HEAVY_PER_SECOND} for an ultra-heavy one) of structure per second on every pushable cell it bears on
 * at once, and on every pushable edge it spans, through
 * {@link MapEditor#damageWall} and {@link MapEditor#damageEdgeBarrier} — the same destruction paths a breaching charge
 * and a Brute's charge use. A pushed wall becomes rubble and a pushed barricade or prop its destroyed art, and the cell
 * or edge is ordinary open ground for every vehicle and unit from then on. Each piece that falls is counted on the
 * hull ({@link GroundBody#recordCrush}) for the crush sound. Opening is the only direction it edits: like every destruction op, it can only make the map more permissive, so
 * nothing derived from it is invalidated beyond the ordinary opened-cell flush.
 *
 * <p><b>Momentum.</b> A hull driving onto what it pushes spends its way on the cells its nose reaches that tick
 * ({@link HullMomentum}), by its mass ({@link VehicleType#massTonnes()}). Where the structure its speed carries and
 * this tick's push cover all those cells have left, they fall at once and the hull drives on at the speed the rest of
 * its energy gives; where they do not, they share its momentum and the hull is brought up against them, to push them
 * down at its rate from a standstill.
 *
 * <p><b>The hull.</b> A crushing hull is held by pushable ground only while it pushes it down. What its footprint
 * overlaps it {@link #grind grinds} at its rate, beside what its leading strip bears on, never twice in one tick;
 * that counts as progress only where nothing it cannot push holds it too. Its route proof counts that ground open, so
 * its turns may sweep there; without this a hull stood against a wall its own plan had driven it into.
 *
 * <p><b>Route proof.</b> {@link #breachView} is a routing copy of the grid with every pushable cell and edge open. A crushing
 * hull's move-order proof and its rolling local planner read that view, laid over the grid it moves over (its
 * tracks' crush view, or a heavy skirt's), so ground it can open counts as reachable for it and for nobody else; its
 * physical sweep still reads the grid it moves over, which is what holds the hull against a wall until it has pushed
 * it down. Pushable cells cost its route {@link #ROUTE_COST}, so it breaches only where going round is dearer.
 */
public final class WallCrush {

    /**
     * The heaviest structure an ultra-heavy hull crushes: ordinary city and gated-housing walls and every barricade,
     * while a fortified wall (150 and up) holds until fire has worn it under, so a fort is still
     * opened by fire and charges first. Its greater mass shows in the momentum it carries through, not in the ceiling.
     */
    public static final int ULTRA_HEAVY_CEILING = 100;
    /** Structure an ultra-heavy hull's weight spends per second on each cell it bears on: a full ordinary wall in 1.25 s. */
    public static final float ULTRA_HEAVY_PER_SECOND = 80f;
    /**
     * The heaviest structure a heavy hull crushes (owner: heavy vehicles break ordinary walls, not fortified ones):
     * the same walls and barricades as an ultra-heavy hull.
     */
    public static final int HEAVY_CEILING = 100;
    /**
     * Structure a heavy hull spends per second on each cell it bears on, slower than an ultra-heavy's: held against a
     * city wall it grinds through in two seconds, not one and a quarter. Its lesser mass carries it less far through
     * a row at speed, too.
     */
    public static final float HEAVY_PER_SECOND = 50f;
    /** How far past the hull's nose the bearing strip reaches, cells. */
    static final float REACH_CELLS = .6f;
    /** How far back from the nose the bearing strip starts, so a cell already touching the hull is still pushed. */
    static final float OVERLAP_CELLS = .2f;
    /** Cells past where its nose gets to this tick that a hull counts as hitting: the sweep's contact skin. */
    static final float CONTACT_CELLS = .05f;
    /** What a pushable cell costs a crushing hull's route: a ramming hull's barricade price. */
    static final float ROUTE_COST = BarricadeRam.ROUTE_COST;

    private final NavigationService navigation;
    private final MapEditor editor;
    private final DoodadService doodads;
    /** Cells the hull bears on this tick, as x/y pairs. Host-thread scratch: vehicles are driven serially. */
    private final int[] bearing = new int[64];
    private int bearingCount;
    /** Pushable edges the hull bears on this tick, as x/y/direction-ordinal triples. */
    private final int[] bearingEdges = new int[96];
    private int bearingEdgeCount;
    /** Cells the hull's nose hits this tick, as x/y pairs. */
    private final int[] struck = new int[64];
    private int struckCount;
    /** Pushable edges the hull's nose hits this tick, as x/y/direction-ordinal triples. */
    private final int[] struckEdges = new int[96];
    private int struckEdgeCount;
    /** Every cell a strip or footprint overlaps this tick, as x/y pairs: what an edge must lie between. */
    private final int[] overlapped = new int[128];
    private int overlappedCount;
    /** Whether something the hull cannot push holds it this tick ({@link #grind}). */
    private boolean heldElsewhere;
    /** Footprint cells a grind bears on beyond what this tick's push bore on, as x/y pairs. */
    private final int[] held = new int[128];
    private int heldCount;
    /** Footprint edges a grind bears on beyond this tick's push's, as x/y/direction-ordinal triples. */
    private final int[] heldEdges = new int[96];
    private int heldEdgeCount;
    /** The hull this tick's push bore on, and what it spent: a grind the same tick spends that, not a second rate. */
    private GroundBody pushed;
    private int pushedSpend;
    /** Breach views by ceiling and base, rebuilt when the map's topology has moved. */
    private final List<View> views = new ArrayList<>(2);
    /** The breach view walkers plan over ({@link #planningGrid(int, NavigationGrid)}). */
    private View walkerView;

    public WallCrush(NavigationService navigation, MapEditor editor, DoodadService doodads) {
        this.navigation = navigation;
        this.editor = editor;
        this.doodads = doodads;
    }

    /**
     * The heaviest structure {@code type} pushes: {@link #ULTRA_HEAVY_CEILING} or {@link #HEAVY_CEILING} by weight,
     * none for a hull that crushes no walls.
     */
    public static int ceiling(VehicleType type) { return ceiling(type.weight()); }

    /** The ceiling of a hull of {@code weight}. */
    static int ceiling(VehicleType.Weight weight) {
        if (weight == VehicleType.Weight.ULTRA_HEAVY) return ULTRA_HEAVY_CEILING;
        return weight.crushesWalls() ? HEAVY_CEILING : 0;
    }

    /** Structure a second {@code type} spends on each cell it bears on; none for a hull that crushes no walls. */
    public static float rate(VehicleType type) { return rate(type.weight()); }

    /** The rate of a hull of {@code weight}. */
    static float rate(VehicleType.Weight weight) {
        if (weight == VehicleType.Weight.ULTRA_HEAVY) return ULTRA_HEAVY_PER_SECOND;
        return weight.crushesWalls() ? HEAVY_PER_SECOND : 0f;
    }

    /** How wide {@code type} bears, cells: its own hull. */
    public static float bearingWidth(VehicleType type) { return type.visualWidthCells; }

    /** Whether an ultra-heavy hull can push cell {@code (x, y)} down right now. See the class rule. */
    public static boolean canPush(NavigationGrid grid, CellTopology topology, DoodadService doodads, int x, int y) {
        return canPush(grid, topology, doodads, x, y, ULTRA_HEAVY_CEILING);
    }

    /** Whether a hull whose ceiling is {@code most} can push cell {@code (x, y)} down right now. */
    public static boolean canPush(NavigationGrid grid, CellTopology topology, DoodadService doodads, int x, int y,
                                  int most) {
        if (!grid.inBounds(x, y) || grid.isWalkable(x, y)
                || grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID)
                || grid.hasTag(x, y, NavigationGrid.CellTag.WORKS)
                || grid.hasTag(x, y, NavigationGrid.CellTag.HARDENED)) return false;
        int structure = structure(grid, topology, doodads, x, y);
        return structure > 0 && structure <= most;
    }

    /**
     * The structure a push on closed cell {@code (x, y)} has to spend, read where {@link MapEditor#damageWall} will
     * spend it: the first destructible prop a hit reaches when it is built ({@link DoodadService#builtPropStructureAt}:
     * none for a rock or a natural object), else the cell's own wall structure when it is a wall or a window cell and
     * no fixture. Zero where nothing the hull can bring down holds the cell.
     */
    static int structure(NavigationGrid grid, CellTopology topology, DoodadService doodads, int x, int y) {
        if (doodads != null && doodads.damageablePropIdAt(x, y, topology) != null) {
            return doodads.builtPropStructureAt(x, y, topology);
        }
        if (!topology.isWall(x, y) && !topology.isWindow(x, y) || topology.isFixture(x, y)) return 0;
        return grid.getWallHp(x, y);
    }

    /**
     * Whether a hull whose ceiling is {@code most} can push the shared edge on {@code direction} of cell
     * {@code (x, y)} down right now: a movement-blocking edge barrier (a window bay) whose structure is above zero and
     * no more than {@code most}, on no works ground either side. See the class rule.
     */
    public static boolean canPushEdge(NavigationGrid grid, int x, int y, Direction direction, int most) {
        int nx = x + direction.dx, ny = y + direction.dy;
        if (!grid.inBounds(x, y) || !grid.inBounds(nx, ny)) return false;
        SharedEdgeBarrier barrier = grid.getEdgeBarrier(x, y, direction);
        if (barrier == null || !barrier.kind().blocksMovement()) return false;
        if (grid.hasTag(x, y, NavigationGrid.CellTag.WORKS) || grid.hasTag(nx, ny, NavigationGrid.CellTag.WORKS)
                || grid.hasTag(x, y, NavigationGrid.CellTag.HARDENED)
                || grid.hasTag(nx, ny, NavigationGrid.CellTag.HARDENED)) {
            return false;
        }
        return barrier.structure() > 0 && barrier.structure() <= most;
    }

    /** Whether the hull grades cell {@code (x, y)}: low crash debris on open ground. */
    public static boolean grades(NavigationGrid grid, int x, int y) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y)
                && grid.hasTag(x, y, NavigationGrid.CellTag.CRASH_DEBRIS);
    }

    /**
     * A routing copy of {@code grid} with every cell an ultra-heavy hull could push open on all sides, and every
     * edge it could push open, as it would leave them. The live grid is not touched. A closed shared edge it cannot
     * push stays closed.
     */
    public static NavigationGrid breachView(NavigationGrid grid, CellTopology topology, DoodadService doodads) {
        return breachView(grid, grid, topology, doodads, ULTRA_HEAVY_CEILING);
    }

    /**
     * {@link #breachView(NavigationGrid, CellTopology, DoodadService)} for a ceiling of {@code most}, laid over
     * {@code base}, a view of {@code grid}.
     */
    static NavigationGrid breachView(NavigationGrid base, NavigationGrid grid, CellTopology topology,
                                     DoodadService doodads, int most) {
        NavigationGrid copy = base.copyRoutingTopology();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!canPush(grid, topology, doodads, x, y, most)) continue;
                copy.setWalkable(x, y, true);
                copy.openAllEdges(x, y);
            }
        }
        for (int i = 0; i < grid.edgeBarrierCount(); i++) {
            SharedEdgeBarrier barrier = grid.edgeBarrierAt(i);
            if (canPushEdge(grid, barrier.cellX(), barrier.cellY(), barrier.direction(), most)) {
                copy.openSharedEdge(barrier.cellX(), barrier.cellY(), barrier.direction());
            }
        }
        return copy;
    }

    /**
     * The breach view {@code type} plans over, laid over {@code base}, the grid it moves over (the live grid, its
     * crush view, or a skirt's view). Gun tanks are common and the map moves all battle, so the view is kept, not
     * rebuilt: it catches up with the live grid's change log cell by cell, as the crush view does, and keeps its
     * identity while its base does. A skirt's view is copied afresh when the map moves, so a breach view over one is
     * too. One view is kept per ceiling and kind of base.
     */
    public NavigationGrid planningGrid(VehicleType type, NavigationGrid base) {
        int most = ceiling(type);
        boolean hover = type.isHover();
        for (View view : views) {
            if (view.most == most && view.hover == hover) return view.caughtUp(base);
        }
        View view = new View(most, hover);
        views.add(view);
        return view.caughtUp(base);
    }

    /**
     * The breach view a walker whose ceiling is {@code most} plans over (a wall-crushing walker, `mech-lines.md`, the
     * Pilgrim), laid over {@code base}, the live grid: kept and caught up as a hull's is.
     */
    public NavigationGrid planningGrid(int most, NavigationGrid base) {
        // Kept apart from the hulls' views: a hull's base is often its own crush view, and one view over two bases
        // would rebuild whole every time the other asked.
        if (walkerView == null || walkerView.most != most) walkerView = new View(most, false);
        return walkerView.caughtUp(base);
    }

    /**
     * The route cost a crushing hull pays: every cell under its ceiling at least {@link #ROUTE_COST}, what a ramming
     * hull pays for a barricade, so it goes round a wall or a barricade on a street where going round is short.
     */
    TerrainCostField costField(VehicleType type, TerrainCostField base) {
        NavigationGrid grid = navigation.getGrid();
        CellTopology topology = navigation.getTopology();
        int most = ceiling(type);
        return base.withFloor((x, y) -> canPush(grid, topology, doodads, x, y, most), ROUTE_COST);
    }

    /** Whether {@code grid} is a breach view kept and caught up with the live grid: one not over a skirt's view. */
    boolean isKeptView(NavigationGrid grid) {
        for (View view : views) if (!view.hover && view.grid == grid) return true;
        return false;
    }

    /** {@link #push(GroundBody, VehicleType, VehicleControlComponent, float, int)} driving forward. */
    public boolean push(GroundBody body, VehicleType type, VehicleControlComponent control, float dt) {
        return push(body, type, control, dt, 1);
    }

    /**
     * One tick of a crushing hull bearing the way it moves at {@code body}'s pose: grades the debris under the hull
     * and the strip, and spends this tick's push on every pushable cell and edge in the strip.
     *
     * @param travel {@code 1} driving forward, the strip past its nose; {@code -1} reversing, past its tail
     * @return whether the hull bore on at least one pushable cell — work that counts as progress for the hull's
     *         stall and wall-contact recovery
     */
    public boolean push(GroundBody body, VehicleType type, VehicleControlComponent control, float dt, int travel) {
        NavigationGrid grid = navigation.getGrid();
        CellTopology topology = navigation.getTopology();
        int most = ceiling(type);
        int way = travel < 0 ? -1 : 1;
        pushed = null;
        float stripLength = REACH_CELLS + OVERLAP_CELLS;
        float stripOffset = way * stripOffset(type, stripLength);
        VehicleFootprint.forEachOverlappedCell(body.x, body.y, body.facingDegrees, 0f,
                type.visualLengthCells, type.visualWidthCells, grid, (x, y) -> {
                    if (grades(grid, x, y)) grid.setTag(x, y, NavigationGrid.CellTag.CRASH_DEBRIS, false);
                });
        bearingCount = 0;
        overlappedCount = 0;
        VehicleFootprint.forEachOverlappedCell(body.x, body.y, body.facingDegrees, stripOffset,
                stripLength, bearingWidth(type), grid, (x, y) -> {
                    if (grades(grid, x, y)) grid.setTag(x, y, NavigationGrid.CellTag.CRASH_DEBRIS, false);
                    overlap(x, y);
                    if (!canPush(grid, topology, doodads, x, y, most)) return;
                    if (bearingCount * 2 >= bearing.length) return;
                    bearing[bearingCount * 2] = x;
                    bearing[bearingCount * 2 + 1] = y;
                    bearingCount++;
                });
        bearingEdgeCount = spannedEdges(grid, most, bearingEdges, false);
        if (bearingCount == 0 && bearingEdgeCount == 0) {
            control.bladeCarry = 0f;
            return false;
        }
        float pending = control.bladeCarry + rate(type) * dt;
        int spend = (int) pending;
        control.bladeCarry = pending - spend;
        strike(body, type, grid, topology, spend, dt, way);
        if (spend > 0) spendOnBearing(body, spend);
        pushed = body;
        pushedSpend = spend;
        return true;
    }

    /**
     * How far along the hull's axis, from its centre toward the end it moves to, the middle of a strip of
     * {@code length} lies: reaching past that end, overlapping it by {@link #OVERLAP_CELLS} so a cell already touching
     * the hull is still borne on.
     */
    static float stripOffset(VehicleType type, float length) {
        return type.visualLengthCells * .5f - OVERLAP_CELLS + length * .5f;
    }

    /**
     * One tick of a hull moving or turning: at {@code body}'s pose, the pose its move would reach, it spends this
     * tick's push on every pushable cell and edge its whole footprint overlaps, whichever way it moved to get there.
     * Called after a {@link #push} of the same hull the same tick, it spends what that push spent and skips what
     * that push already bore on, so nothing takes two rates in one tick.
     *
     * @return whether it bore on pushable ground beyond this tick's push and nothing it cannot push holds it — work
     *         that counts as progress, as {@link #push}'s does. Where something else holds it, it still grinds what
     *         it can, but that is no progress
     */
    public boolean grind(GroundBody body, VehicleType type, VehicleControlComponent control, float dt) {
        NavigationGrid grid = navigation.getGrid();
        CellTopology topology = navigation.getTopology();
        int most = ceiling(type);
        boolean afterPush = pushed == body;
        pushed = null;
        heldCount = 0;
        overlappedCount = 0;
        heldElsewhere = false;
        VehicleFootprint.forEachOverlappedCell(body.x, body.y, body.facingDegrees, 0f,
                type.visualLengthCells, type.visualWidthCells, grid, (x, y) -> {
                    overlap(x, y);
                    if (grid.isWalkable(x, y)) return;
                    if (!canPush(grid, topology, doodads, x, y, most)) {
                        heldElsewhere = true;
                        return;
                    }
                    if (afterPush && inBearing(x, y) || heldCount * 2 >= held.length) return;
                    held[heldCount * 2] = x;
                    held[heldCount * 2 + 1] = y;
                    heldCount++;
                });
        int edges = spannedEdges(grid, most, heldEdges, true);
        if (edges < 0) {
            heldElsewhere = true;
            edges = spannedEdges(grid, most, heldEdges, false);
        }
        heldEdgeCount = 0;
        for (int i = 0; i < edges; i++) {
            int x = heldEdges[i * 3], y = heldEdges[i * 3 + 1], d = heldEdges[i * 3 + 2];
            if (afterPush && inBearingEdges(x, y, d)) continue;
            heldEdges[heldEdgeCount * 3] = x;
            heldEdges[heldEdgeCount * 3 + 1] = y;
            heldEdges[heldEdgeCount * 3 + 2] = d;
            heldEdgeCount++;
        }
        if (heldCount == 0 && heldEdgeCount == 0) return false;
        int spend;
        if (afterPush) {
            spend = pushedSpend;
        } else {
            float pending = control.bladeCarry + rate(type) * dt;
            spend = (int) pending;
            control.bladeCarry = pending - spend;
        }
        if (spend > 0) {
            for (int i = 0; i < heldCount; i++) damageCell(body, held[i * 2], held[i * 2 + 1], spend);
            for (int i = 0; i < heldEdgeCount; i++) {
                damageEdge(body, heldEdges[i * 3], heldEdges[i * 3 + 1], heldEdges[i * 3 + 2], spend);
            }
        }
        return !heldElsewhere;
    }

    private boolean inBearing(int x, int y) {
        for (int i = 0; i < bearingCount; i++) if (bearing[i * 2] == x && bearing[i * 2 + 1] == y) return true;
        return false;
    }

    private boolean inBearingEdges(int x, int y, int direction) {
        for (int i = 0; i < bearingEdgeCount; i++) {
            if (bearingEdges[i * 3] == x && bearingEdges[i * 3 + 1] == y && bearingEdges[i * 3 + 2] == direction) {
                return true;
            }
        }
        return false;
    }

    /** Notes a cell the current strip or footprint overlaps. */
    private void overlap(int x, int y) {
        if (overlappedCount * 2 >= overlapped.length) return;
        overlapped[overlappedCount * 2] = x;
        overlapped[overlappedCount * 2 + 1] = y;
        overlappedCount++;
    }

    private boolean overlaps(int x, int y) {
        for (int i = 0; i < overlappedCount; i++) {
            if (overlapped[i * 2] == x && overlapped[i * 2 + 1] == y) return true;
        }
        return false;
    }

    /** The two directions each shared edge is read from, so each is read once: from its west or south cell. */
    private static final Direction[] FORWARD_EDGES = {Direction.E, Direction.N};
    private static final Direction[] DIRECTIONS = Direction.values();

    /**
     * The pushable edges between two cells both overlapped this tick, written to {@code out} as x/y/direction
     * triples: a convex strip or footprint that overlaps both cells of an edge crosses it. With {@code strict}, an
     * edge between two open cells that is closed and cannot be pushed answers {@code -1}: something else holds the
     * hull.
     */
    private int spannedEdges(NavigationGrid grid, int most, int[] out, boolean strict) {
        int count = 0;
        for (int i = 0; i < overlappedCount; i++) {
            int x = overlapped[i * 2], y = overlapped[i * 2 + 1];
            for (Direction direction : FORWARD_EDGES) {
                int nx = x + direction.dx, ny = y + direction.dy;
                if (!overlaps(nx, ny)) continue;
                if (canPushEdge(grid, x, y, direction, most)) {
                    if (count * 3 >= out.length) continue;
                    out[count * 3] = x;
                    out[count * 3 + 1] = y;
                    out[count * 3 + 2] = direction.ordinal();
                    count++;
                } else if (strict && grid.isWalkable(x, y) && grid.isWalkable(nx, ny)
                        && !grid.isSharedEdgePassable(x, y, direction)) {
                    return -1;
                }
            }
        }
        return count;
    }

    /** Spends {@code spend} on every cell and edge the hull bears on, counting each that falls. */
    private void spendOnBearing(GroundBody body, int spend) {
        for (int i = 0; i < bearingCount; i++) damageCell(body, bearing[i * 2], bearing[i * 2 + 1], spend);
        for (int i = 0; i < bearingEdgeCount; i++) {
            damageEdge(body, bearingEdges[i * 3], bearingEdges[i * 3 + 1], bearingEdges[i * 3 + 2], spend);
        }
    }

    private void damageCell(GroundBody body, int x, int y, int amount) {
        if (amount > 0 && editor.damageWall(x, y, amount)) body.recordCrush(x + .5f, y + .5f);
    }

    private void damageEdge(GroundBody body, int x, int y, int direction, int amount) {
        Direction d = DIRECTIONS[direction];
        if (amount > 0 && editor.damageEdgeBarrier(x, y, d, amount)) {
            body.recordCrush(x + .5f + d.dx * .5f, y + .5f + d.dy * .5f);
        }
    }

    /**
     * The hull's momentum spent on what its leading end ({@code way}: its nose, or its tail reversing) reaches this
     * tick: the pushable cells within the way it moves, not
     * the whole strip it bears on, so a cell beside its path that it never hits does not stop it. Where its momentum
     * and {@code spend} cover all those cells have left they fall now and it drives on at the speed the rest gives;
     * where they do not, they share its momentum and it is brought up against them.
     */
    private void strike(GroundBody body, VehicleType type, NavigationGrid grid, CellTopology topology, int spend,
                        float dt, int way) {
        float tonnes = type.massTonnes();
        // Its way toward the end it leads with: a hull moving the other way strikes nothing.
        float speed = way * body.speed;
        float impact = HullMomentum.structure(tonnes, speed);
        if (impact <= 0f) return;
        int most = ceiling(type);
        float reach = OVERLAP_CELLS + speed * dt + CONTACT_CELLS;
        struckCount = 0;
        overlappedCount = 0;
        VehicleFootprint.forEachOverlappedCell(body.x, body.y, body.facingDegrees,
                way * stripOffset(type, reach), reach, bearingWidth(type), grid,
                (x, y) -> {
                    overlap(x, y);
                    if (!canPush(grid, topology, doodads, x, y, most) || struckCount * 2 >= struck.length) return;
                    struck[struckCount * 2] = x;
                    struck[struckCount * 2 + 1] = y;
                    struckCount++;
                });
        struckEdgeCount = spannedEdges(grid, most, struckEdges, false);
        int pieces = struckCount + struckEdgeCount;
        if (pieces == 0) return;
        int standing = 0;
        for (int i = 0; i < struckCount; i++) standing += left(grid, topology, struck[i * 2], struck[i * 2 + 1], spend);
        for (int i = 0; i < struckEdgeCount; i++) standing += edgeLeft(grid, i, spend);
        if (impact >= standing) {
            // It goes through: what it hits falls, and what that took comes off its way.
            for (int i = 0; i < struckCount; i++) {
                int x = struck[i * 2], y = struck[i * 2 + 1];
                damageCell(body, x, y, left(grid, topology, x, y, spend));
            }
            for (int i = 0; i < struckEdgeCount; i++) {
                damageEdge(body, struckEdges[i * 3], struckEdges[i * 3 + 1], struckEdges[i * 3 + 2],
                        edgeLeft(grid, i, spend));
            }
            body.speed = way * HullMomentum.speedAfter(tonnes, speed, standing);
            return;
        }
        // It is brought up: its whole way goes into what it hits, shared.
        int share = (int) (impact / pieces);
        for (int i = 0; i < struckCount; i++) damageCell(body, struck[i * 2], struck[i * 2 + 1], share);
        for (int i = 0; i < struckEdgeCount; i++) {
            damageEdge(body, struckEdges[i * 3], struckEdges[i * 3 + 1], struckEdges[i * 3 + 2], share);
        }
        body.speed = 0f;
    }

    /** What a struck cell has left once this tick's push of {@code spend} has gone into it. */
    private int left(NavigationGrid grid, CellTopology topology, int x, int y, int spend) {
        return Math.max(0, structure(grid, topology, doodads, x, y) - spend);
    }

    /** What struck edge {@code i} has left once this tick's push of {@code spend} has gone into it. */
    private int edgeLeft(NavigationGrid grid, int i, int spend) {
        SharedEdgeBarrier barrier = grid.getEdgeBarrier(struckEdges[i * 3], struckEdges[i * 3 + 1],
                DIRECTIONS[struckEdges[i * 3 + 2]]);
        return barrier == null ? 0 : Math.max(0, barrier.structure() - spend);
    }

    /** One ceiling's breach view over one kind of base, caught up cell by cell while its base keeps its identity. */
    private final class View {
        final int most;
        final boolean hover;
        NavigationGrid grid;
        NavigationGrid base;
        NavigationGrid of;
        long caughtUp;

        View(int most, boolean hover) {
            this.most = most;
            this.hover = hover;
        }

        NavigationGrid caughtUp(NavigationGrid over) {
            NavigationGrid live = navigation.getGrid();
            CellTopology topology = navigation.getTopology();
            long now = live.changeCount();
            if (grid == null || base != over || of != live || !live.hasCaughtUpFrom(caughtUp)) {
                grid = breachView(over, live, topology, doodads, most);
                base = over;
                of = live;
            } else {
                for (long sequence = caughtUp; sequence < now; sequence++) {
                    int cell = live.changedCellAt(sequence);
                    int x = cell % live.getWidth(), y = cell / live.getWidth();
                    grid.syncRoutingCell(over, cell, 0L);
                    for (Direction direction : Direction.CARDINALS) {
                        if (canPushEdge(live, x, y, direction, most)) grid.openSharedEdge(x, y, direction);
                    }
                    if (!canPush(live, topology, doodads, x, y, most)) continue;
                    grid.setWalkable(x, y, true);
                    grid.openAllEdges(x, y);
                }
            }
            caughtUp = now;
            return grid;
        }
    }
}
