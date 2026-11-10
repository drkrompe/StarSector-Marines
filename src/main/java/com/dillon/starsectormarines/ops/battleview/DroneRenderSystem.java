package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.components.CrashingComponent;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.drone.Drone;
import com.dillon.starsectormarines.battle.drone.DroneLook;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.tender.TenderDroneArt;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Emits the {@link RenderLayer#DRONES} layer — recon/attack drones that hover at
 * roof altitude (painted above ROOFS so a drone over a building overlays the roof
 * rather than being occluded by it). Two passes: crashing wrecks (read straight
 * from the {@link CrashingComponent} component store, fading hull only, drawn first so they
 * sit under the living) then live drones (dense-registry iteration — one rotated
 * {@code SPRITE} hull + an ownership-coded durability bar via the shared
 * {@link DurabilityBarDecor}, placement uses the layer's own {@code HP_BAR_GAP}).
 *
 * <p>Faithful port of the former inline {@code BattleRenderer.renderDrones}: same
 * crash/visibility gating and fade-alpha (VIS_FADING fade + crash fade-out), same
 * hull sizing and HP-bar geometry. The single units-list scan (live + dead) split
 * into a corpse pass over the crash store and a live pass over the registry — a
 * crashing drone is an entity with a {@code CrashingComponent} component and no live
 * registry row, so neither pass needs the legacy units list.
 * The hull goes through the whole-texture {@code SPRITE} command (no batch
 * registration), which resets sprite angle after each draw — so the old
 * end-of-pass {@code setAngle(0)} reset is dropped. The drone sprite is loaded at
 * {@code BattleScreen.attach} (it was lazily ensured inside the pass) so
 * {@link #collect} stays GL-free.
 *
 * <p>A drone floats ({@link DroneFloat}): every drone's shadow is drawn first, its own silhouette offset away from the
 * light, then every hull, each bobbing gently in its own phase.
 */
public final class DroneRenderSystem implements RenderSystem {

    private final BattleSprites sprites;

    public DroneRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.DRONES;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        ShuttleSpriteCache standard = sprites.droneSprite();
        ShuttleSpriteCache tender = sprites.spriteByPath(TenderDroneArt.spritePath());
        if (standard == null && tender == null) return;

        FogOfWarService vis = ctx.sim.getFogOfWar();
        World world = ctx.sim.world();
        BattleCamera cam = ctx.camera;
        ViewCull view = ViewCull.of(cam);
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;
        // A drone is under a cell across and its bar stands just above it; two
        // cells bounds both whichever way the sprite has turned.
        float extentCells = Drone.VISUAL_CELLS * 2f;
        float barW = cellPx * 0.9f;

        float clock = DroneFloat.clock(ctx.animationTimeSeconds, ctx.sim.getSimTickIndex(), BattleSimulation.TICK_DT);
        BattleComponents components = ctx.sim.getBattleComponents();
        EntityWorld entityWorld = ctx.sim.getEntityWorld();

        // Two passes, every shadow and then every hull, so no drone's shadow lies over another drone (DroneFloat).
        for (int pass = 0; pass < 2; pass++) {
            boolean shadows = pass == 0;

            // Crashing (dead, falling) drones first so their fading wrecks paint UNDER
            // the live drones. A dead drone is an entity that no longer sits in the
            // live registry but still carries a CRASHING component on its corpse row —
            // walk the CRASHING query directly (no units-list scan, no Entity handle):
            // the component owns the body the wreck tracks and the timer that fades it
            // out. No vision gate, matching the legacy pass (a crash is always shown).
            // A wreck's shadow closes on it as it fades, so the two meet where it comes down.
            for (ArchetypeTable t : entityWorld.matched(components.crashing)) {
                Object[] states = t.objects(components.CRASHING, BattleComponents.CRASHING_STATE).array();
                for (int r = 0, n = t.rowCount(); r < n; r++) {
                    CrashingComponent crash = (CrashingComponent) states[r];
                    AirBody body = crash.body;
                    ShuttleSpriteCache cache = cacheFor(body, standard, tender, sprites);
                    if (cache == null) continue;
                    float fade = Math.max(0f, Math.min(1f, crash.timer / Drone.CRASH_DURATION_SEC));
                    if (!view.visible(body.x, body.y, extentCells)) continue;
                    float bob = DroneFloat.bob(t.entityAt(r), clock);
                    float lengthCells = heightCells(body, cache);
                    float pxH = lengthCells * cellPx * DroneFloat.scale(bob);
                    float pxW = pxH * cache.aspect;
                    float drawAlpha = alphaMult * fade;
                    if (shadows) {
                        shadow(out, cam, cache, body, pxW, pxH, DroneFloat.shadowCells(lengthCells, bob, fade), drawAlpha);
                    } else {
                        out.addSprite(RenderLayer.DRONES, cache.sprite,
                                cam.cellToScreenX(body.x), cam.cellToScreenY(body.y), pxW, pxH, body.facingDegrees,
                                1f, 1f, 1f, drawAlpha);
                    }
                }
            }

            // Live drones — iterate the dense registry; the corpse never appears.
            for (int i = 0, n = ctx.sim.liveUnitCount(); i < n; i++) {
                long u = ctx.sim.liveUnitAt(i);
                if (!ctx.sim.identity().type(u).isDrone()) continue;
                byte uv = ctx.hostProfile.fogHidesUnits() ? vis.getUnitVisibility(i) : FogOfWarService.VIS_VISIBLE;
                if (uv == FogOfWarService.VIS_HIDDEN) continue;

                // The drone's body is a world KINEMATICS component now (read by id).
                AirBody body = world.kinematics(u);
                ShuttleSpriteCache cache = cacheFor(body, standard, tender, sprites);
                if (cache == null) continue;
                if (!view.visible(body.x, body.y, extentCells)) continue;
                float lengthCells = heightCells(body, cache);
                float restH = lengthCells * cellPx;
                float bob = DroneFloat.bob(u, clock);
                float pxH = restH * DroneFloat.scale(bob);
                float pxW = pxH * cache.aspect;
                float drawAlpha = alphaMult;
                if (uv == FogOfWarService.VIS_FADING) {
                    drawAlpha *= vis.getFadeAlpha(i);
                }
                if (shadows) {
                    shadow(out, cam, cache, body, pxW, pxH, DroneFloat.shadowCells(lengthCells, bob, 1f), drawAlpha);
                    continue;
                }

                float cx = cam.cellToScreenX(body.x);
                float cy = cam.cellToScreenY(body.y);
                out.addSprite(RenderLayer.DRONES, cache.sprite,
                        cx, cy, pxW, pxH, body.facingDegrees,
                        1f, 1f, 1f, drawAlpha);

                // The bar stands off the drone's resting size, so it does not bob with it.
                float barY = cy + restH / 2f + BattleRenderer.HP_BAR_GAP;
                Allegiance owner = Allegiance.of(ctx.sim.identity().faction(u));
                if (world.hasArmor(u)) {
                    DurabilityBarDecor.emit(out, RenderLayer.DRONES, owner, cx, barY, barW,
                            world.hp(u), world.maxHp(u),
                            world.armor(u), world.maxArmor(u), drawAlpha);
                } else {
                    DurabilityBarDecor.emit(out, RenderLayer.DRONES, owner, cx, barY, barW,
                            world.hp(u), world.maxHp(u), drawAlpha);
                }
            }
        }
    }

    /** The drone's own silhouette, dark and faint, {@code cells} down and to the right of it (light from the upper left). */
    private static void shadow(DrawList out, BattleCamera cam, ShuttleSpriteCache cache, AirBody body,
                               float pxW, float pxH, float cells, float alpha) {
        out.addSprite(RenderLayer.DRONES, cache.sprite,
                cam.cellToScreenX(body.x + cells), cam.cellToScreenY(body.y - cells), pxW, pxH, body.facingDegrees,
                0f, 0f, 0f, alpha * DroneFloat.SHADOW_ALPHA);
    }

    /** The sprite a body is drawn with: its look's own, or the shipped drone's when that look's art did not load. */
    private static ShuttleSpriteCache cacheFor(AirBody body, ShuttleSpriteCache standard, ShuttleSpriteCache tender,
                                               BattleSprites sprites) {
        DroneLook look = DroneLook.of(body);
        if (look == DroneLook.TENDER && tender != null) return tender;
        if (look != DroneLook.STANDARD && look.spritePath() != null) {
            ShuttleSpriteCache own = sprites.spriteByPath(look.spritePath());
            if (own != null) return own;
        }
        return standard;
    }

    /**
     * The sprite's long-axis length in cells. The shipped drone has an authored
     * size; a tender's drone is drawn at hull scale from its own pixels, as it is
     * beside its hull in fleet combat.
     */
    private static float heightCells(AirBody body, ShuttleSpriteCache cache) {
        DroneLook look = DroneLook.of(body);
        if (look == DroneLook.TENDER && cache.pxH > 0) return TenderDroneArt.cells(cache.pxH);
        return look.heightCells() > 0f ? look.heightCells() : Drone.VISUAL_CELLS;
    }
}
