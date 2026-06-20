package com.dillon.starsectormarines.combathybrid.host;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.combathybrid.bridge.GroundBattleConfig;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

/**
 * S3c (carrier engagement): point the carriers "above" at the ground band so they actually fight.
 *
 * <p>The playtest problem: vanilla ship AI advances onto a target only when an enemy fleet pushes
 * <em>toward</em> it. Here the ground proxies are stationary and never advance, so carriers — which
 * are skittish by design and expect the enemy to come to them — idle at their spawn row and rarely
 * commit. The fix is the vanilla-native steering lever, not an AI rewrite: drop a waypoint at the
 * ground-band centroid and give every deployed carrier an {@link CombatAssignmentType#ENGAGE}
 * assignment toward it. ENGAGE respects the "carriers stand off" intent — they advance to fighter
 * standoff from the waypoint and let their wings do the air-to-ground, rather than ramming the
 * defenses (which a blunt {@code setFullAssault} would risk).
 *
 * <p>Issued <b>once</b>, on the first frame a carrier is deployed (the scenario carriers are spawned
 * in {@code afterDefinitionLoad} and register as deployed members a frame later). The assignment
 * uses no command point ({@code useCommandPoint=false}), so the spectator side's zero-CP budget is
 * irrelevant. If a future playtest shows the side's admiral reassigning ships off the waypoint, the
 * fallback is to re-issue when {@code getAssignmentFor} goes null — kept out for now to read the
 * raw stickiness first.
 *
 * <p>Session-policy plugin (the heavier {@code setShipAI} takeover — for the S3d landing/descent
 * handoff — is a separate, later slice). Reachable only via the dev probe today.
 */
@DebugOnly
public final class CarrierEngagementPlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(CarrierEngagementPlugin.class);

    private final GroundBattleConfig config;
    private final FleetSide carrierSide;
    private boolean assigned;

    public CarrierEngagementPlugin(GroundBattleConfig config, FleetSide carrierSide) {
        this.config = config;
        this.carrierSide = carrierSide;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (assigned) return;
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        CombatFleetManagerAPI fm = engine.getFleetManager(carrierSide);
        List<DeployedFleetMemberAPI> carriers = fm.getDeployedCopyDFM();
        if (carriers.isEmpty()) return;   // wait until the spawned carriers register as deployed

        Vector2f band = groundBandCentroid();
        CombatTaskManagerAPI tm = fm.getTaskManager(false);
        AssignmentTargetAPI waypoint = fm.createWaypoint(band, false);
        AssignmentInfo engage = tm.createAssignment(CombatAssignmentType.ENGAGE, waypoint, false);
        for (DeployedFleetMemberAPI carrier : carriers) {
            tm.giveAssignment(carrier, engage, false);
        }
        assigned = true;
        LOG.info("ground-bridge: ENGAGE assignment issued to " + carriers.size()
                + " carrier(s) at ground band (" + (int) band.x + ", " + (int) band.y + ").");
    }

    /** Centroid of the live targetable sim entities, projected into combat world coords. */
    private Vector2f groundBandCentroid() {
        BattleSimulation sim = config.sim();
        Vector2f acc = new Vector2f();
        Vector2f tmp = new Vector2f();
        int n = 0;
        for (Entity e : config.targetable()) {
            if (!sim.world().isAlive(e.entityId)) continue;
            config.cellToWorld(sim.world().cellX(e.entityId), sim.world().cellY(e.entityId), tmp);
            acc.x += tmp.x;
            acc.y += tmp.y;
            n++;
        }
        if (n > 0) acc.scale(1f / n);
        return acc;   // empty/all-dead -> world origin (grid center), a sane fallback
    }
}
