package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The heart's Marine company on its way to its first satellite, as the owner would see it: one played Broad Front city
 * battle (verdant, seed 3), recorded through the look preset on one of its squads, once on the march
 * ({@code company-march}: the squad's files) and once where the march meets the swarm ({@code company-contact}: whom it
 * shoots and how it stands under acid). Each stands the battle up and plays it to the shot unrecorded; opt-in.
 */
public final class HeartCompanyLookSuite implements SnapshotSuite {

    /** The battle: the city fixture whose company was killed three to a lob before its files. */
    static final String FIXTURE = "{\"schemaVersion\":2,\"kind\":\"BLOOM_HEART\",\"seed\":3,\"form\":\"BROAD_FRONT\","
            + "\"biome\":\"VERDANT\",\"density\":\"CITY\",\"declaredMechDrops\":{\"MARINE\":{\"lances\":1,\"lance\":[\"BULWARK\",\"HOUND\",\"HOUND\",\"WARDEN\"],\"debugCallTick\":300}},\"shuttles\":["
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6},"
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6},"
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6},"
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6}]}";
    /** The company squad followed: the one the Spitters first reach on this map. */
    static final int SQUAD = 26;

    @Override public String id() { return "heart-company-look"; }

    @Override public String label() { return "Heart Marine company: one squad on the march and at its first contact, Broad Front city"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            List<SnapshotArtifact> out = new ArrayList<>();
            String only = System.getProperty("snapshot.heartCompany.shots", "");
            if (only.isEmpty() || only.contains("march"))
                out.addAll(LookReview.record(context.modRoot(), shot("company-march", 4500, 600, 40f)));
            if (only.isEmpty() || only.contains("contact"))
                out.addAll(LookReview.record(context.modRoot(), shot("company-contact", 5950, 900, 50f)));
            return out;
        }
    }

    /** The battle played unrecorded to {@code fromTick}, then {@code ticks} recorded {@code cellsAcross} wide on {@link #SQUAD}. */
    static LookReview.Shot shot(String name, int fromTick, int ticks, float cellsAcross) {
        String[] keys = {"m0", "m1", "m2", "m3", "m4", "m5"};
        int[] found = {0};
        return new LookReview.Shot(name, LookGround.verdant(), 40, 30, placement -> {
            BattleSimulation sim;
            try {
                sim = HeadlessBattles.fixture(new JSONObject(FIXTURE));
            } catch (Exception e) {
                throw new IllegalStateException("heart fixture", e);
            }
            while (sim.getSimTickIndex() < fromTick) sim.advance(BattleSimulation.TICK_DT);
            int n = 0;
            for (int i = 0, count = sim.liveUnitCount(); i < count && n < keys.length; i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.squad().hasSquad(unit) && sim.squad().squadId(unit) == SQUAD) placement.subject(keys[n++], unit);
            }
            found[0] = n;
            System.out.printf("    [look/%s] squad %d, %d members at tick %d%n", name, SQUAD, n, sim.getSimTickIndex());
            return sim;
        }, placement -> List.of(LookReview.View.follow("play", placement, cellsAcross,
                Arrays.copyOf(keys, Math.max(1, found[0])))), ticks, 6, 4);
    }
}
