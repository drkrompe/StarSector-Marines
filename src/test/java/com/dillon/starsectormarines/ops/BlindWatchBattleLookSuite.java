package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.setup.BlindWatchSequence;
import com.dillon.starsectormarines.battle.world.gen.BlindWatchSounding;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.Fonts;

import java.util.List;

/**
 * Blind Watch played through the look preset, for the owner's acceptance: the production chapter fixture and the
 * company the Corvus evidence plays ({@link CorvusEvidenceCompany}), on the prepared approach: the post stood-to, every
 * side job granted, the squads in the Convoy Escort Issue and the Hound with its SRMs, under autonomous command, from
 * the first landing to the end. One camera rides the company at play zoom, eased; one holds the whole post. Four times
 * real speed at 30 frames a second. Look evidence, not balance; opt-in.
 *
 * <p>The side jobs are granted by {@link BlindWatchSequence#SIDE_OBJECTIVES_PROPERTY}, which the battle reads: run it
 * with {@code -Dbattle.blindWatch.sideObjectives=all}, and without it the suite refuses rather than record another
 * approach.
 */
public final class BlindWatchBattleLookSuite implements SnapshotSuite {

    /** Ticks recorded: the prepared approach's headless run ends at 4,659. */
    static final int TICKS = 4740;
    /** A frame every this many ticks. */
    static final int EVERY = 4;
    /** Times real speed: a frame every four ticks played at 30 frames a second. */
    static final int SPEED = 4;
    /** Company boats, as in the Corvus evidence. */
    static final int BOATS = 2;

    @Override public String id() { return "blind-watch-battle-look"; }

    @Override public String label() { return "Blind Watch's prepared approach played through the look preset, company and post"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        if (!"all".equals(System.getProperty(BlindWatchSequence.SIDE_OBJECTIVES_PROPERTY)))
            throw new IllegalStateException("the prepared approach grants every side job: run with -D"
                    + BlindWatchSequence.SIDE_OBJECTIVES_PROPERTY + "=all");
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            // Before any battle is built, so the salvage caches' and markers' labels draw their text.
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            // The vanilla weapons the Corvus evidence plays with (the Hound's SRMs and the company's rockets among them).
            InstalledVanillaData.install();
            var chapter = CorvusChapter.BLIND_WATCH;
            var mission = CorvusEvidenceCompany.missionFor(chapter);
            // The stood-to post: the campaign holds no marks (Teague was not delivered).
            var muster = CorvusEvidenceCompany.muster(chapter, mission, BOATS, mark -> false,
                    CorvusEvidenceCompany.Kit.PREPARED);
            var start = BlindWatchSounding.generate(mission.battleSeed, chapter.profile(chapter.surface)).landing().centre();
            float wholeAcross = Math.max(BlindWatchSounding.WIDTH, BlindWatchSounding.HEIGHT * 16f / 9f);
            boolean[] houndCalled = {false};
            return LookReview.record(context.modRoot(), new LookReview.Shot("blind-watch",
                    LookGround.of(chapter.surface, mission.battleSeed), 40, 30, placement -> {
                        placement.beforeTick(sim -> {
                            if (!houndCalled[0]) houndCalled[0] = CorvusEvidenceCompany.callHound(sim);
                        });
                        return muster.build();
                    },
                    placement -> List.of(DustLedgerBattleLookSuite.company(start.x() + .5f, start.y() + .5f),
                            LookReview.View.close("post", BlindWatchSounding.WIDTH / 2f, BlindWatchSounding.HEIGHT / 2f,
                                    wholeAcross)),
                    TICKS, EVERY, 4).atSpeed(SPEED));
        }
    }
}
