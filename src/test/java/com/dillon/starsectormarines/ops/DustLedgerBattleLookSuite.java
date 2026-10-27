package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/**
 * The Dust Ledger played as it ships, through the look preset: the production chapter fixture with a two-boat
 * company under autonomous command, from the first landing on the freight apron to the end. One camera rides the
 * company at play zoom; one holds the whole compound. Look evidence, not balance; opt-in.
 */
public final class DustLedgerBattleLookSuite implements SnapshotSuite {

    /** Ticks recorded: the headless baseline ends near 6,700. */
    static final int TICKS = 7200;
    /** A frame every this many ticks: four times real speed at 30 fps. */
    static final int EVERY = 4;
    /** Camera easing per recorded frame, so the follow camera does not jitter with the squads. */
    static final float EASE = 0.12f;

    @Override public String id() { return "dust-ledger-battle-look"; }

    @Override public String label() { return "The Dust Ledger played through the look preset, company and compound"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            var chapter = CorvusChapter.DUST_LEDGER;
            var mission = chapter.mission("look", chapter.planetName, "");
            var manifest = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6),
                    new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
            var fixture = chapter.fixture(mission.battleSeed, manifest, chapter.profile(chapter.surface));
            return LookReview.record(context.modRoot(), new LookReview.Shot("dust-ledger",
                    LookGround.of(SurfacePalette.ARID, mission.battleSeed), 40, 30, placement -> fixture.build(),
                    placement -> List.of(company(22f, 65f), LookReview.View.close("compound", 179f, 50f, 96f)),
                    TICKS, EVERY, 4));
        }
    }

    /**
     * Play zoom on the middle of the company's live ground units, eased; on {@code (startX, startY)} until the first is
     * down, and holding where it was once they are gone.
     */
    static LookReview.View company(float startX, float startY) {
        float[] at = {Float.NaN, Float.NaN};
        return new LookReview.View("company", sim -> {
            float sumX = 0f, sumY = 0f;
            int n = 0;
            for (int i = 0, live = sim.liveUnitCount(); i < live; i++) {
                long id = sim.liveUnitAt(i);
                if (sim.identity().faction(id) != Faction.MARINE || !sim.world().hasPosition(id) || sim.isRiding(id)) continue;
                sumX += sim.world().renderX(id);
                sumY += sim.world().renderY(id);
                n++;
            }
            if (n > 0) {
                float x = sumX / n, y = sumY / n;
                if (Float.isNaN(at[0])) { at[0] = x; at[1] = y; }
                else { at[0] += (x - at[0]) * EASE; at[1] += (y - at[1]) * EASE; }
            }
            if (Float.isNaN(at[0])) return new float[] {startX, startY, 60f};
            return new float[] {at[0], at[1], 60f};
        });
    }
}
