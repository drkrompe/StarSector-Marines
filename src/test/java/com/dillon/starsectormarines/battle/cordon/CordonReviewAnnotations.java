package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.allied.AlliedRequest;
import com.dillon.starsectormarines.battle.command.allied.AlliedTool;
import com.dillon.starsectormarines.battle.command.allied.AlliedToolShed;
import com.dillon.starsectormarines.battle.command.allied.GunStock;
import com.dillon.starsectormarines.battle.command.allied.SupplyDelivery;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveResult;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.objective.optional.OptionalObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
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

    /**
     * The town's gate, truck yard and staging area alone, for a close frame of them: the yard and the staging area
     * boxed, where a truck stops, and the gate's open width as the wall left it.
     */
    public static ReviewAnnotations townYard(BattleSimulation sim) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        CordonSequence cordon = sim.getCordon();
        if (cordon == null) return marks.build();
        CordonStagingArea staging = cordon.staging();
        if (staging != null && !staging.isEmpty()) {
            int l = Integer.MAX_VALUE, t = Integer.MAX_VALUE, r = Integer.MIN_VALUE, b = Integer.MIN_VALUE;
            for (int[] c : staging.cells()) {
                l = Math.min(l, c[0]);
                t = Math.min(t, c[1]);
                r = Math.max(r, c[0]);
                b = Math.max(b, c[1]);
            }
            marks.box(l, t, r, b, "STAGING AREA · " + staging.cells().size() + " cells", ReviewStyle.KEEP);
        }
        addYard(marks, sim, cordon.yard());
        addRooms(marks, sim, cordon.town());
        return marks.build();
    }

    /**
     * The sandbag lines outside the compound ({@link CordonTownSandbags}), each boxed with its number, its length and
     * the way it faces; a line's return is boxed with it and named as its L. The compound boxed for reference.
     */
    public static ReviewAnnotations townSandbags(BattleSimulation sim) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        CordonSequence cordon = sim.getCordon();
        if (cordon == null || cordon.town() == null) return marks.build();
        CordonTown town = cordon.town();
        marks.box(town.left(), town.top(), town.right(), town.bottom(), "COMPOUND", ReviewStyle.KEEP);
        for (String line : sandbagLines(sim)) System.out.println("[cordon-mapgen] sandbags " + line);
        List<Laid> bags = sandbags(cordon);
        int number = 0;
        for (int i = 0; i < bags.size(); i++) {
            List<int[]> cells = bags.get(i).cells();
            if (cells.size() <= CordonTownSandbags.RETURN) continue;
            number++;
            List<int[]> all = new ArrayList<>(cells);
            String text = "SANDBAGS " + number + " · " + cells.size() + " cells · faces " + facing(town, cells);
            if (i + 1 < bags.size() && bags.get(i + 1).cells().size() <= CordonTownSandbags.RETURN) {
                List<int[]> back = bags.get(i + 1).cells();
                all.addAll(back);
                text += " · L faces " + returnFacing(cells, back);
            }
            int[] box = bounds(all);
            marks.box(box[0], box[1], box[2], box[3], text, ReviewStyle.POSITION);
        }
        return marks.build();
    }

    /** Each laid sandbag line as "name: n cells from (x,y) to (x,y)", for the console. */
    static List<String> sandbagLines(BattleSimulation sim) {
        List<String> out = new ArrayList<>();
        CordonSequence cordon = sim.getCordon();
        if (cordon == null) return out;
        for (Laid laid : sandbags(cordon)) {
            List<int[]> c = laid.cells();
            out.add(String.format(Locale.ROOT, "%s: %d cells (%d,%d)-(%d,%d)", laid.name(), c.size(), c.get(0)[0],
                    c.get(0)[1], c.get(c.size() - 1)[0], c.get(c.size() - 1)[1]));
        }
        return out;
    }

    /** The middle cell of each laid sandbag line, returns left out, in order. */
    static List<int[]> sandbagCentres(BattleSimulation sim) {
        List<int[]> out = new ArrayList<>();
        CordonSequence cordon = sim.getCordon();
        if (cordon == null) return out;
        for (Laid laid : sandbags(cordon)) {
            if (laid.cells().size() > CordonTownSandbags.RETURN) out.add(laid.cells().get(laid.cells().size() / 2));
        }
        return out;
    }

    private static List<Laid> sandbags(CordonSequence cordon) {
        List<Laid> out = new ArrayList<>();
        for (Laid laid : cordon.beats().laid().pieces()) {
            if (laid.isLaid() && !laid.cells().isEmpty() && laid.name().startsWith(CordonTownSandbags.NAME)) out.add(laid);
        }
        return out;
    }

    /** A line across an approach faces away from the compound along the axis it does not run on. */
    private static String facing(CordonTown town, List<int[]> cells) {
        int[] b = bounds(cells);
        // The map's y grows north, the way the frames draw it.
        if (b[2] - b[0] >= b[3] - b[1]) return b[1] > town.centreY() ? "N" : "S";
        return b[0] < town.centreX() ? "W" : "E";
    }

    /** A return faces out past the line's outer end, the way from the line's middle to it. */
    private static String returnFacing(List<int[]> line, List<int[]> back) {
        int[] l = bounds(line), r = bounds(back);
        if (l[2] - l[0] >= l[3] - l[1]) return r[0] * 2 > l[0] + l[2] ? "E" : "W";
        return r[1] * 2 > l[1] + l[3] ? "N" : "S";
    }

    private static int[] bounds(List<int[]> cells) {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int[] c : cells) {
            b[0] = Math.min(b[0], c[0]);
            b[1] = Math.min(b[1], c[1]);
            b[2] = Math.max(b[2], c[0]);
            b[3] = Math.max(b[3], c[1]);
        }
        return b;
    }

    /** The rooms the trapped wait in, each with its count, and each of the town's teams where it stands. */
    private static void addRooms(ReviewAnnotations.Builder marks, BattleSimulation sim, CordonTown town) {
        if (town == null) return;
        int[] waiting = town.waiting();
        for (int i = 0; i < town.rooms().size(); i++) {
            int l = Integer.MAX_VALUE, t = Integer.MAX_VALUE, r = Integer.MIN_VALUE, b = Integer.MIN_VALUE;
            for (int[] c : town.rooms().get(i)) {
                l = Math.min(l, c[0]);
                t = Math.min(t, c[1]);
                r = Math.max(r, c[0]);
                b = Math.max(b, c[1]);
            }
            int people = i + 1 < waiting.length ? waiting[i + 1] : 0;
            marks.box(l, t, r, b, "ROOM " + (i + 1) + " · " + people + " townspeople", ReviewStyle.KEEP);
        }
        for (int k = 0; k < town.teams().size(); k++) {
            int squad = town.teams().get(k);
            int l = Integer.MAX_VALUE, t = Integer.MAX_VALUE, r = Integer.MIN_VALUE, b = Integer.MIN_VALUE, n = 0;
            for (int i = 0, m = sim.squadMemberCount(squad); i < m; i++) {
                long id = sim.squadMemberAt(squad, i);
                if (id == 0L || !sim.world().isAlive(id) || !sim.world().hasPosition(id)) continue;
                int x = sim.world().cellX(id), y = sim.world().cellY(id);
                l = Math.min(l, x);
                t = Math.min(t, y);
                r = Math.max(r, x);
                b = Math.max(b, y);
                n++;
            }
            if (n == 0) continue;
            boolean militia = k < 2;
            marks.box(l, t, r, b, (militia ? "MILITIA " + (k + 1) + " · wall" : "GUARD " + (k - 1) + " · inside"),
                    ReviewStyle.ALLY);
        }
    }

    /** The truck yard boxed, where a truck stops to load, and the gate's open run along the wall's line. */
    private static void addYard(ReviewAnnotations.Builder marks, BattleSimulation sim, CordonGateYard yard) {
        if (yard == null) return;
        int l = Integer.MAX_VALUE, t = Integer.MAX_VALUE, r = Integer.MIN_VALUE, b = Integer.MIN_VALUE;
        for (int[] c : yard.cells()) {
            l = Math.min(l, c[0]);
            t = Math.min(t, c[1]);
            r = Math.max(r, c[0]);
            b = Math.max(b, c[1]);
        }
        marks.box(l, t, r, b, "TRUCK YARD", ReviewStyle.OBJECTIVE);
        marks.box(yard.stopX() - 1, yard.stopY() - 1, yard.stopX() + 1, yard.stopY() + 1, "truck stops to load",
                ReviewStyle.NOTE);
        // The gate as the wall left it: its open run along the wall's line.
        int lx = Math.abs(yard.inwardY()), ly = Math.abs(yard.inwardX());
        int lo = 0, hi = 0;
        while (sim.getGrid().inBounds(yard.gateX() - lx * (lo + 1), yard.gateY() - ly * (lo + 1))
                && sim.getGrid().isWalkable(yard.gateX() - lx * (lo + 1), yard.gateY() - ly * (lo + 1)) && lo < 20) lo++;
        while (sim.getGrid().inBounds(yard.gateX() + lx * (hi + 1), yard.gateY() + ly * (hi + 1))
                && sim.getGrid().isWalkable(yard.gateX() + lx * (hi + 1), yard.gateY() + ly * (hi + 1)) && hi < 20) hi++;
        marks.box(Math.min(yard.gateX() - lx * lo, yard.gateX() + lx * hi),
                Math.min(yard.gateY() - ly * lo, yard.gateY() + ly * hi),
                Math.max(yard.gateX() - lx * lo, yard.gateX() + lx * hi),
                Math.max(yard.gateY() - ly * lo, yard.gateY() + ly * hi),
                (yard.oneWay() ? "way in · " : "gate · ") + (lo + hi + 1) + " wide", ReviewStyle.ROUTE);
        // A one-way yard: its exit gate, and the truck's way through, in at the gate, past the stop and out.
        if (yard.oneWay()) {
            int[] exit = yard.exit();
            marks.box(exit[0] - lx * 4, exit[1] - ly * 4, exit[0] + lx * 4, exit[1] + ly * 4, "way out",
                    ReviewStyle.ROUTE);
            List<ReviewAnnotation.Point> path = new ArrayList<>();
            List<int[]> way = new ArrayList<>(yard.loop());
            way.addAll(yard.wayOut().subList(1, yard.wayOut().size()));
            for (int[] c : way) path.add(new ReviewAnnotation.Point(c[0] + 0.5f, c[1] + 0.5f));
            marks.polyline(path, "trucks: in, load, out", ReviewStyle.OBJECTIVE);
        }
    }

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
                    "REDOUBT " + r.number() + " · " + stateWord(r.state()) + gap + lineWords(sim, cordon, r),
                    redoubtStyle(r.state()));
        }
        CordonTown town = cordon.town();
        if (town != null) {
            int standing = 0;
            for (int squad : town.teams()) standing += sim.squadMemberCount(squad);
            marks.box(town.left(), town.top(), town.right(), town.bottom(), "AIN TAFRANT · " + standing + "/"
                    + town.garrison() + " defenders" + (town.calling(standing) ? " · calling" : ""),
                    town.holds() ? ReviewStyle.KEEP : ReviewStyle.NOTE);
            int l = Integer.MAX_VALUE, t = Integer.MAX_VALUE, r = Integer.MIN_VALUE, b = Integer.MIN_VALUE;
            for (int[] c : town.staging().cells()) {
                l = Math.min(l, c[0]);
                t = Math.min(t, c[1]);
                r = Math.max(r, c[0]);
                b = Math.max(b, c[1]);
            }
            if (!town.staging().isEmpty()) {
                marks.box(l, t, r, b, "STAGING AREA · " + town.state().name().toLowerCase(Locale.ROOT),
                        ReviewStyle.ROUTE);
            }
        }
        addYard(marks, sim, cordon.yard());
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
        addAllies(marks, sim, cordon);
        if (beats != null) addCalls(marks, sim, beats);
    }

    /** Seconds a request the base granted stays named over the base. */
    private static final int REQUEST_SHOWN_SECONDS = 20;

    /** What the allied line command says of a redoubt: its guns' rounds when they are short, and Uncovered. */
    private static String lineWords(BattleSimulation sim, CordonSequence cordon, CordonRedoubt r) {
        CordonLineCommand line = cordon.line();
        if (line == null || !r.manned()) return "";
        StringBuilder out = new StringBuilder();
        GunStock stock = line.stock(r);
        float share = stock.pooledShare(sim);
        if (stock.dry() > 0) out.append(" · ").append(stock.dry()).append(" guns dry, rationed");
        else if (share < 0.999f) out.append(String.format(Locale.ROOT, " · rounds %.0f%%", 100f * share));
        if (line.uncovered(r)) out.append(" · uncovered");
        if (line.helpComing(r)) out.append(" · help coming");
        return out.toString();
    }

    /**
     * The allied line command's tools on the field: each ammunition truck on the road with where it is bound and
     * whether a hull escorts it, each escort hull, and over the base every request it granted in the last
     * {@link #REQUEST_SHOWN_SECONDS} seconds.
     */
    private static void addAllies(ReviewAnnotations.Builder marks, BattleSimulation sim, CordonSequence cordon) {
        CordonLineCommand line = cordon.line();
        if (line == null) return;
        Map<Long, Long> escorts = cordon.armour() == null ? Map.of() : cordon.armour().roadEscorts();
        for (Map.Entry<SupplyDelivery, CordonRedoubt> e : line.deliveries().entrySet()) {
            int[] at = e.getKey().truckCell(sim);
            if (at == null) continue;
            CordonRedoubt to = e.getValue();
            boolean escorted = escorts.containsValue(e.getKey().truck());
            marks.box(at[0] - 2, at[1] - 2, at[0] + 2, at[1] + 2, "AMMO TRUCK to redoubt " + to.number()
                    + (escorted ? " · escorted" : " · no escort"), ReviewStyle.ALLY);
            marks.arrow(at[0], at[1], to.centreX(), to.centreY(), "", ReviewStyle.ALLY);
        }
        for (Map.Entry<Long, Long> e : escorts.entrySet()) {
            if (!sim.convoy().isVehicle(e.getKey()) || sim.convoy().structure(e.getKey()) <= 0f) continue;
            GroundBody body = sim.convoy().body(e.getKey());
            marks.box((int) body.x - 2, (int) body.y - 2, (int) body.x + 2, (int) body.y + 2,
                    "ESCORT " + sim.convoy().vehicleType(e.getKey()).name().toLowerCase(Locale.ROOT).replace('_', ' '),
                    ReviewStyle.ALLY);
        }
        AlliedToolShed shed = line.shed();
        CordonLogistics base = cordon.logistics();
        if (shed == null || base == null) return;
        for (AlliedToolShed.Dispatch d : shed.pending()) {
            AlliedRequest r = d.request();
            if (r.tool() == AlliedTool.AMMO_TRUCK) continue;
            marks.label(r.x(), r.y() - 4, r.tool().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    + " on its way (" + r.reason() + ")", ReviewStyle.ALLY);
        }
        int shown = 0;
        int since = sim.getSimTickIndex() - Math.round(REQUEST_SHOWN_SECONDS / BattleSimulation.TICK_DT);
        for (AlliedToolShed.Dispatch d : shed.granted()) {
            if (d.tick() < since) continue;
            AlliedRequest r = d.request();
            // Between the base and where it was asked for, so a frame on either end shows it.
            float mx = (base.doorX() + r.x()) / 2f, my = (base.doorY() + r.y()) / 2f + 3 * shown++;
            marks.label(mx, my, String.format(Locale.ROOT,
                    "REQUEST %s to %s (%s): sent at %d:%02d", r.tool().name().toLowerCase(Locale.ROOT).replace('_', ' '),
                    r.toPlace(), r.reason(), (int) (d.tick() * BattleSimulation.TICK_DT) / 60,
                    (int) (d.tick() * BattleSimulation.TICK_DT) % 60), ReviewStyle.ALLY);
        }
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
            for (String need : o.needs()) {
                for (String piece : OptionalObjective.alternatives(need)) out.put(piece, result);
            }
        }
        return out;
    }

    /** Every laid piece, boxed and named, with the objective it stages when one was rolled on it. */
    private static void addLayout(ReviewAnnotations.Builder marks, CordonBeats beats, Map<String, ObjectiveResult> objectives) {
        for (Laid laid : beats.laid().pieces()) {
            // The town's truck yard is marked with the town, not as a layout piece.
            if (!laid.isLaid() || laid.cells().isEmpty() || laid.kind() == Laid.Kind.YARD) continue;
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
