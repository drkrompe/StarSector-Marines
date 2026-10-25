package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.evacuation.BroodSpires;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFlight;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLook;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.HashMap;
import java.util.Map;

/**
 * A brood spire's waiting brood, clinging to the hooks its painting gives it with their wings folded
 * ({@link BroodSpires#perched}), each now and then half-opening them. Drawn only: they are the pack the spire launches
 * next, not units, and nothing can shoot them until they rise (`colony-breach-nouns.md`, Brood spire).
 */
final class SpirePerchRenderSystem implements RenderSystem {

    /**
     * Where the hooks are on each of the spire's paintings, in cells from the structure's centre (x, y; y up the map),
     * read off the roost paintings by eye: the painting is never turned, so the hooks are fixed on the map too.
     */
    static final float[][][] PERCHES = {
            {{-2.70f, 1.14f}, {-1.73f, 2.05f}, {-0.62f, 0.36f}, {-1.61f, -0.20f}, {-1.98f, 0.98f}},
            {{-1.67f, 3.08f}, {-1.87f, 2.35f}, {-0.16f, 1.98f}, {-0.08f, 1.28f}, {-1.64f, 1.70f}},
            {{-2.34f, 1.45f}, {-2.65f, 0.89f}, {-1.80f, 1.02f}, {-0.94f, 0.27f}, {-1.80f, -0.39f}},
    };
    /** Drawn size against a flying Harrier's: folded and clinging, it is smaller than spread in the air. */
    static final float SCALE = 0.62f;
    /** Seconds between one bird's stirs, and how long a stir holds its wings half open. */
    static final float STIR_PERIOD = 5.5f, STIR_SECONDS = 0.45f;

    private final BattleSprites sprites;
    /** One drawn bird per spire and hook, kept so its rig holds still between frames. */
    private final Map<Long, HarrierFlight> birds = new HashMap<>();

    SpirePerchRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.DRONES;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        BroodSpires spires = ctx.sim.broodSpires();
        if (spires.spires().isEmpty()) return;
        StructureDef def = TurretCatalogRegistry.requireStructure(BroodSpires.STRUCTURE);
        BattleCamera cam = ctx.camera;
        ViewCull view = ViewCull.of(cam);
        float cellPx = cam.cellPxSize();
        FogOfWarService vis = ctx.sim.getFogOfWar();
        float clock = Float.isFinite(ctx.animationTimeSeconds) ? ctx.animationTimeSeconds : 0f;
        for (BroodSpires.Spire spire : spires.spires()) {
            int perched = spires.perched(ctx.sim, spire);
            if (perched == 0) continue;
            float cx = spire.x + 0.5f, cy = spire.y + 0.5f;
            if (!view.visible(cx, cy, 4f)) continue;
            float alpha = ctx.alphaMult * HarrierRenderSystem.visibility(ctx, vis, spire.entityId);
            if (alpha <= 0f) continue;
            float[][] hooks = PERCHES[(def.art.variantFor(spire.entityId) - 1) % PERCHES.length];
            for (int i = 0; i < perched && i < hooks.length; i++) {
                HarrierFlight bird = bird(spire.entityId, i, cx + hooks[i][0], cy + hooks[i][1], cx, cy);
                float stir = (clock + i * 1.7f + (spire.entityId % 7)) % STIR_PERIOD;
                String frame = stir < STIR_SECONDS ? bird.look.fold(1) : bird.look.dive();
                HarrierBodyComposer.compose(sprites, bird, frame, bird.body.x, bird.body.y, SCALE,
                        (part, x, y, degrees, w, h) -> out.addSprite(RenderLayer.DRONES, part.sprite,
                                cam.cellToScreenX(x), cam.cellToScreenY(y), w * cellPx, h * cellPx, degrees,
                                1f, 1f, 1f, alpha));
            }
        }
    }

    /** The bird on hook {@code i} of the spire {@code spireId}, facing out from the spire's centre. */
    private HarrierFlight bird(long spireId, int i, float x, float y, float cx, float cy) {
        return birds.computeIfAbsent(spireId * 8 + i, k -> {
            AirBody body = new AirBody();
            body.x = x;
            body.y = y;
            // Facing outward along the hook: forward is (-sin, cos) of the facing.
            body.facingDegrees = (float) Math.toDegrees(Math.atan2(-(x - cx), y - cy));
            HarrierFlight bird = new HarrierFlight(-k - 1, body, HarrierLook.DEFAULT);
            bird.altitude = bird.shownAltitude = 0f;
            bird.fold = 1f;
            return bird;
        });
    }
}
