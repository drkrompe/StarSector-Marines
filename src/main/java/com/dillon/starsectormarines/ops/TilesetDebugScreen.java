package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sprites.NatureTile;
import com.dillon.starsectormarines.battle.sprites.NatureTileset;
import com.dillon.starsectormarines.battle.sprites.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.sprites.SpriteSheetSlicer;
import com.dillon.starsectormarines.battle.sprites.UrbanTile3;
import com.dillon.starsectormarines.battle.sprites.UrbanTile3Tileset;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.TextFieldWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
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
 * Developer-facing tileset catalog. Loads one sheet at a time, slices it on a
 * fixed pixel grid, and lets the user click a non-empty cell to attach a
 * short {@code name} + {@code description} that persists to
 * {@code data/tilesets/<basename>.catalog.json} via {@link TilesetCatalog}.
 *
 * <p>Drives both: (a) browsing the grid with {@code (col, row)} overlays so
 * we can call out which cells are which without counting tiles in an external
 * editor, and (b) building a stable hand-curated note file we can reference
 * when wiring tiles into {@link com.dillon.starsectormarines.battle.map.TileManifest}.
 *
 * <p>Sheet list is hard-coded — there are four right now and a runtime
 * directory scan adds latency for no benefit. Add a new sheet by appending
 * to {@link #SHEETS}.
 */
public class TilesetDebugScreen implements Screen {

    private static final Logger LOG = Global.getLogger(TilesetDebugScreen.class);

    private static final int ALPHA_THRESHOLD = 16;

    private static final float PAD              = 12f;
    private static final float HEADER_H         = 28f;
    private static final float TAB_H            = 28f;
    private static final float TAB_GAP          = 6f;
    private static final float BACK_W           = 120f;
    private static final float BACK_H           = 32f;
    private static final float SIDEBAR_W        = 320f;
    private static final float SIDEBAR_GAP      = 12f;

    private static final float FORM_FIELD_H     = 30f;
    private static final float FORM_LABEL_H     = 20f;
    private static final float FORM_ROW_GAP     = 8f;
    private static final float SAVE_BTN_H       = 32f;
    private static final float PREVIEW_SIZE     = 96f;
    private static final int   NAME_MAX_CHARS   = 48;
    private static final int   DESC_MAX_CHARS   = 240;

    private static final Color HEADER_COLOR     = new Color(0xC8, 0xE0, 0xFF);
    private static final Color LABEL_COLOR      = new Color(0xFF, 0xE0, 0x70);
    private static final Color LABEL_SHADOW     = new Color(0x00, 0x00, 0x00);
    private static final Color CHECKER_A        = new Color(0x14, 0x1B, 0x26);
    private static final Color CHECKER_B        = new Color(0x1C, 0x24, 0x32);
    private static final Color GRID_LINE        = new Color(0x32, 0x42, 0x58);
    private static final Color FRAME_COLOR      = new Color(0x4A, 0x6B, 0x8C);
    private static final Color SELECT_COLOR     = new Color(0x7A, 0xB7, 0xFF);
    private static final Color CATALOG_BADGE    = new Color(0x4A, 0xCC, 0x88);
    private static final Color SAVE_OK_COLOR    = new Color(0x9A, 0xE6, 0xB4);
    private static final Color SAVE_ERR_COLOR   = new Color(0xFF, 0x9A, 0x88);
    private static final Color BODY_TEXT        = new Color(0xE0, 0xE8, 0xF4);
    private static final Color SUBHEAD_COLOR    = new Color(0xA8, 0xC0, 0xE0);

    private static final class SheetSpec {
        final String name;
        final String path;
        /** Cell size in source pixels for fixed-grid sheets. {@code 0} switches the screen into slicer mode (variable-width cells separated by alpha gutters). */
        final int    tileSize;
        SheetSpec(String name, String path, int tileSize) {
            this.name = name; this.path = path; this.tileSize = tileSize;
        }
        boolean isSliced() { return tileSize == 0; }
    }

    /** Tabs render in list order. Per-sheet tile size — Urban art is 32px, Floors/Water are 16px. Nature uses 0 (slicer mode). */
    private static final List<SheetSpec> SHEETS = new ArrayList<>();
    static {
        SHEETS.add(new SheetSpec("Urban-1", "graphics/tilesets/urban-tileset.png",   32));
        SHEETS.add(new SheetSpec("Urban-2", "graphics/tilesets/urban-tileset-2.png", 32));
        SHEETS.add(new SheetSpec("Floors",  "graphics/tilesets/Floors_Tiles.png",    16));
        SHEETS.add(new SheetSpec("Water",   "graphics/tilesets/Water_tiles.png",     16));
        SHEETS.add(new SheetSpec("Nature",  NatureTileset.SHEET_PATH,                 0));
        SHEETS.add(new SheetSpec("Urban-3", UrbanTile3Tileset.SHEET_PATH,             0));
    }

    private final WidgetRoot widgets = new WidgetRoot();

    private PositionAPI position;
    private MarineOpsContext ctx;

    // Active sheet state — reset on sheet switch.
    private SheetSpec activeSheet;
    private SpriteAPI sheet;
    private boolean[][] cellHasContent; // [col][row]
    private int cols;
    private int rows;
    private int sheetPxW;
    private int sheetPxH;
    private TilesetCatalog catalog;
    /** Non-null when the active sheet is in slicer mode. {@code sliceFrames[col]} is the bounding box of the {@code col}-th detected sprite. */
    private SpriteSheetFrames.Frame[] sliceFrames;

    // Grid layout (recomputed each rebuild, queried by hit-test in processInput).
    private float gridOriginX;
    private float gridOriginY;
    private float gridDisplayCell;

    // Selection — null until the user clicks a tile.
    private int selCol = -1;
    private int selRow = -1;

    // Sidebar form widgets — re-bound on selection change so onChange writes
    // back to the right catalog entry.
    private TextFieldWidget nameField;
    private TextFieldWidget descField;
    private String saveStatus = "";
    private boolean saveStatusError;
    private float saveStatusTimer;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        if (activeSheet == null) {
            switchSheet(SHEETS.get(0));
        }
        rebuild();
    }

    /** Lazy-loads {@code spec}'s sheet, parses non-empty cells at the spec's tile size, and pulls in the catalog sidecar. */
    private void switchSheet(SheetSpec spec) {
        this.activeSheet = spec;
        this.sheet = null;
        this.cellHasContent = null;
        this.sliceFrames = null;
        this.cols = 0;
        this.rows = 0;
        this.selCol = -1;
        this.selRow = -1;
        try {
            Global.getSettings().loadTexture(spec.path);
            sheet = Global.getSettings().getSprite(spec.path);
            if (sheet == null) {
                LOG.warn("TilesetDebugScreen: getSprite returned null for " + spec.path);
                return;
            }
            try (InputStream stream = Global.getSettings().openStream(spec.path)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("TilesetDebugScreen: ImageIO.read returned null for " + spec.path);
                    return;
                }
                sheetPxW = img.getWidth();
                sheetPxH = img.getHeight();
                if (spec.isSliced()) {
                    SpriteSheetFrames sliced = SpriteSheetSlicer.slice(img);
                    sliceFrames = sliced.frames;
                    cols = sliceFrames.length;
                    rows = 1;
                    cellHasContent = new boolean[cols][rows];
                    for (int c = 0; c < cols; c++) cellHasContent[c][0] = true;
                    LOG.info("TilesetDebugScreen: sliced " + spec.path
                            + " (" + sheetPxW + "x" + sheetPxH + "px, "
                            + cols + " frames)");
                } else {
                    int tileSize = spec.tileSize;
                    cols = sheetPxW / tileSize;
                    rows = sheetPxH / tileSize;
                    cellHasContent = new boolean[cols][rows];

                    int[] pixels = new int[sheetPxW * sheetPxH];
                    img.getRGB(0, 0, sheetPxW, sheetPxH, pixels, 0, sheetPxW);
                    for (int row = 0; row < rows; row++) {
                        for (int col = 0; col < cols; col++) {
                            cellHasContent[col][row] = cellHasAnyOpaquePixel(pixels, col, row, tileSize);
                        }
                    }
                    LOG.info("TilesetDebugScreen: loaded " + spec.path
                            + " (" + sheetPxW + "x" + sheetPxH + "px, "
                            + cols + "x" + rows + " cells @ " + tileSize + "px)");
                }
            }
        } catch (Exception e) {
            LOG.error("TilesetDebugScreen: failed to load " + spec.path, e);
            sheet = null;
            cellHasContent = null;
            sliceFrames = null;
        }

        catalog = new TilesetCatalog(spec.path);
        catalog.load();
    }

    private boolean cellHasAnyOpaquePixel(int[] pixels, int col, int row, int tileSize) {
        for (int py = 0; py < tileSize; py++) {
            int yPix = row * tileSize + py;
            int rowStart = yPix * sheetPxW;
            for (int px = 0; px < tileSize; px++) {
                int alpha = (pixels[rowStart + col * tileSize + px] >>> 24) & 0xFF;
                if (alpha >= ALPHA_THRESHOLD) return true;
            }
        }
        return false;
    }

    private void rebuild() {
        widgets.clear();
        nameField = null;
        descField = null;
        if (position == null || ctx == null) return;

        float xL = position.getX();
        float yB = position.getY();
        float wT = position.getWidth();
        float hT = position.getHeight();

        // --- Header label ---
        int tileSize = activeSheet == null ? 0 : activeSheet.tileSize;
        boolean sliced = activeSheet != null && activeSheet.isSliced();
        String displayName = activeSheet == null ? "(none)" : activeSheet.name;
        String header = "Tileset Catalog — " + displayName
                + (sliced
                        ? (" (" + cols + " frames, auto-sliced)")
                        : (" (" + cols + "x" + rows + " @ " + tileSize + "px)"));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                header,
                xL + PAD, yB + hT - PAD - 6f, HEADER_COLOR));

        // --- Sheet tab buttons (row directly under the header) ---
        float tabsY = yB + hT - PAD - HEADER_H - TAB_H;
        float tabX = xL + PAD;
        float tabW = 100f;
        for (SheetSpec spec : SHEETS) {
            final SheetSpec capture = spec;
            boolean active = activeSheet == spec;
            ButtonWidget tab = new ButtonWidget(tabX, tabsY, tabW, TAB_H, () -> {
                if (activeSheet != capture) {
                    switchSheet(capture);
                    rebuild();
                }
            });
            widgets.add(tab);
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    spec.name,
                    tabX + 10f, tabsY + TAB_H - 6f,
                    active ? SELECT_COLOR : HEADER_COLOR));
            tabX += tabW + TAB_GAP;
        }

        // --- Back button (bottom-left) ---
        float backX = xL + PAD;
        float backY = yB + PAD;
        widgets.add(new ButtonWidget(backX, backY, BACK_W, BACK_H,
                () -> ctx.goTo(ScreenId.MISSION_SELECT)));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get("actionBack"),
                backX + 12f, backY + BACK_H - 6f, HEADER_COLOR));

        // --- Sidebar form widgets ---
        // Sidebar pinned to the right side of the panel between tabs and back button.
        float sbX = xL + wT - PAD - SIDEBAR_W;
        float sbY = yB + PAD + BACK_H + PAD;
        float sbH = (yB + hT - PAD - HEADER_H - TAB_H - PAD) - sbY;
        layoutSidebar(sbX, sbY, sbH);

        // Stash grid origin/size for hit-testing and render. Same viewport as v1,
        // shrunk on the right to make room for the sidebar.
        float viewportX = xL + PAD;
        float viewportY = yB + PAD + BACK_H + PAD;
        float viewportW = wT - 2 * PAD - SIDEBAR_W - SIDEBAR_GAP;
        float viewportH = hT - 2 * PAD - BACK_H - PAD - HEADER_H - TAB_H;
        if (cols == 0 || rows == 0 || activeSheet == null) {
            gridDisplayCell = 0f;
            return;
        }
        if (sliced) {
            // Slicer mode: lay frames out in a uniform 20-square strip rather
            // than at their pixel-precise source widths. Each tile renders 1×1
            // in-game, so previewing them as a uniform strip matches in-game
            // intent better than recreating the source PNG's layout.
            float cellByW = viewportW / cols;
            float cellByH = viewportH / rows;
            gridDisplayCell = Math.min(96f, Math.min(cellByW, cellByH));
        } else {
            float scale = Math.min(viewportW / (cols * tileSize), viewportH / (rows * tileSize));
            // 16px sheets need more upscale to be legible; cap at 4x. 32px sheets
            // were fine at native size already.
            float maxScale = tileSize >= 32 ? 2f : 4f;
            scale = Math.min(scale, maxScale);
            gridDisplayCell = tileSize * scale;
        }
        float gridW = cols * gridDisplayCell;
        float gridH = rows * gridDisplayCell;
        gridOriginX = viewportX + (viewportW - gridW) / 2f;
        gridOriginY = viewportY + (viewportH - gridH) / 2f;
    }

    private void layoutSidebar(float sbX, float sbY, float sbH) {
        // Sidebar laid out top-down from sbTop:
        //   "Selected" header
        //   preview swatch (PREVIEW_SIZE square) + (col,row) line beside it
        //   "Name" label
        //   name field
        //   "Description" label
        //   description field
        //   Save button (anchored to bottom of sidebar so the gap between
        //                description and Save grows with available height)
        float sbTop = sbY + sbH;

        float y = sbTop - 6f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "Selected", sbX, y, HEADER_COLOR));
        y -= 24f;

        float previewY = y - PREVIEW_SIZE;
        // Preview swatch is rendered in render() (it needs the active sheet sprite),
        // not as a widget. We just reserve the rect via state below.
        previewRectX = sbX;
        previewRectY = previewY;
        previewRectW = PREVIEW_SIZE;
        previewRectH = PREVIEW_SIZE;

        float textX = sbX + PREVIEW_SIZE + 10f;
        String coord;
        if (selCol < 0) {
            coord = "(none)";
        } else if (activeSheet != null && activeSheet.isSliced()) {
            // Sliced sheets share the SpriteSheetSlicer pipeline but each has
            // its own semantic enum — dispatch by sheet path so the label
            // lookup uses the right one. Falls back to a bare frame index
            // when no enum matches (new sliced sheet added without wiring).
            String label = null;
            if (activeSheet.path.equals(NatureTileset.SHEET_PATH)) {
                NatureTile nt = NatureTile.byFrame(selCol);
                if (nt != null) label = nt.label;
            } else if (activeSheet.path.equals(UrbanTile3Tileset.SHEET_PATH)) {
                UrbanTile3 ut = UrbanTile3.byFrame(selCol);
                if (ut != null) label = ut.label;
            }
            coord = "frame " + selCol + (label != null ? " — " + label : "");
        } else {
            coord = "(" + selCol + ", " + selRow + ")";
        }
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                coord, textX, previewY + PREVIEW_SIZE - 6f, BODY_TEXT));
        y = previewY - 12f;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Name", sbX, y, SUBHEAD_COLOR));
        y -= FORM_LABEL_H;
        nameField = new TextFieldWidget(sbX, y - FORM_FIELD_H, SIDEBAR_W, FORM_FIELD_H,
                Fonts.ORBITRON_20, NAME_MAX_CHARS, "(unnamed)");
        widgets.add(nameField);
        y -= FORM_FIELD_H + FORM_ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Description", sbX, y, SUBHEAD_COLOR));
        y -= FORM_LABEL_H;
        descField = new TextFieldWidget(sbX, y - FORM_FIELD_H, SIDEBAR_W, FORM_FIELD_H,
                Fonts.ORBITRON_20, DESC_MAX_CHARS, "(short description)");
        widgets.add(descField);
        y -= FORM_FIELD_H + FORM_ROW_GAP;

        // Save button at the bottom of the sidebar.
        float saveY = sbY;
        ButtonWidget save = new ButtonWidget(sbX, saveY, SIDEBAR_W, SAVE_BTN_H, this::commitSave);
        widgets.add(save);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Save", sbX + 12f, saveY + SAVE_BTN_H - 6f, HEADER_COLOR));

        // Repopulate fields from the catalog entry for the current selection.
        if (selCol >= 0 && catalog != null) {
            TilesetCatalog.Entry e = catalog.get(selCol, selRow);
            if (e != null) {
                nameField.setText(e.name);
                descField.setText(e.description);
            }
        }

        // Wire onChange to keep the in-memory catalog entry in sync with typing.
        // Save still writes to disk; this just keeps the per-selection state coherent
        // so cycling between selections within a session preserves edits.
        nameField.setOnChange(s -> {
            if (selCol < 0 || catalog == null) return;
            TilesetCatalog.Entry e = catalog.getOrCreate(selCol, selRow);
            e.name = s;
        });
        descField.setOnChange(s -> {
            if (selCol < 0 || catalog == null) return;
            TilesetCatalog.Entry e = catalog.getOrCreate(selCol, selRow);
            e.description = s;
        });
    }

    // Preview rect cached during rebuild so render() can draw it.
    private float previewRectX, previewRectY, previewRectW, previewRectH;

    private void commitSave() {
        if (catalog == null) return;
        try {
            catalog.save();
            saveStatus = "Saved.";
            saveStatusError = false;
        } catch (Exception ex) {
            LOG.error("TilesetDebugScreen: save failed", ex);
            saveStatus = "Save failed: " + ex.getMessage();
            saveStatusError = true;
        }
        saveStatusTimer = 4f;
    }

    @Override
    public void advance(float dt) {
        widgets.advance(dt);
        if (saveStatusTimer > 0f) {
            saveStatusTimer -= dt;
            if (saveStatusTimer <= 0f) {
                saveStatus = "";
            }
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (events == null) return;
        // Tile-grid clicks happen first — they need direct hit-testing against
        // the on-screen grid rather than going through WidgetRoot (which doesn't
        // know about per-cell tiles).
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (!e.isLMBDownEvent()) continue;
            int hit = hitTestGrid(e.getX(), e.getY());
            if (hit >= 0) {
                int col = hit & 0xFFFF;
                int row = hit >>> 16;
                if (cellHasContent != null && cellHasContent[col][row]) {
                    selCol = col;
                    selRow = row;
                    rebuild(); // re-bind sidebar fields to the new selection
                    e.consume();
                }
            }
        }
        widgets.processInput(events);

        // Keyboard delivery to the focused text field (whichever one captured
        // focus from the last mouse-down). Both fields' processKey is a no-op
        // when unfocused.
        if (nameField != null) nameField.routeKeys(events);
        if (descField != null) descField.routeKeys(events);
    }

    /** Returns {@code (row << 16) | col} or -1 on miss. */
    private int hitTestGrid(int px, int py) {
        if (gridDisplayCell <= 0f) return -1;
        float dx = px - gridOriginX;
        float dy = py - gridOriginY;
        if (dx < 0 || dy < 0) return -1;
        int colIdx = (int) (dx / gridDisplayCell);
        int slabRowIdx = (int) (dy / gridDisplayCell);
        if (colIdx < 0 || colIdx >= cols) return -1;
        if (slabRowIdx < 0 || slabRowIdx >= rows) return -1;
        // Grid renders top-to-bottom with row 0 at the top of the gridOrigin+gridH
        // strip, but our origin is the bottom-left of the rendered grid; flip.
        int rowIdx = (rows - 1) - slabRowIdx;
        return (rowIdx << 16) | colIdx;
    }

    @Override
    public void render(float alphaMult) {
        if (position == null) return;
        if (sheet != null && cellHasContent != null && gridDisplayCell > 0f) {
            renderGrid(alphaMult);
            renderSelectionPreview(alphaMult);
        }
        widgets.render(alphaMult);
        renderSaveStatus(alphaMult);
    }

    private void renderGrid(float alphaMult) {
        float gridW = cols * gridDisplayCell;
        float gridH = rows * gridDisplayCell;

        // Pass 1 — checker backdrop
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBegin(GL_QUADS);
        for (int row = 0; row < rows; row++) {
            float y0 = gridOriginY + (rows - 1 - row) * gridDisplayCell;
            for (int col = 0; col < cols; col++) {
                Color c = ((col + row) % 2 == 0) ? CHECKER_A : CHECKER_B;
                glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alphaMult);
                float x0 = gridOriginX + col * gridDisplayCell;
                float x1 = x0 + gridDisplayCell;
                float y1 = y0 + gridDisplayCell;
                glVertex2f(x0, y0);
                glVertex2f(x1, y0);
                glVertex2f(x1, y1);
                glVertex2f(x0, y1);
            }
        }
        glEnd();

        // Pass 2 — tile sprites for non-empty cells
        boolean sliced = activeSheet.isSliced();
        int tileSize = activeSheet.tileSize;
        float texW = sheet.getTextureWidth();
        float texH = sheet.getTextureHeight();
        float texXScale = texW / sheetPxW;
        float texYScale = texH / sheetPxH;
        for (int row = 0; row < rows; row++) {
            float y0 = gridOriginY + (rows - 1 - row) * gridDisplayCell;
            for (int col = 0; col < cols; col++) {
                if (!cellHasContent[col][row]) continue;
                int srcX, srcY, srcW, srcH;
                if (sliced) {
                    SpriteSheetFrames.Frame f = sliceFrames[col];
                    srcX = f.x; srcY = f.y; srcW = f.w; srcH = f.h;
                } else {
                    srcX = col * tileSize;
                    srcY = row * tileSize;
                    srcW = tileSize;
                    srcH = tileSize;
                }
                sheet.setTexX(srcX * texXScale);
                sheet.setTexY((sheetPxH - (srcY + srcH)) * texYScale);
                sheet.setTexWidth(srcW * texXScale);
                sheet.setTexHeight(srcH * texYScale);
                sheet.setSize(gridDisplayCell, gridDisplayCell);
                sheet.setAlphaMult(alphaMult);
                sheet.setColor(Color.WHITE);
                sheet.setNormalBlend();
                float cx = gridOriginX + col * gridDisplayCell + gridDisplayCell / 2f;
                float cy = y0 + gridDisplayCell / 2f;
                sheet.renderAtCenter(cx, cy);
            }
        }

        // Pass 3 — grid lines
        glDisable(GL_TEXTURE_2D);
        glColor4f(GRID_LINE.getRed() / 255f, GRID_LINE.getGreen() / 255f,
                GRID_LINE.getBlue() / 255f, 0.6f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINES);
        for (int c = 0; c <= cols; c++) {
            float lx = gridOriginX + c * gridDisplayCell;
            glVertex2f(lx, gridOriginY);
            glVertex2f(lx, gridOriginY + gridH);
        }
        for (int r = 0; r <= rows; r++) {
            float ly = gridOriginY + r * gridDisplayCell;
            glVertex2f(gridOriginX,         ly);
            glVertex2f(gridOriginX + gridW, ly);
        }
        glEnd();

        // Pass 4 — frame
        glColor4f(FRAME_COLOR.getRed() / 255f, FRAME_COLOR.getGreen() / 255f,
                FRAME_COLOR.getBlue() / 255f, 0.9f * alphaMult);
        glLineWidth(1.5f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(gridOriginX,         gridOriginY);
        glVertex2f(gridOriginX + gridW, gridOriginY);
        glVertex2f(gridOriginX + gridW, gridOriginY + gridH);
        glVertex2f(gridOriginX,         gridOriginY + gridH);
        glEnd();

        // Pass 5 — (col,row) labels per non-empty cell. Sliced sheets show
        // the frame index alone since row is always 0.
        for (int row = 0; row < rows; row++) {
            float y0 = gridOriginY + (rows - 1 - row) * gridDisplayCell;
            for (int col = 0; col < cols; col++) {
                if (!cellHasContent[col][row]) continue;
                String label = sliced ? Integer.toString(col) : (col + "," + row);
                float lx = gridOriginX + col * gridDisplayCell + 2f;
                float ly = y0 + gridDisplayCell - 4f;
                Fonts.ORBITRON_20.drawString(label, lx + 1f, ly - 1f, LABEL_SHADOW, alphaMult);
                Fonts.ORBITRON_20.drawString(label, lx,        ly,        LABEL_COLOR,  alphaMult);

                // Catalog-badge dot in the corner of cells that have an entry.
                if (catalog != null) {
                    TilesetCatalog.Entry e = catalog.get(col, row);
                    if (e != null && !e.isBlank()) {
                        float dotX = gridOriginX + col * gridDisplayCell + gridDisplayCell - 6f;
                        float dotY = y0 + 2f;
                        drawCornerDot(dotX, dotY, alphaMult);
                    }
                }
            }
        }

        // Pass 6 — selection highlight
        if (selCol >= 0 && selRow >= 0) {
            float sx = gridOriginX + selCol * gridDisplayCell;
            float sy = gridOriginY + (rows - 1 - selRow) * gridDisplayCell;
            glDisable(GL_TEXTURE_2D);
            glColor4f(SELECT_COLOR.getRed() / 255f, SELECT_COLOR.getGreen() / 255f,
                    SELECT_COLOR.getBlue() / 255f, 0.95f * alphaMult);
            glLineWidth(2.5f);
            glBegin(GL_LINE_LOOP);
            glVertex2f(sx,                     sy);
            glVertex2f(sx + gridDisplayCell,   sy);
            glVertex2f(sx + gridDisplayCell,   sy + gridDisplayCell);
            glVertex2f(sx,                     sy + gridDisplayCell);
            glEnd();
        }
    }

    private void drawCornerDot(float x, float y, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glColor4f(CATALOG_BADGE.getRed() / 255f, CATALOG_BADGE.getGreen() / 255f,
                CATALOG_BADGE.getBlue() / 255f, 0.95f * alphaMult);
        float d = 4f;
        glBegin(GL_QUADS);
        glVertex2f(x,     y);
        glVertex2f(x + d, y);
        glVertex2f(x + d, y + d);
        glVertex2f(x,     y + d);
        glEnd();
    }

    private void renderSelectionPreview(float alphaMult) {
        // Background swatch
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(CHECKER_A.getRed() / 255f, CHECKER_A.getGreen() / 255f,
                CHECKER_A.getBlue() / 255f, alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(previewRectX,                 previewRectY);
        glVertex2f(previewRectX + previewRectW,  previewRectY);
        glVertex2f(previewRectX + previewRectW,  previewRectY + previewRectH);
        glVertex2f(previewRectX,                 previewRectY + previewRectH);
        glEnd();

        if (selCol >= 0 && sheet != null && activeSheet != null) {
            boolean sliced = activeSheet.isSliced();
            int tileSize = activeSheet.tileSize;
            float texW = sheet.getTextureWidth();
            float texH = sheet.getTextureHeight();
            float texXScale = texW / sheetPxW;
            float texYScale = texH / sheetPxH;
            int srcX, srcY, srcW, srcH;
            if (sliced && sliceFrames != null && selCol < sliceFrames.length) {
                SpriteSheetFrames.Frame f = sliceFrames[selCol];
                srcX = f.x; srcY = f.y; srcW = f.w; srcH = f.h;
            } else {
                srcX = selCol * tileSize;
                srcY = selRow * tileSize;
                srcW = tileSize;
                srcH = tileSize;
            }
            sheet.setTexX(srcX * texXScale);
            sheet.setTexY((sheetPxH - (srcY + srcH)) * texYScale);
            sheet.setTexWidth(srcW * texXScale);
            sheet.setTexHeight(srcH * texYScale);
            sheet.setSize(previewRectW, previewRectH);
            sheet.setAlphaMult(alphaMult);
            sheet.setColor(Color.WHITE);
            sheet.setNormalBlend();
            sheet.renderAtCenter(previewRectX + previewRectW / 2f,
                    previewRectY + previewRectH / 2f);
        }

        // Border
        glDisable(GL_TEXTURE_2D);
        glColor4f(FRAME_COLOR.getRed() / 255f, FRAME_COLOR.getGreen() / 255f,
                FRAME_COLOR.getBlue() / 255f, 0.9f * alphaMult);
        glLineWidth(1.5f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(previewRectX,                 previewRectY);
        glVertex2f(previewRectX + previewRectW,  previewRectY);
        glVertex2f(previewRectX + previewRectW,  previewRectY + previewRectH);
        glVertex2f(previewRectX,                 previewRectY + previewRectH);
        glEnd();
    }

    private void renderSaveStatus(float alphaMult) {
        if (saveStatus.isEmpty()) return;
        Color c = saveStatusError ? SAVE_ERR_COLOR : SAVE_OK_COLOR;
        float fade = Math.min(1f, saveStatusTimer / 1f); // last second fades
        Fonts.ORBITRON_20.drawString(saveStatus,
                position.getX() + PAD + BACK_W + PAD,
                position.getY() + PAD + BACK_H - 6f,
                c, fade * alphaMult);
    }
}
