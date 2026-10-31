package com.dillon.starsectormarines.battle.evacuation;

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The cordon ridge's guns as the owner would see them (`heart-nouns.md`, Cordon's works): the commander look's Broad
 * Front battle played a short way in, then recorded on the forward line's guns and flak nearest the middle of the front
 * while the columns go up through its gaps. Stands the battle up, so opt-in.
 */
public final class HeartRidgeLookSuite implements SnapshotSuite {

    /** Ticks played unrecorded first: the lanes' first waves have stepped off. */
    static final int FROM = 300;
    /** Ridge guns the camera holds on. */
    static final int GUNS = 3;

    @Override public String id() { return "heart-ridge-look"; }

    @Override public String label() { return "Heart cordon ridge: the forward line's guns and flak on Broad Front"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            return new ArrayList<>(LookReview.record(context.modRoot(), shot()));
        }
    }

    static LookReview.Shot shot() {
        String[] keys = new String[GUNS];
        for (int i = 0; i < GUNS; i++) keys[i] = "gun" + i;
        return new LookReview.Shot("ridge-guns", LookGround.verdant(), 40, 30, placement -> {
            BattleSimulation sim;
            try {
                sim = HeadlessBattles.fixture(new JSONObject(HeartCommanderLookSuite.FIXTURE));
            } catch (Exception e) {
                throw new IllegalStateException("heart fixture", e);
            }
            while (sim.getSimTickIndex() < FROM) sim.advance(BattleSimulation.TICK_DT);
            float middle = sim.getGrid().getWidth() / 2f;
            List<Long> guns = new ArrayList<>();
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) == Faction.ALLY && sim.turretState().isTurret(unit)) guns.add(unit);
            }
            guns.sort(Comparator.comparingDouble(u -> Math.abs(sim.world().x(u) - middle)));
            for (int i = 0; i < Math.min(GUNS, guns.size()); i++) placement.subject(keys[i], guns.get(i));
            System.out.printf("    [look/ridge] %d allied guns standing, %d framed%n", guns.size(), Math.min(GUNS, guns.size()));
            return sim;
        }, placement -> List.of(LookReview.View.follow("play", placement, 70f, keys)), 900, 6, 4);
    }
}
