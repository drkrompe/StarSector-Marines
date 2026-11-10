package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.RaidOwnedMap;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Raid's owned maps on their home ({@code raid-mission.md}, The owned map), through the look preset at 1920x1080,
 * for choosing and reviewing them. Nothing is played. Opt-in. The owned maps under every world are
 * {@code planet-skin-look} with {@code -Dsnapshot.planetSkin.maps=raid,raid-heavy-industry,...}.
 *
 * <ul>
 *   <li>{@code -Dsnapshot.raidOwned.candidates=2-7}: every Raid map's seeds in that range, a whole frame each
 *       ({@code candidates/<map>-seed<n>.png}), to pick an owned seed from.</li>
 *   <li>{@code -Dsnapshot.raidOwned.options=true}: the owner's questions' pictures, each on the owned heavy-industry
 *       seed: the reference market at three sizes ({@code options/reference-*.png}) and the map at each Raid scale
 *       ({@code options/scale-*.png}).</li>
 * </ul>
 * {@code -Dsnapshot.raidOwned.maps=raid,raid-mining} narrows the maps.
 */
public final class RaidOwnedMapLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;

    @Override public String id() { return "raid-owned-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "The Raid's owned maps on their home: candidates and the owner's options, look preset"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        List<String> maps = asked("snapshot.raidOwned.maps");
        String range = System.getProperty("snapshot.raidOwned.candidates", "").trim();
        List<SnapshotArtifact> out = new ArrayList<>();
        if (!range.isEmpty()) {
            String[] r = range.split("-");
            long from = Long.parseLong(r[0]), to = r.length > 1 ? Long.parseLong(r[1]) : from;
            for (StoryMap map : StoryMap.values()) {
                if (!RaidOwnedMap.isRaid(map) || !maps.isEmpty() && !maps.contains(map.id)) continue;
                RaidOwnedMap.Ground ground = RaidOwnedMap.Ground.of(map, StoryMapPools.pool(map).home());
                for (long seed = from; seed <= to; seed++) {
                    out.add(frame(context, "candidates/" + map.id + "-seed" + seed + ".png", ground, seed));
                }
            }
        }
        if (Boolean.getBoolean("snapshot.raidOwned.options")) out.addAll(options(context));
        return out;
    }

    private static List<SnapshotArtifact> options(SnapshotContext context) throws Exception {
        StoryMap map = StoryMap.RAID_HEAVY_INDUSTRY;
        SurfacePalette home = StoryMapPools.pool(map).home();
        long seed = StoryMapPools.pool(map).owned().seed();
        List<SnapshotArtifact> out = new ArrayList<>();
        // The reference market: a guarded size-6 town (owned), an open size-4 hamlet, a fortified size-8 city.
        out.add(frame(context, "options/reference-a-size6-guarded.png", RaidOwnedMap.Ground.of(map, home), seed));
        out.add(frame(context, "options/reference-b-size4-open.png", new RaidOwnedMap.Ground(map, home,
                market(4, 1, false, false, home), RaidOwnedMap.TIER, RaidOwnedMap.MACHINES), seed));
        out.add(frame(context, "options/reference-c-size8-fortified.png", new RaidOwnedMap.Ground(map, home,
                market(8, 6, true, true, home), RaidOwnedMap.TIER, RaidOwnedMap.MACHINES), seed));
        // The scale: one map at the middle size (owned), or one per Raid scale with the tier's machines.
        TargetProfile reference = RaidOwnedMap.reference(map, home);
        out.add(frame(context, "options/scale-small-200x112.png",
                new RaidOwnedMap.Ground(map, home, reference, OperationTier.FIRST_CONTRACT, 3), seed));
        out.add(frame(context, "options/scale-middle-280x168.png", RaidOwnedMap.Ground.of(map, home), seed));
        out.add(frame(context, "options/scale-large-400x240.png",
                new RaidOwnedMap.Ground(map, home, reference, OperationTier.REINFORCED, 5), seed));
        return out;
    }

    /** A reference market like {@link RaidOwnedMap#reference} at another size and security. */
    private static TargetProfile market(int size, int defence, boolean groundDefences, boolean batteries,
                                        SurfacePalette home) {
        Set<EconomicFunction> functions = EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.COMMERCE,
                EconomicFunction.SPACEPORT, EconomicFunction.HEAVY_INDUSTRY);
        return new TargetProfile(size, RaidOwnedMap.STABILITY, defence, 1, RaidOwnedMap.FACTION, functions, home,
                SettlementLink.ROAD).withDefences(groundDefences, batteries, false);
    }

    private static SnapshotArtifact frame(SnapshotContext context, String name, RaidOwnedMap.Ground ground, long seed)
            throws Exception {
        boolean works = ground.map() != StoryMap.RAID;
        try (BattleSimulation sim = BattleSetup.createPooledRaid(ground, ground.home(), seed, works, List.of())) {
            int w = sim.getTopology().getWidth(), h = sim.getTopology().getHeight();
            BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                    w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f);
            System.out.printf(Locale.ROOT, "[raid-owned-look] %s %s seed %d %dx%d%n", name, ground.map().id, seed, w, h);
            return new SnapshotArtifact(name, look.render(sim, ""));
        }
    }

    private static List<String> asked(String property) {
        String v = System.getProperty(property, "").trim();
        return v.isEmpty() ? List.of() : List.of(v.split(","));
    }
}
