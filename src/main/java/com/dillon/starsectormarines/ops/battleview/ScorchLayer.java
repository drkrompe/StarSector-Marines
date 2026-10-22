package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.fire.FireFuel;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.VisibleCellRect;

/**
 * Burnt ground shaded per pixel (`fire-nouns.md`, Look), for the styles that are
 * not a flat fill ({@link FireRenderSystem.Scorch#layered()}). It drains with the
 * creep, inside the ground's colour target and before the creep itself, so the
 * relief composite displaces it with the ground it darkens. The picture is read
 * straight off the fire field and the map: how far each cell has burnt, how heavy
 * its fuel was, what lies underfoot, and how long ago it burnt out.
 */
final class ScorchLayer implements RenderSystem {

    @Override
    public RenderLayer layer() { return RenderLayer.CREEP; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        FireRenderSystem.Scorch style = FireRenderSystem.scorchStyle();
        if (!FireRenderSystem.enabled() || !style.layered()) return;
        FireField field = ctx.sim.fireFieldIfPresent();
        if (field == null || field.revision() == 0) return;
        BattleCamera camera = ctx.camera;
        VisibleCellRect view = camera.visibleCells(2, field.width(), field.height());
        // Only the tiles the fire has touched, plus a cell for the singe past their edge.
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int ty = view.minY() / FireField.TILE; ty <= view.maxY() / FireField.TILE; ty++) {
            for (int tx = view.minX() / FireField.TILE; tx <= view.maxX() / FireField.TILE; tx++) {
                if (field.tileRevision(tx, ty) == 0) continue;
                minX = Math.min(minX, tx * FireField.TILE - 1);
                minY = Math.min(minY, ty * FireField.TILE - 1);
                maxX = Math.max(maxX, tx * FireField.TILE + FireField.TILE);
                maxY = Math.max(maxY, ty * FireField.TILE + FireField.TILE);
            }
        }
        minX = Math.max(minX, view.minX());
        minY = Math.max(minY, view.minY());
        maxX = Math.min(maxX, view.maxX());
        maxY = Math.min(maxY, view.maxY());
        if (maxX < minX || maxY < minY) return;
        Picture picture = new Picture(field, ctx.sim.getGrid(), ctx.sim.getTopology());
        out.addCustom(RenderLayer.CREEP, new ScorchPass(picture, style.shading(), minX, minY, maxX, maxY,
                camera.cellToScreenX(0f), camera.cellToScreenY(0f), camera.cellPxSize(), ctx.alphaMult));
    }

    /** How heavy a burnt cell's fuel was, from what burnt there. */
    static float heaviness(int mask) {
        if (FireFuel.CROWN.in(mask)) return 1f;
        if (FireFuel.DEBRIS.in(mask)) return 0.8f;
        if (FireFuel.BRUSH.in(mask)) return 0.75f;
        if (FireFuel.CREEP.in(mask)) return 0.55f;
        if (FireFuel.DEAD_CREEP.in(mask)) return 0.3f;
        return 0.25f;
    }

    /** The fire field and the map read as {@link ScorchShading.Field}; read-only, so the drain's bands may share it. */
    static final class Picture implements ScorchShading.Field {
        private final FireField field;
        private final NavigationGrid grid;
        private final CellTopology topology;
        private final int w;

        Picture(FireField field, NavigationGrid grid, CellTopology topology) {
            this.field = field;
            this.grid = grid;
            this.topology = topology;
            this.w = field.width();
        }

        @Override public int width() { return w; }
        @Override public int height() { return field.height(); }

        @Override public float amount(int x, int y) { return field.burnProgress(y * w + x) / 255f; }

        @Override public float heavy(int x, int y) { return heaviness(field.burnedMaskAt(y * w + x)); }

        @Override
        public int ground(int x, int y) {
            int idx = y * w + x;
            int mask = field.burnedMaskAt(idx);
            if (FireFuel.CREEP.in(mask) || FireFuel.DEAD_CREEP.in(mask)) return ScorchShading.CREEP;
            if (FireFuel.CROWN.in(mask)) return ScorchShading.SOIL;
            if (!grid.isWalkableAt(idx)) return ScorchShading.NONE;
            CellTopology.GroundKind kind = topology.getGroundKind(x, y);
            if (kind == null) return ScorchShading.PAVED;
            return switch (kind) {
                case GRASS, DIRT, SAND, SNOW, RUBBLE -> ScorchShading.SOIL;
                case WATER, VOID -> ScorchShading.NONE;
                default -> ScorchShading.PAVED;
            };
        }

        @Override
        public float ageSeconds(int x, int y) {
            int age = field.burntAge(y * w + x);
            return age < 0 ? 0f : age / 30f;
        }
    }
}
