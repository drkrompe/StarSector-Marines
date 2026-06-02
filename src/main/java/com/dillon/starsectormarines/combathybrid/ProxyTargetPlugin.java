package com.dillon.starsectormarines.combathybrid;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.dillon.starsectormarines.render2d.SolidQuadBatch;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

/**
 * S2 probe — the <b>proxy / avatar pattern</b> at its smallest (overview § "the
 * tractable third framing").
 *
 * <p>Drives a single invisible {@link ShipAPI} that stands in for a sim entity (a
 * turret / squad) inside vanilla's targeting graph. The sim owns its position; vanilla
 * only <em>targets</em> it:
 * <ul>
 *   <li>Invisible ({@code setExtraAlphaMult(0)}) — our renderer would draw the real
 *       sprite; here a crosshair marker shows where it is.</li>
 *   <li>Pinned every frame ({@code getLocation().set(anchor)}, {@code getVelocity().set(0,0)})
 *       so weapon impulse / fighter bumps net zero movement — collision class stays
 *       {@code SHIP} so it's hittable (fact #7: no shootable-but-not-bumpable class).</li>
 *   <li>Holds fire — it's a target, not a combatant.</li>
 *   <li>HP delta logged each frame; despawns at zero.</li>
 * </ul>
 *
 * <p><b>What this answers:</b> does a player carrier's fighter AI launch and strafe a
 * slaved, owner-1 proxy with zero targeting code from us? A yes makes "the fleet above
 * reacts to the ground battle below" mostly plumbing. The HP drain is a standalone
 * counter here; wiring it into {@code BattleSimulation}'s external-damage path is the
 * next story (overview open question #2). Throwaway dev scaffolding.
 */
@DebugOnly
public class ProxyTargetPlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(ProxyTargetPlugin.class);

    /** Half-size of the world-space crosshair marker (the proxy itself is invisible). */
    private static final float MARKER_HALF = 70f;
    private static final float MARKER_THICK = 8f;

    private final ShipAPI proxy;
    private final Vector2f anchor;

    private CombatEngineAPI engine;
    private float lastLoggedHp = -1f;
    private boolean done;

    public ProxyTargetPlugin(ShipAPI proxy, Vector2f anchor) {
        this.proxy = proxy;
        this.anchor = new Vector2f(anchor);
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        if (proxy == null) {
            LOG.warn("S2 proxy: null proxy ship; nothing to drive.");
            done = true;
            return;
        }
        proxy.setExtraAlphaMult(0f);                 // invisible; the marker shows its position
        proxy.setCollisionClass(CollisionClass.SHIP); // hittable by weapons (explicit)
        proxy.setShipAI(null);                        // no brain — we pin it each frame
        lastLoggedHp = proxy.getHitpoints();
        LOG.info("S2 proxy: invisible owner-1 proxy spawned, hp=" + (int) proxy.getHitpoints()
                + "/" + (int) proxy.getMaxHitpoints() + " at (" + (int) anchor.x + "," + (int) anchor.y + ")");
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (done || engine == null || proxy == null) return;

        if (!proxy.isAlive() || proxy.getHitpoints() <= 0f) {
            LOG.info("S2 proxy: destroyed by vanilla AI — despawning. Native carrier/fighter "
                    + "targeting engaged the slaved proxy.");
            engine.removeEntity(proxy);
            done = true;
            return;
        }

        // Sim owns the position: re-pin every frame so weapon impulse and fighter
        // bumps resolve to zero net movement.
        proxy.getLocation().set(anchor);
        proxy.getVelocity().set(0f, 0f);
        proxy.setHoldFire(true);

        float hp = proxy.getHitpoints();
        if (lastLoggedHp < 0f) lastLoggedHp = hp;
        if (hp < lastLoggedHp - 1f) {
            LOG.info("S2 proxy: HP " + (int) hp + "/" + (int) proxy.getMaxHitpoints()
                    + "  (-" + (int) (lastLoggedHp - hp) + " since last)");
            lastLoggedHp = hp;
        }
    }

    @Override
    public void renderInWorldCoords(ViewportAPI viewport) {
        if (done || proxy == null) return;

        float x = anchor.x, y = anchor.y, h = MARKER_HALF, t = MARKER_THICK;
        float r = 1f, g = 0.32f, b = 0.22f, a = 0.9f;
        SolidQuadBatch batch = new SolidQuadBatch(4);
        batch.appendRect(x - h, y - h, x + h, y - h + t, r, g, b, a); // bottom
        batch.appendRect(x - h, y + h - t, x + h, y + h, r, g, b, a); // top
        batch.appendRect(x - h, y - h, x - h + t, y + h, r, g, b, a); // left
        batch.appendRect(x + h - t, y - h, x + h, y + h, r, g, b, a); // right
        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            batch.flush();
        }
    }
}
