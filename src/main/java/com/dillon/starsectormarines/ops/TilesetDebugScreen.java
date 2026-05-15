package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * Developer-only screen for inspecting tilesets. Loads the configured sheet,
 * slices it on a fixed pixel grid, and renders every non-empty cell with its
 * {@code (col,row)} index overlay so the user can call out which indices are
 * floor / wall / door / doodad without having to count tiles in an image editor.
 *
 * <p>Layout splits the sheet into N horizontal slabs side-by-side — at 384×1530
 * with 48px tiles that's 8×31 cells, which doesn't fit a typical dialog at 1:1.
 * The slabs let each cell render at native size so the (col,row) labels fit
 * cleanly in a corner instead of overlapping the tile art.
 *
 * <p>Empty-cell detection uses the same alpha threshold pattern as
 * {@link com.dillon.starsectormarines.battle.SpriteSheetSlicer} — anything below
 * alpha 16 reads as empty whitespace and the cell is shown as just a checker
 * backdrop, no label.
 */
public class TilesetDebugScreen implements Screen {

    private static final Logger LOG = Global.getLogger(TilesetDebugScreen.class);

    private static final String SHEET_PATH = "graphics/battle/scifi_space_rpg_tiles.png";
    private static final int TILE_SIZE = 48;
    private static final int SLAB_COUNT = 2;
    private static final int ALPHA_THRESHOLD = 16;

    private static final float PAD              = 12f;
    private static final float HEADER_H         = 36f;
    private static final float BACK_W           = 120f;
    private static final float BACK_H           = 32f;
    private static final float SLAB_GAP         = 24f;

    private static final Color HEADER_COLOR     = new Color(0xC8, 0xE0, 0xFF);
    private static final Color LABEL_COLOR      = new Color(0xFF, 0xE0, 0x70);
    private static final Color LABEL_SHADOW     = new Color(0x00, 0x00, 0x00);
    private static final Color CHECKER_A        = new Color(0x14, 0x1B, 0x26);
    private static final Color CHECKER_B        = new Color(0x1C, 0x24, 0x32);
    private static final Color GRID_LINE        = new Color(0x32, 0x42, 0x58);
    private static final Color FRAME_COLOR      = new Color(0x4A, 0x6B, 0x8C);

    private final WidgetRoot widgets = new WidgetRoot();

    private PositionAPI position;
    private MarineOpsContext ctx;

    private SpriteAPI sheet;
    private boolean sheetLoadAttempted;
    /** [col][row] occupancy flag. Null until the sheet's been parsed. */
    private boolean[][] cellHasContent;
    private int cols;
    private int rows;
    private int sheetPxW;
    private int sheetPxH;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        ensureSheet();
        rebuild();
    }

    /**
     * Lazy-loads the tileset on first attach. Same lazy-load gotcha as the
     * marine sprite — {@code getSprite} returns a wrapper whose backing texture
     * is null until {@code loadTexture} is called. We also re-read the raw PNG
     * for per-cell alpha sampling because {@code SpriteAPI} doesn't expose
     * pixel-level access.
     */
    private void ensureSheet() {
        if (sheetLoadAttempted) return;
        sheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(SHEET_PATH);
            sheet = Global.getSettings().getSprite(SHEET_PATH);
            if (sheet == null) {
                LOG.warn("TilesetDebugScreen: getSprite returned null for " + SHEET_PATH);
                return;
            }
            try (java.io.InputStream stream = Global.getSettings().openStream(SHEET_PATH)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("TilesetDebugScreen: ImageIO.read returned null for " + SHEET_PATH);
                    return;
                }
                sheetPxW = img.getWidth();
                sheetPxH = img.getHeight();
                cols = sheetPxW / TILE_SIZE;
                rows = sheetPxH / TILE_SIZE;
                cellHasContent = new boolean[cols][rows];

                int[] pixels = new int[sheetPxW * sheetPxH];
                img.getRGB(0, 0, sheetPxW, sheetPxH, pixels, 0, sheetPxW);
                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < cols; col++) {
                        cellHasContent[col][row] = cellHasAnyOpaquePixel(pixels, col, row);
                    }
                }
                LOG.info("TilesetDebugScreen: loaded " + SHEET_PATH
                        + " (" + sheetPxW + "x" + sheetPxH + "px, "
                        + cols + "x" + rows + " cells @ " + TILE_SIZE + "px)");
            }
        } catch (Exception e) {
            LOG.error("TilesetDebugScreen: failed to load " + SHEET_PATH, e);
            sheet = null;
            cellHasContent = null;
        }
    }

    private boolean cellHasAnyOpaquePixel(int[] pixels, int col, int row) {
        for (int py = 0; py < TILE_SIZE; py++) {
            int yPix = row * TILE_SIZE + py;
            int rowStart = yPix * sheetPxW;
            for (int px = 0; px < TILE_SIZE; px++) {
                int alpha = (pixels[rowStart + col * TILE_SIZE + px] >>> 24) & 0xFF;
                if (alpha >= ALPHA_THRESHOLD) return true;
            }
        }
        return false;
    }

    private void rebuild() {
        widgets.clear();
        if (position == null || ctx == null) return;

        // Back button bottom-left
        float backX = position.getX() + PAD;
        float backY = position.getY() + PAD;
        widgets.add(new ButtonWidget(backX, backY, BACK_W, BACK_H,
                () -> ctx.goTo(ScreenId.MISSION_SELECT)));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get("actionBack"),
                backX + 12f, backY + BACK_H - 6f, HEADER_COLOR));

        // Header top-left
        String header = "Tileset Debug — " + SHEET_PATH
                + " (" + cols + "x" + rows + " @ " + TILE_SIZE + "px)";
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                header,
                position.getX() + PAD,
                position.getY() + position.getHeight() - PAD - 6f,
                HEADER_COLOR));
    }

    @Override
    public void advance(float dt) {
        widgets.advance(dt);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }

    @Override
    public void render(float alphaMult) {
        if (position == null) return;
        if (sheet != null && cellHasContent != null) {
            renderSheet(alphaMult);
        }
        widgets.render(alphaMult);
    }

    private void renderSheet(float alphaMult) {
        // Reserve top header strip + bottom back strip from the dialog content.
        float viewportX = position.getX() + PAD;
        float viewportY = position.getY() + PAD + BACK_H + PAD;
        float viewportW = position.getWidth()  - 2 * PAD;
        float viewportH = position.getHeight() - 2 * PAD - BACK_H - PAD - HEADER_H;

        int rowsPerSlab = (rows + SLAB_COUNT - 1) / SLAB_COUNT;
        float slabPxW = cols * TILE_SIZE;
        float slabPxH = rowsPerSlab * TILE_SIZE;

        // Width budget includes slab gaps between adjacent slabs.
        float totalPxW = slabPxW * SLAB_COUNT + SLAB_GAP * (SLAB_COUNT - 1);
        float scale = Math.min(viewportW / totalPxW, viewportH / slabPxH);
        scale = Math.min(scale, 1f); // never upscale — tile art looks worse stretched

        float displayCell  = TILE_SIZE * scale;
        float displaySlabW = slabPxW * scale;
        float displaySlabH = slabPxH * scale;
        float displayGap   = SLAB_GAP * scale;
        float displayTotalW = displaySlabW * SLAB_COUNT + displayGap * (SLAB_COUNT - 1);
        float originX = viewportX + (viewportW - displayTotalW) / 2f;
        float originY = viewportY + (viewportH - displaySlabH) / 2f;

        // Pass 1 — checker backdrops + grid lines per slab so empty cells are visible.
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBegin(GL_QUADS);
        for (int slab = 0; slab < SLAB_COUNT; slab++) {
            int startRow = slab * rowsPerSlab;
            int endRow = Math.min(startRow + rowsPerSlab, rows);
            float slabX = originX + slab * (displaySlabW + displayGap);
            for (int row = startRow; row < endRow; row++) {
                int slabRow = row - startRow;
                float y0 = originY + (rowsPerSlab - 1 - slabRow) * displayCell;
                for (int col = 0; col < cols; col++) {
                    Color c = ((col + row) % 2 == 0) ? CHECKER_A : CHECKER_B;
                    glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alphaMult);
                    float x0 = slabX + col * displayCell;
                    float x1 = x0 + displayCell;
                    float y1 = y0 + displayCell;
                    glVertex2f(x0, y0);
                    glVertex2f(x1, y0);
                    glVertex2f(x1, y1);
                    glVertex2f(x0, y1);
                }
            }
        }
        glEnd();

        // Pass 2 — tile sprites for non-empty cells.
        float texW = sheet.getTextureWidth();
        float texH = sheet.getTextureHeight();
        float texXScale = texW / sheetPxW;
        float texYScale = texH / sheetPxH;

        for (int slab = 0; slab < SLAB_COUNT; slab++) {
            int startRow = slab * rowsPerSlab;
            int endRow = Math.min(startRow + rowsPerSlab, rows);
            float slabX = originX + slab * (displaySlabW + displayGap);
            for (int row = startRow; row < endRow; row++) {
                int slabRow = row - startRow;
                float y0 = originY + (rowsPerSlab - 1 - slabRow) * displayCell;
                for (int col = 0; col < cols; col++) {
                    if (!cellHasContent[col][row]) continue;

                    // Source UVs (V is measured from texture bottom in Starsector's GL convention).
                    sheet.setTexX(col * TILE_SIZE * texXScale);
                    sheet.setTexY((sheetPxH - (row + 1) * TILE_SIZE) * texYScale);
                    sheet.setTexWidth(TILE_SIZE * texXScale);
                    sheet.setTexHeight(TILE_SIZE * texYScale);
                    sheet.setSize(displayCell, displayCell);
                    sheet.setAlphaMult(alphaMult);
                    sheet.setColor(Color.WHITE);
                    sheet.setNormalBlend();

                    float cx = slabX + col * displayCell + displayCell / 2f;
                    float cy = y0 + displayCell / 2f;
                    sheet.renderAtCenter(cx, cy);
                }
            }
        }

        // Pass 3 — grid lines per slab.
        glDisable(GL_TEXTURE_2D);
        glColor4f(GRID_LINE.getRed() / 255f, GRID_LINE.getGreen() / 255f,
                GRID_LINE.getBlue() / 255f, 0.6f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINES);
        for (int slab = 0; slab < SLAB_COUNT; slab++) {
            float slabX = originX + slab * (displaySlabW + displayGap);
            for (int c = 0; c <= cols; c++) {
                float lx = slabX + c * displayCell;
                glVertex2f(lx, originY);
                glVertex2f(lx, originY + displaySlabH);
            }
            for (int r = 0; r <= rowsPerSlab; r++) {
                float ly = originY + r * displayCell;
                glVertex2f(slabX,                ly);
                glVertex2f(slabX + displaySlabW, ly);
            }
        }
        glEnd();

        // Pass 4 — frame outlines around each slab in the column accent color.
        glColor4f(FRAME_COLOR.getRed() / 255f, FRAME_COLOR.getGreen() / 255f,
                FRAME_COLOR.getBlue() / 255f, 0.9f * alphaMult);
        glLineWidth(1.5f);
        glBegin(org.lwjgl.opengl.GL11.GL_LINES);
        for (int slab = 0; slab < SLAB_COUNT; slab++) {
            float slabX = originX + slab * (displaySlabW + displayGap);
            float l = slabX;
            float r = slabX + displaySlabW;
            float t = originY + displaySlabH;
            float b = originY;
            glVertex2f(l, b); glVertex2f(r, b);
            glVertex2f(r, b); glVertex2f(r, t);
            glVertex2f(r, t); glVertex2f(l, t);
            glVertex2f(l, t); glVertex2f(l, b);
        }
        glEnd();

        // Pass 5 — (col,row) labels on each non-empty cell with a 1px shadow
        // for readability against the tile art.
        for (int slab = 0; slab < SLAB_COUNT; slab++) {
            int startRow = slab * rowsPerSlab;
            int endRow = Math.min(startRow + rowsPerSlab, rows);
            float slabX = originX + slab * (displaySlabW + displayGap);
            for (int row = startRow; row < endRow; row++) {
                int slabRow = row - startRow;
                float y0 = originY + (rowsPerSlab - 1 - slabRow) * displayCell;
                for (int col = 0; col < cols; col++) {
                    if (!cellHasContent[col][row]) continue;
                    String label = col + "," + row;
                    float lx = slabX + col * displayCell + 2f;
                    float ly = y0 + displayCell - 4f;
                    Fonts.ORBITRON_20.drawString(label, lx + 1f, ly - 1f, LABEL_SHADOW, alphaMult);
                    Fonts.ORBITRON_20.drawString(label, lx,        ly,        LABEL_COLOR,  alphaMult);
                }
            }
        }
    }
}
