package com.dillon.starsectormarines.battle.sally;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortFrame;
import com.dillon.starsectormarines.battle.mech.MechMoveOrderService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The battle's sally doors and the rules by which a holder opens and shuts one (see {@code garrisonables.md}, sally
 * doors). Without them a holder inside a walled place it holds walks out of its one gate and round its own walls to the
 * ground it owns beyond a gateless face, two to four times the straight line, and arrives as the attack does.
 *
 * <p><b>Where the doors are.</b> Built once, on the first tick: a fort's authored sally ports, and for every tactical
 * node the doors {@link SallyDoorSurvey} finds in its building's walls and its compound's, outside any fort's works.
 *
 * <p><b>Rules, written for sides.</b> A door serves the side that holds its place whole
 * ({@link SallyDoor.Holding#holder}): for a fort, the side holding every aspect; for a compound with a capture record,
 * the side that record says holds it uncontested; for any other place, the side with people inside it and nobody hostile
 * inside it. That side opens a shut door when one of its squads or machines is ordered through it: a member inside the
 * place ordered to ground beyond the door's face, outside the place and within {@link #REACH_CELLS} of the mouth, or a
 * member outside on that face near the mouth ordered home into the place. Never while a hostile body stands within
 * {@link #SAFE_CELLS} of the mouth, and never within {@link #DWELL_TICKS} of the door's last change. Opening makes the
 * door's cells built passage, not rubble, each recorded so that it can be put back.
 *
 * <p><b>Shutting.</b> The same side shuts an open door when a hostile body comes within {@link #SAFE_CELLS} of the mouth
 * (at once), or when the sortie has passed: nothing of the side's has wanted the door or stood within
 * {@link #LINGER_CELLS} of it for {@link #IDLE_TICKS}. A door never shuts on a body of any side standing in it; it shuts
 * on the first pulse it is clear. Shutting puts every cell back as it was ({@code MapEditor.closePassage}) and drops every
 * route that crosses the door. A place nobody holds whole opens and shuts nothing, so a door left open by a holder that
 * lost its place stays open, the attack's: that is a sortie's risk. A shut door is wall like the wall round it, breached as
 * any wall is, so no attacker is given a door it did not cut; a door breached while shut is broken, a hole for good.
 *
 * <p><b>The player's bar.</b> {@link #order} bars an open door (it shuts now, or as soon as it is clear, and nothing opens
 * it) or unbars a shut one (it opens now, the enemy near or not, and the side's reflexes leave it open), for the side
 * holding the place whole. A bar or an unbar lasts until it is ordered again or the place changes hands.
 *
 * <p><b>Controls.</b> {@code -Dbattle.sallyDoors=false} keeps every door shut; {@code -Dbattle.sallyDoors.derived=false}
 * keeps only the forts' authored ports; {@code -Dbattle.sallyDoors.closable=false} never shuts an opened door again.
 */
public final class SallyDoors {

    /** Ticks between looks at the doors. */
    static final int PULSE_TICKS = 15;
    /** A door is not opened while a hostile body stands this near its mouth, and is shut when one comes this near. UNTUNED. */
    public static final float SAFE_CELLS = 12f;
    /** How far beyond its mouth an order's ground may lie for the door to serve it. UNTUNED. */
    public static final float REACH_CELLS = 36f;
    /** A door that changed state is not opened again for this long; shutting in the enemy's face is never held. UNTUNED. */
    public static final int DWELL_TICKS = 90;
    /** A door nothing of its holder's has wanted or stood near for this long shuts: the sortie has passed. UNTUNED. */
    public static final int IDLE_TICKS = 150;
    /** A body of the holder's this near the door's cells is still using it. UNTUNED. */
    public static final float LINGER_CELLS = 4f;
    /** Clearance, beyond a body's own radius, a door's cells keep before they shut. */
    static final float BODY_MARGIN = 0.05f;

    public static final String ENABLED_PROPERTY = "battle.sallyDoors";
    public static final String DERIVED_PROPERTY = "battle.sallyDoors.derived";
    public static final String CLOSABLE_PROPERTY = "battle.sallyDoors.closable";
    private static boolean enabled = Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));
    private static boolean derived = Boolean.parseBoolean(System.getProperty(DERIVED_PROPERTY, "true"));
    private static boolean closable = Boolean.parseBoolean(System.getProperty(CLOSABLE_PROPERTY, "true"));

    /** What a player's order on a door holds it to, until ordered again or the place changes hands. */
    public enum Bar { NONE, BARRED, UNBARRED }

    /** Why a door shut: for evidence. */
    public enum Shut { ENEMY, SORTIE_PASSED, BARRED }

    private List<SallyDoor> doors;
    private boolean[] open = new boolean[0];
    private boolean[] broken = new boolean[0];
    private MapEditor.PassageRecord[][] records = new MapEditor.PassageRecord[0][];
    private int[] changedTick = new int[0];
    private int[] usedTick = new int[0];
    private Bar[] bars = new Bar[0];
    private Faction[] barHolder = new Faction[0];
    /** The side that last held each door's place whole: its keeper while the place stands empty. */
    private Faction[] keepers = new Faction[0];
    /** The side each open door was opened for, or null: whose sortie it is. */
    private Faction[] openedFor = new Faction[0];
    /** Doors in the order they opened, each {door, tick, holder ordinal}: for evidence. */
    private final List<int[]> openings = new ArrayList<>();
    /** Doors in the order they shut, each {door, tick, holder ordinal, {@link Shut} ordinal}: for evidence. */
    private final List<int[]> closings = new ArrayList<>();
    /** Each first time, per opening, a body hostile to the side a door was opened for stood in it: {door, tick, faction}. */
    private final List<int[]> hostileCrossings = new ArrayList<>();
    private final Set<Long> crossed = new HashSet<>();
    /** The player's door orders, queued from input and applied at the top of the next tick: {door, side ordinal}. */
    private final List<int[]> requested = new ArrayList<>();
    private float[][] middles = new float[0][];

    /** Whether doors may be opened in this process. */
    public static boolean enabled() {
        return enabled;
    }

    /** For a control run inside one process (scenes, evidence). */
    public static void setEnabledForEvidence(boolean on) {
        enabled = on;
    }

    /** Whether walled places other than forts get doors in this process. */
    public static boolean derivedEnabled() {
        return derived;
    }

    /** For a control run inside one process (scenes, evidence). */
    public static void setDerivedForEvidence(boolean on) {
        derived = on;
    }

    /** Whether an opened door may be shut again in this process. */
    public static boolean closableEnabled() {
        return closable;
    }

    /** For a control run inside one process (scenes, evidence). */
    public static void setClosableForEvidence(boolean on) {
        closable = on;
    }

    /** The battle's doors, found on first asking. */
    public List<SallyDoor> doors(BattleSimulation sim) {
        if (doors == null) build(sim);
        return doors;
    }

    public boolean isOpen(int door) {
        return door >= 0 && door < open.length && open[door];
    }

    /** Whether the door was breached while shut: a hole for good, never opened or shut again. */
    public boolean isBroken(int door) {
        return door >= 0 && door < broken.length && broken[door];
    }

    /** The player's bar on the door. */
    public Bar bar(int door) {
        return door >= 0 && door < bars.length ? bars[door] : Bar.NONE;
    }

    /** Doors in the order they opened: {door, tick, holder's {@link Faction} ordinal or -1}. Read-only. */
    public List<int[]> openings() {
        return Collections.unmodifiableList(openings);
    }

    /** Doors in the order they shut: {door, tick, holder's {@link Faction} ordinal or -1, {@link Shut} ordinal}. Read-only. */
    public List<int[]> closings() {
        return Collections.unmodifiableList(closings);
    }

    /** First crossings of an open door by a side hostile to the one it was opened for: {door, tick, faction}. Read-only. */
    public List<int[]> hostileCrossings() {
        return Collections.unmodifiableList(hostileCrossings);
    }

    /** The door with a cell within one cell of (x, y), or -1: what a pointer over a door names. */
    public int doorAt(BattleSimulation sim, int x, int y) {
        List<SallyDoor> all = doors(sim);
        for (int i = 0; i < all.size(); i++) {
            for (int[] c : all.get(i).cells()) {
                if (Math.abs(c[0] - x) <= 1 && Math.abs(c[1] - y) <= 1) return i;
            }
        }
        return -1;
    }

    /** The door that is port {@code port} of the fort, or -1. */
    public int fortDoor(BattleSimulation sim, Fort fort, int port) {
        List<SallyDoor> all = doors(sim);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).place() instanceof FortHolding h && h.fort == fort && all.get(i).placeDoor() == port) return i;
        }
        return -1;
    }

    /** Looks at every door on the pulse: opens a shut one its holder wants open, shuts an open one it wants shut. */
    public void tick(BattleSimulation sim) {
        if (!enabled) return;
        applyRequests(sim);
        if (sim.getSimTickIndex() % PULSE_TICKS != 0) return;
        int tick = sim.getSimTickIndex();
        List<SallyDoor> all = doors(sim);
        for (int i = 0; i < all.size(); i++) {
            if (broken[i]) continue;
            SallyDoor door = all.get(i);
            if (!open[i] && breached(sim, door)) {
                broken[i] = true;
                continue;
            }
            Faction holder = keeper(sim, i, door);
            if (holder != null && barHolder[i] != null && holder != barHolder[i]) {
                bars[i] = Bar.NONE;
                barHolder[i] = null;
            }
            if (open[i]) {
                noteCrossings(sim, i, door, tick);
                if (holder == null || !closable) continue;
                if (wanted(sim, i, holder) || holderNear(sim, holder, door)) usedTick[i] = tick;
                Shut why = shutFor(i, tick, hostileNear(sim, holder, door.mouthX(), door.mouthY()));
                if (why != null && !bodyInDoor(sim, door)) close(sim, i, why);
                continue;
            }
            if (holder == null || bars[i] == Bar.BARRED) continue;
            if (tick - changedTick[i] < DWELL_TICKS) continue;
            if (hostileNear(sim, holder, door.mouthX(), door.mouthY())) continue;
            if (!wanted(sim, i, holder)) continue;
            open(sim, i);
        }
    }

    /**
     * The side the door serves now: the side holding its place whole, or, while nobody does and nobody hostile to it is
     * inside, the side that last did. A sortie empties a small place, and the yard a squad has just gone out of is still
     * its own to shut behind it and to come home to until somebody else comes in.
     */
    private Faction keeper(BattleSimulation sim, int index, SallyDoor door) {
        Faction holder = door.place().holder(sim);
        if (holder != null) {
            keepers[index] = holder;
            return holder;
        }
        Faction last = keepers[index];
        if (last == null || hostileInside(sim, door.place(), last)) return null;
        return last;
    }

    private static boolean hostileInside(BattleSimulation sim, SallyDoor.Holding place, Faction side) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!side.hostileTo(sim.identity().faction(unit)) || !onFoot(sim, unit)) continue;
            if (place.inside(sim.world().cellX(unit), sim.world().cellY(unit))) return true;
        }
        return false;
    }

    /** Why an open door of a held place should shut now, or null: the player's bar first, then the enemy, then idleness. */
    private Shut shutFor(int index, int tick, boolean enemyNear) {
        if (bars[index] == Bar.BARRED) return Shut.BARRED;
        if (bars[index] == Bar.UNBARRED) return null;
        if (enemyNear) return Shut.ENEMY;
        return tick - usedTick[index] >= IDLE_TICKS ? Shut.SORTIE_PASSED : null;
    }

    /** Queues {@link #order} from the player's input, to be applied in the simulation's own tick. */
    public void requestOrder(int index, Faction side) {
        synchronized (requested) {
            requested.add(new int[]{index, side.ordinal()});
        }
    }

    private void applyRequests(BattleSimulation sim) {
        List<int[]> batch;
        synchronized (requested) {
            if (requested.isEmpty()) return;
            batch = new ArrayList<>(requested);
            requested.clear();
        }
        for (int[] request : batch) order(sim, request[0], Faction.values()[request[1]]);
    }

    /**
     * The player's order on a door of a place their side holds whole: an open door is barred (shut now, or as soon as
     * nobody stands in it, and kept shut), a shut or barred one unbarred (opened now and left open). Returns false, and
     * changes nothing, for a broken door or one whose place the side does not hold whole.
     */
    public boolean order(BattleSimulation sim, int index, Faction side) {
        List<SallyDoor> all = doors(sim);
        if (index < 0 || index >= all.size() || broken[index] || side == null) return false;
        SallyDoor door = all.get(index);
        if (keeper(sim, index, door) != side) return false;
        if (!open[index] && breached(sim, door)) {
            broken[index] = true;
            return false;
        }
        barHolder[index] = side;
        if (open[index] && bars[index] != Bar.BARRED) {
            bars[index] = Bar.BARRED;
            if (closable && !bodyInDoor(sim, door)) close(sim, index, Shut.BARRED);
            return true;
        }
        bars[index] = Bar.UNBARRED;
        if (!open[index]) open(sim, index);
        return true;
    }

    /** Opens every door at once and returns how many there are: evidence of what the doors are worth, not a rule. */
    public int openAllForEvidence(BattleSimulation sim) {
        List<SallyDoor> all = doors(sim);
        for (int i = 0; i < all.size(); i++) open(sim, i);
        return all.size();
    }

    /** Opens the door as built passage, on the ground of the cell just inside it, recording each cell to shut it again. */
    public void open(BattleSimulation sim, int index) {
        if (isOpen(index) || isBroken(index)) return;
        SallyDoor door = doors(sim).get(index);
        MapEditor editor = sim.mapEditor();
        MapEditor.PassageRecord[] recorded = new MapEditor.PassageRecord[door.cells().length];
        for (int c = 0; c < recorded.length; c++) {
            int[] cell = door.cells()[c];
            recorded[c] = editor.recordPassage(cell[0], cell[1]);
            editor.openPassage(cell[0], cell[1], door.insideX(), door.insideY());
        }
        records[index] = recorded;
        open[index] = true;
        changedTick[index] = sim.getSimTickIndex();
        usedTick[index] = sim.getSimTickIndex();
        door.place().opened(door.placeDoor());
        Faction holder = keeper(sim, index, door);
        openedFor[index] = holder;
        crossed.removeIf(k -> (int) (k >>> 32) == index);
        openings.add(new int[]{index, sim.getSimTickIndex(), holder == null ? -1 : holder.ordinal()});
        // A route planned before the door opened is still legal and still goes round: the holder's people inside drop
        // theirs and plan again through the open door, which is what it was opened for.
        if (holder == null) return;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!holder.friendlyTo(sim.identity().faction(unit)) || sim.identity().type(unit).isStatic()) continue;
            if (door.place().inside(sim.world().cellX(unit), sim.world().cellY(unit))) sim.clearPath(unit);
        }
    }

    /**
     * Shuts the door: every cell back as it was before it opened, and every route that crosses it dropped, so nobody
     * walks into the wall and whoever meant to go through plans again. The caller has seen that nobody stands in it
     * ({@link #bodyInDoor}).
     */
    void close(BattleSimulation sim, int index, Shut why) {
        if (!isOpen(index)) return;
        SallyDoor door = doors(sim).get(index);
        MapEditor editor = sim.mapEditor();
        for (MapEditor.PassageRecord record : records[index]) {
            if (record != null) editor.closePassage(record);
        }
        records[index] = null;
        open[index] = false;
        openedFor[index] = null;
        changedTick[index] = sim.getSimTickIndex();
        door.place().closed(door.placeDoor());
        Faction holder = keeper(sim, index, door);
        closings.add(new int[]{index, sim.getSimTickIndex(), holder == null ? -1 : holder.ordinal(), why.ordinal()});
        float[] box = box(door);
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!onFoot(sim, unit) || sim.isRiding(unit)) continue;
            if (routeCrosses(sim, unit, box, sim.physicalRadius(unit) + 0.5f)) sim.clearPath(unit);
        }
    }

    /** Whether a cell of a shut door is walkable: it was breached as wall, and the door is broken. */
    private static boolean breached(BattleSimulation sim, SallyDoor door) {
        for (int[] c : door.cells()) if (sim.getGrid().isWalkable(c[0], c[1])) return true;
        return false;
    }

    /** {minX, minY, maxX, maxY} of the door's cells, in world units. */
    public static float[] box(SallyDoor door) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int[] c : door.cells()) {
            minX = Math.min(minX, c[0]);
            minY = Math.min(minY, c[1]);
            maxX = Math.max(maxX, c[0] + 1f);
            maxY = Math.max(maxY, c[1] + 1f);
        }
        return new float[]{minX, minY, maxX, maxY};
    }

    /** Whether any body on the ground, of any side, stands in or overlaps the door's cells. */
    public static boolean bodyInDoor(BattleSimulation sim, SallyDoor door) {
        float[] box = box(door);
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!onFoot(sim, unit) || sim.isRiding(unit)) continue;
            float reach = sim.physicalRadius(unit) + BODY_MARGIN;
            if (distanceToBox(box, sim.world().x(unit), sim.world().y(unit)) < reach) return true;
        }
        return false;
    }

    /** Distance from a point to a box, zero inside it. */
    static float distanceToBox(float[] box, float x, float y) {
        float dx = Math.max(0f, Math.max(box[0] - x, x - box[2]));
        float dy = Math.max(0f, Math.max(box[1] - y, y - box[3]));
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Whether the unit's remaining route, from where it stands through each waypoint still ahead of it, comes within
     * {@code reach} of the box: a straight leg between two waypoints can cross a door with neither of them in it.
     */
    public static boolean routeCrosses(BattleSimulation sim, long unit, float[] box, float reach) {
        int[] path = sim.world().path(unit);
        if (path == null || path.length < 2) return false;
        float px = sim.world().x(unit), py = sim.world().y(unit);
        for (int k = Math.max(0, sim.world().pathIdx(unit)) * 2; k + 1 < path.length; k += 2) {
            float qx = path[k] + 0.5f, qy = path[k + 1] + 0.5f;
            if (segmentNearBox(box, px, py, qx, qy, reach)) return true;
            px = qx;
            py = qy;
        }
        return false;
    }

    /** Whether a segment comes within {@code reach} of a box, sampled at quarter-cell steps. */
    static boolean segmentNearBox(float[] box, float ax, float ay, float bx, float by, float reach) {
        float length = (float) Math.hypot(bx - ax, by - ay);
        int steps = Math.max(1, (int) Math.ceil(length / 0.25f));
        for (int s = 0; s <= steps; s++) {
            float t = s / (float) steps;
            if (distanceToBox(box, ax + (bx - ax) * t, ay + (by - ay) * t) <= reach) return true;
        }
        return false;
    }

    /** Records, for evidence, each body hostile to the side the door was opened for standing in it, once per opening. */
    private void noteCrossings(BattleSimulation sim, int index, SallyDoor door, int tick) {
        Faction owner = openedFor[index];
        if (owner == null) return;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            Faction side = sim.identity().faction(unit);
            if (!owner.hostileTo(side) || !onFoot(sim, unit)) continue;
            int x = sim.world().cellX(unit), y = sim.world().cellY(unit);
            for (int[] c : door.cells()) {
                if (c[0] != x || c[1] != y) continue;
                if (crossed.add(((long) index << 32) | (unit & 0xffffffffL))) {
                    hostileCrossings.add(new int[]{index, tick, side.ordinal()});
                }
                break;
            }
        }
    }

    /** Whether a body of the holder's on the ground stands within {@link #LINGER_CELLS} of the door's cells. */
    private static boolean holderNear(BattleSimulation sim, Faction holder, SallyDoor door) {
        float[] box = box(door);
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!holder.friendlyTo(sim.identity().faction(unit)) || !onFoot(sim, unit)) continue;
            if (distanceToBox(box, sim.world().x(unit), sim.world().y(unit)) <= LINGER_CELLS) return true;
        }
        return false;
    }

    /** Whether door {@code index} serves an order to the cell: outside its place, within reach, beyond its face. */
    public boolean serves(BattleSimulation sim, int index, int x, int y) {
        SallyDoor door = doors(sim).get(index);
        if (door.place().inside(x, y)) return false;
        if (Math.hypot(x - door.mouthX(), y - door.mouthY()) > REACH_CELLS) return false;
        return beyondFace(middles[index], door, x, y);
    }

    /**
     * Whether a cell is beyond the door's face: the place's middle-to-cell offset, measured in the place's own
     * half-widths, runs further along the door's outward direction than along any other.
     */
    static boolean beyondFace(float[] middle, SallyDoor door, int x, int y) {
        float nx = (x + 0.5f - middle[0]) / middle[2];
        float ny = (y + 0.5f - middle[1]) / middle[3];
        if (Math.abs(nx) >= Math.abs(ny)) return door.outY() == 0 && door.outX() == (nx > 0 ? 1 : -1);
        return door.outX() == 0 && door.outY() == (ny > 0 ? 1 : -1);
    }

    /** {x, y, halfWidth, halfHeight} of a place's bounds. */
    static float[] middle(int[] b) {
        return new float[]{(b[0] + b[2] + 1) * 0.5f, (b[1] + b[3] + 1) * 0.5f,
                Math.max(1f, (b[2] - b[0] + 1) * 0.5f), Math.max(1f, (b[3] - b[1] + 1) * 0.5f)};
    }

    private void build(BattleSimulation sim) {
        List<SallyDoor> found = new ArrayList<>();
        List<FortFrame> frames = new ArrayList<>();
        for (Fort fort : sim.forts().forts()) {
            FortFrame frame = fort.frame();
            if (frame == null) continue;
            frames.add(frame);
            FortHolding holding = new FortHolding(fort);
            List<FortPlan.SallyPort> ports = frame.plan().sallyPorts();
            for (int p = 0; p < ports.size(); p++) {
                FortPlan.SallyPort port = ports.get(p);
                int[][] cells = new int[port.cells().size()][];
                for (int c = 0; c < cells.length; c++) cells[c] = frame.cell(port.cells().get(c)[0], port.cells().get(c)[1]);
                int[] inside = frame.cell(port.inside()[0], port.inside()[1]);
                int[] mouth = frame.cell(port.mouthX(), port.mouthY());
                found.add(new SallyDoor(cells, inside[0], inside[1], mouth[0], mouth[1], holding, p));
            }
        }
        if (derived && sim.getTacticalMap() != null) {
            Set<Long> taken = new HashSet<>();
            for (SallyDoor door : found) for (int[] c : door.cells()) taken.add(key(c[0], c[1]));
            for (TacticalNode node : sim.getTacticalMap().all()) {
                int[][] rects = {{node.left, node.top, node.right, node.bottom},
                        {node.compoundLeft(), node.compoundTop(), node.compoundRight(), node.compoundBottom()}};
                for (int r = 0; r < rects.length; r++) {
                    int[] rect = rects[r];
                    if (r == 1 && Arrays.equals(rect, rects[0])) continue;
                    PlaceHolding holding = new PlaceHolding(node, rect);
                    int placeDoor = 0;
                    for (SallyDoorSurvey.Spec spec : SallyDoorSurvey.derive(sim.getGrid(), sim.getTopology(), rect[0],
                            rect[1], rect[2], rect[3], node.anchorX, node.anchorY,
                            (x, y) -> taken.contains(key(x, y)) || inWorks(frames, x, y))) {
                        for (int[] c : spec.cells()) taken.add(key(c[0], c[1]));
                        found.add(new SallyDoor(spec.cells(), spec.insideX(), spec.insideY(), spec.mouthX(),
                                spec.mouthY(), holding, placeDoor++));
                    }
                }
            }
        }
        doors = List.copyOf(found);
        int n = doors.size();
        open = new boolean[n];
        broken = new boolean[n];
        records = new MapEditor.PassageRecord[n][];
        changedTick = new int[n];
        Arrays.fill(changedTick, Integer.MIN_VALUE / 2);
        usedTick = new int[n];
        bars = new Bar[n];
        Arrays.fill(bars, Bar.NONE);
        barHolder = new Faction[n];
        keepers = new Faction[n];
        openedFor = new Faction[n];
        middles = new float[n][];
        for (int i = 0; i < n; i++) middles[i] = middle(doors.get(i).place().bounds());
    }

    private static boolean inWorks(List<FortFrame> frames, int x, int y) {
        for (FortFrame frame : frames) if (frame.isWorks(x, y)) return true;
        return false;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    /**
     * Whether the holder has a squad or machine the door serves: a body of it ordered out through the door, or home
     * through it ({@link #through}).
     */
    private boolean wanted(BattleSimulation sim, int index, Faction holder) {
        for (Squad squad : sim.getSquads()) {
            if (squad.aliveMembers <= 0 || !holder.friendlyTo(squad.faction)) continue;
            if (squad.isMechSquad()) {
                for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                    long mech = sim.squadMemberAt(squad.id, i);
                    if (!sim.world().isAlive(mech)) continue;
                    MechMoveOrderService.ActiveOrder order = sim.getMechMoveOrderService().activeOrder(mech);
                    if (order != null && through(sim, index, mech, order.destinationX(), order.destinationY(),
                            sim.world().x(mech), sim.world().y(mech))) return true;
                }
                continue;
            }
            // A player's order is the squad's destination while it stands; otherwise its commander's.
            SquadMoveOrderService.ActiveOrder played = sim.getSquadMoveOrderService().activeOrder(squad.id);
            ObjectiveAssignment order = squad.assignedObjective;
            int x = played != null ? played.destinationX() : order == null ? -1 : order.targetCellX();
            int y = played != null ? played.destinationY() : order == null ? -1 : order.targetCellY();
            if (x < 0 || y < 0) continue;
            for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                long member = sim.squadMemberAt(squad.id, i);
                if (sim.world().isAlive(member) && through(sim, index, member, x, y, squad.centroidX, squad.centroidY)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a body ordered to the cell would go through door {@code index}: out, standing inside the place with the
     * ground the door serves as its destination; or home, standing on the ground the door serves with its destination
     * inside the place. Either way only while the door is still ahead of the body and of its squad ({@code fromX},
     * {@code fromY}: the squad's centroid, or a machine's own place), both farther from the destination than the door's
     * far side is: a squad that has got to its ground has no more use for the door, though a straggler of it stands on
     * the other side.
     */
    private boolean through(BattleSimulation sim, int index, long body, int x, int y, float fromX, float fromY) {
        SallyDoor door = doors.get(index);
        int bx = sim.world().cellX(body), by = sim.world().cellY(body);
        boolean out = door.place().inside(bx, by);
        if (out ? !serves(sim, index, x, y) : !door.place().inside(x, y) || !serves(sim, index, bx, by)) return false;
        int farX = out ? door.mouthX() : door.insideX(), farY = out ? door.mouthY() : door.insideY();
        double beyond = Math.hypot(farX - x, farY - y);
        return Math.hypot(bx - x, by - y) > beyond && Math.hypot(fromX - 0.5f - x, fromY - 0.5f - y) > beyond;
    }

    /** A body that fights on the ground: not an emplacement, drone or aircraft. */
    static boolean onFoot(BattleSimulation sim, long unit) {
        UnitType type = sim.identity().type(unit);
        return !type.isStatic() && !type.isDrone() && !type.isBasedAircraft() && sim.identity().airframe(unit) == null;
    }

    /** Whether a hostile body on the ground stands within {@link #SAFE_CELLS} of the mouth. */
    private static boolean hostileNear(BattleSimulation sim, Faction holder, int x, int y) {
        float reachSq = SAFE_CELLS * SAFE_CELLS;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!holder.hostileTo(sim.identity().faction(unit)) || !onFoot(sim, unit)) continue;
            float dx = sim.world().x(unit) - (x + 0.5f);
            float dy = sim.world().y(unit) - (y + 0.5f);
            if (dx * dx + dy * dy <= reachSq) return true;
        }
        return false;
    }

    /** A fort's works: held whole by the side that holds every aspect. */
    static final class FortHolding implements SallyDoor.Holding {
        final Fort fort;
        private final int[] bounds;

        FortHolding(Fort fort) {
            this.fort = fort;
            FortFrame frame = fort.frame();
            FortPlan plan = frame.plan();
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (int lx = 0; lx < plan.width(); lx++) {
                for (int ly = 0; ly < plan.height(); ly++) {
                    if (!plan.isWorks(lx, ly)) continue;
                    int[] c = frame.cell(lx, ly);
                    minX = Math.min(minX, c[0]);
                    minY = Math.min(minY, c[1]);
                    maxX = Math.max(maxX, c[0]);
                    maxY = Math.max(maxY, c[1]);
                }
            }
            bounds = new int[]{minX, minY, maxX, maxY};
        }

        @Override public Faction holder(BattleSimulation sim) { return fort.wholeHolder(); }
        @Override public boolean inside(int x, int y) { return fort.frame().isWorks(x, y); }
        @Override public int[] bounds() { return bounds; }
        @Override public void opened(int placeDoor) { fort.markSallyPortOpen(placeDoor); }
        @Override public void closed(int placeDoor) { fort.markSallyPortClosed(placeDoor); }
        @Override public String label() { return "fort " + fort.id(); }
    }

    /**
     * A tactical node's building or compound: held by the side its capture record names uncontested where it has one,
     * otherwise by the side with people inside and nobody hostile inside.
     */
    static final class PlaceHolding implements SallyDoor.Holding {
        private final TacticalNode node;
        private final int[] bounds;

        PlaceHolding(TacticalNode node, int[] bounds) {
            this.node = node;
            this.bounds = bounds.clone();
        }

        @Override
        public Faction holder(BattleSimulation sim) {
            var record = sim.getCompoundService().getRecord(node);
            if (record != null) {
                return switch (record.state) {
                    case DEFENDER_HELD -> Faction.DEFENDER;
                    case MARINE_HELD -> Faction.MARINE;
                    default -> null;
                };
            }
            Faction holder = null;
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                if (!inside(sim.world().cellX(unit), sim.world().cellY(unit)) || !onFoot(sim, unit)) continue;
                Faction side = sim.identity().faction(unit);
                if (side == Faction.CIVILIAN) continue;
                if (holder == null) holder = side;
                else if (holder.hostileTo(side)) return null;
            }
            return holder;
        }

        @Override
        public boolean inside(int x, int y) {
            return x >= bounds[0] && y >= bounds[1] && x <= bounds[2] && y <= bounds[3];
        }

        @Override public int[] bounds() { return bounds; }
        @Override public String label() { return node.kind + "@" + node.anchorX + "," + node.anchorY; }
    }
}
