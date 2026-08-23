package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.sim.SquadService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Deterministic battle-time view of a squad's organizational fire teams. */
public final class FireTeamGroups {

    public record Team(int index, List<Long> members) {}

    private FireTeamGroups() {}

    /**
     * Keeps campaign/spawn identity intact while dissolving a team once fewer
     * than two of its members survive. Orphans fold into the nearest viable
     * sibling; if every team is below strength, the survivors form one team.
     */
    public static List<Team> organize(List<Long> members, SquadService squads) {
        if (members == null || members.isEmpty()) return List.of();
        TreeMap<Integer, List<Long>> original = new TreeMap<>();
        for (long member : members) {
            int index = squads.hasSquad(member) ? squads.fireTeamIndex(member) : 0;
            original.computeIfAbsent(Math.max(0, index), ignored -> new ArrayList<>())
                    .add(member);
        }
        for (List<Long> team : original.values()) team.sort(Long::compare);

        TreeMap<Integer, List<Long>> effective = new TreeMap<>();
        for (Map.Entry<Integer, List<Long>> entry : original.entrySet()) {
            if (entry.getValue().size() >= 2) {
                effective.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        if (effective.isEmpty()) {
            List<Long> combined = new ArrayList<>(members);
            combined.sort(Long::compare);
            return List.of(new Team(original.firstKey(), List.copyOf(combined)));
        }
        for (Map.Entry<Integer, List<Long>> entry : original.entrySet()) {
            if (entry.getValue().size() >= 2) continue;
            int target = nearestTeam(entry.getKey(), effective);
            effective.get(target).addAll(entry.getValue());
        }

        List<Team> result = new ArrayList<>(effective.size());
        for (Map.Entry<Integer, List<Long>> entry : effective.entrySet()) {
            entry.getValue().sort(Long::compare);
            result.add(new Team(entry.getKey(), List.copyOf(entry.getValue())));
        }
        return List.copyOf(result);
    }

    /** Role map used by actions that must keep whole fire teams together. */
    public static Map<String, List<Long>> assignments(String prefix, List<Long> members,
                                                       SquadService squads) {
        Map<String, List<Long>> result = new LinkedHashMap<>();
        for (Team team : organize(members, squads)) {
            result.put(prefix + team.index(), team.members());
        }
        return result;
    }

    private static int nearestTeam(int source, TreeMap<Integer, List<Long>> candidates) {
        int best = candidates.firstKey();
        int bestDistance = Math.abs(best - source);
        for (int candidate : candidates.keySet()) {
            int distance = Math.abs(candidate - source);
            if (distance < bestDistance
                    || distance == bestDistance
                    && candidates.get(candidate).size() < candidates.get(best).size()) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }
}
