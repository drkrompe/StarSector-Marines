package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Deterministic Fleet Armory loadout evidence. */
public final class ArmorySnapshotSuite implements SnapshotSuite {

    @Override
    public String id() {
        return "armory";
    }

    @Override
    public String label() {
        return "Fleet Armory";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) {
        HeadlessArmoryPreviewRenderer renderer =
                new HeadlessArmoryPreviewRenderer(context.modRoot());
        List<HeadlessArmoryPreviewRenderer.PreviewCase> cases =
                HeadlessArmoryPreviewRenderer.previewCases();
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        List<BufferedImage> rendered = new ArrayList<>();
        for (HeadlessArmoryPreviewRenderer.PreviewCase preview : cases) {
            BufferedImage image = renderer.render(preview.billet());
            rendered.add(image);
            artifacts.add(new SnapshotArtifact(preview.slug() + ".png", image));
        }
        artifacts.add(new SnapshotArtifact(
                "armory-loadout-contact.png", contactSheet(cases, rendered)));
        return List.copyOf(artifacts);
    }

    private static BufferedImage contactSheet(
            List<HeadlessArmoryPreviewRenderer.PreviewCase> cases,
            List<BufferedImage> rendered) {
        int previewWidth = ArmoryLoadoutPreviewComposer.SURFACE_WIDTH;
        int previewHeight = ArmoryLoadoutPreviewComposer.SURFACE_HEIGHT;
        BufferedImage contact = new BufferedImage(
                previewWidth * 2, (previewHeight + 34) * 2,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = contact.createGraphics();
        configure(graphics);
        graphics.setColor(new Color(0x06, 0x0B, 0x11));
        graphics.fillRect(0, 0, contact.getWidth(), contact.getHeight());
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        for (int index = 0; index < cases.size(); index++) {
            int x = index % 2 * previewWidth;
            int y = index / 2 * (previewHeight + 34);
            graphics.setColor(new Color(0xD9, 0xE8, 0xF1));
            graphics.drawString(cases.get(index).label(), x + 12, y + 22);
            graphics.drawImage(rendered.get(index), x, y + 34, null);
        }
        graphics.dispose();
        return contact;
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
    }
}
