package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The screen a battle shows while its textures load, in each look and with each kind of
 * progress line, at 1920x1080 and 100% (`battle-textures.md`, Loading screen). The minimum
 * display time is shown as a timeline of a fast load, since a duration has no still picture.
 */
public final class BattleLoadingSnapshotSuite implements SnapshotSuite {

    private static final String OPERATION = "TAKE BACK THE DEPOT";
    private static final String AREA = "Agreus - freight yards, east district";
    private static final String STEP = "Armor: Charcoal";
    private static final float PROGRESS = 0.45f;
    private static final long BYTES = 212L * 1024 * 1024;

    @Override public String id() { return "battle-loading"; }
    @Override public String label() { return "Battle loading screen options"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        List<SnapshotArtifact> out = new ArrayList<>();
        out.add(new SnapshotArtifact("look-01-card.png", frame(context, renderer,
                BattleLoadingOverlayModel.Look.CARD, BattleLoadingOverlayModel.Detail.STEP, PROGRESS)));
        out.add(new SnapshotArtifact("look-02-strip.png", frame(context, renderer,
                BattleLoadingOverlayModel.Look.STRIP, BattleLoadingOverlayModel.Detail.STEP, PROGRESS)));
        out.add(new SnapshotArtifact("look-03-minimal.png", frame(context, renderer,
                BattleLoadingOverlayModel.Look.MINIMAL, BattleLoadingOverlayModel.Detail.STEP, PROGRESS)));
        out.add(new SnapshotArtifact("detail-01-step.png", frame(context, renderer,
                BattleLoadingOverlayModel.Look.CARD, BattleLoadingOverlayModel.Detail.STEP, PROGRESS)));
        out.add(new SnapshotArtifact("detail-02-percent.png", frame(context, renderer,
                BattleLoadingOverlayModel.Look.CARD, BattleLoadingOverlayModel.Detail.PERCENT, PROGRESS)));
        out.add(new SnapshotArtifact("detail-03-megabytes.png", frame(context, renderer,
                BattleLoadingOverlayModel.Look.CARD, BattleLoadingOverlayModel.Detail.MEGABYTES, PROGRESS)));
        BufferedImage card = frame(context, renderer,
                BattleLoadingOverlayModel.Look.CARD, BattleLoadingOverlayModel.Detail.STEP, 1f);
        out.add(new SnapshotArtifact("minimum-01-none.png", timeline(card, 0f)));
        out.add(new SnapshotArtifact("minimum-02-half-second.png", timeline(card, 0.5f)));
        out.add(new SnapshotArtifact("minimum-03-one-second.png", timeline(card, 1f)));
        return out;
    }

    private static BufferedImage frame(SnapshotContext context, HeadlessUiRenderer renderer,
                                       BattleLoadingOverlayModel.Look look,
                                       BattleLoadingOverlayModel.Detail detail, float progress) throws Exception {
        Reactor reactor = new Reactor();
        BattleLoadingOverlayModel model = new BattleLoadingOverlayModel(reactor, look, detail);
        model.heading(OPERATION, AREA);
        model.update(progress, STEP, BYTES);
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                List.of(BattleLoadingOverlay.COMPONENT_PATH));
        loader.reload();
        try (MarkupInstance markup = loader.build(reactor, BattleLoadingOverlay.COMPONENT, model.props())) {
            markup.requireElement("battle-loading-progress").align(UiAlign.START, UiAlign.STRETCH);
            UiDocument document = new UiDocument(markup.root());
            for (var style : markup.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
        }
    }

    /**
     * What a 0.2-second load looks like under a minimum display time: the loading screen's
     * stretch of a two-second strip, then the battle.
     */
    private static BufferedImage timeline(BufferedImage card, float minimumSeconds) {
        float loadSeconds = 0.2f;
        float shown = Math.max(loadSeconds, minimumSeconds);
        BufferedImage image = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0x0b110e));
            g.fillRect(0, 0, 1920, 1080);
            g.setColor(new Color(0xe6e7da));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
            g.drawString(minimumSeconds == 0f ? "No minimum"
                    : "Minimum " + trim(minimumSeconds) + " s", 120, 150);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
            g.setColor(new Color(0xa0ada8));
            g.drawString("A small battle whose textures load in 0.2 s. The loading screen stays up "
                    + trim(shown) + " s.", 120, 200);

            int left = 120, right = 1800, top = 300, height = 260;
            float span = 2f;
            float pxPerSecond = (right - left) / span;
            int split = left + Math.round(shown * pxPerSecond);
            // The loading screen, squeezed into its stretch of the strip.
            g.drawImage(card, left, top, split - left, height, null);
            g.setColor(new Color(0x2d3a33));
            g.fillRect(split, top, right - split, height);
            g.setColor(new Color(0xa7ccc4));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            g.drawString("battle", split + 30, top + height / 2 + 10);
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(0x727b79));
            g.drawRect(left, top, right - left, height);
            g.drawLine(split, top - 20, split, top + height + 20);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
            g.setColor(new Color(0xa0ada8));
            for (int tick = 0; tick <= 4; tick++) {
                float s = tick * 0.5f;
                int x = left + Math.round(s * pxPerSecond);
                g.drawLine(x, top + height, x, top + height + 12);
                g.drawString(trim(s) + " s", x - 18, top + height + 42);
            }
            int loadEnd = left + Math.round(loadSeconds * pxPerSecond);
            g.setColor(new Color(0xffd464));
            g.drawLine(loadEnd, top + height + 60, loadEnd, top + height + 90);
            g.drawString("textures loaded", loadEnd - 10, top + height + 120);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static String trim(float seconds) {
        return seconds == Math.round(seconds) ? Integer.toString(Math.round(seconds)) : Float.toString(seconds);
    }
}
