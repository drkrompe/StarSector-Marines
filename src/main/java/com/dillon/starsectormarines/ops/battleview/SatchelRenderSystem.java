package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.satchel.SatchelChargeService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;
import com.fs.starfarer.api.graphics.SpriteAPI;

/** Draws legally known attached satchels and their friendly-fire blast footprint. */
public final class SatchelRenderSystem implements RenderSystem {

    private static final int RING_SEGMENTS = 48;
    private final BattleSprites sprites;
    private final SpecialEquipmentDef equipment;
    private final PolyMesh rings = new PolyMesh(96);

    public SatchelRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
        this.equipment = SpecialEquipmentRegistry.require(
                SpecialEquipmentRegistry.SATCHEL_CHARGE_ID);
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.HAZARDS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        rings.reset();
        BattleCamera camera = ctx.camera;
        float cellPx = camera.cellPxSize();
        SpriteAPI pack = sprites.satchelChargeSprite();
        for (SatchelChargeService.ChargeView charge
                : ctx.sim.satchelCharges().activeCharges()) {
            int cellX = (int) Math.floor(charge.x());
            int cellY = (int) Math.floor(charge.y());
            if (charge.sourceFaction() != Faction.MARINE
                    && !ctx.sim.getFogOfWar().isCellRevealed(cellX, cellY)) continue;

            float sx = camera.cellToScreenX(charge.x());
            float sy = camera.cellToScreenY(charge.y());
            float fuse = charge.totalFuse() > 0f
                    ? Math.max(0f, Math.min(1f, charge.remaining() / charge.totalFuse())) : 0f;
            float urgency = 1f - fuse;
            float pulse = 0.82f + 0.18f * (float) Math.sin(
                    charge.remaining() * (8f + urgency * 18f));
            float radius = charge.blastRadius() * cellPx;
            PolyTess.appendAnnulus(rings, sx, sy, radius - 2.5f, radius,
                    RING_SEGMENTS, 1f, 0.20f + 0.40f * fuse, 0.05f,
                    (0.24f + urgency * 0.24f) * ctx.alphaMult);
            PolyTess.appendArc(rings, sx, sy, radius - 6f, radius - 3.5f,
                    fuse, RING_SEGMENTS, 1f, 0.76f, 0.16f,
                    0.75f * ctx.alphaMult);

            if (pack != null) {
                float authoredSize = equipment.presentation().deployed().visualCells();
                float size = cellPx * (authoredSize + urgency * 0.05f);
                out.addSprite(RenderLayer.HAZARDS, pack, sx, sy,
                        size, size, (charge.id() * 37f) % 360f,
                        1f, 0.84f + 0.16f * pulse, 0.72f * pulse,
                        ctx.alphaMult);
            }
        }
        if (!rings.isEmpty()) out.addPoly(RenderLayer.HAZARDS, rings);
    }
}
