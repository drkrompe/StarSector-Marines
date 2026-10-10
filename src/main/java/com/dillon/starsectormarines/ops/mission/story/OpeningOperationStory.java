package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.OpeningBroker;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.Random;

/** Mara Venn's opening sequence, followed by recurring Independent militia work. */
public final class OpeningOperationStory implements StoryMissionDef {

    private final OpeningOperationKind kind;

    public OpeningOperationStory(OpeningOperationKind kind) {
        if (kind == null) throw new IllegalArgumentException("opening operation kind is required");
        this.kind = kind;
    }

    @Override
    public String id() {
        return kind.missionId;
    }

    @Override
    public boolean isEligible(StoryEligibilityContext ctx) {
        if (ctx == null || ctx.roster == null || ctx.client == null) return false;
        if (!"independent".equals(ctx.client.factionId) || ctx.client.patronHouseId != -1L || ctx.client.locked) return false;
        if (!ctx.openingBrokerPresent
                && !ctx.roster.hasCompletedStory(OpeningOperationKind.COUNTERATTACK.missionId)) return false;
        if (kind == OpeningOperationKind.COUNTERATTACK
                && !ctx.roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId)) {
            return false;
        }
        return true;
    }

    @Override
    public Mission build(StoryEligibilityContext ctx) {
        Random random = new Random(ctx.seed ^ id().hashCode());
        float x = 0.12f + random.nextFloat() * 0.76f;
        float y = 0.12f + random.nextFloat() * 0.76f;
        String planetName = ctx.planet != null ? ctx.planet.getName() : null;

        boolean repeat = ctx.roster != null && ctx.roster.hasCompletedStory(kind.missionId);
        String relief = ctx.openingBrokerPresent
                ? OpeningBroker.NAME + ": " + (repeat ? "Wayline needs another relief crew. " : "First, keep our people alive. ")
                    + "Wayline's militia post on " + planetName + " is under bandit attack. "
                    + "Land beside the line and break the assault. The cooperative supplies local troops and one shuttle. "
                    + (repeat ? "Same terms. Keep the line intact and get your people home."
                        : "Do this, and we'll talk about the depot they took from us.")
                : "A local militia post is under bandit attack. Land beside the line and break the assault. "
                    + "The employer supplies local troops and one shuttle.";
        String counterattack = ctx.openingBrokerPresent
                ? OpeningBroker.NAME + ": " + (repeat ? "Another depot needs clearing. " : "You kept Wayline's line intact. ")
                    + "Our militia traced the raiders to the cooperative's occupied freight depot on " + planetName
                    + ". Go in with the local troops and clear the bandits out. "
                    + "One employer shuttle is available. "
                    + (repeat ? "Same terms. Clear the depot and get your people home."
                        : "Finish this and I'll vouch for you with other Independent brokers.")
                : "Local militia have located an occupied freight depot. Join their advance and clear the bandits out. "
                    + "The employer supplies local troops and one shuttle.";
        return switch (kind) {
            case RELIEF -> Mission.builder()
                    .id(id())
                    .name(kind.displayName)
                    .type(MissionType.ASSAULT)
                    .source(MissionSource.STORY)
                    .payout(kind.payout)
                    .risk(RiskLevel.LOW)
                    .requirements("Player lift for 1 sortie")
                    .flavor(relief)
                    .mapPosition(x, y)
                    .requiredDrops(2)
                    .employerShuttles(1)
                    .targetPlanetName(planetName)
                    .build();
            case COUNTERATTACK -> Mission.builder()
                    .id(id())
                    .name(kind.displayName)
                    .type(MissionType.ASSAULT)
                    .source(MissionSource.STORY)
                    .payout(kind.payout)
                    .risk(RiskLevel.LOW)
                    .requirements("Player lift for 2 sorties")
                    .flavor(counterattack)
                    .mapPosition(x, y)
                    .requiredDrops(3)
                    .employerShuttles(1)
                    .targetPlanetName(planetName)
                    .build();
        };
    }

}
