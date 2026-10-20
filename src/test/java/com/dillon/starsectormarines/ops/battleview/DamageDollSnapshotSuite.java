package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ops.ModStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Every damage doll under {@code mod/graphics/ui/dolls/}, intact beside a scripted damage state
 * that walks every colour the doll can show: whole, worn armor, armor gone, structure low,
 * destroyed. {@code dolls-ui100-*.png} draws each at its HUD size on 1920x1080 sheets;
 * {@code dolls-detail-*.png} draws the same art three times larger for line review.
 */
public final class DamageDollSnapshotSuite implements SnapshotSuite {

    @Override public String id() { return "damage-doll"; }
    @Override public String label() { return "Damage dolls"; }

    /** Per location: armor fraction, structure fraction, destroyed. Absent locations are skipped. */
    private static final Map<DollLocation, float[]> DAMAGED = new EnumMap<>(DollLocation.class);
    static {
        DAMAGED.put(DollLocation.FRONT, new float[] {1f, 1f, 0f});
        DAMAGED.put(DollLocation.LEFT, new float[] {0f, .35f, 0f});
        DAMAGED.put(DollLocation.RIGHT, new float[] {.55f, 1f, 0f});
        DAMAGED.put(DollLocation.REAR, new float[] {.15f, 1f, 0f});
        DAMAGED.put(DollLocation.HULL, new float[] {0f, .7f, 0f});
        DAMAGED.put(DollLocation.TURRET, new float[] {0f, 0f, 1f});
        DAMAGED.put(DollLocation.DRIVE, new float[] {0f, .3f, 0f});
        DAMAGED.put(DollLocation.HEAD, new float[] {.6f, 1f, 0f});
        DAMAGED.put(DollLocation.TORSO, new float[] {0f, .55f, 0f});
        DAMAGED.put(DollLocation.TORSO_REAR, new float[] {.3f, 1f, 0f});
        DAMAGED.put(DollLocation.ARMS, new float[] {1f, 1f, 0f});
        DAMAGED.put(DollLocation.LEFT_SHOULDER, new float[] {0f, 0f, 1f});
        DAMAGED.put(DollLocation.RIGHT_SHOULDER, new float[] {.85f, 1f, 0f});
        DAMAGED.put(DollLocation.LEGS, new float[] {0f, .2f, 0f});
    }
    private static final boolean[] HAS_ARMOR = new boolean[DollLocation.values().length];
    static {
        for (DollLocation location : DollLocation.values()) HAS_ARMOR[location.ordinal()] = true;
        HAS_ARMOR[DollLocation.HULL.ordinal()] = false;
        HAS_ARMOR[DollLocation.DRIVE.ordinal()] = false;
    }

    private static final Color BACKDROP = new Color(18, 25, 30);
    private static final Color GRID = new Color(25, 34, 39);
    private static final Color CAPTION = new Color(0xC5, 0xD6, 0xCB);

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        Path modRoot = context.modRoot();
        SvgAsset.Source previous = SvgAsset.setSource(path -> Files.readString(modRoot.resolve(path)));
        try {
            HeadlessUiRenderer renderer = new HeadlessUiRenderer(modRoot, context.starsectorCore());
            List<String> dolls;
            try (Stream<Path> files = Files.list(modRoot.resolve(DamageDollCanvas.DOLL_DIR))) {
                dolls = files.map(file -> file.getFileName().toString())
                        .filter(name -> name.endsWith(".svg"))
                        .map(name -> name.substring(0, name.length() - 4)).sorted().toList();
            }
            List<SnapshotArtifact> artifacts = new ArrayList<>();
            UnaryOperator<String> strings = ModStrings.fromDisk(modRoot);
            artifacts.addAll(sheets(renderer, strings, dolls, 1f, "dolls-ui100", 8, 2));
            artifacts.addAll(sheets(renderer, strings, dolls, 3.5f, "dolls-detail", 2, 1));
            return artifacts;
        } finally {
            SvgAsset.setSource(previous);
        }
    }

    /** Sheets of {@code perSheet} dolls, each as an intact / damaged pair, drawn at {@code scale}. */
    private static List<SnapshotArtifact> sheets(HeadlessUiRenderer renderer, Function<String, String> strings,
                                                 List<String> dolls, float scale,
                                                 String prefix, int perSheet, int pairsPerRow) {
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        int w = Math.round(DamageDollCanvas.SURFACE_WIDTH * scale);
        int h = Math.round(DamageDollCanvas.SURFACE_HEIGHT * scale);
        for (int sheet = 0; sheet * perSheet < dolls.size(); sheet++) {
            BufferedImage image = backdrop();
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
                List<String> page = dolls.subList(sheet * perSheet, Math.min(dolls.size(), (sheet + 1) * perSheet));
                int rows = (page.size() + pairsPerRow - 1) / pairsPerRow;
                int rowPitch = h + 44;
                int top = (1080 - rowPitch * rows) / 2 + 30;
                int gap = 40;
                int pairGap = 140;
                int pairWidth = w * 2 + gap;
                int left = (1920 - pairWidth * pairsPerRow - pairGap * (pairsPerRow - 1)) / 2;
                for (int index = 0; index < page.size(); index++) {
                    String doll = page.get(index);
                    String path = DamageDollCanvas.DOLL_DIR + doll + ".svg";
                    int y = top + (index / pairsPerRow) * rowPitch;
                    int pairLeft = left + (index % pairsPerRow) * (pairWidth + pairGap);
                    for (int col = 0; col < 2; col++) {
                        boolean damaged = col == 1;
                        DamageDollCanvas.View view = view(path, damaged);
                        DamageDollCanvas canvas = new DamageDollCanvas(view, strings);
                        CanvasProducer producer = scale == 1f ? canvas
                                : context -> SvgAsset.load(path).drawParts(context, 0f, 0f, w, h,
                                part -> {
                                    DollLocation location = DollLocation.ofPart(part);
                                    return location == null ? null : view.color(location.ordinal());
                                });
                        BufferedImage tile = renderer.renderCanvas(producer, w, h);
                        int x = pairLeft + col * (w + gap);
                        graphics.drawImage(tile, x, y, null);
                        graphics.setColor(CAPTION);
                        graphics.drawString(doll + (damaged ? ", damaged" : ", intact"), x, y - 10);
                    }
                }
            } finally {
                graphics.dispose();
            }
            artifacts.add(new SnapshotArtifact(prefix + "-" + (sheet + 1) + ".png", image));
        }
        return artifacts;
    }

    private static DamageDollCanvas.View view(String path, boolean damaged) {
        DamageDollCanvas.View view = new DamageDollCanvas.View();
        view.begin(path);
        for (String part : SvgAsset.load(path).parts()) {
            DollLocation location = DollLocation.ofPart(part);
            if (location == null) continue;
            boolean armored = HAS_ARMOR[location.ordinal()];
            if (!damaged) {
                view.set(location, true, armored, 1f, 1f, false);
            } else {
                float[] state = DAMAGED.get(location);
                view.set(location, true, armored, state[0], state[1], state[2] > 0f);
            }
        }
        return view;
    }

    private static BufferedImage backdrop() {
        BufferedImage image = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(BACKDROP);
        graphics.fillRect(0, 0, 1920, 1080);
        graphics.setColor(GRID);
        for (int x = 0; x < 1920; x += 48) graphics.drawLine(x, 0, x, 1080);
        for (int y = 0; y < 1080; y += 48) graphics.drawLine(0, y, 1920, y);
        graphics.dispose();
        return image;
    }
}
