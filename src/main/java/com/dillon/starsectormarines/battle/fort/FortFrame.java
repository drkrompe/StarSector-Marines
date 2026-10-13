package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Placement;

import java.util.ArrayList;
import java.util.List;

/**
 * Where one fort stands: the map position and cardinal turn of its authored local frame. The works are authored once
 * in a local frame ({@code BatteryFort}); this maps that frame onto the map exactly as the generator did, so
 * everything drawn from the layout (a building's roof, a gate's leaves) lands on the cells that were stamped.
 *
 * <p>Pure geometry: it holds no simulation state and cannot disagree with the generator, because it asks the
 * generator's own {@link Placement} for every answer.
 */
public final class FortFrame {

    private final FortPlan plan;
    private final Placement placement;
    private final int quarterTurns;

    public FortFrame(FortPlan plan, int originX, int originY, int quarterTurns) {
        this.plan = plan;
        this.placement = plan.layout().at(originX, originY, quarterTurns);
        this.quarterTurns = quarterTurns;
    }

    /** The authored plan this frame places. */
    public FortPlan plan() {
        return plan;
    }

    public int quarterTurns() {
        return quarterTurns;
    }

    /** The world cell of a local cell. */
    public int[] cell(int localX, int localY) {
        return placement.cell(localX, localY);
    }

    /**
     * The world cells covered by the local rectangle ({@code x}, {@code y}, {@code w}, {@code h}), as the inclusive
     * bounds {@code {minX, minY, maxX, maxY}}. A cardinal turn keeps a rectangle a rectangle.
     */
    public int[] bounds(int x, int y, int w, int h) {
        int[] a = cell(x, y);
        int[] b = cell(x + w - 1, y + h - 1);
        return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])};
    }

    /**
     * The direction on the map that local +x points, as a unit grid vector {@code {dx, dy}}. With {@link #downward()}
     * it says how a sprite authored in the local frame must be turned to stand on the map.
     */
    public int[] across() {
        int[] a = cell(0, 0), b = cell(1, 0);
        return new int[]{b[0] - a[0], b[1] - a[1]};
    }

    /** The direction on the map that local +y points, as a unit grid vector. */
    public int[] downward() {
        int[] a = cell(0, 0), b = cell(0, 1);
        return new int[]{b[0] - a[0], b[1] - a[1]};
    }

    /**
     * The world cells of the works that stand out from outside: the wall with its gatehouses and baffles, the gates and
     * the bastion nests, as packed {@code x, y} pairs. Seeing any of these is seeing the fort, whether or not its inner
     * buildings or its guns are in view. The outer belt is left out on purpose: the stage drops a belt cell wherever the
     * city already holds the ground, so a belt cell in view may be a city wall and not the fort.
     */
    public int[] exteriorCells() {
        List<int[]> cells = new ArrayList<>();
        for (int x = 0; x < plan.width(); x++) {
            for (int y = 0; y < plan.height(); y++) {
                if (plan.isWall(x, y)) cells.add(cell(x, y));
            }
        }
        for (int[] gate : plan.gateCells()) cells.add(cell(gate[0], gate[1]));
        for (int[] nest : plan.nests()) cells.add(cell(nest[0], nest[1]));
        int[] packed = new int[cells.size() * 2];
        for (int i = 0; i < cells.size(); i++) {
            packed[i * 2] = cells.get(i)[0];
            packed[i * 2 + 1] = cells.get(i)[1];
        }
        return packed;
    }
}
