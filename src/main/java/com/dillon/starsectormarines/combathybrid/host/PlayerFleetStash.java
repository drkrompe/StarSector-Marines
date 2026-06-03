package com.dillon.starsectormarines.combathybrid.host;

import com.dillon.starsectormarines.DebugOnly;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Temporarily empties the <b>real</b> player fleet for a spectator probe battle, then
 * restores it when combat ends.
 *
 * <p>Why: {@code CampaignUIAPI.startBattle} sources the player's deployable ships from
 * the <em>actual</em> player fleet, ignoring the {@link com.fs.starfarer.api.combat.BattleCreationContext}'s
 * player fleet (confirmed by playtest — an empty context fleet still showed the
 * deployment picker). The only way to give a spectator battle nothing to deploy is to
 * remove the members from the real fleet for the duration of the fight.
 *
 * <p><b>Safety</b> (this mutates the player's save):
 * <ul>
 *   <li>Snapshots members + flagship before removal; restores both in order.</li>
 *   <li>A transient {@link RestoreScript} guarantees restore once combat is over —
 *       and <em>also</em> restores on a timeout if the battle never started, so the
 *       fleet can't be stranded empty.</li>
 *   <li>Idempotent: a second stash while one is pending is refused.</li>
 * </ul>
 * The one unguarded window is a hard crash <em>during</em> the probe battle (the
 * stashed members live only in this static, not the save). Acceptable for dev-gated
 * scaffolding; the members are never destroyed, only detached and re-attached.
 */
@DebugOnly
public final class PlayerFleetStash {

    private static final Logger LOG = Global.getLogger(PlayerFleetStash.class);

    /** Restore unconditionally if combat never started within this many seconds. */
    private static final float SAFETY_RESTORE_SECONDS = 8f;

    private static List<FleetMemberAPI> stashed;        // null when nothing is stashed
    private static FleetMemberAPI stashedFlagship;
    private static boolean combatEntered;

    private PlayerFleetStash() {}

    /** Detach all player-fleet members and schedule a guaranteed restore. */
    public static void stashAndScheduleRestore() {
        if (stashed != null) {
            LOG.warn("PlayerFleetStash: a stash is already pending; skipping re-stash.");
            return;
        }
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        if (pf == null) return;

        List<FleetMemberAPI> members = pf.getFleetData().getMembersListCopy();
        FleetMemberAPI flagship = null;
        for (FleetMemberAPI m : members) {
            if (m.isFlagship()) {
                flagship = m;
                break;
            }
        }

        stashed = members;
        stashedFlagship = flagship;
        combatEntered = false;
        for (FleetMemberAPI m : members) {
            pf.getFleetData().removeFleetMember(m);
        }
        LOG.info("PlayerFleetStash: detached " + members.size() + " player ships for the spectator battle.");

        Global.getSector().addTransientScript(new RestoreScript());
    }

    /** Called from the combat side once the probe battle is actually running. */
    public static void markCombatEntered() {
        combatEntered = true;
    }

    /**
     * Re-attach the stashed members. Idempotent (no-op if nothing is stashed).
     *
     * <p>Called from the combat side a beat into the spectator battle — NOT after
     * combat ends — so the post-battle campaign resolution reads a <em>healthy</em>
     * fleet. Ending combat with an empty player fleet makes the campaign conclude the
     * fleet was wiped ("defeated" / game over). By the time {@code advance} first
     * runs, the deploy-skip decision is already locked, so restoring here doesn't
     * bring the picker back; it only re-arms the fleet for resolution. The transient
     * {@link RestoreScript} remains as a safety net for paths that never enter combat.
     */
    public static void restore() {
        if (stashed == null) return;
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        if (pf != null) {
            for (FleetMemberAPI m : stashed) {
                pf.getFleetData().addFleetMember(m);
            }
            if (stashedFlagship != null) {
                pf.getFleetData().setFlagship(stashedFlagship);
            }
            LOG.info("PlayerFleetStash: restored " + stashed.size() + " player ships.");
        }
        stashed = null;
        stashedFlagship = null;
        combatEntered = false;
    }

    /**
     * Transient campaign script. Campaign scripts don't advance during combat, so the
     * first {@code advance} after the probe battle (with {@link #combatEntered} set)
     * is the restore point. The timeout branch covers a battle that never launched.
     */
    private static final class RestoreScript implements EveryFrameScript {

        private float elapsed;
        private boolean done;

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public boolean runWhilePaused() {
            return true;
        }

        @Override
        public void advance(float amount) {
            if (done) return;
            elapsed += amount;

            if (combatEntered) {
                restore();
                done = true;
                return;
            }
            if (elapsed > SAFETY_RESTORE_SECONDS) {
                LOG.warn("PlayerFleetStash: combat never entered after " + SAFETY_RESTORE_SECONDS
                        + "s; safety-restoring player fleet.");
                restore();
                done = true;
            }
        }
    }
}
