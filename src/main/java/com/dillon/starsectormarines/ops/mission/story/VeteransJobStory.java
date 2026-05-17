package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.Random;

/**
 * "The Veteran's Job" — one-shot intro story mission. Appears at any Independent
 * client once the player has at least one ranked-up captain (Corporal+), as a
 * recognition-of-status beat: word's getting around, and a discreet broker has a
 * job that pays in real money.
 *
 * <p>Demo def for the story-mission plumbing. No special on-victory hook — payout
 * is the reward, and the completion tracker keeps it from reappearing. Future defs
 * can layer side effects (free captain on victory, faction rep swing, follow-up
 * mission unlock) once we know what hooks the gameplay actually wants.
 */
public final class VeteransJobStory implements StoryMissionDef {

    public static final String ID = "story_veterans_job";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isEligible(StoryEligibilityContext ctx) {
        if (ctx.roster == null || ctx.roster.hasCompletedStory(ID)) return false;
        if (ctx.client == null || !"independent".equals(ctx.client.factionId)) return false;
        // Word travels — at least one captain has earned a stripe.
        for (MarineCaptain c : ctx.roster.all()) {
            if (c.status() == Status.ACTIVE && c.rank().ordinal() >= Rank.CORPORAL.ordinal()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Mission build(StoryEligibilityContext ctx) {
        Random r = new Random(ctx.seed ^ ID.hashCode());
        float x = 0.12f + r.nextFloat() * 0.76f;
        float y = 0.12f + r.nextFloat() * 0.76f;

        String flavor =
                "A broker on the back-channel reached out — your name's getting around, " +
                "and apparently that's worth something. The job is straightforward on paper: " +
                "hit a specific cache, get out clean, no questions asked about what's inside. " +
                "The pay is real money. The broker's name isn't.";

        // Payout sized like a MEDIUM mission with a story premium — meaningful without
        // being a balance break. Risk is MEDIUM regardless of the planet's defense rating;
        // story missions set their own difficulty.
        int payout = 25_000;

        return new Mission(
                ID, "The Veteran's Job",
                MissionType.RAID, MissionSource.STORY,
                payout, RiskLevel.MEDIUM,
                "50+ marines, discretion",
                flavor,
                x, y,
                FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                3, 0,
                ctx.planet != null ? ctx.planet.getName() : null,
                null);
    }
}
