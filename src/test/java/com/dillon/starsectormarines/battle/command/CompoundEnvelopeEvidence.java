package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.ConquestBattleFixture;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.CompoundPreparedGround;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A compound's two layers of garrisonable on production Conquest battles with forts (`garrisonables.md`, building
 * envelopes): the real fixture market with four battery forts, stood up at tick zero on five seeds. Per seed it reports
 * the forts placed, the compound places and how many offer an envelope (with how many posts), the aprons, and the
 * question left open by the aprons: how many of them the registry reads as a fort's belt because they stand within
 * {@link GarrisonableRegistry#FORT_REACH_CELLS} of a fort. Asserts that every envelope is a place a squad can be sent
 * to and found on again. Writes {@code build/reports/garrison/conquest-envelopes.md}.
 */
@Tag("generated-space-evidence")
class CompoundEnvelopeEvidence {

    private static final long[] SEEDS = {1L, 2L, 3L, 5L, 9L};

    @Test
    void conquestCompoundBuildingsAreGarrisonables() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        InstalledVanillaData.install();
        TargetProfile profile = fixtureProfile().withDeclaredDefences(
                new GroundDefences(0, false).withForts(FortKind.BATTERY_FORT, 4));
        StringBuilder out = new StringBuilder("# Conquest compound layers\n\n")
                .append("The fixture market with four battery forts, at tick zero. Envelopes: compound places ")
                .append("(command post, barracks, armory) offered as garrisonables, and their posts. Aprons: stretches ")
                .append("standing within ").append(CompoundGround.REACH_CELLS).append(" cells of a compound place's ")
                .append("building; read as a fort's belt when the registry attributes them to a fort.\n\n")
                .append("| seed | forts | compound places | envelopes | posts min / median / max | aprons | aprons read as a fort's belt |\n")
                .append("|---|---|---|---|---|---|---|\n");
        int allPlaces = 0, allEnvelopes = 0, allAprons = 0, allFortAprons = 0, allForts = 0;
        Map<TacticalNode.Kind, int[]> byKind = new EnumMap<>(TacticalNode.Kind.class);
        for (long seed : SEEDS) {
            try (BattleSimulation sim = BattleSetup.createConquest(seed,
                    List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6)), false,
                    OperationTier.FIRST_CONTRACT, RiskLevel.LOW, profile)) {
                GarrisonableRegistry places = sim.garrisonables();
                List<TacticalNode> compounds = new ArrayList<>();
                for (TacticalNode node : sim.getTacticalMap().all()) {
                    if (CompoundPreparedGround.isCompoundKind(node.kind)) compounds.add(node);
                }
                int envelopes = 0;
                List<Integer> posts = new ArrayList<>();
                for (TacticalNode node : compounds) {
                    int g = places.envelopeOf(node);
                    int[] counts = byKind.computeIfAbsent(node.kind, k -> new int[2]);
                    counts[0]++;
                    if (g < 0) continue;
                    counts[1]++;
                    envelopes++;
                    posts.add(places.size(g));
                    assertTrue(sim.getGrid().isWalkable(places.centreX(g), places.centreY(g)),
                            "an envelope's centre is a walkable post, seed " + seed);
                    assertEquals(g, places.idAt(places.centreX(g), places.centreY(g)),
                            "an envelope is found again by its centre, seed " + seed);
                }
                int aprons = 0, fortAprons = 0;
                for (int g = 0; g < places.stretchCount(); g++) {
                    if (!frontsACompound(places, g, compounds)) continue;
                    aprons++;
                    if (places.fortId(g) >= 0) fortAprons++;
                }
                int forts = sim.forts().forts().size();
                Collections.sort(posts);
                String spread = posts.isEmpty() ? "-" : posts.get(0) + " / " + posts.get(posts.size() / 2) + " / "
                        + posts.get(posts.size() - 1);
                out.append(String.format("| %d | %d | %d | %d | %s | %d | %d |%n", seed, forts, compounds.size(),
                        envelopes, spread, aprons, fortAprons));
                allPlaces += compounds.size();
                allEnvelopes += envelopes;
                allAprons += aprons;
                allFortAprons += fortAprons;
                allForts += forts;
            }
        }
        out.append("\nIn all: ").append(allForts).append(" forts; ").append(allEnvelopes).append(" envelopes on ")
                .append(allPlaces).append(" compound places; ").append(allAprons).append(" aprons, of them ")
                .append(allFortAprons).append(" read as a fort's belt.\n\nBy kind (places / with an envelope):");
        for (Map.Entry<TacticalNode.Kind, int[]> entry : byKind.entrySet()) {
            out.append(' ').append(entry.getKey()).append(' ').append(entry.getValue()[0]).append('/')
                    .append(entry.getValue()[1]);
        }
        out.append('\n');
        assertTrue(allForts > 0, "the fixture market stands forts");
        assertTrue(allAprons > 0, "a Conquest of places lays aprons too: " + allAprons + "/" + allPlaces);
        assertTrue(allEnvelopes * 2 >= allPlaces, "most compound places offer an envelope: " + allEnvelopes + "/" + allPlaces);
        Path dir = root.resolve("build/reports/garrison");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("conquest-envelopes.md"), out.toString());
        System.out.println(out);
    }

    /** Whether a stretch stands within the apron reach of a compound place's building, whatever fort it is read as. */
    private static boolean frontsACompound(GarrisonableRegistry places, int g, List<TacticalNode> compounds) {
        int x = places.centreX(g);
        int y = places.centreY(g);
        for (TacticalNode node : compounds) {
            int gap = Math.max(Math.max(node.left - x, x - node.right), Math.max(node.top - y, y - node.bottom));
            if (gap <= CompoundGround.REACH_CELLS) return true;
        }
        return false;
    }

    /** The market a real Conquest is generated against. */
    private static TargetProfile fixtureProfile() throws Exception {
        try (InputStream stream = CompoundEnvelopeEvidence.class.getResourceAsStream(
                "/battle-fixtures/conquest-reinforced-south-v3.json")) {
            BattleFixture loaded = BattleFixtureJson.fromJson(new JSONObject(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
            if (loaded instanceof BattleLaunchFixture launch) loaded = launch.construction();
            return ((ConquestBattleFixture) loaded).targetProfile();
        }
    }
}
