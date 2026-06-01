package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.detachment.Detachment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.TargetProfileResolver;

import java.util.List;

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
     */
    public static BattleSimulation buildSimulation(MarineOpsContext ctx,
                                                   Mission m,
                                                   List<ShuttleType> committedShuttles,
                                                   FlybyRoster committedWings) {
        Detachment det = DetachmentResolver.resolve(m, committedShuttles, committedWings);

        // Heavy-armaments availability on the target world drives whether the
        // defender side fields a HEAVY_MECH (see BattleSetup).
        boolean enemyHasHeavyArmor = DetachmentResolver.planetHasHeavyArmaments(m.targetPlanetName);

        // Campaign → battle bridge: the target world's planetary defenses /
        // market read, distilled once at the boundary so generation can reflect
        // which world the battle is over. NEUTRAL for story ops with no market.
        TargetProfile profile = TargetProfileResolver.resolve(m.targetPlanetName);

        long seed = System.currentTimeMillis();
        BattleSimulation sim;
        switch (m.type) {
            case SABOTAGE:
                sim = BattleSetup.createSabotage(seed, det.shuttleManifest, enemyHasHeavyArmor, m.risk);
                break;
            case CONQUEST:
                sim = BattleSetup.createConquest(seed, det.shuttleManifest, enemyHasHeavyArmor, m.risk, profile);
                break;
            case ASSAULT:
            case RAID:
            case EXTRACTION:
            default:
                sim = BattleSetup.createPlaceholder(seed, det.shuttleManifest, enemyHasHeavyArmor, m.risk, m.type);
        }

        // Marine-side fighter cover (committed bays + employer) combined with the
        // mission's enemy support; then the active command-power roster.
        sim.setFlybyRoster(FlybyRoster.combine(det.marineWings, m.enemyFighterSupport));
        sim.setCommandPowers(det.powers);

        ctx.setDetachment(det);
        return sim;
    }
}
