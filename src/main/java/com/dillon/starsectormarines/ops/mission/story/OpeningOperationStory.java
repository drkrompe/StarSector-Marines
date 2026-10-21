package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.MarineArrivalPolicy;
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
            case RELIEF -> Strings.get("storyRecurringReliefFlavor");
            case COUNTERATTACK -> Strings.get("storyRecurringCounterattackFlavor");
        } : WaylineArc.briefing(kind, planet);
        // The story chapter's company rides up on Wayline's truck as its ore dust's escort and starts ashore;
        // only the employer's militia shuttle flies. The repeatable posting still needs company lift.
        boolean groundStart = !recurring && kind == OpeningOperationKind.RELIEF;
        int playerSorties = groundStart ? 0 : kind == OpeningOperationKind.RELIEF ? 1 : 2;
        Mission.Builder builder = Mission.builder()
                .id(id).name(recurring ? kind.recurringName() : kind.displayName())
                .type(MissionType.ASSAULT).source(recurring ? MissionSource.RECURRING : MissionSource.STORY)
                .payout(kind.payout).risk(RiskLevel.LOW)
                .requirements(groundStart ? Strings.get("storyOpeningRequirementsGround")
                        : Strings.format("storyOpeningRequirementsLift", playerSorties))
                .flavor(flavor).mapPosition(0.12f + random.nextFloat() * 0.76f, 0.12f + random.nextFloat() * 0.76f)
                .requiredDrops(playerSorties + 1).employerShuttles(1).targetPlanetName(planet);
        if (groundStart) builder.marineArrivalPolicy(MarineArrivalPolicy.SECURED_GROUND);
        return builder.build();
    }
}
