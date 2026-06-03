package com.dillon.starsectormarines.combathybrid;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * The event-translated sim⇄vanilla coupling: <b>one sim, many proxies</b>. The sim is
 * the source of truth, owned externally and merely <em>referenced</em> here; this bridge
 * spawns one invisible vanilla proxy per targetable sim {@link Entity} and drives them all
 * from a single {@code advance}. It never constructs the sim — the mission sim exists
 * independently and proxies are a thin mirror layer over it.
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
 * {@link GroundBattleConfig#damageScale()} maps vanilla ship-gun damage (hundreds/sec,
 * bursty fighter passes) onto the sim's infantry-scale turret HP (50–85) so a turret
 * attrits over several passes rather than dying on the first salvo. Placeholder for the
 * real cross-scale damage convention deferred to S3c.
 *
 * <p>Durable coupling core (see {@code roadmap/vanilla-combat-bridge/production-architecture.md}).
 * Reachable only via the dev probe today ({@code DevConfig.S0_COMBAT_PROBE}); production triggers
 * it through the mission flow once that seam lands.
 */
@DebugOnly
public class SimProxyMirror extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(SimProxyMirror.class);

    /** One mirrored sim unit ↔ vanilla proxy pair. */
    private static final class ProxyLink {
        final Entity unit;
        final ShipAPI proxy;
        final Vector2f anchor = new Vector2f();
        boolean simDead;   // set by the death subscriber when the sim reports this unit dead
        boolean removed;   // proxy already despawned

        ProxyLink(Entity unit, ShipAPI proxy) {
            this.unit = unit;
            this.proxy = proxy;
        }
    }

    private final BattleSimulation sim;
    private final List<Entity> targetable;
    private final int gridW;
    private final int gridH;
    private final float worldUnitsPerCell;
    private final String proxyVariant;
    private final float damageScale;

    private final List<ProxyLink> links = new ArrayList<>();
    private CombatEngineAPI engine;
    private boolean initialized;

    public SimProxyMirror(GroundBattleConfig cfg) {
        this.sim = cfg.sim();
        this.targetable = new ArrayList<>(cfg.targetable());
        this.gridW = cfg.gridW();
        this.gridH = cfg.gridH();
        this.worldUnitsPerCell = cfg.worldUnitsPerCell();
        this.proxyVariant = cfg.proxyVariant();
        this.damageScale = cfg.damageScale();
    }

    @Override
    public void init(CombatEngineAPI engine) {
        if (initialized) return;   // the engine inits plugins more than once — don't double-spawn
        initialized = true;
        this.engine = engine;

        if (sim == null || Global.getSettings().getVariant(proxyVariant) == null) {
            LOG.warn("ground-bridge: missing sim or proxy variant [" + proxyVariant + "]; no proxies spawned.");
            return;
        }

        // sim → vanilla: one subscription routes each death to its link by identity.
        sim.subscribeDeath(event -> {
            for (ProxyLink link : links) {
                if (link.unit == event.unit()) {
                    link.simDead = true;
                    break;
                }
            }
        });

        for (Entity u : targetable) {
            Vector2f loc = new Vector2f();
            cellToWorld(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId), loc);
            ShipAPI proxy = engine.getFleetManager(FleetSide.ENEMY)
                    .spawnShipOrWing(proxyVariant, loc, 270f);
            proxy.setExtraAlphaMult(0f);                  // invisible; the scene's UNITS layer draws it
            proxy.setCollisionClass(CollisionClass.SHIP); // hittable by weapons (explicit)
            proxy.setShipAI(null);                         // no brain — we pin it each frame
            proxy.getLocation().set(loc);
            ProxyLink link = new ProxyLink(u, proxy);
            link.anchor.set(loc);
            links.add(link);
        }
        LOG.info("ground-bridge: up. " + links.size() + " proxy/unit links over one sim; "
                + "dmg scale=" + damageScale + ", proxy maxHp="
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
            if (sim.world().isAlive(link.unit.entityId)) {
                cellToWorld(sim.world().cellX(link.unit.entityId), sim.world().cellY(link.unit.entityId), link.anchor);
            }
            proxy.getLocation().set(link.anchor);
            proxy.getVelocity().set(0f, 0f);
            proxy.setHoldFire(true);

            float max = proxy.getMaxHitpoints();
            float vanillaDamage = max - proxy.getHitpoints();
            if (vanillaDamage > 0f && sim.world().isAlive(link.unit.entityId)) {
                sim.applyExternalDamage(link.unit, vanillaDamage * damageScale);
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
                LOG.info("ground-bridge: despawned proxy for " + link.unit.id
                        + (link.simDead ? " (sim owned the death)." : " (vanilla destroyed it)."));
            }
        }
    }

    /** Center-grid projection: the center cell maps to combat world origin. */
    private void cellToWorld(int cellX, int cellY, Vector2f out) {
        out.set((cellX - gridW / 2f) * worldUnitsPerCell,
                (cellY - gridH / 2f) * worldUnitsPerCell);
    }
}
