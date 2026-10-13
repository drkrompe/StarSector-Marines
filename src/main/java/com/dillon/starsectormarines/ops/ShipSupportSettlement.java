package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.warship.ShipSupportResult;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * What a committed ship pays for having been called over a battle (slice 2 of
 * {@code ship-support.md}).
 *
 * <ul>
 *   <li><b>Hull.</b> The hull is carried out of the battle proportionally: the member's hull
 *       becomes its prior hull times the share the ship had left, never below {@link #HULL_FLOOR}
 *       and never above what it started with.</li>
 *   <li><b>Destroyed is crippled, not lost.</b> A ship the battle took to nothing comes back at
 *       {@link #HULL_FLOOR} of its hull (or less, if it was already lower) with combat readiness
 *       zero. Losing a capital to one ground battle is harsher than the fun it buys.</li>
 *   <li><b>Readiness.</b> A ship that was called pays the member's own CR-to-deploy
 *       ({@code FleetMemberAPI.getDeployCost}, the hull's {@code ship_data.csv} "CR to deploy",
 *       0.12 for an Onslaught, 0.20 for a Paragon or Shepherd), exactly what vanilla charges
 *       for deploying it, floored at zero.</li>
 *   <li><b>Uncalled pays nothing.</b> A committed ship that was never called has no
 *       {@link ShipSupportResult}, so nothing is written to it.</li>
 * </ul>
 *
 * <p>Debug and employer ships have no member id, so they produce no result and nothing is
 * written. The rules are pure ({@link #plan}) and the member is reached through
 * {@link Member}, so they are decided without a running game.
 */
public final class ShipSupportSettlement {

    private static final Logger LOG = Global.getLogger(ShipSupportSettlement.class);

    /** The least hull a called ship carries home, as a share; also what a destroyed ship returns at. */
    public static final float HULL_FLOOR = 0.10f;

    private ShipSupportSettlement() { }

    /** The campaign member's state, as far as settlement reads and writes it. */
    public interface Member {
        float hullFraction();
        float combatReadiness();
        /** Combat readiness, as a fraction, the member pays to be deployed. */
        float deployCost();
        void setHullFraction(float fraction);
        void setCombatReadiness(float fraction);
    }

    /** What the member's hull and readiness become. */
    public record Plan(float hullFraction, float combatReadiness) { }

    /** The pure rule: the member's new state, given its state before the battle. */
    public static Plan plan(ShipSupportResult result, float priorHull, float priorReadiness,
                            float deployCost) {
        float hull = Math.min(priorHull, Math.max(HULL_FLOOR, priorHull * result.hullFraction()));
        float readiness = result.destroyed()
                ? 0f : Math.max(0f, priorReadiness - Math.max(0f, deployCost));
        return new Plan(hull, readiness);
    }

    /** Writes one result to its member. */
    public static Plan apply(ShipSupportResult result, Member member) {
        Plan plan = plan(result, member.hullFraction(), member.combatReadiness(), member.deployCost());
        member.setHullFraction(plan.hullFraction());
        member.setCombatReadiness(plan.combatReadiness());
        return plan;
    }

    /** Writes every result to the player fleet's matching members; a member no longer in the fleet is skipped. */
    public static void settle(List<ShipSupportResult> results) {
        if (results == null || results.isEmpty()) return;
        CampaignFleetAPI fleet = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getFleetData() == null) return;
        for (ShipSupportResult result : results) {
            FleetMemberAPI member = null;
            for (FleetMemberAPI candidate : fleet.getFleetData().getMembersListCopy()) {
                if (result.memberId().equals(candidate.getId())) { member = candidate; break; }
            }
            if (member == null) {
                LOG.info("MarineOps: called ship " + result.shipName() + " is no longer in the fleet — no writeback");
                continue;
            }
            Plan plan = apply(result, adapt(member));
            LOG.info("MarineOps: " + result.shipName() + (result.destroyed() ? " crippled" : " returned")
                    + " — hull " + Math.round(plan.hullFraction() * 100f) + "%, CR "
                    + Math.round(plan.combatReadiness() * 100f) + "%");
        }
    }

    private static Member adapt(FleetMemberAPI member) {
        return new Member() {
            @Override public float hullFraction() { return member.getStatus().getHullFraction(); }
            @Override public float combatReadiness() { return member.getRepairTracker().getCR(); }
            @Override public float deployCost() { return member.getDeployCost(); }
            @Override public void setHullFraction(float fraction) { member.getStatus().setHullFraction(fraction); }
            @Override public void setCombatReadiness(float fraction) { member.getRepairTracker().setCR(fraction); }
        };
    }
}
