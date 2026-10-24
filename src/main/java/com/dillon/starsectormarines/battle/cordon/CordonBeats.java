package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveBoard;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A Cordon battle's running beats ({@code cordon-nouns.md}, Optional objectives): what every beat may
 * read (the plan, the laid layout, the corridor, the sequence), the allied command that drives the
 * beats' own hulls (the ammo truck, the dozer), and the places the beats want the company. The
 * sequence advances it each tick; it advances the hulls, then the board.
 */
public final class CordonBeats {

    /** Who drives the beats' hulls, as the command log names it. */
    public static final String ISSUER = "cordon-beats";
    /** How far from where it was asked a hull is set down on ground its footprint fits. */
    static final int STAND_REACH = 12;

    private final CordonVariant variant;
    private final PrecinctPlan plan;
    private final LaidLayout laid;
    private final List<int[]> corridor;
    private final long battleSeed;
    private final CordonSequence sequence;
    private final AlliedCommand command = new AlliedCommand(ISSUER);
    private final List<CordonBeat> running = new ArrayList<>();
    private ObjectiveBoard board;

    CordonBeats(CordonVariant variant, PrecinctPlan plan, LaidLayout laid, List<int[]> corridor, long battleSeed,
                CordonSequence sequence) {
        this.variant = variant;
        this.plan = plan;
        this.laid = laid == null ? LaidLayout.EMPTY : laid;
        this.corridor = corridor;
        this.battleSeed = battleSeed;
        this.sequence = sequence;
    }

    public CordonVariant variant() { return variant; }
    PrecinctPlan plan() { return plan; }
    LaidLayout laid() { return laid; }
    /** The Road's corridor as found at setup, or {@code null}. */
    List<int[]> corridor() { return corridor; }
    long battleSeed() { return battleSeed; }
    CordonSequence sequence() { return sequence; }
    AlliedCommand command() { return command; }
    /** The rolled beats' runtimes, in roll order (the board's results' order). */
    List<CordonBeat> running() { return Collections.unmodifiableList(running); }
    /** The board the beats run on, or {@code null} before it is installed. */
    public ObjectiveBoard board() { return board; }

    void setBoard(ObjectiveBoard board) {
        this.board = board;
    }

    <T extends CordonBeat> T register(T beat) {
        running.add(beat);
        return beat;
    }

    /** One tick: the beats' hulls drive, then every running beat steps. */
    void advance(BattleSimulation sim, int tick) {
        command.advance(sim);
        for (CordonBeat beat : running) beat.watch(sim, tick);
        if (board != null) board.advance(tick);
    }

    /** Where the running beats want the company, {x, y, squads}, in roll order. */
    public List<int[]> calls(BattleSimulation sim) {
        List<int[]> out = new ArrayList<>();
        for (CordonBeat beat : running) {
            if (!beat.active()) continue;
            int[] call = beat.call(sim);
            if (call != null) out.add(call);
        }
        return out;
    }

    /** The running beats' own hulls that want an allied escort, in roll order. */
    public List<Long> escortHulls(BattleSimulation sim) {
        List<Long> out = new ArrayList<>();
        for (CordonBeat beat : running) {
            if (!beat.active()) continue;
            long hull = beat.escortHull(sim);
            if (hull != 0L) out.add(hull);
        }
        return out;
    }

    /** Whether an armed allied hull stood alongside this one at the armour's last look (the radio's Escorted). */
    boolean escorted(long hull) {
        return sequence.armour() != null && sequence.armour().escorted(hull);
    }

    /** The hull the allied armour sent to escort this one at its last look, or {@code 0} where none was free. */
    long escortOf(long hull) {
        return sequence.armour() == null ? 0L : sequence.armour().escortOf(hull);
    }

    /**
     * Whether the company need not walk with a beat's road hull: an armed allied hull is alongside it, or one is on its
     * way to it, which the hull waits for ({@code EscortPace}). Only a hull the armour had nothing free for asks.
     */
    boolean covered(long hull) {
        return escorted(hull) || escortOf(hull) != 0L;
    }

    /** Each running beat's status and what it is doing, one line each, for evidence. */
    public List<String> describe(BattleSimulation sim) {
        List<String> out = new ArrayList<>();
        for (CordonBeat beat : running) {
            String what = beat.describe(sim);
            out.add(beat.getClass().getSimpleName() + " " + beat.status().name().toLowerCase(Locale.ROOT)
                    + (what.isEmpty() ? "" : ": " + what));
        }
        return out;
    }

    /** The middle of a laid piece's cells (a site's or structure's two corners), or {@code null}. */
    int[] centre(String name) {
        Laid piece = laid.find(name);
        if (piece == null || !piece.isLaid() || piece.cells().isEmpty()) return null;
        long sx = 0, sy = 0;
        for (int[] c : piece.cells()) {
            sx += c[0];
            sy += c[1];
        }
        return new int[]{(int) (sx / piece.cells().size()), (int) (sy / piece.cells().size())};
    }

    /** The bounds of a laid piece's cells, {@code [x0, y0, x1, y1]}, or {@code null}. */
    int[] bounds(String name) {
        Laid piece = laid.find(name);
        if (piece == null || !piece.isLaid() || piece.cells().isEmpty()) return null;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (int[] c : piece.cells()) {
            x0 = Math.min(x0, c[0]);
            y0 = Math.min(y0, c[1]);
            x1 = Math.max(x1, c[0]);
            y1 = Math.max(y1, c[1]);
        }
        return new int[]{x0, y0, x1, y1};
    }

    /** The redoubt of this number, or {@code null} when it was not manned. */
    CordonRedoubt redoubt(int number) {
        for (CordonRedoubt r : sequence.redoubts()) if (r.number() == number) return r;
        return null;
    }

    /** Rows behind the line's mean row a cell stands; negative in front of it. */
    int behindLine(int y) {
        return Math.round((CordonFront.lineY(plan) - y) * CordonFront.frontward(plan));
    }

    /** The open cell nearest {@code (x, y)} {@code rows} behind it, toward the rear. */
    int[] rearOf(BattleSimulation sim, int x, int y, int rows) {
        int[] at = CordonGround.nearestWalkable(sim.getGrid(), x, y - CordonFront.frontward(plan) * rows);
        return at != null ? at : CordonGround.nearestWalkable(sim.getGrid(), x, y);
    }

    /** Sets an allied hull down on ground its footprint fits near {@code (x, y)}, under the beats' command; 0 when none fits. */
    long deploy(BattleSimulation sim, VehicleType type, int x, int y) {
        VehicleClearance mask = VehicleClearance.erode(sim.getGrid(), VehicleClearance.radiusForWidth(type.visualWidthCells));
        int[] at = VehicleRoutePlanner.snapToMask(mask, x, y, STAND_REACH);
        if (at == null) return 0L;
        float facing = CordonFront.frontward(plan) > 0 ? 0f : 180f;
        return command.deploy(sim, type, at[0], at[1], facing);
    }

    /** Whether a hull is still on the field. */
    static boolean alive(BattleSimulation sim, long hull) {
        return hull != 0L && sim.convoy().isVehicle(hull) && sim.convoy().structure(hull) > 0f;
    }
}
