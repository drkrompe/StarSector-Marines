package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.SurfaceSignature;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.VisibleCellRect;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_DST_COLOR;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_ZERO;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * A planet type's signature detail on screen ({@code mapgen-nouns.md}, Surface signatures; {@link SignatureLook}
 * owns the colours). Two passes, both under every unit, so nothing here hides a marine:
 *
 * <ul>
 *   <li><b>The ground</b> (this system, {@link RenderLayer#GROUND}, after the cliffs): the wild ground and its hills
 *       multiplied by the signature's grade, softened across the cell where wild ground meets built ground; the
 *       metal flecks of a cast-iron world glinting in it.</li>
 *   <li><b>The marks</b> ({@link #marks()}, {@link RenderLayer#DECALS}): a cryovolcanic world's frost-rimmed vent
 *       mounds and a bombarded world's crater bowls, over the tiles and under the props.</li>
 *   <li><b>The air</b> ({@link #air()}, {@link RenderLayer#DOODADS}, after the props): a venus-like world's haze
 *       drifting in banks over the ground and the props, and the vapour each vent breathes out in puffs that swell
 *       and drift downwind. Under the fog, so the fog still says what the player sees.</li>
 * </ul>
 */
final class SignatureRenderSystem implements RenderSystem {

    /** Vanilla's fog sheet, as the lava rift's smoke and the crevasse's mist. */
    static final String PUFF_SHEET = MoltenVoidRenderSystem.SMOKE_SHEET;
    static final int PUFF_SHEET_CELLS = MoltenVoidRenderSystem.SMOKE_SHEET_CELLS;
    /** A soft round light, as the city drop's lamps; its core is about a tenth of it. */
    static final String DOT_SPRITE = VoidBackdropRenderSystem.LIGHT_SPRITE;
    /** The vent mound, top-down, on a transparent ground. */
    static final String MOUND_SPRITE = "graphics/battle/signatures/cryo-vent-mound.png";
    /** An impact crater, bowl, lip and ejecta, top-down, on a transparent ground. */
    static final String CRATER_SPRITE = "graphics/battle/signatures/impact-crater.png";
    /** Below this many pixels a cell, flecks are not drawn: a whole-map view would be all speckle. */
    static final float FLECK_MIN_CELL_PX = 5f;
    /** The wind the haze and vapour drift on. */
    static final float WIND_X = .92f, WIND_Y = .38f;

    private final BattleSprites sprites;

    /** Each grid vertex's share of wild ground, {@code (w + 1) * (h + 1)}, for the softened grade. */
    private float[] wild;
    private CellTopology wildFor;

    SignatureRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override public RenderLayer layer() { return RenderLayer.GROUND; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        CellTopology topology = ctx.sim.getTopology();
        if (topology == null) return;
        SurfaceSignature signature = topology.signature();
        if (signature == SurfaceSignature.NONE) return;
        BattleCamera cam = ctx.camera;
        VisibleCellRect view = cam.visibleCells(VisibleCellRect.GEOMETRY_MARGIN_CELLS,
                topology.getWidth(), topology.getHeight());
        if (SignatureLook.graded(signature)) {
            if (wildFor != topology) {
                wild = wildShares(topology);
                wildFor = topology;
            }
            float[] grade = SignatureLook.grade(signature);
            float[] shares = wild;
            int w = topology.getWidth();
            out.addCustom(RenderLayer.GROUND, () -> drawGrade(cam, view, shares, w, grade));
        }
        if (signature == SurfaceSignature.METAL_FLECKED && cam.cellPxSize() >= FLECK_MIN_CELL_PX)
            flecks(topology, cam, view, FireLight.clock(ctx.animationTimeSeconds), ctx.alphaMult, out);
    }

    /**
     * Whether a cell is the world's own ground: natural, unbuilt and unpaved. A prop standing on it does not change
     * that: the grade reaches only the ground's colour target, so the ground under a formation is graded with the rest
     * and no light ring is left round it.
     */
    static boolean wildCell(CellTopology t, int x, int y) {
        if (t.isWall(x, y) || t.getBuildingId(x, y) != 0) return false;
        GroundKind kind = t.getGroundKind(x, y);
        if (kind != GroundKind.GRASS && kind != GroundKind.DIRT && kind != GroundKind.STONE
                && kind != GroundKind.SAND && kind != GroundKind.SNOW) return false;
        if (t.roadSurface() != null && t.roadSurface().underlay(x, y) != null) return false;
        String surface = t.getSurfaceId(x, y);
        return surface == null || surface.startsWith("floors.");
    }

    /** Each vertex's share of the four cells round it that are wild, so the grade fades across one cell at an edge. */
    static float[] wildShares(CellTopology t) {
        int w = t.getWidth(), h = t.getHeight();
        boolean[] cell = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) cell[y * w + x] = wildCell(t, x, y);
        float[] v = new float[(w + 1) * (h + 1)];
        for (int y = 0; y <= h; y++) {
            for (int x = 0; x <= w; x++) {
                int n = 0, on = 0;
                for (int dy = -1; dy <= 0; dy++) for (int dx = -1; dx <= 0; dx++) {
                    int cx = x + dx, cy = y + dy;
                    if (cx < 0 || cy < 0 || cx >= w || cy >= h) continue;
                    n++;
                    if (cell[cy * w + cx]) on++;
                }
                v[y * (w + 1) + x] = n == 0 ? 0f : on / (float) n;
            }
        }
        return v;
    }

    /**
     * The grade, multiplied into the ground's colour target. Opaque in alpha, so the composite reads no shade from
     * it (a translucent piece over the tiles thins the target's alpha, which the composite reads as shadow).
     */
    private static void drawGrade(BattleCamera cam, VisibleCellRect view, float[] shares, int w, float[] grade) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_DST_COLOR, GL_ZERO);
        glBegin(GL_QUADS);
        int stride = w + 1;
        for (int y = view.minY(); y <= view.maxY(); y++) {
            float y0 = cam.cellToScreenY(y), y1 = cam.cellToScreenY(y + 1);
            for (int x = view.minX(); x <= view.maxX(); x++) {
                float a = shares[y * stride + x], b = shares[y * stride + x + 1];
                float c = shares[(y + 1) * stride + x + 1], d = shares[(y + 1) * stride + x];
                if (a + b + c + d <= 0f) continue;
                float x0 = cam.cellToScreenX(x), x1 = cam.cellToScreenX(x + 1);
                vertex(grade, a, x0, y0);
                vertex(grade, b, x1, y0);
                vertex(grade, c, x1, y1);
                vertex(grade, d, x0, y1);
            }
        }
        glEnd();
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);
    }

    private static void vertex(float[] grade, float share, float x, float y) {
        glColor4f(1f + (grade[0] - 1f) * share, 1f + (grade[1] - 1f) * share, 1f + (grade[2] - 1f) * share, 1f);
        glVertex2f(x, y);
    }

    /** Metal in the rock, a fleck on some wild cells, each catching the light on its own beat. Added light. */
    private void flecks(CellTopology t, BattleCamera cam, VisibleCellRect view, float time, float alpha, DrawList out) {
        ShuttleSpriteCache dot = sprites.spriteByPath(DOT_SPRITE);
        if (dot == null) return;
        float cellPx = cam.cellPxSize(), size = SignatureLook.FLECK_CELLS * 10f * cellPx;
        float[] rgb = SignatureLook.FLECK_RGB;
        for (int y = view.minY(); y <= view.maxY(); y++) {
            for (int x = view.minX(); x <= view.maxX(); x++) {
                int hash = hash(x, y);
                if ((hash & 1023) / 1024f >= SignatureLook.FLECK_SHARE || !wildCell(t, x, y)) continue;
                float fx = x + ((hash >>> 10) & 255) / 255f, fy = y + ((hash >>> 18) & 255) / 255f;
                float glint = SignatureLook.glint(((hash >>> 4) & 255) / 255f, time);
                out.addAdditiveSprite(RenderLayer.GROUND, dot.sprite, cam.cellToScreenX(fx), cam.cellToScreenY(fy),
                        size, size, 0f, rgb[0], rgb[1], rgb[2], SignatureLook.FLECK_ALPHA * glint * alpha);
            }
        }
    }

    /** The signature's marks on the ground, over the tiles and under the props: vent mounds, crater bowls. */
    RenderSystem marks() {
        return RenderSystem.of(RenderLayer.DECALS, (ctx, out) -> {
            CellTopology topology = ctx.sim.getTopology();
            if (topology == null || topology.signatureMarks().length == 0) return;
            if (topology.signature() == SurfaceSignature.FISSURES) {
                cracks(topology.signatureMarks(), ctx.camera, ctx.alphaMult, out);
                return;
            }
            boolean craters = topology.signature() == SurfaceSignature.CRATER_FIELD;
            ShuttleSpriteCache art = sprites.spriteByPath(craters ? CRATER_SPRITE : MOUND_SPRITE);
            if (art != null) marks(topology.signatureMarks(), craters, art, ctx.camera, ctx.alphaMult, out);
        });
    }

    /**
     * An unstable world's cracks, each a header {@code {-1, points, 0}} and its points' {@code {x, y, width}}: a dark
     * opening as wide as the crack is there, a near-black depth down its middle, and the ground's broken lip catching
     * the light on the side toward it.
     */
    static void cracks(float[] marks, BattleCamera cam, float alpha, DrawList out) {
        float cellPx = cam.cellPxSize();
        float[] open = SignatureLook.CRACK_OPEN_RGB, deep = SignatureLook.CRACK_DEEP_RGB;
        for (int i = 0; i + 2 < marks.length; ) {
            int n = (int) marks[i + 1];
            int first = i + 3;
            i = first + n * 3;
            for (int pass = 0; pass < 3; pass++) {
                for (int k = 0; k + 1 < n; k++) {
                    int a = first + k * 3, b = a + 3;
                    float w = (marks[a + 2] + marks[b + 2]) * .5f * cellPx;
                    if (w < .6f) continue;
                    float x0 = cam.cellToScreenX(marks[a]), y0 = cam.cellToScreenY(marks[a + 1]);
                    float x1 = cam.cellToScreenX(marks[b]), y1 = cam.cellToScreenY(marks[b + 1]);
                    if (!onScreen(cam, x0, y0, w * 4f) && !onScreen(cam, x1, y1, w * 4f)) continue;
                    if (pass == 0) {
                        // The lip: offset toward the light, which falls from the upper left.
                        float lx = -w * SignatureLook.CRACK_LIP_SHIFT, ly = w * SignatureLook.CRACK_LIP_SHIFT;
                        out.addLine(RenderLayer.DECALS, x0 + lx, y0 + ly, x1 + lx, y1 + ly, w * 1.25f,
                                1f, 1f, 1f, SignatureLook.CRACK_LIP_ALPHA * alpha);
                    } else if (pass == 1) {
                        out.addLine(RenderLayer.DECALS, x0, y0, x1, y1, w, open[0], open[1], open[2], alpha);
                    } else {
                        out.addLine(RenderLayer.DECALS, x0, y0, x1, y1, w * .45f, deep[0], deep[1], deep[2], alpha);
                    }
                }
            }
        }
    }

    private static void marks(float[] marks, boolean craters, ShuttleSpriteCache art, BattleCamera cam, float alpha,
                              DrawList out) {
        for (int i = 0; i + 2 < marks.length; i += 3) {
            float cells = craters ? SignatureLook.craterCells(marks[i + 2]) : SignatureLook.MOUND_CELLS;
            float size = cells * cam.cellPxSize();
            float px = cam.cellToScreenX(marks[i]), py = cam.cellToScreenY(marks[i + 1]);
            if (!onScreen(cam, px, py, size)) continue;
            // A crater's light falls from one side, so it keeps its turn; a mound is lit evenly and may spin.
            int spin = craters ? 0 : Math.floorMod(hash((int) marks[i], (int) marks[i + 1]), 360);
            out.addSprite(RenderLayer.DECALS, art.sprite, px, py, size, size, spin, 1f, 1f, 1f, alpha);
        }
    }

    static int hash(int x, int y) {
        int h = x * 0x27d4eb2d ^ y * 0x165667b1;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return h & 0x7fffffff;
    }

    private static boolean onScreen(BattleCamera cam, float px, float py, float pad) {
        return px >= cam.vpX() - pad && px <= cam.vpX() + cam.vpW() + pad
                && py >= cam.vpY() - pad && py <= cam.vpY() + cam.vpH() + pad;
    }

    /** The haze and the vents' vapour, over the props and under the fog and the units. */
    RenderSystem air() {
        return RenderSystem.of(RenderLayer.DOODADS, this::collectAir);
    }

    private void collectAir(RenderContext ctx, DrawList out) {
        CellTopology topology = ctx.sim.getTopology();
        if (topology == null) return;
        SurfaceSignature signature = topology.signature();
        boolean hazy = signature == SurfaceSignature.HOT_HAZE;
        float[] vents = signature == SurfaceSignature.CRYO_VENTS ? topology.signatureMarks() : new float[0];
        if (!hazy && vents.length == 0) return;
        ShuttleSpriteCache sheet = sprites.spriteByPath(PUFF_SHEET);
        if (sheet == null || sheet.pxW <= 0) return;
        BattleCamera cam = ctx.camera;
        float time = FireLight.clock(ctx.animationTimeSeconds);
        if (hazy) haze(topology, cam, sheet, time, ctx.alphaMult, out);
        for (int i = 0; i + 2 < vents.length; i += 3) vapour(vents[i], vents[i + 1], i, cam, sheet, time, ctx.alphaMult, out);
    }

    /** Banks of haze on a grid drifting with the wind, thick where the noise piles them and thin between. */
    private static void haze(CellTopology t, BattleCamera cam, ShuttleSpriteCache sheet, float time, float alpha,
                             DrawList out) {
        int cells = PUFF_SHEET_CELLS, cw = sheet.pxW / cells, ch = sheet.pxH / cells;
        float spacing = SignatureLook.HAZE_SPACING, puff = SignatureLook.HAZE_PUFF * cam.cellPxSize();
        float driftX = (time * SignatureLook.HAZE_DRIFT_X) % spacing, driftY = (time * SignatureLook.HAZE_DRIFT_Y) % spacing;
        VisibleCellRect view = cam.visibleCells(VisibleCellRect.GEOMETRY_MARGIN_CELLS, t.getWidth(), t.getHeight());
        float[] rgb = SignatureLook.HAZE_RGB;
        int gx0 = (int) Math.floor((view.minX() - SignatureLook.HAZE_PUFF) / spacing) - 1;
        int gx1 = (int) Math.ceil((view.maxX() + SignatureLook.HAZE_PUFF) / spacing) + 1;
        int gy0 = (int) Math.floor((view.minY() - SignatureLook.HAZE_PUFF) / spacing) - 1;
        int gy1 = (int) Math.ceil((view.maxY() + SignatureLook.HAZE_PUFF) / spacing) + 1;
        for (int gy = gy0; gy <= gy1; gy++) {
            for (int gx = gx0; gx <= gx1; gx++) {
                int hash = hash(gx, gy);
                float x = gx * spacing + driftX + ((hash & 255) / 255f - .5f) * spacing;
                float y = gy * spacing + driftY + (((hash >>> 8) & 255) / 255f - .5f) * spacing;
                float density = SignatureLook.hazeDensity(x, y, time, 0x48415a45);
                if (density <= .02f) continue;
                float px = cam.cellToScreenX(x), py = cam.cellToScreenY(y);
                if (!onScreen(cam, px, py, puff)) continue;
                int cell = hash % (cells * cells);
                out.addImageRegion(RenderLayer.DOODADS, sheet.sprite, sheet.pxW, sheet.pxH,
                        cell % cells * cw, cell / cells * ch, cw, ch, px, py, puff, puff, (hash >>> 4) % 360,
                        rgb[0], rgb[1], rgb[2], SignatureLook.HAZE_ALPHA * density * alpha);
            }
        }
    }

    /**
     * A vent's vapour: puffs leaving the mound one after another, swelling, thinning and drifting downwind, in
     * breaths, so a vent puffs, rests and puffs again. A puff already out keeps going when the vent stops.
     */
    private static void vapour(float vx, float vy, int seed, BattleCamera cam, ShuttleSpriteCache sheet, float time,
                               float alpha, DrawList out) {
        int cells = PUFF_SHEET_CELLS, cw = sheet.pxW / cells, ch = sheet.pxH / cells;
        int beat = hash(seed, 7);
        float phase = (beat & 1023) / 1023f;
        float period = SignatureLook.VENT_SECONDS * (.85f + ((beat >>> 10) & 63) / 63f * .3f);
        float[] rgb = SignatureLook.VAPOUR_RGB;
        for (int p = 0; p < SignatureLook.VENT_PUFFS; p++) {
            float u = (float) ((time / period + p / (float) SignatureLook.VENT_PUFFS) % 1.0);
            float strength = SignatureLook.breath(phase, time - u * period);
            if (strength <= 0f) continue;
            float reach = u * SignatureLook.VENT_REACH;
            float wobble = (float) Math.sin(u * 4f + p) * .6f;
            float x = vx + WIND_X * reach - WIND_Y * wobble, y = vy + WIND_Y * reach + WIND_X * wobble;
            float size = (SignatureLook.VENT_BASE + u * SignatureLook.VENT_SWELL) * cam.cellPxSize();
            float px = cam.cellToScreenX(x), py = cam.cellToScreenY(y);
            if (!onScreen(cam, px, py, size)) continue;
            // Dense as it leaves the vent, thinning as it spreads.
            float a = SignatureLook.VENT_ALPHA * strength * (float) Math.sqrt(Math.sin(Math.PI * Math.min(1f, u * 1.4f + .05f)))
                    * (1f - u) * alpha;
            int cell = Math.floorMod(beat + p * 7, cells * cells);
            int turn = (beat + p * 53) % 360;
            // Its shadow on the ground first, offset away from the light and growing with the puff's height:
            // white vapour over a snowfield is seen by the shade it casts.
            float lift = u * SignatureLook.SHADOW_LIFT * cam.cellPxSize();
            out.addImageRegion(RenderLayer.DOODADS, sheet.sprite, sheet.pxW, sheet.pxH,
                    cell % cells * cw, cell / cells * ch, cw, ch, px + lift, py - lift, size, size, turn,
                    SignatureLook.SHADOW_RGB[0], SignatureLook.SHADOW_RGB[1], SignatureLook.SHADOW_RGB[2],
                    a * SignatureLook.SHADOW_ALPHA);
            out.addImageRegion(RenderLayer.DOODADS, sheet.sprite, sheet.pxW, sheet.pxH,
                    cell % cells * cw, cell / cells * ch, cw, ch, px, py, size, size, turn,
                    rgb[0], rgb[1], rgb[2], a);
        }
    }
}
