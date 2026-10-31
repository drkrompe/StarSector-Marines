package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * The Burn's air wing as it ships: the airbase on station behind the start line and an observer with each
 * battlegroup's lane (`heart-nouns.md`, Forward observer). One Burn battle (the evidence fixture, seed 202) played
 * unrecorded to the first sortie the wing flies, then recorded through the look preset over its aim. Plays the battle
 * to the sortie, which costs a minute or two; opt-in.
 */
public final class BurnAirLookSuite implements SnapshotSuite {

    /** The latest tick the wing's first sortie is waited for. */
    static final int SORTIE_BY = 14000;
    /** Ticks recorded from the sortie: its run in, the stick and the smoke. */
    static final int RECORD_TICKS = 450;

    @Override public String id() { return "burn-air-look"; }

    @Override public String label() { return "The Burn's air wing: its first sortie from the airbase behind the start line"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            JSONObject fixture = new JSONObject(Files.readString(context.projectRoot()
                    .resolve("src/test/resources/battle-fixtures/heart-burn-v1.json"), StandardCharsets.UTF_8));
            float[] aim = firstSortie(fixture);
            return LookReview.record(context.modRoot(), shot("burn-air", fixture, aim));
        }
    }

    /**
     * Plays the battle unrecorded to the wing's first launch; returns its tick and aim
     * as {@code {tick, x, y}}, or a zero tick when none flew by {@link #SORTIE_BY}.
     */
    static float[] firstSortie(JSONObject fixture) throws Exception {
        BattleSimulation sim = HeadlessBattles.fixture(fixture);
        while (sim.getSimTickIndex() < SORTIE_BY) {
            sim.advance(BattleSimulation.TICK_DT);
            if (sim.getSimTickIndex() % 600 == 0) gates(sim);
            for (AirPoolService.Event e : sim.airPool().events()) {
                if (e.side() == Faction.ALLY && e.type() == AirPoolService.EventType.LAUNCHED) {
                    System.out.printf("    [look/burn] first sortie at tick %d over (%.0f, %.0f): %s%n",
                            sim.getSimTickIndex(), e.x(), e.y(), sim.airPool().events());
                    return new float[]{sim.getSimTickIndex(), e.x(), e.y()};
                }
            }
        }
        System.out.println("    [look/burn] no sortie flew by tick " + SORTIE_BY);
        return new float[3];
    }

    /** The battle played unrecorded to {@code aim}'s tick and recorded over its point. */
    static LookReview.Shot shot(String name, JSONObject fixture, float[] aim) {
        return new LookReview.Shot(name, LookGround.verdant(), 40, 30, placement -> {
            BattleSimulation sim;
            try {
                sim = HeadlessBattles.fixture(fixture);
            } catch (Exception e) {
                throw new IllegalStateException("burn fixture", e);
            }
            while (sim.getSimTickIndex() < aim[0]) sim.advance(BattleSimulation.TICK_DT);
            return sim;
        }, placement -> List.of(LookReview.View.play(aim[1], aim[2])), RECORD_TICKS, 3, 4);
    }

    /** Every twenty seconds: which of the officer's gates the field stands at, for the question's note. */
    private static void gates(BattleSimulation sim) {
        List<CommsOperators.Operator> eyes = CommsOperators.available(sim, Faction.ALLY);
        long seen = com.dillon.starsectormarines.battle.air.EnemyConcentration.densestOnGround(sim, Faction.ALLY, u -> {
            for (CommsOperators.Operator eye : eyes) {
                if (CommsOperators.knowledge(sim, eye, sim.world().x(u), sim.world().y(u), false) == CommsOperators.Knowledge.SIGHT) return true;
            }
            return false;
        });
        long anywhere = com.dillon.starsectormarines.battle.air.EnemyConcentration.densestOnGround(sim, Faction.ALLY, u -> true);
        AirStrikeDirector officer = sim.airPool().director(Faction.ALLY);
        String verdict = seen == 0L ? "none in sight"
                : officer.dangerClose(sim, sim.world().x(seen), sim.world().y(seen)) ? "in sight, danger close"
                : String.format("in sight, risk %.2f", officer.risk(sim, sim.world().x(seen), sim.world().y(seen)));
        System.out.printf("    [burn-gates] t=%ds observers free %d, a mass anywhere %s, %s%n", sim.getSimTickIndex() / 30,
                eyes.size(), anywhere != 0L, verdict);
    }
}
