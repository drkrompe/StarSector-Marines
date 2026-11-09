package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure.Aim;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure.Push;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The stir's pushes in the field ({@code ruins-pressure.md}, slice 3): each {@link Push} the stir sends is raised by
 * the occupant's {@link Force} at a wake cell in the push's layer, and its squads are then commanded here. A hunt
 * walks to the company's last noise, fighting on the way, and keeps after it; a way-back push walks to the stand of
 * the layer behind the company, the cell the company held to take that layer, and holds it until killed.
 *
 * <p>The Rav are the exception: their push surges a bore ({@code RuinsSleepers}), and the swarm director commands what
 * comes out, so their force returns no squads to command.
 */
public final class RuinsPushes {

    /** The issuer the pushes' squads are commanded under. */
    public static final String ISSUER = "ruins-pressure";
    /** Ticks between orders (half a second). */
    static final int PULSE_TICKS = 15;
    /** A hunt's goal that moved less than this many cells is not re-issued. */
    static final int REISSUE_CELLS = 3;
    /** How near its stand a way-back push counts as there and holds, cells. */
    static final int HOLD_RADIUS = 4;

    /** Whose voice the push's cues speak in: the occupant's. */
    public enum Voice { SECURITY, CREW, SLEEPERS }

    /** How an occupant raises a push. */
    public interface Force {
        /**
         * Raises {@code push} near {@code (cellX, cellY)}.
         *
         * @return the squads raised for this layer to command; empty when the occupant commands them itself or could
         *         not raise any
         */
        List<Integer> raise(BattleSimulation sim, Push push, int cellX, int cellY);
    }

    /** What a push's squad is told: walk there fighting, or hold the area around it. */
    enum Kind { ATTACK_MOVE, DEFEND }

    /** One order for a push's squad. */
    record Order(Kind kind, int cellX, int cellY) { }

    /** One squad of a push in the field, with the stand it holds when its push is a way-back push. */
    record Fielded(Push push, int squadId, int holdX, int holdY) { }

    private final Voice voice;
    private final Force force;
    private final RuinsDelve delve;
    private final List<Fielded> fielded = new ArrayList<>();
    private final List<Order> issued = new ArrayList<>();

    public RuinsPushes(Voice voice, Force force, RuinsDelve delve) {
        if (voice == null || force == null || delve == null) throw new IllegalArgumentException("A push needs its side");
        this.voice = voice;
        this.force = force;
        this.delve = delve;
    }

    public Voice voice() { return voice; }

    /** The squads raised so far, oldest first. */
    List<Fielded> fielded() { return Collections.unmodifiableList(fielded); }

    /** The ids of the squads raised so far, oldest first: for probes and look shots. */
    public List<Integer> squads() {
        List<Integer> out = new ArrayList<>();
        for (Fielded push : fielded) out.add(push.squadId());
        return out;
    }

    /**
     * Raises one push: at the wake cell of its layer farthest from the company, so it comes up out of sight, and for a
     * way-back push with the stand of that layer to hold.
     */
    void field(BattleSimulation sim, Push push, float companyX, float companyY) {
        RuinsDelve.Layer layer = layer(push.layer());
        if (layer == null) return;
        NavigationGrid grid = sim.getGrid();
        Standable standable = (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y);
        // The layer's wake point when it has one standing; the layer's open ground otherwise.
        RuinsWorks works = sim.getRuinsWorks();
        RuinsWorks.WakePoint point = works != null ? works.wakePoint(push.layer()) : null;
        int[] wake = point != null && !works.wakeCollapsed(push.layer()) && standable.at(point.x(), point.y())
                ? new int[]{point.x(), point.y()} : null;
        if (wake == null) wake = wakeCell(candidates(layer), companyX, companyY, standable);
        if (wake == null) return;
        int[] hold = stand(push.layer());
        for (int squad : force.raise(sim, push, wake[0], wake[1])) {
            sim.claimSquadCommand(squad, CommandAuthority.MISSION_COMMAND, ISSUER, reason(push));
            fielded.add(new Fielded(push, squad, hold[0], hold[1]));
            issued.add(null);
        }
    }

    /** Every step: on the pulse, each push's squad still standing is told where to go. */
    void tick(BattleSimulation sim, int noiseX, int noiseY) {
        if (fielded.isEmpty() || sim.getSimTickIndex() % PULSE_TICKS != 0) return;
        for (int i = 0; i < fielded.size(); i++) {
            Fielded push = fielded.get(i);
            if (sim.squadMemberCount(push.squadId()) <= 0) continue;
            long lead = sim.squadMemberAt(push.squadId(), 0);
            Order order = order(push.push().aim(), sim.world().x(lead), sim.world().y(lead), push.holdX(),
                    push.holdY(), noiseX, noiseY);
            if (!changed(issued.get(i), order)) continue;
            issued.set(i, order);
            ObjectiveAssignment assignment = order.kind() == Kind.DEFEND
                    ? ObjectiveAssignment.defendArea(push.squadId(), order.cellX(), order.cellY(), HOLD_RADIUS)
                    : ObjectiveAssignment.attackMove(push.squadId(), order.cellX(), order.cellY());
            sim.assignSquadCommand(assignment, CommandAuthority.MISSION_COMMAND, ISSUER, reason(push.push()));
        }
    }

    /**
     * A push's order from where its lead stands: a hunt walks at the company's last noise; a way-back push walks to
     * its stand and, once within {@link #HOLD_RADIUS}, holds it.
     */
    static Order order(Aim aim, float x, float y, int holdX, int holdY, int noiseX, int noiseY) {
        if (aim == Aim.HUNT) return new Order(Kind.ATTACK_MOVE, noiseX, noiseY);
        boolean there = Math.max(Math.abs(x - (holdX + 0.5f)), Math.abs(y - (holdY + 0.5f))) <= HOLD_RADIUS;
        return new Order(there ? Kind.DEFEND : Kind.ATTACK_MOVE, holdX, holdY);
    }

    /** Whether an order differs enough from the last to be issued: a new kind, or a goal moved some cells. */
    static boolean changed(Order last, Order next) {
        if (last == null || last.kind() != next.kind()) return true;
        return Math.max(Math.abs(last.cellX() - next.cellX()), Math.abs(last.cellY() - next.cellY())) >= REISSUE_CELLS;
    }

    /** Whether a cell can be stood on. */
    interface Standable {
        boolean at(int x, int y);
    }

    /**
     * Where a push comes up: of the candidates that can be stood on, the one farthest from the company's middle, the
     * first on a tie. Null when none can.
     */
    static int[] wakeCell(List<int[]> candidates, float companyX, float companyY, Standable standable) {
        int[] best = null;
        float far = -1f;
        for (int[] cell : candidates) {
            if (!standable.at(cell[0], cell[1])) continue;
            float dx = cell[0] + 0.5f - companyX, dy = cell[1] + 0.5f - companyY;
            float d = dx * dx + dy * dy;
            if (d > far) {
                far = d;
                best = cell;
            }
        }
        return best;
    }

    /**
     * Where a push may come up in a layer with no wake point standing: the open ground of its ways inside the layer's
     * band, its stand, and its middle.
     */
    List<int[]> candidates(RuinsDelve.Layer layer) {
        List<int[]> out = new ArrayList<>();
        for (RuinsDelve.Way way : layer.openWays()) {
            for (RuinsDelve.Cell end : List.of(way.near(), way.far())) {
                if (layer.ground().contains(end.x(), end.y())) out.add(new int[]{end.x(), end.y()});
            }
        }
        int[] stand = stand(layer.index());
        if (layer.ground().contains(stand[0], stand[1])) out.add(stand);
        out.add(new int[]{layer.ground().centreX(), layer.ground().centreY()});
        return out;
    }

    /** A layer's stand: its junction's, where the company holds to take the layer; its middle without one. */
    int[] stand(int index) {
        for (RuinsDelve.Junction junction : delve.junctions()) {
            if (junction.layer() == index) return new int[]{junction.stand().x(), junction.stand().y()};
        }
        RuinsDelve.Layer layer = layer(index);
        return layer == null ? new int[]{0, 0} : new int[]{layer.ground().centreX(), layer.ground().centreY()};
    }

    private RuinsDelve.Layer layer(int index) {
        for (RuinsDelve.Layer layer : delve.layers()) if (layer.index() == index) return layer;
        return null;
    }

    private static String reason(Push push) {
        return push.aim() == Aim.HUNT ? "a stir push hunts the company's noise"
                : "a stir push holds the way back at layer " + (push.layer() + 1);
    }
}
