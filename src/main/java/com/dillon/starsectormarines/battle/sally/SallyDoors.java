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
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The battle's sally doors and the rule by which a holder opens one (see {@code garrisonables.md}, sally doors). Without
 * them a holder inside a walled place it holds walks out of its one gate and round its own walls to the ground it owns
 * beyond a gateless face, two to four times the straight line, and arrives as the attack does.
 *
 * <p><b>Where the doors are.</b> Built once, on the first tick: a fort's authored sally ports, and for every tactical
 * node the doors {@link SallyDoorSurvey} finds in its building's walls and its compound's, outside any fort's works.
 *
 * <p><b>Rules, written for sides.</b> A door serves the side that holds its place whole
 * ({@link SallyDoor.Holding#holder}): for a fort, the side holding every aspect; for a compound with a capture record,
 * the side that record says holds it uncontested; for any other place, the side with people inside it and nobody hostile
 * inside it. That side opens a closed door when one of its squads or machines with a member inside the place is ordered
 * to ground beyond the door's face, outside the place and within {@link #REACH_CELLS} of the mouth, and never while a
 * hostile body stands within {@link #SAFE_CELLS} of the mouth. Opening makes the door's cells built passage, not rubble,
 * and it stays open for everybody for the rest of the battle, because the map only becomes more permissive at runtime:
 * that is a sortie's price, another way in the attack can find. A closed door is wall like the wall round it, breached as
 * any wall is, so no attacker is given a door it did not cut.
 *
 * <p><b>Controls.</b> {@code -Dbattle.sallyDoors=false} keeps every door closed; {@code -Dbattle.sallyDoors.derived=false}
 * keeps only the forts' authored ports.
 */
public final class SallyDoors {

    /** Ticks between looks at the doors. */
    static final int PULSE_TICKS = 15;
    /** A door is not opened while a hostile body stands this near its mouth. UNTUNED. */
    public static final float SAFE_CELLS = 12f;
    /** How far beyond its mouth an order's ground may lie for the door to serve it. UNTUNED. */
    public static final float REACH_CELLS = 36f;

    public static final String ENABLED_PROPERTY = "battle.sallyDoors";
    public static final String DERIVED_PROPERTY = "battle.sallyDoors.derived";
    private static boolean enabled = Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));
    private static boolean derived = Boolean.parseBoolean(System.getProperty(DERIVED_PROPERTY, "true"));

    private List<SallyDoor> doors;
    private boolean[] open = new boolean[0];
    /** Doors in the order they opened, each {door, tick, holder ordinal}: for evidence. */
    private final List<int[]> openings = new ArrayList<>();
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

    /** The battle's doors, found on first asking. */
    public List<SallyDoor> doors(BattleSimulation sim) {
        if (doors == null) build(sim);
        return doors;
    }

    public boolean isOpen(int door) {
        return door >= 0 && door < open.length && open[door];
    }

    /** Doors in the order they opened: {door, tick, holder's {@link Faction} ordinal or -1}. Read-only. */
    public List<int[]> openings() {
        return Collections.unmodifiableList(openings);
    }

    /** The door that is port {@code port} of the fort, or -1. */
    public int fortDoor(BattleSimulation sim, Fort fort, int port) {
        List<SallyDoor> all = doors(sim);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).place() instanceof FortHolding h && h.fort == fort && all.get(i).placeDoor() == port) return i;
        }
        return -1;
    }

    /** Looks at the closed doors on the pulse and opens any whose holder now wants it open. */
    public void tick(BattleSimulation sim) {
        if (!enabled || sim.getSimTickIndex() % PULSE_TICKS != 0) return;
        List<SallyDoor> all = doors(sim);
        for (int i = 0; i < all.size(); i++) {
            if (open[i]) continue;
            SallyDoor door = all.get(i);
            Faction holder = door.place().holder(sim);
            if (holder == null) continue;
            if (hostileNear(sim, holder, door.mouthX(), door.mouthY())) continue;
            if (!wanted(sim, i, holder)) continue;
            open(sim, i);
        }
    }

    /** Opens every door at once and returns how many there are: evidence of what the doors are worth, not a rule. */
    public int openAllForEvidence(BattleSimulation sim) {
        List<SallyDoor> all = doors(sim);
        for (int i = 0; i < all.size(); i++) open(sim, i);
        return all.size();
    }

    /** Opens the door as built passage, on the ground of the cell just inside it. */
    public void open(BattleSimulation sim, int index) {
        if (isOpen(index)) return;
        SallyDoor door = doors(sim).get(index);
        for (int[] cell : door.cells()) sim.mapEditor().openPassage(cell[0], cell[1], door.insideX(), door.insideY());
        open[index] = true;
        door.place().opened(door.placeDoor());
        Faction holder = door.place().holder(sim);
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
        open = new boolean[doors.size()];
        middles = new float[doors.size()][];
        for (int i = 0; i < doors.size(); i++) middles[i] = middle(doors.get(i).place().bounds());
    }

    private static boolean inWorks(List<FortFrame> frames, int x, int y) {
        for (FortFrame frame : frames) if (frame.isWorks(x, y)) return true;
        return false;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    /** Whether the holder has a squad or machine with a member inside the door's place, ordered to ground it serves. */
    private boolean wanted(BattleSimulation sim, int index, Faction holder) {
        SallyDoor.Holding place = doors.get(index).place();
        for (Squad squad : sim.getSquads()) {
            if (squad.aliveMembers <= 0 || !holder.friendlyTo(squad.faction)) continue;
            if (squad.isMechSquad()) {
                for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                    long mech = sim.squadMemberAt(squad.id, i);
                    if (!sim.world().isAlive(mech)) continue;
                    MechMoveOrderService.ActiveOrder order = sim.getMechMoveOrderService().activeOrder(mech);
                    if (order == null || !place.inside(sim.world().cellX(mech), sim.world().cellY(mech))) continue;
                    if (serves(sim, index, order.destinationX(), order.destinationY())) return true;
                }
                continue;
            }
            // A player's order is the squad's destination while it stands; otherwise its commander's.
            SquadMoveOrderService.ActiveOrder played = sim.getSquadMoveOrderService().activeOrder(squad.id);
            ObjectiveAssignment order = squad.assignedObjective;
            int x = played != null ? played.destinationX() : order == null ? -1 : order.targetCellX();
            int y = played != null ? played.destinationY() : order == null ? -1 : order.targetCellY();
            if (x < 0 || y < 0 || !serves(sim, index, x, y)) continue;
            if (memberInside(sim, place, squad)) return true;
        }
        return false;
    }

    private static boolean memberInside(BattleSimulation sim, SallyDoor.Holding place, Squad squad) {
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            if (sim.world().isAlive(member) && place.inside(sim.world().cellX(member), sim.world().cellY(member))) return true;
        }
        return false;
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
