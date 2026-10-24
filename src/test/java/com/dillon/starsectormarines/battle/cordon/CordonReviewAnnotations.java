package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveResult;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.objective.optional.OptionalObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a Cordon frame names ({@code cordon-nouns.md}): each redoubt by number with its state (and, on the
 * Road, whether it stands beside the gap), the logistics base, the town's compound, the landing field, every
 * bore, the mission layout's pieces (picket lines, the switch line, the set pieces) and the rolled
 * objectives, each with where it stands and, while it runs, where it wants the company. Never Conquest's
 * keep, barracks or armory: the generic compound words would say the wrong battle.
 *
 * <p>Read off the battle as it was built and as it stands, so the static mapgen frames and the played
 * evidence frames draw the same words; it lives in the Cordon's own package to read the beats' layout.
 */
public final class CordonReviewAnnotations {

    /** Cells a bore's box keeps round its mouth. */
    private static final int BORE_HALF = 3;

    private CordonReviewAnnotations() { }

    public static ReviewAnnotations forBattle(BattleSimulation sim) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        add(marks, sim);
        return marks.build();
    }

    /** Adds the Cordon's marks to {@code marks}; nothing when the battle is not a Cordon. */
    public static void add(ReviewAnnotations.Builder marks, BattleSimulation sim) {
        CordonSequence cordon = sim.getCordon();
        if (cordon == null) return;
        CordonBeats beats = cordon.beats();
        // Routes first, so the places and their words draw over them.
        List<int[]> corridor = cordon.convoys() != null ? cordon.convoys().corridor() : beats != null ? beats.corridor() : null;
        if (corridor != null && !corridor.isEmpty()) {
            List<ReviewAnnotation.Point> points = new ArrayList<>();
            for (int[] cell : corridor) points.add(new ReviewAnnotation.Point(cell[0] + 0.5f, cell[1] + 0.5f));
            String detour = cordon.convoys() != null && cordon.convoys().detoured() ? ", detoured" : "";
            marks.polyline(points, String.format(Locale.ROOT, "corridor, %.0f%% on road%s",
                    100f * CordonCorridor.pavedShare(sim.getTopology(), corridor), detour), ReviewStyle.ROUTE);
        }
        if (beats != null) {
            addLayout(marks, beats, objectivesByPiece(beats));
            addPrecincts(marks, beats);
        }
        for (int i = 0; i < cordon.redoubts().size(); i++) {
            CordonRedoubt r = cordon.redoubts().get(i);
            String gap = cordon.gap().contains(i) ? " · gap" : "";
            marks.box(r.left(), Math.min(r.top(), r.bottom()), r.right(), Math.max(r.top(), r.bottom()),
                    "REDOUBT " + r.number() + " · " + stateWord(r.state()) + gap, redoubtStyle(r.state()));
        }
        CordonLogistics base = cordon.logistics();
        if (base != null) {
            marks.box(base.doorX() - 2, base.doorY() - 2, base.doorX() + 2, base.doorY() + 2, "machine door", ReviewStyle.ROUTE);
            for (int[] slot : base.slots()) marks.box(slot[0] - 1, slot[1] - 1, slot[0] + 1, slot[1] + 1, "staging", ReviewStyle.NOTE);
        }
        for (SwarmBore bore : sim.swarmBores().bores()) {
            String name = "BORE " + bore.number() + " (" + bore.tier().name().toLowerCase(Locale.ROOT) + ")";
            marks.box(bore.x() - BORE_HALF, bore.y() - BORE_HALF, bore.x() + BORE_HALF, bore.y() + BORE_HALF,
                    bore.collapsed() ? name + " · down" : name, bore.collapsed() ? ReviewStyle.NOTE : ReviewStyle.KEEP);
        }
        addLandingField(marks, sim);
        if (beats != null) addCalls(marks, sim, beats);
    }

    /** What the frame says of a redoubt's state. */
    static String stateWord(CordonRedoubt.State state) {
        return switch (state) {
            case HOLDING -> "holding";
            case CALLING -> "calling";
            case RELIEVED -> "relieved";
            case FALLEN -> "fallen";
            case GIVEN_UP -> "given up";
        };
    }

    private static ReviewStyle redoubtStyle(CordonRedoubt.State state) {
        return switch (state) {
            case HOLDING, RELIEVED -> ReviewStyle.POSITION;
            case CALLING -> ReviewStyle.KEEP;
            case FALLEN, GIVEN_UP -> ReviewStyle.NOTE;
        };
    }

    /** Each rolled objective's result, under every laid piece it needs. */
    private static Map<String, ObjectiveResult> objectivesByPiece(CordonBeats beats) {
        Map<String, ObjectiveResult> out = new HashMap<>();
        if (beats.board() == null) return out;
        Map<String, OptionalObjective> pool = new HashMap<>();
        for (OptionalObjective o : CordonObjectives.pool(beats.variant(), null)) pool.put(o.key(), o);
        for (ObjectiveResult result : beats.board().results()) {
            OptionalObjective o = pool.get(result.key());
            if (o == null) continue;
            for (String piece : o.needs()) out.put(piece, result);
        }
        return out;
    }

    /** Every laid piece, boxed and named, with the objective it stages when one was rolled on it. */
    private static void addLayout(ReviewAnnotations.Builder marks, CordonBeats beats, Map<String, ObjectiveResult> objectives) {
        for (Laid laid : beats.laid().pieces()) {
            if (!laid.isLaid() || laid.cells().isEmpty()) continue;
            String state = laid.status() == Laid.Status.LAID ? "" : " (" + laid.status().name().toLowerCase(Locale.ROOT) + ")";
            ObjectiveResult objective = objectives.get(laid.name());
            String text = laid.name() + state + (objective == null ? "" : " · " + objective.title() + ", " + statusWord(objective.status()));
            if (laid.kind() == Laid.Kind.ROAD) {
                // A road's box would be the whole dog-leg: name it at the middle of what was carved.
                int[] mid = laid.cells().get(laid.cells().size() / 2);
                marks.label(mid[0], mid[1], text, objective != null ? ReviewStyle.OBJECTIVE : ReviewStyle.ROUTE);
                continue;
            }
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
            for (int[] c : laid.cells()) {
                x0 = Math.min(x0, c[0]);
                y0 = Math.min(y0, c[1]);
                x1 = Math.max(x1, c[0]);
                y1 = Math.max(y1, c[1]);
            }
            ReviewStyle style = objective != null ? ReviewStyle.OBJECTIVE
                    : laid.kind() == Laid.Kind.LINE ? ReviewStyle.ROUTE : ReviewStyle.NOTE;
            marks.box(x0, y0, x1, y1, text, style);
            for (int[] nest : laid.nests()) marks.box(nest[0] - 1, nest[1] - 1, nest[0] + 1, nest[1] + 1, "nest", ReviewStyle.OBJECTIVE);
        }
    }

    /** The plan's named places at their seeds: the colony, the rear hamlets, the town's compound, the base. */
    private static void addPrecincts(ReviewAnnotations.Builder marks, CordonBeats beats) {
        if (beats.plan() == null) return;
        for (Precinct p : beats.plan().precincts()) {
            String text = switch (p.name()) {
                case "colony" -> "the colony";
                case "installation" -> "installation";
                case "suburb" -> "suburb";
                case "rear-west", "rear-east" -> "rear hamlet";
                case CivilDefenceCompound.PLACE -> "THE TOWN'S COMPOUND";
                case CordonPlan.BASE_PLACE -> "LOGISTICS BASE";
                default -> null;
            };
            if (text != null) {
                boolean held = p.name().equals(CivilDefenceCompound.PLACE) || p.name().equals(CordonPlan.BASE_PLACE);
                marks.label(p.seedX(), p.seedY(), text, held ? ReviewStyle.LANDING : ReviewStyle.NOTE);
            }
        }
    }

    /**
     * The landing field, once: one box round every berth the company came down on, which on the Cordon
     * is one field in the rear; the beachhead compound where there are no berths to read.
     */
    private static void addLandingField(ReviewAnnotations.Builder marks, BattleSimulation sim) {
        List<LandingPad> pads = sim.getMarineLandingPads();
        if (pads != null && !pads.isEmpty()) {
            int left = Integer.MAX_VALUE, bottom = Integer.MAX_VALUE, right = Integer.MIN_VALUE, top = Integer.MIN_VALUE;
            for (LandingPad pad : pads) {
                left = Math.min(left, Math.min(pad.left(), pad.right()));
                right = Math.max(right, Math.max(pad.left(), pad.right()));
                bottom = Math.min(bottom, Math.min(pad.bottom(), pad.top()));
                top = Math.max(top, Math.max(pad.bottom(), pad.top()));
            }
            marks.box(left, bottom, right, top, "LANDING FIELD", ReviewStyle.LANDING);
            return;
        }
        CompoundService compounds = sim.getCompoundService();
        if (compounds == null) return;
        for (CompoundService.Record record : compounds.getRecords()) {
            TacticalNode node = record.node;
            if (node.kind != TacticalNode.Kind.BEACHHEAD) continue;
            marks.box(node.left, node.top, node.right, node.bottom, "LANDING FIELD", ReviewStyle.LANDING);
            return;
        }
    }

    /** Where each running objective wants the company now, named for the objective. */
    private static void addCalls(ReviewAnnotations.Builder marks, BattleSimulation sim, CordonBeats beats) {
        if (beats.board() == null) return;
        List<ObjectiveResult> results = beats.board().results();
        List<CordonBeat> running = beats.running();
        for (int i = 0; i < running.size() && i < results.size(); i++) {
            CordonBeat beat = running.get(i);
            if (!beat.active()) continue;
            int[] call = beat.call(sim);
            if (call == null) continue;
            marks.label(call[0], call[1], "objective: " + results.get(i).title() + " (" + call[2] + " squads)", ReviewStyle.OBJECTIVE);
        }
    }

    private static String statusWord(Status status) {
        return status.name().toLowerCase(Locale.ROOT);
    }
}
