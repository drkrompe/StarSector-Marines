package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import com.dillon.starsectormarines.battle.cordon.CordonCorridor;
import com.dillon.starsectormarines.battle.cordon.CordonPlan;
import com.dillon.starsectormarines.battle.cordon.CordonRedoubt;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.evacuation.ColonyRedoubts;
import com.dillon.starsectormarines.battle.evacuation.ColonyEruptionPlan;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.cordon.CordonFront;
import com.dillon.starsectormarines.battle.cordon.CordonGapLaw;
import com.dillon.starsectormarines.battle.cordon.CordonGapNests;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartLayout;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartOrigin;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A pooled entry's acceptance ({@code story-map-pools.md}): the map generated again with its patch must pass its
 * mission's own setup tests on the first generation, its placed structures must stand where they were put, a Heart
 * must build nothing on a way in its start-side third, and the battle must stand up. The regeneration check
 * ({@code storyMapPools}) and the Maps page both read it, so a page save and the check cannot disagree.
 */
public final class StoryMapAcceptance {

    private StoryMapAcceptance() { }

    /** The entry's reading on this biome when it passes; throws, naming what it lacks, when it does not. */
    public static String check(StoryMap map, SurfacePalette biome, StoryMapPool.Entry entry) throws Exception {
        Form form = BloomHeartSetup.formOf(map);
        return map == StoryMap.COLONY_BREACH ? colony(biome, entry)
                : form != null ? heart(map, biome, entry) : cordon(map, biome, entry);
    }

    /** Generates the pooled map (which throws on a failed acceptance), checks the line and stands the battle up. */
    static String cordon(StoryMap map, SurfacePalette biome, StoryMapPool.Entry entry) throws Exception {
        CordonVariant variant = CordonSetup.variantOf(map);
        CordonSetup.CordonMap cordon = CordonSetup.map(variant, CordonSetup.densityOf(map), biome, entry);
        List<Precinct> redoubts = CordonPlan.redoubts(cordon.plan());
        if (redoubts.size() != CordonPlan.REDOUBTS) throw new AssertionError(redoubts.size() + " redoubts");
        for (int i = 1; i < redoubts.size(); i++) {
            if (redoubts.get(i).seedX() <= redoubts.get(i - 1).seedX()) throw new AssertionError("redoubts out of order");
        }
        int spread = redoubts.get(redoubts.size() - 1).seedX() - redoubts.get(0).seedX();
        if (spread <= CordonSetup.WIDTH / 2) throw new AssertionError("the line spans only " + spread + " cells");
        Precinct base = null;
        for (Precinct p : cordon.plan().precincts()) if (p.name().equals(CordonPlan.BASE_PLACE)) base = p;
        String detail = String.format(Locale.ROOT, "rear %s; base %d,%d; line row %d to %d",
                cordon.plan().attackerFrom().y0() == 0f ? "south" : "north", base.seedX(), base.seedY(),
                redoubts.stream().mapToInt(Precinct::seedY).min().orElse(0),
                redoubts.stream().mapToInt(Precinct::seedY).max().orElse(0));
        if (cordon.corridor() != null) {
            detail += String.format(Locale.ROOT, "; corridor %d cells, %.0f%% on road", cordon.corridor().size(),
                    100f * CordonCorridor.pavedShare(cordon.map().topology, cordon.corridor()));
        }
        StringBuilder works = new StringBuilder();
        for (Laid laid : cordon.map().layout.pieces()) {
            works.append(works.length() == 0 ? "" : ", ").append(laid.name()).append(' ').append(laid.status().name().toLowerCase(Locale.ROOT));
            boolean said = laid.status() == Laid.Status.REFUSED || laid.kind() == Laid.Kind.ROAD;
            if (said && !laid.reason().isEmpty()) works.append(" (").append(laid.reason()).append(')');
        }
        detail += "; layout " + works;
        boolean south = cordon.plan().attackerFrom().y0() == 0f;
        int h = cordon.map().grid.getHeight();
        detail += "; " + onRoads(cordon.map().layout, (x, y) -> south ? y / (float) (h - 1) : 1f - y / (float) (h - 1)).text();
        StringBuilder gaps = new StringBuilder();
        for (CordonGapLaw.Gap gap : CordonSetup.gaps(cordon.map(), cordon.plan())) {
            gaps.append(gaps.length() == 0 ? "" : " ").append(String.format(Locale.ROOT, "%d-%d %.0f%s (towers %.0f)",
                    gap.west(), gap.east(), gap.widestStep(), gap.holds() ? "" : "!", gap.towerToTower()));
        }
        detail += String.format(Locale.ROOT, "; gaps %s, law %.1f", gaps, CordonGapLaw.LIMIT);
        StringBuilder nests = new StringBuilder();
        int added = 0, mouths = 0;
        CordonGapNests.Ground ground = CordonGapNests.mapGround(cordon.map().grid, cordon.map().topology, Set.of());
        int front = CordonFront.frontward(cordon.plan());
        for (int n = 1; n < redoubts.size(); n++) {
            Laid picket = cordon.map().layout.find(CordonPlan.PICKET_LINE + n + "-" + (n + 1));
            nests.append(nests.length() == 0 ? "" : "-").append(picket == null ? 0 : picket.nests().size());
            int k = CordonGapNests.added(picket);
            added += k;
            for (int i = picket == null ? 0 : picket.nests().size() - k; k > 0 && i < picket.nests().size(); i++) {
                if (CordonGapNests.mouth(ground, picket.nests().get(i)[0], picket.nests().get(i)[1], front)) mouths++;
            }
        }
        detail += "; picket nests " + nests + " (" + added + " added for the gaps, " + mouths + " at street mouths)";
        try (BattleSimulation sim = CordonSetup.create(cordon, variant, List.of(), CordonSequence.Timetable.STANDARD)) {
            if (sim.getCordon() == null) throw new AssertionError("the battle has no cordon");
            StringBuilder guns = new StringBuilder();
            for (CordonRedoubt r : sim.getCordon().redoubts()) guns.append(guns.length() == 0 ? "" : "-").append(r.guns().size());
            detail += "; redoubt guns " + guns;
        }
        return detail;
    }

    /** Stands the Colony Breach up on the pooled seed alone (which throws when it no longer carries the mission). */
    static String colony(SurfacePalette biome, StoryMapPool.Entry entry) {
        try (BattleSimulation sim = BattleSetup.createPooledColonyBreach(biome, entry, List.of())) {
            List<int[]> holdouts = ColonyRedoubts.holdoutCentres(sim.getCivilianRedoubts());
            List<ColonyEruptionPlan.Site> bores = sim.getColonyBreach().plan().sites();
            double nearest = Double.MAX_VALUE;
            for (int[] h : holdouts) for (ColonyEruptionPlan.Site b : bores) nearest = Math.min(nearest, Math.hypot(h[0] - b.x(), h[1] - b.y()));
            return holdouts.size() + " holdouts; " + bores.size() + " bore sites"
                    + (holdouts.isEmpty() || bores.isEmpty() ? "" : String.format(Locale.ROOT, "; nearest holdout to bore %.0f cells", nearest));
        }
    }

    static String heart(StoryMap map, SurfacePalette biome, StoryMapPool.Entry entry) throws Exception {
        BloomHeartSetup.Built built = BloomHeartSetup.country(BloomHeartSetup.formOf(map), biome,
                BloomHeartSetup.densityOf(map), entry);
        try (BattleSimulation sim = built.sim()) {
            int laid = 0, pieces = 0, airbases = 0, hamlet = 0, locustPits = 0;
            // A structure a person placed by hand that did not go down where it was put is a broken pool entry.
            for (Structure placed : built.plan().layout().placed()) {
                Laid piece = built.map().layout.find(placed.name());
                if (piece == null || !piece.isLaid() || piece.cells().get(0)[0] != placed.x()
                        || piece.cells().get(0)[1] != placed.y()) {
                    throw new IllegalStateException(placed.name() + " was placed at " + placed.x() + "," + placed.y()
                            + " but " + (piece == null ? "not laid" : piece.isLaid() ? "laid elsewhere" : "refused: " + piece.reason()));
                }
            }
            StringBuilder missing = new StringBuilder();
            String dressing = "none";
            Form form = BloomHeartSetup.formOf(map);
            OnRoads roads = onRoads(built.map().layout, form::depth);
            if (roads.start() > 0) throw new AssertionError("built on roads in the start-side third: " + roads.text());
            for (Laid piece : built.map().layout.pieces()) {
                pieces++;
                if (piece.name().equals(HeartLayout.ORIGIN_DRESSING) && piece.isLaid()) dressing = piece.reason();
                if (piece.name().matches(HeartLayout.AIRBASE + "[0-9]+") && piece.isLaid()) airbases++;
                if (piece.name().matches(HeartLayout.AIRBASE + "[0-9]+[.][0-9]+") && piece.isLaid()) hamlet++;
                if (piece.name().startsWith(HeartLayout.LOCUST_PIT) && piece.isLaid()) locustPits++;
                if (piece.isLaid()) laid++;
                else missing.append(missing.length() == 0 ? "" : ", ").append(piece.name())
                        .append(piece.reason().isBlank() ? "" : ": " + piece.reason());
            }
            return HeartOrigin.of(HeartCountry.place(built.plan(), HeartCountry.WORKINGS)).name().toLowerCase(Locale.ROOT) + " origin"
                    + (BloomHeartSetup.originCore(built.map(), built.plan()) == null ? " (no core)" : "")
                    + "; " + built.heart().sites().size() + " bores (" + built.heart().mothers().size() + " mothers); landing " + built.plan().landingPlace().seedX() + ","
                    + built.plan().landingPlace().seedY() + "; layout " + laid + " of " + pieces + " laid"
                    + "; dressing " + dressing
                    + (BloomHeartSetup.formOf(map) == Form.BROAD_FRONT ? "; airbases " + airbases + " (hamlet buildings " + hamlet + ")" : "")
                    + "; locust pits " + locustPits
                    + "; supply road " + BloomHeartSetup.supplyRoad(built.map(), built.plan()).size() + " cells"
                    + (missing.length() == 0 ? "" : " (not " + missing + ")")
                    + "; " + roads.text();
        }
    }

    /** Depth from a map's start edge toward its far side, 0 to 1. */
    private interface Depth {
        float of(int x, int y);
    }

    /** Structure and works cells on roads, start-side third and deeper, with the offending pieces named. */
    private record OnRoads(int start, int deeper, String text) { }

    /** Where structures start being covered by creep and ruin rather than standing on clean ground: about a third in. */
    static final float CLEAN_DEPTH = 0.34f;

    /**
     * The reading of "nothing is built on a road" ({@code LaidLayout.onWays}): cells of laid structures and lines of
     * works on a road, farm track or landscape track, split at {@link #CLEAN_DEPTH}, offenders by piece name.
     */
    private static OnRoads onRoads(LaidLayout layout, Depth depth) {
        int start = 0, deeper = 0;
        List<String> startNames = new ArrayList<>(), deeperNames = new ArrayList<>();
        for (Map.Entry<String, List<int[]>> e : new TreeMap<>(layout.onWays()).entrySet()) {
            int s = 0, d = 0;
            for (int[] c : e.getValue()) {
                if (depth.of(c[0], c[1]) < CLEAN_DEPTH) s++;
                else d++;
            }
            if (s > 0) startNames.add(e.getKey() + " " + s);
            if (d > 0) deeperNames.add(e.getKey() + " " + d);
            start += s;
            deeper += d;
        }
        return new OnRoads(start, deeper, "on roads " + start + " start-side" + (startNames.isEmpty() ? "" : " " + startNames)
                + ", " + deeper + " deeper" + (deeperNames.isEmpty() ? "" : " " + deeperNames));
    }

}
