package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.BiomeSkin;
import com.dillon.starsectormarines.battle.world.gen.Climate;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap;
import com.dillon.starsectormarines.battle.world.gen.OwnedMapCatalog;
import com.dillon.starsectormarines.battle.world.gen.OwnedMapRecipe;
import com.dillon.starsectormarines.battle.world.gen.RelayNineCompound;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfaceZoning;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * One owned mission map under every biome skin ({@code owned-maps.md}): Relay Nine's map
 * ({@code relay-nine.map.json}), built as the mission builds it for a world of each vanilla family, through the look
 * preset. The layout is the same in every frame; only the skin differs. Per world, the whole map
 * ({@code <world>-1-whole.png}), the works ({@code -2-works.png}), the west gate at play zoom
 * ({@code -3-gate.png}), where a sealed works shows its lock, and the map's first drop ({@code -4-drop.png}). Nothing is played. Opt-in.
 * {@code -Dsnapshot.ownedMaps.worlds=jungle,toxic} picks the worlds; {@code -Dsnapshot.ownedMaps.map=<id>} the map;
 * {@code -Dsnapshot.ownedMaps.noDrops=true} draws it without its drops, for a pick between the two.
 */
public final class OwnedMapLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final float WORKS_CELLS = 130f;

    @Override public String id() { return "owned-map-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "An owned mission map (Relay Nine) under every biome skin, look preset"; }

    /** A world to skin the map for: a vanilla planet type and what its air does. */
    private record World(String tag, String planetType, Climate climate) { }

    private static final List<World> WORLDS = List.of(
            new World("rock", "barren", new Climate(EnumSet.of(Climate.Hazard.AIRLESS))),
            new World("arid", "desert", Climate.NONE),
            new World("verdant", "terran", Climate.NONE),
            new World("jungle", "jungle", Climate.NONE),
            new World("tundra", "tundra", Climate.NONE),
            new World("ocean", "water", Climate.NONE),
            new World("frozen", "frozen", Climate.NONE),
            new World("volcanic", "lava", Climate.NONE),
            new World("irradiated", "irradiated", new Climate(EnumSet.of(Climate.Hazard.RADIATION))),
            new World("toxic", "toxic", new Climate(EnumSet.of(Climate.Hazard.TOXIC))));

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        String mapId = System.getProperty("snapshot.ownedMaps.map", OwnedMapCatalog.RELAY_NINE);
        List<String> asked = List.of(System.getProperty("snapshot.ownedMaps.worlds", "").split(","));
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            for (World world : WORLDS) {
                if (!asked.get(0).isBlank() && !asked.contains(world.tag())) continue;
                TargetProfile profile = new TargetProfile(4, 5, 1, 1, "independent",
                        EnumSet.of(EconomicFunction.HABITATION), SurfaceZoning.forPlanetType(world.planetType()),
                        SettlementLink.ROAD)
                        .withClimate(world.climate())
                        .withSignature(SurfaceZoning.signatureFor(world.planetType()))
                        .withLivingWorld(SurfaceZoning.livingWorld(world.planetType()));
                OwnedMap owned = OwnedMapCatalog.map(mapId);
                boolean noDrops = Boolean.getBoolean("snapshot.ownedMaps.noDrops");
                if (noDrops) owned = new OwnedMap(owned.id(), owned.width(), owned.height(), owned.roads(),
                        owned.roadGraphFrom(), owned.terrain(), owned.origin(), owned.objective(), owned.grounds(),
                        owned.pads(), owned.sites(), owned.settlements(), owned.walls(), owned.props(), List.of(),
                        owned.rises(), owned.routes());
                MapResult map = mapId.equals(OwnedMapCatalog.RELAY_NINE) && !noDrops
                        ? RelayNineCompound.generate(8101, profile).map()
                        : OwnedMapRecipe.result(OwnedMapRecipe.build(owned, profile, 8101));
                BiomeSkin skin = BiomeSkin.of(profile);
                try (BattleSimulation sim = new BattleSimulation(map.grid, map.topology)) {
                    for (Doodad d : map.doodads) sim.addDoodad(d);
                    sim.setSurface(profile.surface());
                    int w = map.topology.getWidth(), h = map.topology.getHeight();
                    String tag = mapId + (noDrops ? "-nodrops-" : "-") + world.tag() + "-";
                    BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                            w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f);
                    out.add(new SnapshotArtifact(tag + "1-whole.png", look.render(sim, "")));
                    OwnedMap.Box lot = owned.settlements().isEmpty() ? new OwnedMap.Box(0, 0, w - 1, h - 1)
                            : owned.settlements().get(0).lot();
                    look.refocus((lot.x0() + lot.x1() + 1) / 2f, (lot.y0() + lot.y1() + 1) / 2f, WORKS_CELLS);
                    out.add(new SnapshotArtifact(tag + "2-works.png", look.render(sim, "")));
                    OwnedMap.Cell gate = owned.settlements().isEmpty() || owned.settlements().get(0).gates().isEmpty()
                            ? owned.origin() : owned.settlements().get(0).gates().get(0);
                    look.refocus(gate.x() + .5f, gate.y() + .5f, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS);
                    out.add(new SnapshotArtifact(tag + "3-gate.png", look.render(sim, "")));
                    if (!owned.drops().isEmpty()) {
                        List<OwnedMap.Cell> path = owned.drops().get(0).path();
                        OwnedMap.Cell mid = path.get(path.size() / 2);
                        look.refocus(mid.x() + .5f, mid.y() + .5f, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 1.5f);
                        out.add(new SnapshotArtifact(tag + "4-drop.png", look.render(sim, "")));
                    }
                    System.out.printf(Locale.ROOT, "[%s] %s %s: palette %s, habitat %s, adaptation %s, drop %s%n", id(),
                            mapId, world.tag(), skin.palette(), skin.habitat(),
                            owned.settlements().isEmpty() ? "-" : skin.adaptation(owned, owned.settlements().get(0)),
                            map.topology.naturalVoid() == null ? "none" : map.topology.naturalVoid().style());
                }
            }
        }
        return out;
    }
}
