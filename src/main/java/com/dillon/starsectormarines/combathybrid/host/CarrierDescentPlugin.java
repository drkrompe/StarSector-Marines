package com.dillon.starsectormarines.combathybrid.host;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.combathybrid.bridge.GroundBattleConfig;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

/**
 * S3d descent foundation — the in-combat trigger that hands one carrier over to a {@link
 * CarrierDescentBrain}. Press <b>L</b> ("land") during a SIM_COUPLED bridge battle: this picks the
 * first live carrier-side ship and {@code setShipAI}s the descent brain onto it, which then flies it
 * to the live ground band (the targetable-structure centroid, the same point {@link
 * CarrierEngagementPlugin} steers the fleet toward).
 *
 * <p>The trigger is keyed off an in-combat input event ({@code processInputPreCoreControls}) rather
 * than the campaign hotkey listener, because the takeover only makes sense once the combat instance
 * is live. {@code L} is safe in the spectator canvas: there is no player ship (player-ship control
 * is disabled each frame) and {@link SpectatorCanvasPlugin} consumes only WASD / RMB / scroll, so
 * {@code L} passes through to here. One takeover per battle (a probe demonstrates the mechanism, not
 * a fleet-wide descent); re-pressing is a no-op while a ship is descending.
 *
 * <p>Session-policy plugin, installed by {@link CombatBridgeSession#enterEngine}. Reachable only via
 * the dev probe today.
 */
@DebugOnly
public final class CarrierDescentPlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(CarrierDescentPlugin.class);

    /** "L" for land — the in-combat key that triggers one carrier's descent. */
    private static final int TRIGGER_KEY = Keyboard.KEY_L;

    private final GroundBattleConfig config;
    private final FleetSide carrierSide;
    private CombatEngineAPI engine;
    private ShipAPI descending;   // the single carrier we've taken over (null until L is pressed)

    public CarrierDescentPlugin(GroundBattleConfig config, FleetSide carrierSide) {
        this.config = config;
        this.carrierSide = carrierSide;
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (e.isKeyDownEvent() && e.getEventValue() == TRIGGER_KEY) {
                e.consume();
                triggerDescent();
                break;
            }
        }
    }

    private void triggerDescent() {
        if (engine == null) return;
        if (descending != null && descending.isAlive()) {
            LOG.info("ground-bridge(descent): " + descending.getHullSpec().getHullId()
                    + " is already descending; ignoring trigger.");
            return;
        }

        ShipAPI carrier = pickCarrier(engine.getFleetManager(carrierSide));
        if (carrier == null) {
            LOG.warn("ground-bridge(descent): no live carrier-side ship to take over.");
            return;
        }

        Vector2f target = new Vector2f();
        config.targetableCentroid(target);
        carrier.setShipAI(new CarrierDescentBrain(carrier, target));
        descending = carrier;
        LOG.info("ground-bridge(descent): took over " + carrier.getHullSpec().getHullId()
                + " — descending to ground band at (" + (int) target.x + ", " + (int) target.y + ").");
    }

    /** First live, non-fighter ship on the carrier side. */
    private ShipAPI pickCarrier(CombatFleetManagerAPI fm) {
        for (DeployedFleetMemberAPI dfm : fm.getDeployedCopyDFM()) {
            if (dfm.isFighterWing()) continue;
            ShipAPI ship = dfm.getShip();
            if (ship != null && ship.isAlive()) return ship;
        }
        return null;
    }
}
