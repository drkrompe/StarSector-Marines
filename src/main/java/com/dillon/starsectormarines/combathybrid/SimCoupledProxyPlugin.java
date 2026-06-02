package com.dillon.starsectormarines.combathybrid;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
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
 * S3a probe — the first <b>real</b> two-engine coupling. S2 ({@link ProxyTargetPlugin})
 * proved vanilla carrier/fighter AI strafes a slaved invisible proxy with zero
 * targeting code from us; that proxy's HP was a standalone throwaway counter. S3a
 * replaces it with a live {@link BattleSimulation} {@code Unit} (one VULCAN turret)
 * and closes both loops of the event-translated coupling
 * (see {@code roadmap/vanilla-combat-bridge/architecture.md}, Decision 1):
 *
 * <ul>
 *   <li><b>vanilla → sim:</b> each frame, the damage vanilla dealt the proxy
 *       ({@code maxHp - hp}) is fed to {@link BattleSimulation#applyExternalDamage}.
 *       The proxy's vanilla HP is a <em>throwaway hittable surface</em> — reset to
 *       full every frame so vanilla never owns the kill; it's a damage sensor, not
 *       a health bar.</li>
 *   <li><b>sim → vanilla:</b> we {@link BattleSimulation#subscribeDeath subscribe}
 *       to the sim's death mailbox; when the sim says the turret died, that event
 *       (not the adapter) despawns the proxy.</li>
 *   <li><b>position:</b> the proxy is pinned each frame to the turret's cell
 *       projected into combat world coords.</li>
 * </ul>
 *
 * <p>No HP is mirrored: the sim owns the turret's health and its death. The proxy
 * is invisible (a crosshair marker shows where the sim unit is) until the cityscape
 * backdrop (S3b) draws the real scene under the ships.
 *
 * <h2>Scale</h2>
 * Vanilla ship-gun damage (hundreds/sec) dwarfs the sim's infantry-scale turret HP
 * (VULCAN = 50). {@link #SIM_DAMAGE_SCALE} bridges the two so a strafing run reads
 * as a few seconds of attrition rather than an instant kill. It is a placeholder for
 * the real cross-scale damage convention the architecture defers to S3c — the point
 * of S3a is the round-trip, not the balance.
 *
 * <h2>Tick coupling</h2>
 * The sim is ticked with the real combat frame {@code dt}; {@code BattleSimulation}
 * fixes that to its internal 30Hz step. A lethal hit publishes the death inline, but
 * it isn't fanned out until the next sim tick drains the mailbox — so the despawn can
 * trail the killing blow by up to one 30Hz step (~33ms). Acceptable for the probe;
 * noted here as the honest cost of not force-ticking the sim per frame.
 *
 * <p>Throwaway dev scaffolding; gated by {@code DevConfig.S0_COMBAT_PROBE}.
 */
@DebugOnly
public class SimCoupledProxyPlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(SimCoupledProxyPlugin.class);

    /** Half-size of the world-space crosshair marker (the proxy itself is invisible). */
    private static final float MARKER_HALF = 70f;
    private static final float MARKER_THICK = 8f;

    /**
     * Vanilla-damage → sim-damage divisor. See the class "Scale" note: a placeholder
     * until the real cross-scale convention lands (architecture, S3c). Tuned so a
     * VULCAN turret (50 HP) survives a few seconds of fighter strafing.
     */
    private static final float SIM_DAMAGE_SCALE = 0.1f;

    private final ShipAPI proxy;
    private final int gridW;
    private final int gridH;
    private final float worldUnitsPerCell;

    private BattleSimulation sim;
    private MapTurret turret;
    private CombatEngineAPI engine;

    /** Cached world position of the turret's cell — last read while it was alive. */
    private final Vector2f anchor = new Vector2f();
    /** Set by the death-mailbox subscriber when the sim reports the turret dead. */
    private boolean simTurretDead;
    private float lastLoggedSimHp = -1f;
    private boolean done;

    public SimCoupledProxyPlugin(ShipAPI proxy, int gridW, int gridH, float worldUnitsPerCell) {
        this.proxy = proxy;
        this.gridW = gridW;
        this.gridH = gridH;
        this.worldUnitsPerCell = worldUnitsPerCell;
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        if (proxy == null) {
            LOG.warn("S3a: null proxy ship; nothing to couple.");
            done = true;
            return;
        }

        // Minimal open arena: every cell walkable, one turret at the center.
        NavigationGrid grid = new NavigationGrid(gridW, gridH);
        CellTopology topology = new CellTopology(gridW, gridH);
        for (int y = 0; y < gridH; y++) {
            for (int x = 0; x < gridW; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        sim = new BattleSimulation(grid, topology);

        int cx = gridW / 2;
        int cy = gridH / 2;
        turret = new MapTurret("bridge_turret", Faction.DEFENDER, TurretKind.VULCAN, cx, cy);
        sim.addUnit(turret);
        // Keep the one-unit sim from auto-completing (which would stop ticks and
        // strand the death event) — see NeverEndObjective.
        sim.addObjective(new NeverEndObjective(Faction.DEFENDER));

        // sim → vanilla: the sim, not the adapter, decides when the proxy dies.
        sim.subscribeDeath(event -> {
            if (event.unit() == turret) {
                simTurretDead = true;
                LOG.info("S3a: SIM death event for the turret (cell " + event.cellX() + ","
                        + event.cellY() + ") — vanilla proxy will despawn this frame.");
            }
        });

        cellToWorld(cx, cy, anchor);
        proxy.setExtraAlphaMult(0f);                  // invisible; the marker shows its position
        proxy.setCollisionClass(CollisionClass.SHIP); // hittable by weapons (explicit)
        proxy.setShipAI(null);                         // no brain — we pin it each frame
        proxy.getLocation().set(anchor);
        lastLoggedSimHp = turret.getHp();
        LOG.info("S3a: sim-coupled proxy up. Sim turret VULCAN hp=" + (int) turret.getHp()
                + "/" + (int) turret.getMaxHp() + " at cell (" + cx + "," + cy + ") -> world ("
                + (int) anchor.x + "," + (int) anchor.y + "). Vanilla proxy maxHp="
                + (int) proxy.getMaxHitpoints() + ", dmg scale=" + SIM_DAMAGE_SCALE);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (done || engine == null || proxy == null || sim == null) return;

        if (!proxy.isAlive()) {
            // Shouldn't happen — we reset HP to full each frame so the sim owns the
            // kill — but if vanilla destroyed it anyway, stop cleanly.
            LOG.warn("S3a: vanilla destroyed the proxy before the sim did; despawning. "
                    + "(HP reset may have been outrun by a single huge hit.)");
            done = true;
            return;
        }

        // Position is sim-owned: pin the proxy to the turret's cell each frame.
        // Read the live cell only while alive (a released unit's cell accessors are
        // fail-loud); otherwise keep the last-known anchor for the death frame.
        if (turret.isAlive()) {
            cellToWorld(turret.getCellX(), turret.getCellY(), anchor);
        }
        proxy.getLocation().set(anchor);
        proxy.getVelocity().set(0f, 0f);
        proxy.setHoldFire(true);

        // vanilla → sim: whatever vanilla shaved off the proxy this frame becomes
        // scaled sim damage. applyExternalDamage no-ops on a dead/zero target, so a
        // stray hit after the turret dies (before despawn) is harmless.
        float max = proxy.getMaxHitpoints();
        float vanillaDamage = max - proxy.getHitpoints();
        if (vanillaDamage > 0f && turret.isAlive()) {
            float simDamage = vanillaDamage * SIM_DAMAGE_SCALE;
            sim.applyExternalDamage(turret, simDamage);
            LOG.info("S3a: vanilla dmg " + (int) vanillaDamage + " -> sim dmg "
                    + String.format("%.1f", simDamage) + "  (turret hp now "
                    + String.format("%.1f", turret.isAlive() ? turret.getHp() : 0f) + ")");
        }
        // The proxy is a damage sensor, not a health bar: reset to full so vanilla
        // never owns the kill.
        proxy.setHitpoints(max);

        // Tick the sim (drains the death mailbox -> our subscriber may flip simTurretDead).
        sim.advance(amount);

        if (turret.isAlive()) {
            float hp = turret.getHp();
            if (lastLoggedSimHp < 0f) lastLoggedSimHp = hp;
            if (hp < lastLoggedSimHp - 0.5f) {
                LOG.info("S3a: sim turret HP " + String.format("%.1f", hp) + "/"
                        + (int) turret.getMaxHp());
                lastLoggedSimHp = hp;
            }
        }

        if (simTurretDead) {
            LOG.info("S3a: round-trip complete — sim owned the death, despawning vanilla proxy.");
            engine.removeEntity(proxy);
            done = true;
        }
    }

    /** Center-grid projection: the center cell maps to combat world origin. */
    private void cellToWorld(int cellX, int cellY, Vector2f out) {
        out.set((cellX - gridW / 2f) * worldUnitsPerCell,
                (cellY - gridH / 2f) * worldUnitsPerCell);
    }

    @Override
    public void renderInWorldCoords(ViewportAPI viewport) {
        if (done || proxy == null) return;

        float x = anchor.x, y = anchor.y, h = MARKER_HALF, t = MARKER_THICK;
        // Amber while the sim turret lives; the marker just disappears on despawn.
        float r = 1f, g = 0.75f, b = 0.15f, a = 0.9f;
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
