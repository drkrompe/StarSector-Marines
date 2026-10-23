package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.command.ConquestLaneChain;
import com.dillon.starsectormarines.battle.command.HeartLane;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.objective.CivilianEvacuationObjective;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPlacement;
import com.dillon.starsectormarines.battle.evacuation.CivilianRedoubt;
import com.dillon.starsectormarines.battle.evacuation.ColonyBreachSequence;
import com.dillon.starsectormarines.battle.evacuation.ColonyEruptionPlan;
import com.dillon.starsectormarines.battle.evacuation.ColonyLivingField;
import com.dillon.starsectormarines.battle.evacuation.HeartLanes;
import com.dillon.starsectormarines.battle.evacuation.HeartSequence;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.power.WalkingBarrage;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry;
import com.dillon.starsectormarines.battle.world.gen.precinct.LaneRoute;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

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
        if (simulation.getColonyBreach() != null) return forColonyBreach(simulation);
        if (simulation.getHeart() != null) return forHeart(simulation);
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        // Routes first, so the compound boxes and their words draw over them
        // rather than under: a lane runs through the places it joins, and the
        // reader is judging the places.
        addLaneRoutes(marks, simulation);
        addHeartSectors(marks, simulation);
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

    /**
     * A heart frame: where the company landed, every bore (each mother by number,
     * each satellite, and whether it is down), the reservoir yard the mothers
     * stand in, each allied lane or sector, and on The Burn the gun line with how
     * many guns still stand. Compounds are not drawn: the market town's buildings
     * are ruins, and Conquest's keep-and-barracks words would say the wrong battle.
     */
    private static ReviewAnnotations forHeart(BattleSimulation simulation) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        HeartSequence heart = simulation.getHeart();
        HeartLanes lanes = heart.lanes();
        if (lanes != null && lanes.companySector() != null) {
            addHeartSectors(marks, simulation);
        } else if (lanes != null) {
            int width = simulation.getGrid().getWidth(), height = simulation.getGrid().getHeight();
            List<HeartLane> grounds = lanes.grounds();
            // The allied lanes come first; a ground past them is the company's own armour support.
            int allied = HeartCountry.laneAcross().length;
            for (int i = 0; i < grounds.size(); i++) addSector(marks, grounds.get(i), width, height,
                    i < allied ? "LANE " + (i + 1) : "COMPANY LANE", i < allied ? ReviewStyle.ROUTE : ReviewStyle.LANDING);
        }
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (SwarmBore bore : simulation.getSwarmBores()) {
            String name = bore.mother() ? bore.beatKey().replace("mother-", "MOTHER ") : "SATELLITE " + bore.beatKey();
            marks.box(bore.x() - BORE_HALF, bore.y() - BORE_HALF, bore.x() + BORE_HALF, bore.y() + BORE_HALF,
                    bore.collapsed() ? name + " · down" : name, bore.collapsed() ? ReviewStyle.NOTE : ReviewStyle.KEEP);
            if (!bore.mother()) continue;
            left = Math.min(left, bore.x()); top = Math.min(top, bore.y());
            right = Math.max(right, bore.x()); bottom = Math.max(bottom, bore.y());
        }
        float[] landing = addLandingZones(marks, simulation);
        if (left <= right) {
            marks.box(left - YARD_MARGIN, top - YARD_MARGIN, right + YARD_MARGIN, bottom + YARD_MARGIN,
                    "RESERVOIR YARD", ReviewStyle.OBJECTIVE);
            if (landing != null) {
                marks.arrow(landing[0], landing[1], (left + right) / 2f + 0.5f, (top + bottom) / 2f + 0.5f,
                        "to the heart", ReviewStyle.APPROACH);
            }
        }
        addGunLine(marks, simulation, heart.barrage());
        return marks.build();
    }

    /** The Burn's battery: one box round its guns, with how many are left standing. */
    private static void addGunLine(ReviewAnnotations.Builder marks, BattleSimulation simulation, WalkingBarrage barrage) {
        if (barrage == null) return;
        long[] guns = barrage.guns();
        if (guns.length == 0) return;
        float left = Float.MAX_VALUE, top = Float.MAX_VALUE, right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        int standing = 0;
        for (long gun : guns) {
            if (simulation.resolveUnit(gun) == 0L || !simulation.world().isAlive(gun)) continue;
            standing++;
            float x = simulation.world().x(gun), y = simulation.world().y(gun);
            left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x); bottom = Math.max(bottom, y);
        }
        if (standing == 0) return;
        marks.box((int) left - GUN_MARGIN, (int) top - GUN_MARGIN, (int) right + GUN_MARGIN, (int) bottom + GUN_MARGIN,
                "GUN LINE · " + standing + "/" + guns.length + " guns", ReviewStyle.POSITION);
    }

    /** Cells the reservoir yard's box keeps round its mothers. */
    private static final int YARD_MARGIN = 8;
    /** Cells the gun line's box keeps round its guns. */
    private static final int GUN_MARGIN = 3;

    /**
     * The Burn's front: each allied battlegroup's sector and the company's own,
     * boxed across the whole approach so the reader sees who holds which band.
     */
    private static void addHeartSectors(ReviewAnnotations.Builder marks, BattleSimulation simulation) {
        HeartSequence heart = simulation.getHeart();
        HeartLanes lanes = heart == null ? null : heart.lanes();
        if (lanes == null || lanes.companySector() == null) return;
        int width = simulation.getGrid().getWidth(), height = simulation.getGrid().getHeight();
        List<HeartLane> grounds = lanes.grounds();
        for (int i = 0; i < grounds.size(); i++) addSector(marks, grounds.get(i), width, height,
                "BATTLEGROUP " + (i + 1), ReviewStyle.ROUTE);
        addSector(marks, lanes.companySector(), width, height, "COMPANY SECTOR", ReviewStyle.LANDING);
    }

    private static void addSector(ReviewAnnotations.Builder marks, HeartLane sector, int width, int height,
                                  String text, ReviewStyle style) {
        int low = sector.across() - sector.halfWidth(), high = sector.across() + sector.halfWidth();
        if (sector.advancesAlongY()) marks.box(low, 1, high, height - 2, text, style);
        else marks.box(1, low, width - 2, high, text, style);
    }

    /**
     * A Colony Breach frame: where the company landed, the shelter, the lift,
     * and every eruption site — numbered as the beats number them, pending
     * until it opens — with the walk in to the shelter and out to the lift.
     * Each holdout is boxed as REDOUBT N with whether it is holding, reached
     * or fallen; the shelter is REDOUBT 1 when there are others.
     * Compounds are not drawn: a colony's held buildings are militia posts,
     * and Conquest's keep-and-barracks words would say the wrong battle.
     */
    private static ReviewAnnotations forColonyBreach(BattleSimulation simulation) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        CivilianEvacuationObjective cohort = null;
        for (Objective objective : simulation.getObjectives()) {
            if (objective instanceof CivilianEvacuationObjective evacuation) cohort = evacuation;
        }
        float[] landing = addLandingZones(marks, simulation);
        ColonyBreachSequence breach = simulation.getColonyBreach();
        List<ColonyEruptionPlan.Site> sites = breach.plan().sites();
        for (int i = 0; i < sites.size(); i++) {
            ColonyEruptionPlan.Site site = sites.get(i);
            boolean open = breach.isErupted(i);
            String name = site.mother() ? "MOTHER BORE" : "BORE " + site.number();
            marks.box(site.x() - BORE_HALF, site.y() - BORE_HALF, site.x() + BORE_HALF, site.y() + BORE_HALF,
                    open ? name : name + " · pending", open ? ReviewStyle.KEEP : ReviewStyle.NOTE);
        }
        addLivingField(marks, simulation);
        if (cohort == null) return marks.build();
        int sx = cohort.sourceCellX(), sy = cohort.sourceCellY();
        int lx = cohort.egressCellX(), ly = cohort.egressCellY();
        int shelter = CivilianEvacuationPlacement.SHELTER_ZONE_RADIUS;
        List<CivilianRedoubt> redoubts = simulation.getCivilianRedoubts();
        boolean several = redoubts.size() > 1;
        // The main redoubt's ground: the shelter's zone, or the compound walled round it.
        CivilianRedoubt main = redoubts.isEmpty() ? null : redoubts.get(0);
        if (main != null) {
            marks.box(main.left(), main.top(), main.right(), main.bottom(),
                    several ? "REDOUBT 1 · SHELTER" : "SHELTER", ReviewStyle.OBJECTIVE);
        } else {
            marks.box(sx - shelter, sy - shelter, sx + shelter, sy + shelter, "SHELTER", ReviewStyle.OBJECTIVE);
        }
        for (CivilianRedoubt redoubt : redoubts) {
            if (redoubt.isMain()) continue;
            String state = redoubt.fallen() ? "FALLEN" : redoubt.released() ? "reached" : "holding";
            marks.box(redoubt.left(), redoubt.top(), redoubt.right(), redoubt.bottom(),
                    "REDOUBT " + redoubt.number() + " · " + state,
                    redoubt.fallen() ? ReviewStyle.KEEP : redoubt.released() ? ReviewStyle.NOTE : ReviewStyle.OBJECTIVE);
        }
        marks.box(lx - LIFT_HALF, ly - LIFT_HALF, lx + LIFT_HALF, ly + LIFT_HALF, "LIFT", ReviewStyle.POSITION);
        if (landing != null) {
            marks.arrow(landing[0], landing[1], sx + 0.5f, sy + 0.5f, "to the shelter", ReviewStyle.APPROACH);
        }
        marks.arrow(sx + 0.5f, sy + 0.5f, lx + 0.5f, ly + 0.5f, "to the lift", ReviewStyle.ROUTE);
        return marks.build();
    }

    /**
     * The colony's living field: each homestead boxed with whether its guns
     * still hold, each militia knot on its ground with how many are standing,
     * and each family walking out boxed round where its people are now.
     */
    private static void addLivingField(ReviewAnnotations.Builder marks, BattleSimulation simulation) {
        ColonyLivingField field = simulation.colonyLivingField();
        if (field == null) return;
        for (ColonyLivingField.Homestead home : field.homesteads()) {
            int guns = ColonyLivingField.aliveCount(simulation, home.guns());
            marks.box(home.left(), home.top(), home.right(), home.bottom(),
                    guns > 0 ? "HOMESTEAD · " + guns + " guns" : "HOMESTEAD · OVERRUN",
                    guns > 0 ? ReviewStyle.POSITION : ReviewStyle.KEEP);
        }
        for (ColonyLivingField.Knot knot : field.knots()) {
            int standing = ColonyLivingField.aliveCount(simulation, knot.members());
            marks.box(knot.x() - KNOT_HALF, knot.y() - KNOT_HALF, knot.x() + KNOT_HALF, knot.y() + KNOT_HALF,
                    standing > 0 ? "MILITIA · " + standing : "MILITIA · DOWN",
                    standing > 0 ? ReviewStyle.POSITION : ReviewStyle.KEEP);
        }
        for (ColonyLivingField.WalkingGroup group : field.walkingGroups(simulation)) {
            marks.box((int) Math.floor(group.minX()) - FAMILY_MARGIN, (int) Math.floor(group.minY()) - FAMILY_MARGIN,
                    (int) Math.ceil(group.maxX()) + FAMILY_MARGIN, (int) Math.ceil(group.maxY()) + FAMILY_MARGIN,
                    "FAMILY · " + group.walkers(), ReviewStyle.NOTE);
        }
    }

    /** Half-width of a militia knot's box: the ground it stands on. */
    private static final int KNOT_HALF = 3;
    /** Cells a walking family's box keeps round its members. */
    private static final int FAMILY_MARGIN = 1;

    /** Half-width of the box an eruption site is drawn in. */
    private static final int BORE_HALF = 3;
    /** Half-width of the lift's box: its pickup formation, not just the pad. */
    private static final int LIFT_HALF = 8;

    /** The two ends of the approach, whichever of them the battle has. */
    private record Ends(TacticalNode keep, TacticalNode beachhead) { }

    /**
     * One line per lane, drawn through its numbered links.
     *
     * <p>A bend is the thing a lane path exists to be able to say, and it is
     * invisible in every other mark on the frame — the compounds are boxes and
     * the approach is one straight arrow from the beachhead to the keep. So the
     * route is drawn as it actually runs, cell by cell, and each link carries
     * its position along the lane, so a reader can see both that lane 2 goes
     * round the reservoir and which of its places is next.
     *
     * <p>The objective is not numbered. Every lane ends there, three labels
     * would land on the same pixels, and the keep already says what it is.
     */
    private static void addLaneRoutes(ReviewAnnotations.Builder marks,
                                      BattleSimulation simulation) {
        ConquestLaneChain chain = chainOf(simulation);
        IntPredicate held = marineHeldZones(simulation);
        for (LaneRoute lane : simulation.getLaneRoutes()) {
            List<ReviewAnnotation.Point> points = new ArrayList<>(lane.route().size());
            for (LaneRoute.Cell cell : lane.route()) {
                points.add(new ReviewAnnotation.Point(cell.x(), cell.y()));
            }
            marks.polyline(points, "lane " + (lane.lane() + 1), ReviewStyle.ROUTE);
            int front = chain.frontLink(lane.lane(), held);
            int link = 0;
            for (int index = 0; index < lane.links().size(); index++) {
                LaneRoute.Link at = lane.links().get(index);
                if (at.band() == LaneRoute.OBJECTIVE_BAND) continue;
                link++;
                boolean isFront = index == front;
                marks.label(at.x(), at.y(),
                        (lane.lane() + 1) + "." + link + (isFront ? " front" : ""),
                        isFront ? ReviewStyle.FRONT : ReviewStyle.ROUTE);
            }
        }
    }

    /**
     * The lanes read as chains, so a frame can say which place each of them is
     * being fought over.
     *
     * <p>Built here rather than read off the commander for the same reason
     * everything else on the frame is: a review is a neutral observer's
     * picture, and the commander's own chain is a marine-perspective object it
     * has no business reaching into. Both are read from the same recorded lanes
     * and the same compound records, so they agree by construction.
     */
    private static ConquestLaneChain chainOf(BattleSimulation simulation) {
        CompoundService compounds = simulation.getCompoundService();
        if (compounds == null) return ConquestLaneChain.NONE;
        List<ConquestLaneChain.Compound> places = new ArrayList<>();
        for (CompoundService.Record record : compounds.getRecords()) {
            places.add(new ConquestLaneChain.Compound(
                    compounds.captureZoneId(record, simulation),
                    record.node.anchorX, record.node.anchorY));
        }
        return ConquestLaneChain.of(simulation.getLaneRoutes(), places);
    }

    /** Which capture zones the marines hold right now. */
    private static IntPredicate marineHeldZones(BattleSimulation simulation) {
        CompoundService compounds = simulation.getCompoundService();
        if (compounds == null) return zone -> false;
        Set<Integer> held = new LinkedHashSet<>();
        for (CompoundService.Record record : compounds.getRecords()) {
            if (record.state != CompoundService.CompoundState.MARINE_HELD) continue;
            held.add(compounds.captureZoneId(record, simulation));
        }
        return held::contains;
    }

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
