package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
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
 * shoots and how it stands under acid), and once on a Broad Front town battle (arid, seed 3) after its own satellites
 * are down, going to one the lanes left standing ({@code company-helps}). Each stands the battle up and plays it to the
 * shot unrecorded; opt-in. {@code lance-standoff} follows the company's mech lance on Broad Front verdant 3 as the
 * Brutes reach it, stepping back from their charges. {@code frozen5-endgame} watches Broad Front frozen 5's last mother
 * fall.
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
    /** The town battle the company wins by helping the lanes (2026-10-25), and the squad followed there. */
    static final String HELP_FIXTURE = "{\"schemaVersion\":2,\"kind\":\"BLOOM_HEART\",\"seed\":3,\"form\":\"BROAD_FRONT\","
            + "\"biome\":\"ARID\",\"density\":\"TOWN\",\"declaredMechDrops\":{\"MARINE\":{\"lances\":1,"
            + "\"lance\":[\"BULWARK\",\"HOUND\",\"HOUND\",\"WARDEN\"],\"debugCallTick\":300}},\"shuttles\":["
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6},"
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6},"
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6},"
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6}]}";
    static final int HELP_SQUAD = 28;
    /** The open-country battle whose lance the Brutes caught before the stand-off (2026-10-25), and the lance's squad. */
    static final String LANCE_FIXTURE = HELP_FIXTURE.replace("\"ARID\"", "\"VERDANT\"").replace(",\"density\":\"TOWN\"", "");
    static final int LANCE_SQUAD = 30;
    /** Broad Front frozen 5, which the company and the lanes win together (2026-10-26), and its last mother's cell. */
    static final String FROZEN_FIXTURE = LANCE_FIXTURE.replace("\"VERDANT\"", "\"FROZEN\"").replace("\"seed\":3", "\"seed\":5");
    static final float[] MOTHER_1 = {371.5f, 312.5f};

    @Override public String id() { return "heart-company-look"; }

    @Override public String label() { return "Heart Marine company: one squad on the march and at its first contact (Broad Front city), and helping the lanes (Broad Front town)"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            List<SnapshotArtifact> out = new ArrayList<>();
            String only = System.getProperty("snapshot.heartCompany.shots", "");
            if (only.isEmpty() || only.contains("march"))
                out.addAll(LookReview.record(context.modRoot(), shot("company-march", FIXTURE, SQUAD, LookGround.verdant(), 4500, 600, 40f)));
            if (only.isEmpty() || only.contains("contact"))
                out.addAll(LookReview.record(context.modRoot(), shot("company-contact", FIXTURE, SQUAD, LookGround.verdant(), 5950, 900, 50f)));
            if (only.isEmpty() || only.contains("helps"))
                out.addAll(LookReview.record(context.modRoot(), shot("company-helps", HELP_FIXTURE, HELP_SQUAD,
                        LookGround.of(SurfacePalette.ARID, 3), 14000, 900, 60f)));
            if (only.isEmpty() || only.contains("lance"))
                out.addAll(LookReview.record(context.modRoot(), shot("lance-standoff", LANCE_FIXTURE, LANCE_SQUAD,
                        LookGround.verdant(), 6500, 900, 50f)));
            if (only.isEmpty() || only.contains("endgame"))
                out.addAll(LookReview.record(context.modRoot(), watch("frozen5-endgame", FROZEN_FIXTURE,
                        LookGround.of(SurfacePalette.FROZEN, 5), 24500, 900, MOTHER_1[0], MOTHER_1[1], 60f)));
            return out;
        }
    }

    /** {@code fixture} played unrecorded to {@code fromTick}, then {@code ticks} recorded on a fixed camera at {@code (x, y)}. */
    static LookReview.Shot watch(String name, String fixture, LookGround ground, int fromTick, int ticks,
                                 float x, float y, float cellsAcross) {
        return new LookReview.Shot(name, ground, 40, 30, placement -> {
            BattleSimulation sim;
            try {
                sim = HeadlessBattles.fixture(new JSONObject(fixture));
            } catch (Exception e) {
                throw new IllegalStateException("heart fixture", e);
            }
            while (sim.getSimTickIndex() < fromTick) sim.advance(BattleSimulation.TICK_DT);
            System.out.printf("    [look/%s] at tick %d%n", name, sim.getSimTickIndex());
            return sim;
        }, placement -> List.of(LookReview.View.close("play", x, y, cellsAcross)), ticks, 6, 4);
    }

    /** {@code fixture} played unrecorded to {@code fromTick}, then {@code ticks} recorded {@code cellsAcross} wide on {@code squad}. */
    static LookReview.Shot shot(String name, String fixture, int squad, LookGround ground, int fromTick, int ticks,
                                float cellsAcross) {
        String[] keys = {"m0", "m1", "m2", "m3", "m4", "m5"};
        int[] found = {0};
        return new LookReview.Shot(name, ground, 40, 30, placement -> {
            BattleSimulation sim;
            try {
                sim = HeadlessBattles.fixture(new JSONObject(fixture));
            } catch (Exception e) {
                throw new IllegalStateException("heart fixture", e);
            }
            while (sim.getSimTickIndex() < fromTick) sim.advance(BattleSimulation.TICK_DT);
            int n = 0;
            for (int i = 0, count = sim.liveUnitCount(); i < count && n < keys.length; i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.squad().hasSquad(unit) && sim.squad().squadId(unit) == squad) placement.subject(keys[n++], unit);
            }
            found[0] = n;
            System.out.printf("    [look/%s] squad %d, %d members at tick %d%n", name, squad, n, sim.getSimTickIndex());
            return sim;
        }, placement -> List.of(LookReview.View.follow("play", placement, cellsAcross,
                Arrays.copyOf(keys, Math.max(1, found[0])))), ticks, 6, 4);
    }
}
