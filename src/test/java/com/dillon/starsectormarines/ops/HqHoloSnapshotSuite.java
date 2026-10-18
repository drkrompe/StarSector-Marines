package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.HqActionItem.Action;
import com.dillon.starsectormarines.ops.HqActionItem.Kind;
import com.dillon.starsectormarines.ops.HqActionItem.Verb;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The command-deck banner with the action board's hologram over the plotting table,
 * drawn at the pixel size the banner has on a 1920x1080 / 100% HQ page. A fixture board
 * holds one item of each kind plus an obligation falling due; one still catches the
 * markers rising in, one the settled board with a selection and a hover, and a GIF the
 * sweep and pulse.
 */
public final class HqHoloSnapshotSuite implements SnapshotSuite {
    /** The banner canvas on the 1920x1080 / 100% HQ page. */
    private static final int WIDTH = 1872;
    private static final int HEIGHT = 372;
    private static final int FRAME_DELAY_MILLIS = 80;
    private static final int FRAME_COUNT = 82;

    @Override public String id() { return "hq-holo"; }
    @Override public String label() { return "Company HQ holo action board"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        Fonts.INSIGNIA_15_AA.installMetrics(Files.readString(
                context.starsectorCore().resolve("graphics/fonts/insignia15LTaa.fnt")));
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        CommandDeckLayout layout = CommandDeckLayout.parse(
                Files.readString(context.modRoot().resolve(CommandDeckLayout.PATH)));
        CommandDeckBanner banner = new CommandDeckBanner(new CompanyHqNewsDesk(new Reactor()), layout);
        banner.board(fixture());

        List<BufferedImage> frames = new ArrayList<>(FRAME_COUNT);
        BufferedImage rising = null;
        BufferedImage settled = null;
        BufferedImage selected = null;
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            if (frame == 40) {
                banner.holoSelected("story-corvus");
                banner.holoHovered("side-distress");
            }
            BufferedImage image = renderer.renderCanvas(banner, WIDTH, HEIGHT);
            frames.add(image);
            if (frame == 5) rising = image;
            if (frame == 36) settled = image;
            if (frame == 72) selected = image;
            banner.advance(FRAME_DELAY_MILLIS / 1000f);
        }
        return List.of(
                new SnapshotArtifact("hq-holo-rising-1872x372.png", rising),
                new SnapshotArtifact("hq-holo-settled-1872x372.png", settled),
                new SnapshotArtifact("hq-holo-selected-1872x372.png", selected),
                SnapshotArtifact.animation("hq-holo-1872x372.gif", frames, FRAME_DELAY_MILLIS));
    }

    /** One item of each kind, an obligation falling due, and two with no position (rim). */
    static HqActionBoard fixture() {
        Action act = Action.of(Verb.PLOT_COURSE, () -> { });
        return HqActionBoard.of(List.of(
                new HqActionItem("obligation-garrison", Kind.OBLIGATION, "Garrison defense", "Sindria",
                        1, Action.of(Verb.RESPOND, () -> { }), null, new float[]{-5200f, -7800f}),
                new HqActionItem("story-corvus", Kind.STORY, "Corvus account", "Asharu / Corvus",
                        HqActionItem.NO_DEADLINE, act, null, new float[]{-2400f, -2600f}),
                new HqActionItem("letter-hegemony", Kind.LETTER, "Hegemony procurement terms", "",
                        HqActionItem.NO_DEADLINE, Action.of(Verb.OPEN, () -> { }), null, null),
                new HqActionItem("side-distress", Kind.SIDE_JOB, "Distress call", "Eochu Bres",
                        HqActionItem.NO_DEADLINE, act, null, new float[]{9800f, 4200f}),
                new HqActionItem("side-bounty", Kind.SIDE_JOB, "Pirate base bounty", "Magec",
                        HqActionItem.NO_DEADLINE, act, null, new float[]{-14000f, 12500f}),
                new HqActionItem("obligation-term", Kind.OBLIGATION, "Stationing term ends", "Jangala",
                        12, Action.unavailable(Verb.TRACKING, "Tracking"), null, null)));
    }
}
