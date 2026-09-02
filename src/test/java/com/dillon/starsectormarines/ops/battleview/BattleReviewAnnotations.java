package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The marks a reader of a mission frame wants without having to be told:
 * where the compounds are and who holds them, where the marines came ashore,
 * and which way the battle runs.
 *
 * <p>Derived entirely from the live simulation's own public surface. The
 * generator is deliberately not consulted — a second derivation of the same
 * geometry is a second answer, and the one worth drawing is the one the
 * battle was actually built on.
 */
public final class BattleReviewAnnotations {

    private BattleReviewAnnotations() { }

    /** Nothing at all for a null simulation, so a caller may be careless. */
    public static ReviewAnnotations forBattle(BattleSimulation simulation) {
        if (simulation == null) return ReviewAnnotations.NONE;
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        Ends ends = addCompounds(marks, simulation);
        // The landing place when the map has one, and the berths themselves
        // when it does not. A beachhead that is a compound is boxed by the
        // compound pass above, so drawing it again from its pads would put two
        // labels on the same ground.
        float[] beachhead = ends.beachhead() != null
                ? centre(ends.beachhead())
                : addLandingZones(marks, simulation);
        if (ends.keep() != null && beachhead != null) {
            marks.arrow(beachhead[0], beachhead[1],
                    ends.keep().anchorX + 0.5f, ends.keep().anchorY + 0.5f,
                    "approach", ReviewStyle.APPROACH);
        }
        return marks.build();
    }

    /** The two ends of the approach, whichever of them the battle has. */
    private record Ends(TacticalNode keep, TacticalNode beachhead) { }

    /**
     * One box per compound, labelled with what it is and who holds it.
     *
     * <p>The beachhead is one of them. It is the marines' own compound — held
     * from tick zero and losable like any other — so it is drawn by kind and
     * state exactly as the keep and the supply hubs are, in the landing style
     * rather than the objective one so a reader can tell at a glance which end
     * of the arrow is which.
     *
     * @return the keep and the beachhead, either of which may be null
     */
    private static Ends addCompounds(ReviewAnnotations.Builder marks,
                                     BattleSimulation simulation) {
        CompoundService compounds = simulation.getCompoundService();
        if (compounds == null) return new Ends(null, null);
        TacticalNode keep = null;
        TacticalNode beachhead = null;
        for (CompoundService.Record record : compounds.getRecords()) {
            TacticalNode node = record.node;
            boolean isKeep = node.kind == TacticalNode.Kind.COMMAND_POST;
            boolean isBeachhead = node.kind == TacticalNode.Kind.BEACHHEAD;
            if (isKeep && keep == null) keep = node;
            if (isBeachhead && beachhead == null) beachhead = node;
            marks.box(node.left, node.top, node.right, node.bottom,
                    placeName(node.kind) + " · " + holder(record.state),
                    isKeep ? ReviewStyle.KEEP
                            : isBeachhead ? ReviewStyle.LANDING : ReviewStyle.OBJECTIVE);
        }
        return new Ends(keep, beachhead);
    }

    /** The middle of a node's footprint, in cell coordinates. */
    private static float[] centre(TacticalNode node) {
        return new float[]{
                (node.left + node.right + 1) / 2f,
                (node.top + node.bottom + 1) / 2f};
    }

    /**
     * One box per patch of ground the marines landed on.
     *
     * <p>Not one per berth. A paired arrival puts two five-cell berths a
     * couple of cells apart and then cycles a handful of areas across every
     * shuttle in the manifest, so a berth appears in several arrival slots and
     * its neighbour is close enough that both words land on the same pixels.
     * Berths that touch are one landing area to a reader and are drawn as one,
     * which is also what the map authored them as.
     *
     * @return the centre of the landing ground, or null where there is none
     */
    private static float[] addLandingZones(ReviewAnnotations.Builder marks,
                                           BattleSimulation simulation) {
        List<int[]> grounds = landingGrounds(simulation.getMarineLandingPads());
        if (grounds.isEmpty()) return null;
        float sumX = 0f;
        float sumY = 0f;
        for (int[] ground : grounds) {
            marks.box(ground[0], ground[1], ground[2], ground[3],
                    "LZ", ReviewStyle.LANDING);
            sumX += (ground[0] + ground[2] + 1) / 2f;
            sumY += (ground[1] + ground[3] + 1) / 2f;
        }
        return new float[]{sumX / grounds.size(), sumY / grounds.size()};
    }

    /**
     * Cells of open ground either side of a berth that still read as the same
     * place. An authored Conquest area sets its two five-cell berths four
     * cells apart from centre to centre, leaving three cells between their
     * footprints, so this has to be wide enough to close that.
     */
    private static final int SAME_GROUND = 4;

    /**
     * The pads' footprints, deduplicated and then merged while any two of them
     * are within {@link #SAME_GROUND} cells of each other. Each entry is an
     * inclusive {@code left, bottom, right, top} rect.
     */
    private static List<int[]> landingGrounds(List<LandingPad> pads) {
        if (pads == null || pads.isEmpty()) return List.of();
        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> grounds = new ArrayList<>(pads.size());
        for (LandingPad pad : pads) {
            if (!seen.add(((long) pad.centerX << 32) ^ (pad.centerY & 0xffffffffL))) continue;
            grounds.add(new int[]{pad.left(), pad.bottom(), pad.right(), pad.top()});
        }
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int i = 0; i < grounds.size() && !merged; i++) {
                for (int j = i + 1; j < grounds.size() && !merged; j++) {
                    if (!adjacent(grounds.get(i), grounds.get(j))) continue;
                    grounds.set(i, union(grounds.get(i), grounds.remove(j)));
                    merged = true;
                }
            }
        }
        return grounds;
    }

    private static boolean adjacent(int[] a, int[] b) {
        return a[0] - SAME_GROUND <= b[2] && b[0] - SAME_GROUND <= a[2]
                && a[1] - SAME_GROUND <= b[3] && b[1] - SAME_GROUND <= a[3];
    }

    private static int[] union(int[] a, int[] b) {
        return new int[]{
                Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                Math.max(a[2], b[2]), Math.max(a[3], b[3])};
    }

    /** What a reader calls the place, rather than what the graph calls the node. */
    private static String placeName(TacticalNode.Kind kind) {
        return switch (kind) {
            case COMMAND_POST -> "KEEP";
            case BARRACKS -> "BARRACKS";
            case ARMORY -> "ARMORY";
            case BEACHHEAD -> "LZ";
            default -> kind.name();
        };
    }

    private static String holder(CompoundService.CompoundState state) {
        if (state == null) return "unknown";
        return switch (state) {
            case DEFENDER_HELD -> "held";
            case CONTESTED -> "contested";
            case MARINE_HELD -> "taken";
        };
    }
}
