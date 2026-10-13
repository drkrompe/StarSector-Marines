package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;
import com.dillon.starsectormarines.ui.Fonts;

import java.awt.Color;

/** Read-only pickup markers. Unknown caches emit no commands, including in observer captures. */
public final class SalvageCacheRenderSystem implements RenderSystem {
    private final BattleSprites sprites;
    private final PolyMesh mesh = new PolyMesh(256);
    public SalvageCacheRenderSystem(BattleSprites sprites) { this.sprites = sprites; }
    @Override public RenderLayer layer() { return RenderLayer.OBJECTIVES; }

    public static String label(SalvageCache cache) {
        return cache.quantity + " " + cache.resource.displayName;
    }
    public static String status(SalvageCache cache) {
        if (cache.secured()) return "SECURED / CLAIM AFTER VICTORY";
        if (cache.contested()) return "CONTESTED / CLEAR THE AREA";
        if (cache.fraction() > 0) return "SECURING / " + (int) (cache.fraction() * 100) + "%";
        return "OPTIONAL / INFANTRY NEARBY / HOLD 5s";
    }

    @Override public void collect(RenderContext ctx, DrawList out) {
        mesh.reset();
        var cam = ctx.camera;
        float px = cam.cellPxSize();
        var registry = TileRegistry.installed();
        var crate = registry == null ? null : registry.doodad("doodad.crate");
        for (var cache : ctx.sim.fieldSalvage().caches()) {
            if (!cache.discovered()) continue;
            float x = cam.cellToScreenX(cache.cellX + .5f), y = cam.cellToScreenY(cache.cellY + .5f);
            if (!ViewCull.of(cam).visibleScreen(x, y, 350)) continue;
            boolean seen = ctx.sim.playerSeesCell(cache.cellX, cache.cellY);
            float alpha = ctx.alphaMult * (seen ? 1 : .5f);
            float r = cache.secured() ? .45f : 1f, g = cache.contested() ? .3f : .78f, b = .35f;
            float radius = Math.max(12, px * .9f);
            PolyTess.appendAnnulus(mesh, x, y, radius - 2, radius, 4, r, g, b, alpha);
            if (!cache.secured() && seen && crate != null) {
                var sheet = crate.sheetPath.equals(TileManifest.SHEET) ? sprites.tileSheet()
                        : sprites.extraTerrainSheet(crate.sheetPath);
                if (sheet != null) {
                    int cell = crate.sourceCellPx > 0 ? crate.sourceCellPx : 32;
                    out.addSheetQuad(layer(), sheet, crate.col * cell, crate.row * cell,
                            cell * crate.footprintCellsX, cell * crate.footprintCellsY,
                            x, y, px * 1.2f, px * 1.2f, 1, 1, 1, alpha);
                }
            }
            if (!cache.secured() && cache.fraction() > 0)
                PolyTess.appendArc(mesh, x, y, radius + 3, radius + 6, cache.fraction(), 32, r, g, b, alpha);
            if (px < 8) continue;
            String title = label(cache), detail = seen || cache.secured() ? status(cache) : "LAST SEEN / OPTIONAL SALVAGE";
            float textY = y - radius - 6;
            out.addSolidRect(layer(), x - 155, textY - 29, x + 155, textY + 2, .03f, .045f, .055f, .85f * alpha);
            out.addCustom(layer(), new WorldLabel(Fonts.ORBITRON_12_BOLD, title, x, textY,
                    new Color(r, g, b), alpha));
            out.addCustom(layer(), new WorldLabel(Fonts.ORBITRON_12_BOLD, detail, x, textY - 15,
                    Color.LIGHT_GRAY, alpha));
        }
        if (!mesh.isEmpty()) out.addPoly(layer(), mesh);
    }
}
