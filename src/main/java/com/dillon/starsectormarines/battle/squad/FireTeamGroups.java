package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.sim.SquadService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic battle-time view of a squad's organizational fire teams. */
public final class FireTeamGroups {

    public record Team(int index, List<Long> members) {}

    /**
     * Reusable result of {@link #organizeInto}: teams in ascending index order,
     * each a slice of one flat member array sorted by unit id. A caller owns its
     * instance and reads it before the next {@code organizeInto} on it.
     */
    public static final class Teams {
        private int count;
        private int[] index = new int[16];
        private int[] start = new int[16];
        private int[] length = new int[16];
        private long[] members = new long[16];

        // Working storage for organizeInto, kept so a call allocates nothing.
        private long[] sortedIds = new long[16];
        private int[] sortedTeams = new int[16];
        private int[] groupTeam = new int[16];
        private int[] groupStart = new int[16];
        private int[] groupLength = new int[16];
        private int[] groupTarget = new int[16];
        private int[] effectiveGroup = new int[16];
        private int[] effectiveSize = new int[16];

        public int count() { return count; }

        /** The fire-team index team {@code team} carries. */
        public int index(int team) { return index[team]; }

        public int length(int team) { return length[team]; }

        /** Member {@code k} of team {@code team}, in ascending id order. */
        public long member(int team, int k) { return members[start[team] + k]; }

        private void ensureWork(int needed) {
            if (sortedIds.length >= needed) return;
            int capacity = Math.max(needed, sortedIds.length * 2);
            sortedIds = new long[capacity];
            sortedTeams = new int[capacity];
            groupTeam = new int[capacity];
            groupStart = new int[capacity];
            groupLength = new int[capacity];
            groupTarget = new int[capacity];
            effectiveGroup = new int[capacity];
            effectiveSize = new int[capacity];
            index = new int[capacity];
            start = new int[capacity];
            length = new int[capacity];
            members = new long[capacity];
        }
    }

    private static final ThreadLocal<Teams> SCRATCH = ThreadLocal.withInitial(Teams::new);

    private FireTeamGroups() {}

    /**
     * Keeps campaign/spawn identity intact while dissolving a team once fewer
     * than two of its members survive. Orphans fold into the nearest viable
     * sibling; if every team is below strength, the survivors form one team.
     */
    public static List<Team> organize(List<Long> members, SquadService squads) {
        if (members == null || members.isEmpty()) return List.of();
        int size = members.size();
        long[] ids = new long[size];
        for (int i = 0; i < size; i++) ids[i] = members.get(i);
        Teams teams = SCRATCH.get();
        organizeInto(ids, 0, size, squads, teams);
        List<Team> result = new ArrayList<>(teams.count);
        for (int t = 0; t < teams.count; t++) {
            List<Long> teamMembers = new ArrayList<>(teams.length[t]);
            for (int k = 0; k < teams.length[t]; k++) teamMembers.add(teams.member(t, k));
            result.add(new Team(teams.index[t], List.copyOf(teamMembers)));
        }
        return List.copyOf(result);
    }

    /**
     * Allocation-free form of {@link #organize}: groups {@code length} members of
     * {@code source} starting at {@code from} into {@code out}. The grouping rules
     * are the same, so both forms answer identically.
     */
    public static void organizeInto(long[] source, int from, int length,
                                    SquadService squads, Teams out) {
        out.count = 0;
        if (length <= 0) return;
        out.ensureWork(length);
        long[] ids = out.sortedIds;
        int[] teams = out.sortedTeams;

        // Order by (team, id) with an insertion sort: a squad is a dozen units.
        for (int i = 0; i < length; i++) {
            long member = source[from + i];
            int team = Math.max(0, squads.hasSquad(member) ? squads.fireTeamIndex(member) : 0);
            int j = i - 1;
            while (j >= 0 && (teams[j] > team || teams[j] == team && ids[j] > member)) {
                ids[j + 1] = ids[j];
                teams[j + 1] = teams[j];
                j--;
            }
            ids[j + 1] = member;
            teams[j + 1] = team;
        }

        int groups = 0;
        int effective = 0;
        for (int i = 0; i < length; ) {
            int end = i + 1;
            while (end < length && teams[end] == teams[i]) end++;
            out.groupTeam[groups] = teams[i];
            out.groupStart[groups] = i;
            out.groupLength[groups] = end - i;
            out.groupTarget[groups] = -1;
            if (end - i >= 2) {
                out.effectiveGroup[effective] = groups;
                out.effectiveSize[effective] = end - i;
                effective++;
            }
            groups++;
            i = end;
        }

        if (effective == 0) {
            System.arraycopy(ids, 0, out.members, 0, length);
            sortById(out.members, 0, length);
            addTeam(out, out.groupTeam[0], 0, length);
            return;
        }

        for (int g = 0; g < groups; g++) {
            if (out.groupLength[g] >= 2) continue;
            int target = nearestTeam(out.groupTeam[g], out, effective);
            out.groupTarget[g] = target;
            out.effectiveSize[target] += out.groupLength[g];
        }

        int position = 0;
        for (int e = 0; e < effective; e++) {
            int teamStart = position;
            int own = out.effectiveGroup[e];
            System.arraycopy(ids, out.groupStart[own], out.members, position, out.groupLength[own]);
            position += out.groupLength[own];
            for (int g = 0; g < groups; g++) {
                if (out.groupTarget[g] != e) continue;
                System.arraycopy(ids, out.groupStart[g], out.members, position, out.groupLength[g]);
                position += out.groupLength[g];
            }
            sortById(out.members, teamStart, position);
            addTeam(out, out.groupTeam[own], teamStart, position - teamStart);
        }
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

    private static void addTeam(Teams out, int index, int start, int length) {
        out.index[out.count] = index;
        out.start[out.count] = start;
        out.length[out.count] = length;
        out.count++;
    }

    private static void sortById(long[] values, int from, int to) {
        if (to - from > 32) {
            Arrays.sort(values, from, to);
            return;
        }
        for (int i = from + 1; i < to; i++) {
            long value = values[i];
            int j = i - 1;
            while (j >= from && values[j] > value) {
                values[j + 1] = values[j];
                j--;
            }
            values[j + 1] = value;
        }
    }

    /** The effective team nearest {@code source}; ties go to the smaller team so far. */
    private static int nearestTeam(int source, Teams work, int effective) {
        int best = 0;
        int bestDistance = Math.abs(work.groupTeam[work.effectiveGroup[0]] - source);
        for (int e = 1; e < effective; e++) {
            int distance = Math.abs(work.groupTeam[work.effectiveGroup[e]] - source);
            if (distance < bestDistance
                    || distance == bestDistance
                    && work.effectiveSize[e] < work.effectiveSize[best]) {
                best = e;
                bestDistance = distance;
            }
        }
        return best;
    }
}
