package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BattleFixtureTestSupport {

    private static final String DEFAULT_RESOURCE =
            "/battle-fixtures/civilian-rescue-v1.json";
    private static final String CONQUEST_RESOURCE =
            "/battle-fixtures/conquest-undercommitted-v1.json";
    private static final String CONQUEST_LAUNCH_RESOURCE =
            "/battle-fixtures/conquest-launch-v2.json";

    private BattleFixtureTestSupport() {}

    public static BattleFixture loadDefaultFixture() throws Exception {
        return loadResource(DEFAULT_RESOURCE);
    }

    public static ConquestBattleFixture loadConquestFixture() throws Exception {
        return (ConquestBattleFixture) loadResource(CONQUEST_RESOURCE);
    }

    public static BattleLaunchFixture loadConquestLaunchFixture() throws Exception {
        return (BattleLaunchFixture) loadResource(CONQUEST_LAUNCH_RESOURCE);
    }

    private static BattleFixture loadResource(String resource) throws Exception {
        try (InputStream stream = BattleFixtureTestSupport.class
                .getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing fixture resource: " + resource);
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return BattleFixtureJson.fromJson(new JSONObject(json));
        }
    }

    public static BattleFixture loadSelectedFixture() throws Exception {
        String fixturePath = System.getProperty("battle.fixture.path", "").trim();
        if (!fixturePath.isEmpty()) {
            return BattleFixtureJson.fromJson(new JSONObject(
                    Files.readString(Path.of(fixturePath))));
        }
        return loadDefaultFixture();
    }

    /** Deterministic tick-zero fingerprint shared by factory-parity tests. */
    public static String initialFingerprint(BattleSimulation sim) {
        StringBuilder fingerprint = new StringBuilder()
                .append(sim.getGrid().getWidth()).append('x')
                .append(sim.getGrid().getHeight()).append('|')
                .append(sim.liveUnitCount()).append('|');
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        byte[] edges = grid.getEdgePassabilityArray();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                int cell = grid.index(x, y);
                fingerprint.append(topology.getGroundKind(x, y).ordinal()).append(':')
                        .append(topology.getBuildingId(x, y)).append(':')
                        .append(topology.getWallDirMask(x, y)).append(':')
                        .append(edges[cell]).append(':');
                for (CellTopology.Tag tag : CellTopology.Tag.values()) {
                    if (topology.hasTag(x, y, tag)) {
                        fingerprint.append('t').append(tag.ordinal());
                    }
                }
                fingerprint.append(';');
            }
        }
        for (Doodad doodad : sim.getDoodads()) {
            fingerprint.append('d').append(doodad.cellX).append(',')
                    .append(doodad.cellY).append(':')
                    .append(doodad.sheetPath).append(':')
                    .append(doodad.tile.col).append(',').append(doodad.tile.row)
                    .append(':').append(doodad.cover).append(';');
        }
        for (Objective objective : sim.getObjectives()) {
            fingerprint.append('o').append(objective.getClass().getName()).append(':')
                    .append(objective.owningFaction()).append(':')
                    .append(objective.displayName()).append(';');
        }
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long entity = sim.liveUnitAt(i);
            fingerprint.append(entity).append(':')
                    .append(sim.identity().faction(entity)).append(':')
                    .append(sim.identity().type(entity)).append(':')
                    .append(sim.world().cellX(entity)).append(',')
                    .append(sim.world().cellY(entity)).append(':')
                    .append(Float.floatToIntBits(sim.world().hp(entity))).append(';');
        }
        for (long aircraft : sim.getAirEntityIds()) {
            fingerprint.append('a').append(aircraft).append(':')
                    .append(sim.world().airFaction(aircraft)).append(':')
                    .append(sim.world().mission(aircraft).totalCycles).append(';');
        }
        for (FighterWing wing : sim.getFlybyRoster().wings) {
            fingerprint.append('f').append(wing.profile).append(':')
                    .append(wing.side).append(':').append(wing.sortieCount)
                    .append(':').append(Float.floatToIntBits(wing.firstArrivalSec))
                    .append(':').append(Float.floatToIntBits(wing.spawnIntervalSec))
                    .append(';');
        }
        sim.getCompoundService().getRecords().stream()
                .sorted((left, right) -> {
                    int byY = Integer.compare(left.node.anchorY, right.node.anchorY);
                    if (byY != 0) return byY;
                    return Integer.compare(left.node.anchorX, right.node.anchorX);
                })
                .forEach(record -> fingerprint.append('c')
                        .append(record.node.kind).append('@')
                        .append(record.node.anchorX).append(',')
                        .append(record.node.anchorY).append(':')
                        .append(record.state).append(';'));
        return fingerprint.toString();
    }
}
