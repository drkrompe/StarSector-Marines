package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.fixture.MarineSeatCommitment;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.OfficerCommandProfile;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Frozen credit and consequences for one officer and the named marines they led. */
public record OfficerMissionResult(OfficerCommandProfile officer, Set<String> soldierIds,
        int xpGained, Status status, float injuredUntilDay, Rank promotedTo) {
    public OfficerMissionResult { soldierIds = Set.copyOf(soldierIds); }

    public static List<OfficerMissionResult> compute(List<MarineSeatCommitment> seats,
            OfficerCommandProfile commander, Set<String> survivors, Set<String> fallen,
            boolean victory, int payout, String missionId, float day, int engaged, int lost) {
        Map<String, OfficerCommandProfile> officers = new LinkedHashMap<>();
        Map<String, Set<String>> members = new LinkedHashMap<>();
        if (commander != null) officers.put(commander.id(), commander);
        for (MarineSeatCommitment seat : seats) {
            String id = seat.campaignSoldierId();
            if (!survivors.contains(id) && !fallen.contains(id)) continue;
            var group = seat.commandGroupIdentity();
            if (group == null || group.profile() == null) continue;
            officers.putIfAbsent(group.officerId(), group.profile());
            members.computeIfAbsent(group.officerId(), key -> new LinkedHashSet<>()).add(id);
        }
        List<OfficerMissionResult> results = new ArrayList<>();
        for (OfficerCommandProfile officer : officers.values()) {
            Set<String> ids = members.getOrDefault(officer.id(), Set.of());
            int casualties = ids.isEmpty() ? lost : (int) ids.stream().filter(fallen::contains).count();
            int strength = ids.isEmpty() ? engaged : ids.size();
            // A cancelled landing neither teaches field experience nor uses a specialty.
            int xp = strength == 0 ? 0 : victory ? payout / (casualties <= 2 ? 100 : 150) : 50;
            if (officer.naturalLeader()) xp = (int) (xp * 1.5f);
            Rank rank = officer.rank();
            int progress = officer.xp() + xp;
            while (!rank.isTerminal() && progress >= rank.xpToNext()) {
                progress -= rank.xpToNext(); rank = rank.promote();
            }
            Status status = officer.status();
            float until = 0f;
            if (!victory) {
                boolean wipe = strength > 0 && casualties >= strength;
                long seed = ((long) officer.id().hashCode() << 32) ^ missionId.hashCode();
                status = wipe && new Random(seed).nextFloat() < 0.6f ? Status.KIA : Status.INJURED;
                if (status == Status.INJURED) until = day + (wipe ? 45f : 7f);
            }
            results.add(new OfficerMissionResult(officer, ids, xp, status, until,
                    rank != officer.rank() ? rank : null));
        }
        return List.copyOf(results);
    }

    /** Career receipt guards XP, promotions, status and history, including across save/reload. */
    public static void apply(MarineRoster roster, MissionOutcome outcome, int day) {
        if (roster == null || outcome.missionSource.isDebug()) return;
        for (OfficerMissionResult result : outcome.officers) {
            MarineCaptain captain = roster.byId(result.officer.id());
            if (captain == null || !captain.career().record(outcome.officerAttemptId,
                    outcome.victory, result.xpGained)) continue;
            captain.addXp(result.xpGained);
            while (!captain.rank().isTerminal() && captain.xp() >= captain.rank().xpToNext()) {
                captain.addXp(-captain.rank().xpToNext());
                captain.setRank(captain.rank().promote());
                captain.commendations().add("Day " + day + ": Promoted to " + captain.rank().displayName()
                        + ". Specialty training available.");
            }
            captain.setStatus(result.status);
            captain.setInjuredUntilDay(result.injuredUntilDay);
            captain.commendations().add("Day " + day + ": " + outcome.missionName + " / "
                    + (outcome.victory ? "victory" : "defeat") + " / +" + result.xpGained
                    + " XP / " + result.status + ".");
        }
    }

    public static int salvageBonus(List<OfficerMissionResult> officers, boolean victory, int engaged) {
        return victory && engaged > 0 ? officers.stream()
                .mapToInt(o -> o.officer().salvage() * 10).max().orElse(0) : 0;
    }

    public String summary() {
        return officer.name() + " / +" + xpGained + " XP / " + status
                + (promotedTo != null ? " / " + promotedTo.displayName() + " - training available" : "");
    }
}
