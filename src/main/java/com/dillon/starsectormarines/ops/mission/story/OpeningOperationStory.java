package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.WaylineArc;

import java.util.Random;

/** The two finite Wayline chapters. A defeat leaves its chapter available. */
public final class OpeningOperationStory implements StoryMissionDef {
    private final OpeningOperationKind kind;

    public OpeningOperationStory(OpeningOperationKind kind) {
        if (kind == null) throw new IllegalArgumentException("opening operation kind is required");
        this.kind = kind;
    }

    @Override public String id() { return kind.missionId; }

    @Override public boolean isEligible(StoryEligibilityContext ctx) {
        if (!eligibleBroker(ctx) || !ctx.openingBrokerPresent) return false;
        return kind == OpeningOperationKind.RELIEF
                ? WaylineArc.stage(ctx.roster) == WaylineArc.Stage.RELIEF
                : WaylineArc.stage(ctx.roster) == WaylineArc.Stage.DEPOT;
    }

    static boolean eligibleBroker(StoryEligibilityContext ctx) {
        return ctx != null && ctx.roster != null && ctx.client != null
                && "independent".equals(ctx.client.factionId)
                && ctx.client.patronHouseId == -1L && !ctx.client.locked;
    }

    @Override public Mission build(StoryEligibilityContext ctx) {
        return buildOperation(ctx, kind, false);
    }

    /** Shared battle/economy setup; recurring jobs retain their own identity and voice. */
    static Mission buildOperation(StoryEligibilityContext ctx, OpeningOperationKind kind, boolean recurring) {
        String id = recurring ? kind.recurringId : kind.missionId;
        Random random = new Random(ctx.seed ^ id.hashCode());
        String planet = ctx.planet != null ? ctx.planet.getName() : null;
        String flavor = recurring ? switch (kind) {
            case RELIEF -> "A freight checkpoint is under bandit attack. Land on the rear apron, reinforce the gate, and eliminate "
                    + "the assault force, including groups approaching along both access roads. The employer provides militia and one shuttle. "
                    + "The posting covers this relief sortie; other militia crews continue to need help.";
            case COUNTERATTACK -> "A local freight crew needs an occupied depot cleared. Join the militia "
                    + "advance from the western apron through the gate and loading yard into dispatch. "
                    + "Eliminate the depot guards; expect a reserve inside. The employer provides one shuttle. "
                    + "Independent freight crews pool their security funds for jobs like this.";
        } : WaylineArc.briefing(kind, planet);
        int playerSorties = kind == OpeningOperationKind.RELIEF ? 1 : 2;
        return Mission.builder()
                .id(id).name(recurring ? kind.recurringName : kind.displayName)
                .type(MissionType.ASSAULT).source(recurring ? MissionSource.RECURRING : MissionSource.STORY)
                .payout(kind.payout).risk(RiskLevel.LOW)
                .requirements("Player lift for " + playerSorties + (playerSorties == 1 ? " sortie" : " sorties"))
                .flavor(flavor).mapPosition(0.12f + random.nextFloat() * 0.76f, 0.12f + random.nextFloat() * 0.76f)
                .requiredDrops(playerSorties + 1).employerShuttles(1).targetPlanetName(planet).build();
    }
}
