package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The story commanders' way into reactive manning (`garrisonables.md`, story commanders): a bespoke story commander
 * holds one of these and, after planning its authored duties, {@link #offerDuty offers} the squads whose duty is a
 * standing one — a wait at a post, a guard on a stage, holding home — bound to places near that post. The planner's
 * orders replace only those squads' proposals and are the commander's own, under its issuer and authority, so the
 * commander still reads a squad manning a window as its own; a squad whose duty moves is simply not offered, is
 * forgotten that pulse, and has its authored order back in the same plan. The commander's published picture is left as
 * the story wrote it, so the observers that turn it into beats read what they always read.
 *
 * <p><b>Not every fight is into prepared positions.</b> At most {@link #HOLDER_SHARE} of the commander's own infantry
 * holds a place at once, so the rest stay on their posts in the street and the yard. {@link #PROPERTY} switched off is
 * the control: nothing is offered and the battle is as it was.
 */
final class StoryGarrisons {

    /** {@code -Dbattle.story.reactiveDefence=false} switches the story commanders' reactive manning off. */
    static final String PROPERTY = "battle.story.reactiveDefence";
    /** {@code -Dbattle.story.reactiveDefence.share=<0..1>} overrides {@link #HOLDER_SHARE}. */
    static final String SHARE_PROPERTY = PROPERTY + ".share";
    /** Share of the commander's own infantry squads that may hold a place at once. UNTUNED. */
    static final float HOLDER_SHARE = 1f / 3f;

    static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true"));
    }

    private final MissionGarrisons planner = new MissionGarrisons();
    private final Map<Integer, MissionGarrisons.Offer> offers = new TreeMap<>();
    private int ownInfantry;
    /** Squad-pulses offered, and pulses that offered somebody while the share allowed nobody; for evidence. */
    private int offeredSquadPulses;
    private int shareClosedPulses;

    /** Offers this squad this pulse, bound to places within the duty radius of its post at {@code (x, y)}. */
    void offerDuty(CommandSquadState squad, int x, int y) {
        if (squad == null || squad.machine()) return;
        offers.put(squad.squadId(), MissionGarrisons.Offer.duty(x, y));
    }

    /** Counts one of the commander's own squads toward the share; call for every squad it owns this pulse. */
    void own(CommandSquadState squad) {
        if (squad != null && squad.aliveMembers() > 0 && !squad.machine() && !squad.crew()) ownInfantry++;
    }

    /** How many of {@code own} infantry squads may hold a place at once: the share, at least one from two squads up; none for a lone squad. */
    static int holderCap(int own) {
        if (own < 2) return 0;
        float share = HOLDER_SHARE;
        String override = System.getProperty(SHARE_PROPERTY);
        if (override != null && !override.isBlank()) share = Math.max(0f, Math.min(1f, Float.parseFloat(override)));
        return Math.max(1, (int) Math.floor(own * share));
    }

    /**
     * Plans this pulse's offers and returns the commander's proposals with the manning in place of its own for the
     * squads it names; clears the offers for the next pulse. {@code places} may be {@code null} (no registry disclosed).
     */
    List<CommandProposal> apply(CommandFrame frame, GarrisonableRegistry places, List<CommandProposal> proposals,
                                String issuer) {
        try {
            if (!enabled() || places == null) return proposals;
            int cap = holderCap(ownInfantry);
            offeredSquadPulses += offers.size();
            if (!offers.isEmpty() && cap == 0) shareClosedPulses++;
            Map<Integer, MissionGarrisons.Manning> manning = planner.plan(frame, places, offers, issuer, cap);
            return manning.isEmpty() ? proposals : MissionGarrisons.rewrite(proposals, manning, frame.squads());
        } finally {
            offers.clear();
            ownInfantry = 0;
        }
    }

    /** Gives and ends machine guards; call from the commander's afterCommit. */
    void afterCommit(CommandEffects effects, Faction side) {
        planner.afterCommit(effects, side);
    }

    /** The planner's figures ({@link ReactiveDefenceSource#figures}) and this story's offers. */
    Map<String, Integer> figures() {
        Map<String, Integer> out = ReactiveDefenceSource.figures(planner.stats());
        out.put("offeredSquadPulses", offeredSquadPulses);
        out.put("shareClosedPulses", shareClosedPulses);
        return out;
    }
}
