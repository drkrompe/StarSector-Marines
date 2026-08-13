package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.evacuation.SwarmDefenseRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.detachment.Detachment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.TargetProfileResolver;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;

import java.util.List;
import com.dillon.starsectormarines.marine.MarineRosterScript;

/**
 * The single accept-path both pre-battle entry points ({@link BriefingScreen},
 * {@link CommsConsolePanel}) route through: resolve the committed
 * {@link Detachment}, build the type-specific {@link BattleSimulation}, and wire
 * the detachment's support (fighter cover + command powers) into it.
 *
 * <p>Collapses logic the two screens used to copy-paste. The screens keep only
 * their own transient toggle state (which transports / wings are committed) and
 * hand the resolved lists in; everything from "resolve" onward lives here so the
 * two paths can't drift.
 */
public final class MissionLaunch {

    private MissionLaunch() {}

    /**
     * Build the battle for {@code m} from the player's committed support and
     * store the resolved detachment on {@code ctx}. The caller is responsible for
     * {@code ctx.setBattleSimulation(...)} + the screen transition.
     *
     * @param committedShuttles the player's committed transports (priority-sorted)
     * @param committedWings    the player's committed marine-side fighter cover
     * @param debugWings        force-spawned debug wings (both sides), from the
     *                          {@code DEBUG_AIRCRAFT_PICKER} briefing panel;
     *                          {@link FlybyRoster#EMPTY} in normal play
     */
    public static BattleSimulation buildSimulation(MarineOpsContext ctx,
                                                   Mission m,
                                                   List<ShuttleType> committedShuttles,
                                                   FlybyRoster committedWings,
                                                   FlybyRoster debugWings) {
        Detachment det = m.source == MissionSource.STATIONING
                ? DetachmentResolver.resolveStationed(m)
                : DetachmentResolver.resolve(m, committedShuttles, committedWings);

        // Heavy-armaments availability on the target world drives whether the
        // defender side fields a HEAVY_MECH (see BattleSetup).
        boolean enemyHasHeavyArmor = DetachmentResolver.planetHasHeavyArmaments(m.targetPlanetName);

        // Campaign → battle bridge: the target world's planetary defenses /
        // market read, distilled once at the boundary so generation can reflect
        // which world the battle is over. NEUTRAL for story ops with no market.
        TargetProfile profile = TargetProfileResolver.resolve(m.targetPlanetName);

        long seed = System.currentTimeMillis();
        BattleSimulation sim;
        if (isCivilianRescueBattle(m)) {
            int firstWaveMarineSeats = firstWaveSeats(det.shuttleManifest);
            int swarmCount = m.source.isDebug()
                    ? SwarmDefenseRoster.debugCountFor(
                            m.risk, firstWaveMarineSeats)
                    : SwarmDefenseRoster.countFor(m.risk);
            sim = BattleSetup.createCivilianRescue(seed,
                    det.shuttleManifest, enemyHasHeavyArmor, m.risk, swarmCount, profile);
        } else switch (m.type) {
            case SABOTAGE:
                sim = BattleSetup.createSabotage(seed, det.shuttleManifest,
                        enemyHasHeavyArmor, m.risk, profile);
                break;
            case CONQUEST:
                sim = BattleSetup.createConquest(seed, det.shuttleManifest, enemyHasHeavyArmor, m.risk, profile);
                break;
            case ASSAULT:
            case RAID:
            case EXTRACTION:
            default:
                sim = BattleSetup.createPlaceholder(seed, det.shuttleManifest,
                        enemyHasHeavyArmor, m.risk, m.type, profile);
        }

        // Scenario factories author seat roles/objectives first; the persistent
        // roster then overlays each seat's identity, progression, armor and gear.
        int firstPlayerShuttle = m.source == MissionSource.STATIONING
                ? 0 : DetachmentResolver.employerPhysicalShipCount(m);
        int playerSeats = CampaignMarineDeployment.requiredSeats(
                det.shuttleManifest, firstPlayerShuttle);
        ctx.setMarineDeploymentCapacity(playerSeats);
        MarineRosterScript personnel = MarineRosterScript.getInstance();
        if (m.source.isDebug()) {
            CampaignMarineDeployment.debugFixture(
                    ctx.getDebugPersonnelPreset(), playerSeats)
                    .applyTo(sim, firstPlayerShuttle);
        } else if (personnel != null) {
            CampaignMarineDeployment.freezeSelection(personnel.roster(),
                    ctx.getSelectedMarineSquadIds(), playerSeats)
                    .applyTo(sim, firstPlayerShuttle);
        }

        // Marine-side fighter cover (committed bays + employer) combined with the
        // mission's enemy support, then any force-spawned debug wings (both sides
        // — each FighterWing carries its own side, so the overlay spawns it right);
        // then the active command-power roster.
        sim.setFlybyRoster(FlybyRoster.combine(
                FlybyRoster.combine(det.marineWings, m.enemyFighterSupport), debugWings));
        sim.setCommandPowers(det.powers);

        ctx.setDetachment(det);
        return sim;
    }

    static boolean isCivilianRescueBattle(Mission mission) {
        return mission != null && mission.source.isCivilianRescue();
    }

    private static int firstWaveSeats(List<ShuttleAssignment> manifest) {
        if (manifest == null) return 0;
        int seats = 0;
        for (ShuttleAssignment assignment : manifest) {
            if (assignment != null) seats += assignment.type.capacity;
        }
        return seats;
    }
}
