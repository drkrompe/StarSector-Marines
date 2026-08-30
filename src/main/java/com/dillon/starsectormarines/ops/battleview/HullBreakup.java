package com.dillon.starsectormarines.ops.battleview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * How a burnt airframe comes apart: a V cut through the hull, torn along a
 * random walk, leaving three pieces that have shifted where they lie.
 *
 * <p>Pure geometry over a normalised hull — no sprite, no camera, no GL. The
 * hull is a {@link #GRID}x{@link #GRID} lattice addressed <b>across</b> (0 at
 * the port edge, 1 at starboard) and <b>along</b> (0 at the nose, 1 at the
 * tail), and every cell of it belongs to exactly one piece. A renderer maps
 * that lattice onto whatever sprite and scale it happens to be drawing at.
 *
 * <p><b>Why a V.</b> A straight cut reads as a sprite somebody has sliced,
 * because a straight line is the one thing that never happens to a structure
 * that has burst from the inside. The V is the shape a fuselage actually parts
 * in: the nose section comes off forward of where the tanks were, and what is
 * left splits down the spine. Both boundaries are then walked — a small
 * accumulating wander per column and per row — so the tear is ragged rather
 * than drawn, and ragged differently for every hull on the field.
 *
 * <p><b>The lattice is the tear.</b> The boundary follows cell edges rather
 * than cutting through them, which is exactly right here: torn metal is a
 * staircase of buckled panels, not a curve. It is also what makes this
 * drawable at all — a lattice boundary needs no per-pixel masking, so each
 * row's run of cells is an ordinary source rectangle out of a sprite the game
 * owns and nothing is allowed to edit.
 *
 * <p><b>Deterministic.</b> Everything comes from the seed, so a wreck looks the
 * same on every frame of its battle and the same again in a replay of it.
 * Nothing here is re-rolled per draw.
 */
public final class HullBreakup {

    /** Cells across and along the hull. Fine enough for a ragged edge, coarse enough that a piece is a handful of runs. */
    public static final int GRID = 28;

    /** Piece indices, in the order {@link #pieces()} returns them. */
    public static final int NOSE = 0;
    public static final int PORT_REAR = 1;
    public static final int STARBOARD_REAR = 2;

    /** Where the V apex sits along the hull — aft of the midpoint, roughly over the tanks. */
    private static final float APEX_ALONG = 0.60f;
    /** How much further forward the V legs reach by the time they meet the hull edge. */
    private static final float LEG_REACH = 0.24f;
    /** One step of either tear's random walk, in normalised hull units. */
    private static final float WALK_STEP = 0.030f;
    /** How far the V tear may wander from the ideal V before it is pulled back. */
    private static final float V_WANDER = 0.05f;
    /** How far the spine tear may wander off the centreline. */
    private static final float SPINE_WANDER = 0.07f;

    /**
     * How far each piece has shifted, as a fraction of hull length, and how far
     * it has turned.
     *
     * <p>Small on purpose. This is a hull that burst and settled where it
     * stood, not debris thrown across the apron — far enough that the tears
     * open and the wreck reads as three things, near enough that it still reads
     * as one aircraft.
     */
    private static final float[][] SLIDE = {
            //  across,  along (positive is toward the tail)
            {  0.000f, -0.030f },   // nose section, forward off the break
            { -0.028f,  0.020f },   // port rear, out and back
            {  0.028f,  0.020f },   // starboard rear, out and back
    };
    private static final float[] SPIN_DEGREES = { -3.0f, 4.0f, -5.0f };
    /** Per-hull variation on the authored slide and spin, so no two wrecks lie identically. */
    private static final float SLIDE_JITTER = 0.012f;
    private static final float SPIN_JITTER = 2.5f;

    /** One unbroken row of cells belonging to one piece — the unit a renderer draws. */
    public record Run(int row, int firstCol, int colCount) { }

    /** One piece of the hull: the cells it kept, where its centre is, and how far it has shifted. */
    public static final class Piece {
        private final List<Run> runs;
        private final float centreAcross;
        private final float centreAlong;
        private final float slideAcross;
        private final float slideAlong;
        private final float spinDegrees;

        private Piece(List<Run> runs, float centreAcross, float centreAlong,
                      float slideAcross, float slideAlong, float spinDegrees) {
            this.runs = Collections.unmodifiableList(runs);
            this.centreAcross = centreAcross;
            this.centreAlong = centreAlong;
            this.slideAcross = slideAcross;
            this.slideAlong = slideAlong;
            this.spinDegrees = spinDegrees;
        }

        /** The rows of cells this piece kept, nose end of the hull first. */
        public List<Run> runs() { return runs; }
        /** Centre of this piece's own cells, normalised across the hull. It turns about this point. */
        public float centreAcross() { return centreAcross; }
        /** Centre of this piece's own cells, normalised along the hull. */
        public float centreAlong() { return centreAlong; }
        /** How far the piece has shifted across the hull, as a fraction of hull <em>length</em>. */
        public float slideAcross() { return slideAcross; }
        /** How far the piece has shifted along the hull (positive toward the tail), as a fraction of hull length. */
        public float slideAlong() { return slideAlong; }
        /** How far the piece has turned where it lies. */
        public float spinDegrees() { return spinDegrees; }
    }

    private final List<Piece> pieces;

    private HullBreakup(List<Piece> pieces) {
        this.pieces = Collections.unmodifiableList(pieces);
    }

    /** The three pieces, nose first. Always three, and every one of them holds cells. */
    public List<Piece> pieces() { return pieces; }

    /**
     * Tears a hull apart. The same seed always gives the same wreck.
     *
     * <p>Seed it off something stable about the aircraft's place on the map
     * rather than off a clock, so a wreck is a fact about the battle and not
     * about when somebody happened to look at it.
     */
    public static HullBreakup of(long seed) {
        Random random = new Random(seed);
        float[] vTear = vTear(random);
        float[] spineTear = spineTear(random);

        List<List<Run>> runsByPiece = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) runsByPiece.add(new ArrayList<>());
        int[] cellCount = new int[3];
        float[] sumAcross = new float[3];
        float[] sumAlong = new float[3];

        for (int row = 0; row < GRID; row++) {
            int runStart = 0;
            int runPiece = pieceAt(0, row, vTear, spineTear);
            for (int col = 1; col <= GRID; col++) {
                int piece = col < GRID ? pieceAt(col, row, vTear, spineTear) : -1;
                if (piece == runPiece) continue;
                runsByPiece.get(runPiece).add(new Run(row, runStart, col - runStart));
                for (int c = runStart; c < col; c++) {
                    cellCount[runPiece]++;
                    sumAcross[runPiece] += (c + 0.5f) / GRID;
                    sumAlong[runPiece] += (row + 0.5f) / GRID;
                }
                runStart = col;
                runPiece = piece;
            }
        }

        List<Piece> pieces = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            int cells = Math.max(1, cellCount[i]);
            pieces.add(new Piece(runsByPiece.get(i),
                    sumAcross[i] / cells, sumAlong[i] / cells,
                    SLIDE[i][0] + jitter(random, SLIDE_JITTER),
                    SLIDE[i][1] + jitter(random, SLIDE_JITTER),
                    SPIN_DEGREES[i] + jitter(random, SPIN_JITTER)));
        }
        return new HullBreakup(pieces);
    }

    /**
     * Where the V tear crosses each column, as a distance along the hull.
     *
     * <p>The ideal V — reaching furthest aft down the centreline and
     * {@link #LEG_REACH} further forward by either edge, so the nose comes away
     * as a wedge and the tail is left to split — plus a walk that is pulled
     * back whenever it strays past {@link #V_WANDER}. Pulling it back rather
     * than letting it run is what keeps the shape a V at all: an unbounded walk
     * over twenty-eight columns wanders far enough to stop being one.
     *
     * <p>Pointing the wedge aft rather than forward is a judgement made by
     * looking at both: the other way round leaves a notch bitten out of the
     * nose and two rear pieces reaching up around it, which reads as damage to
     * the front of an intact aircraft rather than as a hull in three parts.
     */
    private static float[] vTear(Random random) {
        float[] tear = new float[GRID];
        float wander = 0f;
        for (int col = 0; col < GRID; col++) {
            float across = (col + 0.5f) / GRID;
            wander = clamp(wander + jitter(random, WALK_STEP), -V_WANDER, V_WANDER);
            tear[col] = APEX_ALONG - LEG_REACH * Math.abs(2f * across - 1f) + wander;
        }
        return tear;
    }

    /** Where the spine tear crosses each row, as a distance across the hull. */
    private static float[] spineTear(Random random) {
        float[] tear = new float[GRID];
        float wander = 0f;
        for (int row = 0; row < GRID; row++) {
            wander = clamp(wander + jitter(random, WALK_STEP), -SPINE_WANDER, SPINE_WANDER);
            tear[row] = 0.5f + wander;
        }
        return tear;
    }

    /** Which piece owns one cell: forward of the V it is the nose, aft of it whichever side of the spine it fell. */
    private static int pieceAt(int col, int row, float[] vTear, float[] spineTear) {
        float across = (col + 0.5f) / GRID;
        float along = (row + 0.5f) / GRID;
        if (along < vTear[col]) return NOSE;
        return across < spineTear[row] ? PORT_REAR : STARBOARD_REAR;
    }

    private static float jitter(Random random, float magnitude) {
        return (random.nextFloat() * 2f - 1f) * magnitude;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
