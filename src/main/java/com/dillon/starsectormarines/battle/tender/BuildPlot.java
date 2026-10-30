package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.world.model.TileManifest;

import java.util.ArrayList;
import java.util.List;

/**
 * The ground a staged build lays before its gun: a square plot about the site's centre, paved, rimmed with the tight
 * emplacement parapet, and for a fire base dressed with sandbags and ammunition (`tender-ship.md`, Staged builds).
 *
 * <p>A plot is authored facing north: {@code right} is east and {@code forward} is north, and {@link #pieces} turns it
 * to the facing the order chose. The stages split the build's progress; the gun is the last share and is the option's
 * own structure, stood up when the order completes.
 *
 * @param reach       the plot reaches this many cells from its centre each way (1 for 3x3, 2 for 5x5)
 * @param rimBodyHalf the body the rim hugs, as {@link TileManifest#emplacementTightParapetId}'s {@code bodyHalf}
 * @param rimGaps     ring cells left open, as {@code {right, forward}}
 * @param gunRight    the gun's cell, from the centre
 * @param gunForward  the gun's cell, from the centre
 * @param stores      sandbags and stacks inside the rim; {@code {f}} in an id is the facing's compass letter
 * @param padEnd      share of the build done when the pad is down
 * @param parapetEnd  share done when the rim is up
 * @param storesEnd   share done when the stores are in (equal to {@code parapetEnd} when there are none)
 */
public record BuildPlot(int reach, int rimBodyHalf, List<int[]> rimGaps, int gunRight, int gunForward,
                        List<Prop> stores, float padEnd, float parapetEnd, float storesEnd) {

    /** The stages laid before the gun, in order. */
    public enum Stage { PAD, PARAPET, STORES }

    /** One dressing prop: a doodad at a cell of the plot. */
    public record Prop(String doodad, int right, int forward) {}

    /** One thing a stage puts on the ground: a doodad id at a cell, or a bare paved cell when {@code doodad} is null. */
    public record Piece(int x, int y, String doodad) {}

    public BuildPlot {
        if (reach < 1) throw new IllegalArgumentException("a plot is wider than its gun");
        if (!(0f < padEnd && padEnd <= parapetEnd && parapetEnd <= storesEnd && storesEnd < 1f)) {
            throw new IllegalArgumentException("stages run in order and leave the gun a share");
        }
        rimGaps = List.copyOf(rimGaps);
        stores = List.copyOf(stores);
    }

    /** The 3x3 nest: pad 4 s, rim 6 s, gun 10 s of a 20 s build. The gun sits in the middle with the rim all round. */
    public static final BuildPlot NEST = new BuildPlot(1, 0, List.of(), 0, 0, List.of(), .2f, .5f, .5f);

    /**
     * The 5x5 fire base: pad 8 s, rim 10 s, stores 4 s, gun 12 s of a 34 s build. The gun stands a cell forward of
     * centre, the rim is open at the rear for its crew, sandbags flank the gun and two ammunition stacks sit behind.
     */
    public static final BuildPlot FIRE_BASE = new BuildPlot(2, 1,
            List.of(new int[]{-1, -2}, new int[]{0, -2}, new int[]{1, -2}), 0, 1,
            List.of(new Prop("doodad.sandbag-straight-{f}", -1, 0), new Prop("doodad.sandbag-straight-{f}", 1, 0),
                    new Prop("doodad.industrial-crate-stack", -1, -1), new Prop("doodad.industrial-crate-stack", 1, -1)),
            8f / 34f, 18f / 34f, 22f / 34f);

    /** The plot's side, in cells. */
    public int span() {
        return reach * 2 + 1;
    }

    /** The stages this plot has, in order: a plot without stores skips them. */
    public List<Stage> stages() {
        return stores.isEmpty() ? List.of(Stage.PAD, Stage.PARAPET) : List.of(Stage.PAD, Stage.PARAPET, Stage.STORES);
    }

    /** The share of the build done when {@code stage} is complete. */
    public float end(Stage stage) {
        return switch (stage) {
            case PAD -> padEnd;
            case PARAPET -> parapetEnd;
            case STORES -> storesEnd;
        };
    }

    /** The gun's cell for a plot centred on {@code (cx, cy)} facing {@code (fx, fy)}. */
    public int[] gun(int cx, int cy, int fx, int fy) {
        return turn(cx, cy, fx, fy, gunRight, gunForward);
    }

    /** What {@code stage} lays on a plot centred on {@code (cx, cy)} facing the cardinal {@code (fx, fy)}. */
    public List<Piece> pieces(Stage stage, int cx, int cy, int fx, int fy) {
        List<Piece> out = new ArrayList<>();
        switch (stage) {
            case PAD -> {
                for (int dx = -reach; dx <= reach; dx++) {
                    for (int dy = -reach; dy <= reach; dy++) out.add(new Piece(cx + dx, cy + dy, null));
                }
            }
            case PARAPET -> {
                for (int r = -reach; r <= reach; r++) {
                    for (int f = -reach; f <= reach; f++) {
                        if (Math.max(Math.abs(r), Math.abs(f)) != reach || gap(r, f)) continue;
                        int[] at = turn(cx, cy, fx, fy, r, f);
                        String id = TileManifest.emplacementTightParapetId(at[0] - cx, at[1] - cy, rimBodyHalf);
                        if (id != null) out.add(new Piece(at[0], at[1], id));
                    }
                }
            }
            case STORES -> {
                for (Prop prop : stores) {
                    int[] at = turn(cx, cy, fx, fy, prop.right(), prop.forward());
                    out.add(new Piece(at[0], at[1], prop.doodad().replace("{f}", compass(fx, fy))));
                }
            }
        }
        return out;
    }

    /**
     * The cardinal facing nearest the direction {@code (dx, dy)}: north when it is zero. Ties go to the north-south
     * axis, so a plot laid square on to its threat faces it.
     */
    public static int[] facing(float dx, float dy) {
        if (dx == 0f && dy == 0f) return new int[]{0, 1};
        return Math.abs(dx) > Math.abs(dy) ? new int[]{(int) Math.signum(dx), 0} : new int[]{0, (int) Math.signum(dy)};
    }

    private boolean gap(int right, int forward) {
        for (int[] g : rimGaps) if (g[0] == right && g[1] == forward) return true;
        return false;
    }

    /** A cell given from the centre as {@code right} and {@code forward} of a plot facing {@code (fx, fy)}. */
    private static int[] turn(int cx, int cy, int fx, int fy, int right, int forward) {
        // Right of a facing (fx, fy) is (fy, -fx): east of north, south of east.
        return new int[]{cx + right * fy + forward * fx, cy - right * fx + forward * fy};
    }

    private static String compass(int fx, int fy) {
        if (fy > 0) return "n";
        if (fy < 0) return "s";
        return fx > 0 ? "e" : "w";
    }
}
