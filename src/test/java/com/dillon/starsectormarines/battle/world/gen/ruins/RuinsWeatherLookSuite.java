package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.fixture.RuinsBattleFixture;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The ruins' weathering ({@link RuinsWeather}) through the look preset at 1920x1080, one extensive site per world:
 * the dead town at play zoom, the first mouth into the megastructure with its roof off, and a corridor stretch deeper
 * in. {@code -Dsnapshot.ruinsWeather.surfaces} (default every world) and {@code -Dsnapshot.ruinsWeather.seed} (7).
 * The control is the same run with {@code -Dbattle.ruins.weather=false}.
 */
public final class RuinsWeatherLookSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final float PLAY = 60f;

    @Override public String id() { return "ruins-weather-look"; }

    @Override public String label() { return "Ruins weathering: dead town, a mouth and a corridor on each world, look preset"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        long seed = Long.getLong("snapshot.ruinsWeather.seed", 7L);
        String surfaces = System.getProperty("snapshot.ruinsWeather.surfaces", "FROZEN,ARID,VERDANT,VOLCANIC,ROCK,TOXIC");
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String name : surfaces.split(",")) {
            SurfacePalette surface = SurfacePalette.valueOf(name.trim().toUpperCase(Locale.ROOT));
            RuinsBattleFixture fixture = RuinsBattleFixture.fromFactoryInputs(seed, BattleSetup.defaultManifest(), false,
                    OperationTier.VETERAN, RiskLevel.MEDIUM, RuinsGroundEvidence.profile(surface), FlybyRoster.EMPTY,
                    FlybyRoster.EMPTY, RuinsGrade.EXTENSIVE, RuinsOccupant.SCAVENGERS);
            try (BattleSimulation sim = fixture.build()) {
                RuinsDelve delve = sim.getRuinsWorks().delve();
                String dir = surface.name().toLowerCase(Locale.ROOT) + "/";
                RuinsDelve.Area landing = delve.landingGround();
                out.add(new SnapshotArtifact(dir + "1-town.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        W, H, landing.right() + PLAY * 0.6f, landing.centreY(), PLAY).render(sim, "")));
                RuinsDelve.Interior inside = delve.interior();
                if (inside == null || inside.entrances().isEmpty()) continue;
                RuinsDelve.Area door = inside.entrances().get(inside.entrances().size() / 2);
                out.add(new SnapshotArtifact(dir + "2-mouth.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        W, H, door.right() + 10f, door.centreY(), PLAY * 0.6f).withoutRoofs().render(sim, "")));
                RuinsDelve.Area hull = inside.footprint();
                out.add(new SnapshotArtifact(dir + "3-corridors.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        W, H, (hull.left() + hull.right()) / 2f, hull.centreY(), PLAY).withoutRoofs().render(sim, "")));
            }
        }
        return out;
    }
}
