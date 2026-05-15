package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.BattleSetup;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.ShotEvent;
import com.dillon.starsectormarines.battle.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.SpriteSheetSlicer;
import com.dillon.starsectormarines.battle.TileManifest;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
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
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * Top-down 2D auto-battler screen. Owns a layout + speed control state and
 * reads the active {@link BattleSimulation} from {@link MarineOpsContext}.
 *
 * <p>Loop: {@link #advance(float)} multiplies real dt by the player's speed
 * multiplier (pause / 1x / 2x / 4x) and feeds it to the sim, which catches up
 * in 1/30s ticks. {@link #render(float)} draws floor + walls + units + HP bars,
 * plus a centered Victory/Defeat banner when the sim completes.
 *
 * <p>Back returns to {@link ScreenId#MISSION_SELECT}. A dedicated RESULTS screen
 * (casualties, XP, payout) comes after MVP — for now the player just sees the
 * banner and backs out.
 */
public class BattleScreen implements Screen {

    private static final Logger LOG = Global.getLogger(BattleScreen.class);

    private static final Color FLOOR_COLOR    = new Color(0x18, 0x22, 0x30);
    private static final Color WALL_COLOR     = new Color(0x06, 0x0A, 0x10);
    private static final Color GRID_LINE      = new Color(0x25, 0x32, 0x44);
    private static final Color MARINE_COLOR   = new Color(0x5A, 0xA0, 0xE0);
    private static final Color DEFENDER_COLOR = new Color(0xE0, 0x6A, 0x6A);
    private static final Color HP_BG          = new Color(0x60, 0x20, 0x20);
    private static final Color HP_FG          = new Color(0x40, 0xC0, 0x40);
    private static final Color HEADER_COLOR   = new Color(0xC8, 0xE0, 0xFF);
    private static final Color ACTIVE_SPEED   = new Color(0xFF, 0xB8, 0x00);
    private static final Color BANNER_BG      = new Color(0x10, 0x14, 0x1E);
    private static final Color VICTORY_COLOR  = new Color(0x80, 0xE0, 0x80);
    private static final Color DEFEAT_COLOR   = new Color(0xE0, 0x60, 0x60);
    private static final Color MARINE_TRACER  = new Color(0xFF, 0xE0, 0x70);
    private static final Color DEFENDER_TRACER = new Color(0xFF, 0x70, 0x40);

    /** Sim-seconds shots live for — must match {@code BattleSimulation.SHOT_LIFETIME}. Used to fade tracer alpha. */
    private static final float SHOT_LIFETIME_REF = 0.15f;

    private static final float UNIT_FRAC      = 1.00f; // sprite fills the cell
    private static final float HP_BAR_H       = 3f;
    private static final float HP_BAR_GAP     = 2f;
    private static final float SPEED_BTN_W    = 60f;
    private static final float SPEED_BTN_H    = 32f;
    private static final float SPEED_BTN_GAP  = 6f;
    private static final float SPEED_MARK_H   = 3f;

    /** Marine sprite sheet path (Starsector resource lookup). */
    private static final String MARINE_SHEET  = "graphics/battle/marine.png";
    /** Window (s) after a unit fires during which we show the weapon-up pose. */
    private static final float WEAPON_UP_TIME = 0.25f;
    /** Multiplicative tint applied to defender sprites (marines are untinted). */
    private static final Color DEFENDER_TINT  = new Color(0xE0, 0x90, 0x90);

    private static final float[] SPEED_OPTIONS = {0f, 1f, 2f, 4f};
    private static final String[] SPEED_KEYS   = {
            "battleSpeedPause", "battleSpeed1x", "battleSpeed2x", "battleSpeed4x"
    };

    private final WidgetRoot widgets = new WidgetRoot();

    private PositionAPI position;
    private MarineOpsContext ctx;
    private BattleLayout layout;
    private float speedMultiplier = 1f;
    /** Pixel x-center of each speed button, captured at layout time for the active-marker dot. */
    private final float[] speedBtnCenterX = new float[SPEED_OPTIONS.length];
    private float speedBtnBottomY;
    /** Tracks the last-seen sim completion flag so we can rebuild widgets when it flips. */
    private boolean lastSimComplete;
    /** Cached marine sprite sheet — lazy-loaded once per Screen lifetime. Null if load failed. */
    private SpriteAPI marineSheet;
    /** Detected sprite bounding boxes on {@link #marineSheet}. Null until sheet is loaded. */
    private SpriteSheetFrames marineFrames;
    private boolean marineSheetLoadAttempted;
    /** Cached tileset sheet — lazy-loaded once per Screen lifetime. Null if load failed. */
    private SpriteAPI tileSheet;
    /** Pixel dimensions of the tileset PNG content (pre-POT-padding). */
    private int tileSheetPxW;
    private int tileSheetPxH;
    private boolean tileSheetLoadAttempted;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        this.speedMultiplier = 1f;
        ensureMarineSheet();
        ensureTileSheet();
        rebuild();
    }

    /**
     * Lazy-loads the battle tileset on first attach. Reads the raw PNG to
     * capture content dimensions — {@code SpriteAPI.getTextureWidth()} reports
     * the POT-padded texture size, but per-tile UV math needs the content
     * width to compute texture-fraction coords correctly. Cached across attach
     * calls; survives screen re-entry without re-decoding the PNG.
     */
    private void ensureTileSheet() {
        if (tileSheetLoadAttempted) return;
        tileSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(TileManifest.SHEET);
            tileSheet = Global.getSettings().getSprite(TileManifest.SHEET);
            if (tileSheet == null) {
                LOG.warn("BattleScreen: getSprite returned null for " + TileManifest.SHEET);
                return;
            }
            try (java.io.InputStream stream = Global.getSettings().openStream(TileManifest.SHEET)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleScreen: ImageIO.read returned null for " + TileManifest.SHEET);
                    tileSheet = null;
                    return;
                }
                tileSheetPxW = img.getWidth();
                tileSheetPxH = img.getHeight();
                LOG.info("BattleScreen: loaded tileset " + TileManifest.SHEET
                        + " (" + tileSheetPxW + "x" + tileSheetPxH + ")");
            }
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load tileset " + TileManifest.SHEET, e);
            tileSheet = null;
        }
    }

    /**
     * Lazy-loads the marine sprite sheet on first attach and auto-slices it
     * into per-frame bounding boxes. {@code getSprite} alone returns a wrapper
     * whose backing texture is null until {@code loadTexture} is called — same
     * pattern BitmapFont uses for font pages. Survives across multiple attach
     * calls via the cached fields.
     *
     * <p>{@link SpriteSheetSlicer} runs on the raw PNG bytes via
     * {@code openStream + ImageIO} — it handles both clean uniform-grid sheets
     * (the reference marine sprite) and AI-generated sheets with irregular
     * sprite spacing, so we don't have to keep the source art on a strict grid.
     */
    private void ensureMarineSheet() {
        if (marineSheetLoadAttempted) return;
        marineSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(MARINE_SHEET);
            marineSheet = Global.getSettings().getSprite(MARINE_SHEET);
            if (marineSheet == null) {
                LOG.warn("BattleScreen: getSprite returned null for " + MARINE_SHEET);
                return;
            }
            try (java.io.InputStream stream = Global.getSettings().openStream(MARINE_SHEET)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleScreen: ImageIO.read returned null for " + MARINE_SHEET);
                    return;
                }
                marineFrames = SpriteSheetSlicer.slice(img);
                LOG.info("BattleScreen: auto-sliced " + MARINE_SHEET + " — "
                        + marineFrames.frames.length + " frames detected");
            }
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load marine sheet " + MARINE_SHEET, e);
            marineSheet = null;
            marineFrames = null;
        }
    }

    private void rebuild() {
        widgets.clear();
        if (position == null || ctx == null) return;

        BattleSimulation sim = ctx.getBattleSimulation();
        int gridW = sim != null ? sim.getGrid().getWidth()  : BattleSetup.GRID_W;
        int gridH = sim != null ? sim.getGrid().getHeight() : BattleSetup.GRID_H;
        layout = new BattleLayout(position, gridW, gridH);
        lastSimComplete = sim != null && sim.isComplete();

        // Bottom-left action button — Back when in-progress, Continue when done.
        String actionLabelKey = lastSimComplete ? "battleContinue" : "actionBack";
        ButtonWidget actionBtn = new ButtonWidget(layout.backX, layout.backY,
                BattleLayout.BACK_W, BattleLayout.BACK_H,
                () -> onBackOrContinue());
        widgets.add(actionBtn);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get(actionLabelKey),
                layout.backX + 12f, layout.backY + BattleLayout.BACK_H - 6f, HEADER_COLOR));

        // Speed buttons (top-right of controls strip)
        float rowW = SPEED_OPTIONS.length * SPEED_BTN_W
                + (SPEED_OPTIONS.length - 1) * SPEED_BTN_GAP;
        float startX = layout.controlsX + layout.controlsW - rowW;
        float btnY = layout.controlsY + (layout.controlsH - SPEED_BTN_H) / 2f;
        speedBtnBottomY = btnY;
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            float bx = startX + i * (SPEED_BTN_W + SPEED_BTN_GAP);
            final float target = SPEED_OPTIONS[i];
            ButtonWidget btn = new ButtonWidget(bx, btnY, SPEED_BTN_W, SPEED_BTN_H,
                    () -> speedMultiplier = target);
            widgets.add(btn);
            // Center the label inside the button.
            String label = Strings.get(SPEED_KEYS[i]);
            float labelW = Fonts.ORBITRON_20.measureWidth(label);
            float labelX = bx + (SPEED_BTN_W - labelW) / 2f;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, label,
                    labelX, btnY + SPEED_BTN_H - 6f, HEADER_COLOR));
            speedBtnCenterX[i] = bx + SPEED_BTN_W / 2f;
        }
    }

    @Override
    public void advance(float dt) {
        widgets.advance(dt);
        BattleSimulation sim = ctx != null ? ctx.getBattleSimulation() : null;
        if (sim == null) return;
        if (speedMultiplier > 0f) {
            sim.advance(dt * speedMultiplier);
        }
        // Rebuild widgets when the sim transitions to complete so the bottom
        // action button swaps from Back to Continue.
        if (sim.isComplete() != lastSimComplete) {
            lastSimComplete = sim.isComplete();
            rebuild();
        }
    }

    private void onBackOrContinue() {
        if (ctx == null) return;
        BattleSimulation sim = ctx.getBattleSimulation();
        if (sim != null && sim.isComplete()) {
            // Compute + apply outcome once, then hand off to RESULTS.
            Mission mission = ctx.getSelectedMission();
            MissionOutcome outcome = MissionResolver.compute(sim, mission, ctx.getSelectedCaptain());
            MissionResolver.apply(outcome);
            ctx.setLastOutcome(outcome);
            ctx.goTo(ScreenId.RESULTS);
        } else {
            // Abandon mid-battle — no resolution, no penalty.
            ctx.goTo(ScreenId.MISSION_SELECT);
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }

    @Override
    public void render(float alphaMult) {
        if (layout == null) return;
        BattleSimulation sim = ctx != null ? ctx.getBattleSimulation() : null;

        if (sim != null) {
            renderGrid(sim.getGrid(), alphaMult);
            renderUnits(sim.getUnits(), alphaMult);
            renderShots(sim.getActiveShots(), alphaMult);
        }

        renderSpeedMarker(alphaMult);

        if (sim != null && sim.isComplete()) {
            renderBanner(sim.getWinner(), alphaMult);
        }

        widgets.render(alphaMult);
    }

    // ---- rendering ---------------------------------------------------------

    /** Hide grid lines below this cell size — at small scales they read as visual noise rather than structure. */
    private static final float GRID_LINE_MIN_CELL = 16f;

    /**
     * Renders walkable cells as floor tiles from the manifest's floor pool and
     * non-walkable cells as wall variants from the manifest's wall pool. Both
     * picks are deterministic via {@link #cellHash} so tiles stay stable across
     * frames and reload — no flicker.
     *
     * <p>Walls render with a 2-tall source frame squashed into one cell. No
     * directional / autotile inspection — the source sheet's wall inventory
     * doesn't form a clean autotile pattern. Variety comes from the pool;
     * edge / corner pieces wait for a later polish pass.
     */
    private void renderTiledFloorsAndWalls(NavigationGrid grid, float alphaMult) {
        float texW = tileSheet.getTextureWidth();
        float texH = tileSheet.getTextureHeight();
        float texXScale = texW / tileSheetPxW;
        float texYScale = texH / tileSheetPxH;
        int sheetPxH = tileSheetPxH;

        TileManifest.TileFrame[] floorPool = TileManifest.FLOOR_POOL;
        TileManifest.TileFrame[] wallPool  = TileManifest.WALL_POOL;

        // Floor pass — every walkable cell gets a pool sample.
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!grid.isWalkable(x, y)) continue;
                TileManifest.TileFrame f = floorPool[cellHash(x, y) % floorPool.length];
                drawTile(f, x, y, texXScale, texYScale, sheetPxH, alphaMult);
            }
        }

        // Wall pass — interior cells get the roof tile, edge cells get a pooled variant.
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (grid.isWalkable(x, y)) continue;
                TileManifest.TileFrame tile = isInteriorWall(grid, x, y)
                        ? TileManifest.INTERIOR_ROOF
                        : wallPool[cellHash(x, y) % wallPool.length];
                drawTile(tile, x, y, texXScale, texYScale, sheetPxH, alphaMult);
            }
        }
    }

    /** A wall cell is "interior" when all 4 cardinal neighbors are also walls (or out of bounds). */
    private static boolean isInteriorWall(NavigationGrid grid, int x, int y) {
        return isWallOrOob(grid, x + 1, y)
                && isWallOrOob(grid, x - 1, y)
                && isWallOrOob(grid, x, y + 1)
                && isWallOrOob(grid, x, y - 1);
    }

    private static boolean isWallOrOob(NavigationGrid grid, int x, int y) {
        if (!grid.inBounds(x, y)) return true;
        return !grid.isWalkable(x, y);
    }

    /**
     * Draws a single {@link TileManifest.TileFrame} into one grid cell. For
     * frames with {@code heightTiles > 1} the source region is the full multi-row
     * rectangle but the destination is always one cellSize × cellSize — so 2-tall
     * walls render visually compressed but contain both upper and lower detail
     * from the source pair.
     */
    private void drawTile(TileManifest.TileFrame f, int gridX, int gridY,
                          float texXScale, float texYScale, int sheetPxH, float alphaMult) {
        int srcPxX = f.col * TileManifest.TILE_SIZE;
        int srcTopPxY = f.row * TileManifest.TILE_SIZE;
        int srcPxW = TileManifest.TILE_SIZE;
        int srcPxH = f.heightTiles * TileManifest.TILE_SIZE;

        tileSheet.setTexX(srcPxX * texXScale);
        tileSheet.setTexY((sheetPxH - (srcTopPxY + srcPxH)) * texYScale);
        tileSheet.setTexWidth(srcPxW * texXScale);
        tileSheet.setTexHeight(srcPxH * texYScale);
        tileSheet.setSize(layout.cellSize, layout.cellSize);
        tileSheet.setAlphaMult(alphaMult);
        tileSheet.setColor(Color.WHITE);
        tileSheet.setNormalBlend();

        float cx = layout.gridX + (gridX + 0.5f) * layout.cellSize;
        float cy = layout.gridY + (gridY + 0.5f) * layout.cellSize;
        tileSheet.renderAtCenter(cx, cy);
    }

    /** Stable per-cell hash for picking from tile pools — same cell always picks the same tile. */
    private static int cellHash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        return h & 0x7FFFFFFF;
    }

    private void renderGrid(NavigationGrid grid, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // One floor quad covering the whole arena. Acts as the floor color when
        // the tileset hasn't loaded, and as a safety underlay if any tile sprite
        // happens to be transparent.
        glColor4f(FLOOR_COLOR.getRed() / 255f, FLOOR_COLOR.getGreen() / 255f,
                FLOOR_COLOR.getBlue() / 255f, alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(layout.gridX,                  layout.gridY);
        glVertex2f(layout.gridX + layout.gridW,   layout.gridY);
        glVertex2f(layout.gridX + layout.gridW,   layout.gridY + layout.gridH);
        glVertex2f(layout.gridX,                  layout.gridY + layout.gridH);
        glEnd();

        if (tileSheet != null) {
            renderTiledFloorsAndWalls(grid, alphaMult);
        } else {
            // Fallback — solid-color walls. Same path the renderer used before
            // the tileset landed; kept so the sim still reads if texture load fails.
            glColor4f(WALL_COLOR.getRed() / 255f, WALL_COLOR.getGreen() / 255f,
                    WALL_COLOR.getBlue() / 255f, alphaMult);
            glBegin(GL_QUADS);
            for (int y = 0; y < grid.getHeight(); y++) {
                for (int x = 0; x < grid.getWidth(); x++) {
                    if (grid.isWalkable(x, y)) continue;
                    float x0 = layout.gridX + x * layout.cellSize;
                    float y0 = layout.gridY + y * layout.cellSize;
                    float x1 = x0 + layout.cellSize;
                    float y1 = y0 + layout.cellSize;
                    glVertex2f(x0, y0);
                    glVertex2f(x1, y0);
                    glVertex2f(x1, y1);
                    glVertex2f(x0, y1);
                }
            }
            glEnd();
        }

        // Grid lines — only when cells are big enough to read.
        if (layout.cellSize >= GRID_LINE_MIN_CELL) {
            glColor4f(GRID_LINE.getRed() / 255f, GRID_LINE.getGreen() / 255f,
                    GRID_LINE.getBlue() / 255f, 0.4f * alphaMult);
            glBegin(org.lwjgl.opengl.GL11.GL_LINES);
            for (int x = 0; x <= grid.getWidth(); x++) {
                float px = layout.gridX + x * layout.cellSize;
                glVertex2f(px, layout.gridY);
                glVertex2f(px, layout.gridY + layout.gridH);
            }
            for (int y = 0; y <= grid.getHeight(); y++) {
                float py = layout.gridY + y * layout.cellSize;
                glVertex2f(layout.gridX,                py);
                glVertex2f(layout.gridX + layout.gridW, py);
            }
            glEnd();
        }
    }

    private void renderUnits(List<Unit> units, float alphaMult) {
        float unitSize = layout.cellSize * UNIT_FRAC;
        float half = unitSize / 2f;

        SpriteAPI sheet = marineSheet;
        SpriteSheetFrames frames = marineFrames;
        if (sheet != null && frames != null && frames.frames.length > 0) {
            float texW = sheet.getTextureWidth();
            float texH = sheet.getTextureHeight();
            int sheetW = frames.sheetWidth;
            int sheetH = frames.sheetHeight;

            for (Unit u : units) {
                if (!u.isAlive()) continue;

                Facing facing = computeFacing(u);
                boolean weaponUp = u.cooldownTimer > (u.attackCooldown - WEAPON_UP_TIME)
                        && u.cooldownTimer > 0f;
                int frameIdx = pickFrame(facing, weaponUp);
                if (frameIdx >= frames.frames.length) frameIdx = 0; // safety
                boolean flipY = weaponUp && facing == Facing.SOUTH;
                SpriteSheetFrames.Frame f = frames.frames[frameIdx];

                // Convert pixel bbox into the texture-fraction coords setTex* expects.
                // V is measured from the bottom of the texture in Starsector's GL convention.
                sheet.setTexX((float) f.x * texW / sheetW);
                sheet.setTexWidth((float) f.w * texW / sheetW);
                if (flipY) {
                    // Vertical mirror — negative texHeight, anchor at the top of the frame.
                    sheet.setTexY((float) (sheetH - f.y) * texH / sheetH);
                    sheet.setTexHeight(-(float) f.h * texH / sheetH);
                } else {
                    sheet.setTexY((float) (sheetH - f.y - f.h) * texH / sheetH);
                    sheet.setTexHeight((float) f.h * texH / sheetH);
                }

                // Preserve the frame's aspect ratio so weapon-up poses with extended
                // guns can be wider than idle poses without squishing.
                float targetH = unitSize;
                float targetW = targetH * f.w / (float) f.h;
                sheet.setSize(targetW, targetH);
                sheet.setAlphaMult(alphaMult);
                sheet.setNormalBlend();
                sheet.setColor(u.faction == Faction.MARINE ? Color.WHITE : DEFENDER_TINT);

                float cx = layout.gridX + (u.renderX + 0.5f) * layout.cellSize;
                float cy = layout.gridY + (u.renderY + 0.5f) * layout.cellSize;
                sheet.renderAtCenter(cx, cy);
            }
            // Reset tint so the singleton sprite doesn't carry our red into
            // anything that draws it next session.
            sheet.setColor(Color.WHITE);
        } else {
            // Sprite missing — fall back to colored quads so the sim is still readable.
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glBegin(GL_QUADS);
            for (Unit u : units) {
                if (!u.isAlive()) continue;
                Color c = (u.faction == Faction.MARINE) ? MARINE_COLOR : DEFENDER_COLOR;
                glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alphaMult);
                float cx = layout.gridX + (u.renderX + 0.5f) * layout.cellSize;
                float cy = layout.gridY + (u.renderY + 0.5f) * layout.cellSize;
                glVertex2f(cx - half, cy - half);
                glVertex2f(cx + half, cy - half);
                glVertex2f(cx + half, cy + half);
                glVertex2f(cx - half, cy + half);
            }
            glEnd();
        }

        // HP bars above each unit (always, regardless of sprite fallback)
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            float cx = layout.gridX + (u.renderX + 0.5f) * layout.cellSize;
            float cy = layout.gridY + (u.renderY + 0.5f) * layout.cellSize;
            float barW = unitSize;
            float barX = cx - barW / 2f;
            float barY = cy + half + HP_BAR_GAP;
            fillRect(barX, barY, barW, HP_BAR_H, HP_BG, alphaMult);
            float frac = Math.max(0f, Math.min(1f, u.hp / u.maxHp));
            fillRect(barX, barY, barW * frac, HP_BAR_H, HP_FG, alphaMult);
        }
    }

    /**
     * Renders active tracers as faction-colored lines, fading alpha over
     * remaining lifetime. Hits draw a clean shooter-to-target line; misses
     * draw to the randomized near-miss endpoint baked into the {@link ShotEvent}
     * on emit, so misses read as stray rounds whizzing past the target.
     */
    private void renderShots(List<ShotEvent> shots, float alphaMult) {
        if (shots.isEmpty()) return;
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        org.lwjgl.opengl.GL11.glLineWidth(2f);
        glBegin(org.lwjgl.opengl.GL11.GL_LINES);
        for (ShotEvent s : shots) {
            float t = Math.max(0f, Math.min(1f, s.lifetime / SHOT_LIFETIME_REF));
            Color c = s.shooterFaction == Faction.MARINE ? MARINE_TRACER : DEFENDER_TRACER;
            glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, t * alphaMult);
            float x0 = layout.gridX + s.fromX * layout.cellSize;
            float y0 = layout.gridY + s.fromY * layout.cellSize;
            float x1 = layout.gridX + s.toX   * layout.cellSize;
            float y1 = layout.gridY + s.toY   * layout.cellSize;
            glVertex2f(x0, y0);
            glVertex2f(x1, y1);
        }
        glEnd();
        org.lwjgl.opengl.GL11.glLineWidth(1f);
    }

    private enum Facing { WEST, NORTH, EAST, SOUTH }

    /** Prefer target direction (units face their target while attacking); fall back to movement; default south. */
    private static Facing computeFacing(Unit u) {
        if (u.target != null && u.target.isAlive()) {
            int dx = u.target.cellX - u.cellX;
            int dy = u.target.cellY - u.cellY;
            if (dx != 0 || dy != 0) return facingFromDelta(dx, dy);
        }
        if (!u.path.isEmpty() && u.pathIdx < u.path.size()) {
            int[] next = u.path.get(u.pathIdx);
            int dx = next[0] - u.cellX;
            int dy = next[1] - u.cellY;
            if (dx != 0 || dy != 0) return facingFromDelta(dx, dy);
        }
        return Facing.SOUTH;
    }

    private static Facing facingFromDelta(int dx, int dy) {
        if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? Facing.EAST : Facing.WEST;
        return dy > 0 ? Facing.NORTH : Facing.SOUTH;
    }

    /** Maps (facing, weaponUp) to a slot in the sheet. South+weaponUp uses slot 6 + a vertical flip. */
    private static int pickFrame(Facing facing, boolean weaponUp) {
        if (weaponUp) {
            switch (facing) {
                case WEST:  return 4;
                case EAST:  return 5;
                case NORTH: return 6;
                case SOUTH: return 6; // vertical mirror applied at draw time
            }
        } else {
            switch (facing) {
                case WEST:  return 0;
                case NORTH: return 1;
                case EAST:  return 2;
                case SOUTH: return 3;
            }
        }
        return 3;
    }

    /** Amber underline under whichever speed button is currently active. */
    private void renderSpeedMarker(float alphaMult) {
        int activeIdx = -1;
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            if (SPEED_OPTIONS[i] == speedMultiplier) { activeIdx = i; break; }
        }
        if (activeIdx < 0) return;
        float w = SPEED_BTN_W - 16f;
        float markX = speedBtnCenterX[activeIdx] - w / 2f;
        float markY = speedBtnBottomY - SPEED_MARK_H - 2f;
        fillRect(markX, markY, w, SPEED_MARK_H, ACTIVE_SPEED, alphaMult);
    }

    private void renderBanner(Faction winner, float alphaMult) {
        boolean victory = winner == Faction.MARINE;
        String text = Strings.get(victory ? "battleVictory" : "battleDefeat");
        Color color = victory ? VICTORY_COLOR : DEFEAT_COLOR;

        float textW = Fonts.ORBITRON_24_BOLD.measureWidth(text);
        float textH = Fonts.ORBITRON_24_BOLD.getLineHeight();
        float padX = 24f;
        float padY = 12f;
        float boxW = textW + 2 * padX;
        float boxH = textH + 2 * padY;
        float boxX = layout.gridX + (layout.gridW - boxW) / 2f;
        float boxY = layout.gridY + (layout.gridH - boxH) / 2f;

        fillRect(boxX, boxY, boxW, boxH, BANNER_BG, 0.92f * alphaMult);
        // Color-tinted border
        outlineRect(boxX, boxY, boxW, boxH, color, alphaMult);

        Fonts.ORBITRON_24_BOLD.drawString(text,
                boxX + padX, boxY + padY + textH, color, alphaMult);
    }

    // ---- raw-GL helpers ----------------------------------------------------

    private static void fillRect(float rx, float ry, float rw, float rh, Color c, float alpha) {
        if (rw <= 0f || rh <= 0f) return;
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(rx,      ry);
        glVertex2f(rx + rw, ry);
        glVertex2f(rx + rw, ry + rh);
        glVertex2f(rx,      ry + rh);
        glEnd();
    }

    private static void outlineRect(float rx, float ry, float rw, float rh, Color c, float alpha) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
        org.lwjgl.opengl.GL11.glLineWidth(1.5f);
        glBegin(org.lwjgl.opengl.GL11.GL_LINE_LOOP);
        glVertex2f(rx,      ry);
        glVertex2f(rx + rw, ry);
        glVertex2f(rx + rw, ry + rh);
        glVertex2f(rx,      ry + rh);
        glEnd();
    }
}
