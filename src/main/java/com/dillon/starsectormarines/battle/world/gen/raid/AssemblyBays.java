package com.dillon.starsectormarines.battle.world.gen.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The final assembly bays inside the autoplant's assembly hall ({@link LightIndustryWorks#ASSEMBLY}): where light
 * craft are assembled from the sections the hall's sub-assembly lines and the shops send in, under cover. A band of
 * the hall's floor along one long wall, the wall nearer dispatch, holds the bays end to end, each a craft's length
 * along the hall and its width across it; the rest of the hall stays its sub-assembly lines.
 *
 * <ul>
 *   <li><b>Mating.</b> The aft section in its jig and the nose section brought up in front of it on the gantry, a
 *   gap still between them: a craft put together from what was built elsewhere.</li>
 *   <li><b>Fitting out.</b> The whole hull under its gantry, its fit-out fed from a work strip beside it.</li>
 *   <li><b>Finished.</b> A finished craft by the hall's end nearer dispatch, waiting to be rolled out.</li>
 * </ul>
 *
 * <p>The craft are vanilla civilian light hulls ({@link #MODELS}) from the game's own sprites at true scale, about
 * four hull pixels a cell, as a landed ship is ({@code landed-ships-true-scale.md}); one model a works. Vanilla light
 * industry makes domestic and luxury goods, not hulls: the autoplant is the owner's direction.
 */
public final class AssemblyBays {

    /** What a bay is doing to its craft. */
    public enum Stage { MATING, FITTING, FINISHED }

    /**
     * A craft the bays build: its sprite name in the {@code autoplant.<name>-<crop>} doodads, its width and length in
     * cells, and the length of its nose section and of its aft section (the aft two thirds).
     */
    public record Model(String name, int width, int length, int nose, int aft) {
        String hull() { return "autoplant." + name + "-hull"; }

        String noseSection() { return "autoplant." + name + "-nose"; }

        String aftSection() { return "autoplant." + name + "-half"; }

        /** A bay's length along the hall: mating needs the gap between its sections. */
        int bayLength(Stage stage) {
            return stage == Stage.MATING ? aft + JOIN_GAP + nose : length;
        }
    }

    /** The light civilian hulls the bays may build; matches the {@code autoplant-*.tileset.json} crops. */
    public static final List<Model> MODELS = List.of(
            new Model("aeroshuttle", 20, 16, 6, 11),
            new Model("mudskipper", 16, 21, 8, 13));

    /** Cells between a mating craft's aft section and its nose section. UNTUNED. */
    static final int JOIN_GAP = 4;
    /** Cells of aisle between bays, and between the band and the hall's walls. UNTUNED. */
    static final int AISLE = 2;
    /** Cells of work strip beside a hull, on the band's inner side, for its fit-out. */
    static final int STRIP = 2;

    /**
     * One piece of a craft set in a bay: its doodad, anchor, footprint after its turn, and quarter turns; the bay's
     * stage; and where a gantry spans the bay, as the cell along the hall its span stands at ({@code -1}: none). The
     * span runs across the hall, over the craft.
     */
    public record Piece(Stage stage, String art, int x, int y, int fx, int fy, int turns, int gantry) {
        /** Whether the craft lies along x: turned a quarter, its nose points along x. */
        boolean alongX() { return turns % 2 == 1; }
    }

    /** The bays as planned: the band of floor they clear ({@code {x0, y0, x1, y1}} inclusive) and their pieces. */
    public record Plan(int[] band, List<Piece> pieces) { }

    private AssemblyBays() { }

    /** The model a works builds, drawn from its seed. */
    static Model model(SplittableRandom rng) {
        return MODELS.get(rng.nextInt(MODELS.size()));
    }

    /** The stages of {@code n} bays from the hall's far end to its end nearer dispatch. */
    static List<Stage> stages(int n) {
        return switch (n) {
            case 0 -> List.of();
            case 1 -> List.of(Stage.FITTING);
            case 2 -> List.of(Stage.MATING, Stage.FITTING);
            default -> {
                List<Stage> out = new ArrayList<>();
                for (int k = 0; k < n - 2; k++) out.add(Stage.MATING);
                out.add(Stage.FITTING);
                out.add(Stage.FINISHED);
                yield out;
            }
        };
    }

    /**
     * Plans the bays in a hall whose floor is {@code (x0, y0)} to {@code (x1, y1)} inclusive. The band runs along the
     * hall's long axis against the long wall nearer {@code (towardX, towardY)} (dispatch), the craft lying along it
     * with their noses toward that point's end of the hall. As many bays as fit, at most {@code most}; null when the
     * hall cannot take one.
     */
    public static Plan plan(int x0, int y0, int x1, int y1, float towardX, float towardY, Model m, int most) {
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        boolean alongX = w >= h;
        int length = alongX ? w : h, depth = alongX ? h : w;
        int band = m.width() + STRIP;
        if (depth < band + 2 * AISLE) return null;
        // Which long wall, and which end, dispatch lies toward.
        boolean highSide = alongX ? towardY > (y0 + y1) / 2f : towardX > (x0 + x1) / 2f;
        boolean highEnd = alongX ? towardX > (x0 + x1) / 2f : towardY > (y0 + y1) / 2f;
        // How many bays fit end to end, with an aisle at either end and between them.
        int n = 0, used = 0;
        for (int k = Math.min(most, 8); k >= 1; k--) {
            used = AISLE;
            for (Stage s : stages(k)) used += m.bayLength(s) + AISLE;
            if (used <= length) {
                n = k;
                break;
            }
        }
        if (n == 0) return null;
        List<Stage> stages = stages(n);
        // The band across the hall: AISLE off the long wall dispatch is toward.
        int a0 = highSide ? (alongX ? y1 : x1) - AISLE - band + 1 : (alongX ? y0 : x0) + AISLE;
        int hullA = highSide ? a0 + STRIP : a0;
        List<Piece> pieces = new ArrayList<>();
        // Bays laid from the far end toward dispatch's end, the run centred down the hall; along-hall position
        // measured from that far end.
        int cursor = AISLE + (length - used) / 2;
        for (Stage stage : stages) {
            int len = m.bayLength(stage);
            // Nose toward dispatch's end: the aft end of a bay is its far-end side.
            int turns = alongX ? (highEnd ? 3 : 1) : (highEnd ? 0 : 2);
            if (stage == Stage.MATING) {
                // The gantry over the nose section it is bringing up to the aft section, the gap between them open.
                int joint = along(cursor + m.aft() + JOIN_GAP + m.nose() / 2 - 1, 2, highEnd, alongX ? x0 : y0,
                        length);
                pieces.add(piece(stage, m.aftSection(), m.aft(), cursor, hullA, m, alongX, highEnd, x0, y0, length,
                        turns, -1));
                pieces.add(piece(stage, m.noseSection(), m.nose(), cursor + m.aft() + JOIN_GAP, hullA, m, alongX,
                        highEnd, x0, y0, length, turns, joint));
            } else {
                int middle = along(cursor + m.length() / 2 - 1, 2, highEnd, alongX ? x0 : y0, length);
                pieces.add(piece(stage, m.hull(), m.length(), cursor, hullA, m, alongX, highEnd, x0, y0, length,
                        turns, stage == Stage.FITTING ? middle : -1));
            }
            cursor += len + AISLE;
        }
        int[] bandRect = alongX
                ? new int[]{x0, a0, x1, a0 + band - 1}
                : new int[]{a0, y0, a0 + band - 1, y1};
        return new Plan(bandRect, pieces);
    }

    /**
     * One piece {@code l} cells long starting {@code from} cells from the hall's far end, its width from across-hall
     * cell {@code a}.
     */
    private static Piece piece(Stage stage, String art, int l, int from, int a, Model m, boolean alongX,
                               boolean highEnd, int x0, int y0, int length, int turns, int gantry) {
        // Measured from the far end: the low end when dispatch is toward the high end, else the high end.
        int lo = highEnd ? from : length - from - l;
        if (alongX) return new Piece(stage, art, x0 + lo, a, l, m.width(), turns, gantry);
        return new Piece(stage, art, a, y0 + lo, m.width(), l, turns, gantry);
    }

    /** The low cell, along the hall, of a run {@code l} cells long starting {@code from} cells from the far end. */
    private static int along(int from, int l, boolean highEnd, int origin, int length) {
        return origin + (highEnd ? from : length - from - l);
    }

    /** Whether a cell of a piece's footprint is under the hull: inside the ellipse its footprint inscribes. */
    static boolean underHull(Piece p, int x, int y) {
        float rx = p.fx() / 2f, ry = p.fy() / 2f;
        float u = (x + 0.5f - p.x() - rx) / (rx * 0.92f), v = (y + 0.5f - p.y() - ry) / (ry * 0.92f);
        return u * u + v * v <= 1f;
    }

    /** What a fitting bay's work strip holds. */
    static List<String> feed() {
        return List.of("doodad.stores-cargo-loader", "machine-shop.electrical", "doodad.industrial-cable-reel",
                "doodad.industrial-crate-stack");
    }
}
