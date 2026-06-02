package com.dillon.starsectormarines.combathybrid;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.dillon.starsectormarines.render2d.SolidQuadBatch;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * S3a (fan-out) — the event-translated coupling generalized to <b>one sim, many
 * proxies</b>. The sim is the source of truth, owned externally and merely
 * <em>referenced</em> here; this bridge spawns one invisible vanilla proxy per
 * targetable sim {@link Unit} and drives them all from a single {@code advance}.
 * It never constructs the sim — that inverts S3a's throwaway-per-plugin scaffold
 * into the real shape ground combat needs (the mission sim exists independently;
 * proxies are a thin mirror layer over it).
 *
 * <p>Per the architecture's Decision 1 (event-translated, not state-mirrored):
 * <ul>
 *   <li><b>vanilla → sim:</b> each frame, every proxy's damage delta
 *       ({@code maxHp - hp}) is fed to {@link BattleSimulation#applyExternalDamage}
 *       (scaled), then the proxy HP is reset to full — the proxy is a damage
 *       <em>sensor</em>, never a health bar, so vanilla can't own a kill.</li>
 *   <li><b>sim → vanilla:</b> a single {@link BattleSimulation#subscribeDeath}
 *       subscription flips the matching link's death flag; the sim death event
 *       (not the adapter) despawns that proxy.</li>
 *   <li><b>position:</b> each proxy is pinned to its unit's cell, projected into
 *       combat world coords (grid centered on world origin).</li>
 * </ul>
 *
 * <p>The sim is ticked <b>once</b> per combat frame (after all proxies push their
 * damage), with the real frame {@code dt} fixed to 30Hz internally. {@code init}
 * is idempotent — the combat engine calls it more than once, and we must not
 * spawn duplicate proxies or double-subscribe.
 *
 * <h2>Scale</h2>
 * {@link #SIM_DAMAGE_SCALE} maps vanilla ship-gun damage (hundreds/sec, bursty
 * fighter passes) onto the sim's infantry-scale turret HP (50–85) so a turret
 * attrits over several passes rather than dying on the first salvo. Placeholder
 * for the real cross-scale damage convention deferred to S3c.
 *
 * <p>Throwaway dev scaffolding; gated by {@code DevConfig.S0_COMBAT_PROBE}.
 */
@DebugOnly
public class GroundSimBridge extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GroundSimBridge.class);

    /** Half-size of the per-proxy world-space crosshair marker (proxies are invisible). */
    private static final float MARKER_HALF = 55f;
    private static final float MARKER_THICK = 7f;

    /**
     * Vanilla-damage → sim-damage divisor. Lowered from the single-proxy slice's
     * 0.1 so a turret survives several fighter passes (a 1300-damage salvo scales
     * to ~26 against a 50-HP VULCAN). A placeholder for the real cross-scale
     * convention (architecture, S3c) — the knob, not the answer.
     */
    private static final float SIM_DAMAGE_SCALE = 0.02f;

    /** One mirrored sim unit ↔ vanilla proxy pair. */
    private static final class ProxyLink {
        final Unit unit;
        final ShipAPI proxy;
        final Vector2f anchor = new Vector2f();
        boolean simDead;   // set by the death subscriber when the sim reports this unit dead
        boolean removed;   // proxy already despawned

        ProxyLink(Unit unit, ShipAPI proxy) {
            this.unit = unit;
            this.proxy = proxy;
        }
    }

    private final BattleSimulation sim;
    private final List<Unit> targetable;
    private final int gridW;
    private final int gridH;
    private final float worldUnitsPerCell;
    private final String proxyVariant;

    private final List<ProxyLink> links = new ArrayList<>();
    private CombatEngineAPI engine;
    private boolean initialized;

    /**
     * @param sim               the externally-owned ground sim (source of truth)
     * @param targetable        the sim units to mirror as proxies (the targetable tier)
     * @param gridW             sim grid width in cells (for cell→world projection)
     * @param gridH             sim grid height in cells
     * @param worldUnitsPerCell combat world units per sim cell
     * @param proxyVariant      hull behind each invisible proxy (sprite hidden)
     */
    public GroundSimBridge(BattleSimulation sim, List<Unit> targetable,
                           int gridW, int gridH, float worldUnitsPerCell, String proxyVariant) {
        this.sim = sim;
        this.targetable = new ArrayList<>(targetable);
        this.gridW = gridW;
        this.gridH = gridH;
        this.worldUnitsPerCell = worldUnitsPerCell;
        this.proxyVariant = proxyVariant;
    }

    @Override
    public void init(CombatEngineAPI engine) {
        if (initialized) return;   // the engine inits plugins more than once — don't double-spawn
        initialized = true;
        this.engine = engine;

        if (sim == null || Global.getSettings().getVariant(proxyVariant) == null) {
            LOG.warn("S3a: missing sim or proxy variant [" + proxyVariant + "]; no proxies spawned.");
            return;
        }

        // sim → vanilla: one subscription routes each death to its link by identity.
        sim.subscribeDeath(event -> {
            for (ProxyLink link : links) {
                if (link.unit == event.unit()) {
                    link.simDead = true;
                    LOG.info("S3a: SIM death event for unit " + link.unit.id
                            + " (cell " + event.cellX() + "," + event.cellY() + ") — proxy will despawn.");
                    break;
                }
            }
        });

        for (Unit u : targetable) {
            Vector2f loc = new Vector2f();
            cellToWorld(u.getCellX(), u.getCellY(), loc);
            ShipAPI proxy = engine.getFleetManager(FleetSide.ENEMY)
                    .spawnShipOrWing(proxyVariant, loc, 270f);
            proxy.setExtraAlphaMult(0f);                  // invisible; the marker shows its position
            proxy.setCollisionClass(CollisionClass.SHIP); // hittable by weapons (explicit)
            proxy.setShipAI(null);                         // no brain — we pin it each frame
            proxy.getLocation().set(loc);
            ProxyLink link = new ProxyLink(u, proxy);
            link.anchor.set(loc);
            links.add(link);
        }
        LOG.info("S3a: ground-sim bridge up. " + links.size() + " proxy/unit links over one sim; "
                + "dmg scale=" + SIM_DAMAGE_SCALE + ", proxy maxHp="
                + (links.isEmpty() ? -1 : (int) links.get(0).proxy.getMaxHitpoints()) + ".");
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (!initialized || engine == null || sim == null) return;

        // vanilla → sim: push every proxy's damage into the sim FIRST, then tick once.
        for (ProxyLink link : links) {
            if (link.removed) continue;
            ShipAPI proxy = link.proxy;

            // Position is sim-owned. Read the live cell only while alive (a released
            // unit's cell accessors are fail-loud); else keep the last-known anchor.
            if (link.unit.isAlive()) {
                cellToWorld(link.unit.getCellX(), link.unit.getCellY(), link.anchor);
            }
            proxy.getLocation().set(link.anchor);
            proxy.getVelocity().set(0f, 0f);
            proxy.setHoldFire(true);

            float max = proxy.getMaxHitpoints();
            float vanillaDamage = max - proxy.getHitpoints();
            if (vanillaDamage > 0f && link.unit.isAlive()) {
                float simDamage = vanillaDamage * SIM_DAMAGE_SCALE;
                sim.applyExternalDamage(link.unit, simDamage);
                LOG.info("S3a: " + link.unit.id + " vanilla dmg " + (int) vanillaDamage
                        + " -> sim dmg " + String.format("%.1f", simDamage) + "  (hp now "
                        + String.format("%.1f", link.unit.isAlive() ? link.unit.getHp() : 0f) + ")");
            }
            // Damage sensor, not a health bar: reset so vanilla never owns the kill.
            proxy.setHitpoints(max);
        }

        // One sim tick for the whole frame — drains the death mailbox, which flips
        // link.simDead via the subscriber for any unit that died this frame.
        sim.advance(amount);

        // sim → vanilla: despawn proxies the sim reported dead this (or a prior) frame.
        for (ProxyLink link : links) {
            if (link.removed) continue;
            if (link.simDead || !link.proxy.isAlive()) {
                engine.removeEntity(link.proxy);
                link.removed = true;
                LOG.info("S3a: despawned proxy for " + link.unit.id
                        + (link.simDead ? " (sim owned the death)." : " (vanilla destroyed it)."));
            }
        }
    }

    /** Center-grid projection: the center cell maps to combat world origin. */
    private void cellToWorld(int cellX, int cellY, Vector2f out) {
        out.set((cellX - gridW / 2f) * worldUnitsPerCell,
                (cellY - gridH / 2f) * worldUnitsPerCell);
    }

    @Override
    public void renderInWorldCoords(ViewportAPI viewport) {
        if (!initialized) return;
        SolidQuadBatch batch = new SolidQuadBatch(links.size() * 4 + 4);
        float h = MARKER_HALF, t = MARKER_THICK;
        float r = 1f, g = 0.75f, b = 0.15f, a = 0.9f; // amber while the sim unit lives
        boolean any = false;
        for (ProxyLink link : links) {
            if (link.removed) continue;
            any = true;
            float x = link.anchor.x, y = link.anchor.y;
            batch.appendRect(x - h, y - h, x + h, y - h + t, r, g, b, a); // bottom
            batch.appendRect(x - h, y + h - t, x + h, y + h, r, g, b, a); // top
            batch.appendRect(x - h, y - h, x - h + t, y + h, r, g, b, a); // left
            batch.appendRect(x + h - t, y - h, x + h, y + h, r, g, b, a); // right
        }
        if (!any) return;
        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            batch.flush();
        }
    }
}
