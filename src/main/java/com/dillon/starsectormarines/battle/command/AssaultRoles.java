package com.dillon.starsectormarines.battle.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The roles an assault plan needs filled, what each asks of the squad that fills it, and the assignment of a company's
 * squads to them (`phased-assault.md`, "Capabilities and roles"). Pure over {@link CapabilityProfile}s: no squad,
 * weapon or unit is named, so any equipment that rates on a capability can fill a role that asks for it.
 *
 * <p>Only roles that need something particular are matched here. Taking and holding ground is left to whoever
 * walks: a squad given no role is the assault element if it can enter a building and a supporting gun if it cannot.
 *
 * <h2>The assignment</h2>
 * Needs are filled <em>scarcest first</em>: the need the company can least afford to fail (its total supply of the
 * capability against what the need asks) picks before the others. Within a need the squads that can fill the fewest
 * <em>other</em> needs go first, so a squad that is the only answer to one role is not spent on a role anyone else
 * could fill, and then the strongest contribution, then the lowest squad id so the answer is repeatable. A squad takes
 * one role. A need the company cannot meet is reported as a {@link Gap}, with the squads that went partway still
 * assigned, so the plan can ask for what is missing (a lance, a squad with charges) rather than guess.
 */
public final class AssaultRoles {

    private AssaultRoles() {}

    /** What the squad is for in the plan. */
    public enum Role {
        /** Silences or kills the nests covering the way in, from outside their reach where it can. */
        BASE_OF_FIRE,
        /** Opens the wall section of a breach plan, at range or at the foot of it. */
        BREACH,
        /** Takes the way in and the objective behind it. */
        ENTRY
    }

    /**
     * One thing the plan asks of whoever fills a role: enough combined rate, and enough reserve to last, summed over
     * the listed capabilities (any of them counts).
     */
    public record Need(Role role, EnumSet<AssaultCapability> any, float rate, float reserve) {
        public Need {
            any = EnumSet.copyOf(any);
        }

        /** What a profile contributes to this need. */
        float rateOf(CapabilityProfile profile) {
            float sum = 0f;
            for (AssaultCapability capability : any) sum += profile.rate(capability);
            return sum;
        }

        float reserveOf(CapabilityProfile profile) {
            float sum = 0f;
            for (AssaultCapability capability : any) {
                if (profile.has(capability)) sum += profile.reserve(capability);
            }
            return sum;
        }

        public String label() {
            StringBuilder names = new StringBuilder();
            for (AssaultCapability capability : any) {
                if (names.length() > 0) names.append('/');
                names.append(capability.name().toLowerCase(Locale.ROOT));
            }
            return role.name().toLowerCase(Locale.ROOT) + " needs " + names;
        }
    }

    /** A need the company could not meet, and how much of it is missing. */
    public record Gap(Need need, float rateMissing, float reserveMissing) {
        public String describe() {
            return need.label() + ": " + Math.round(rateMissing) + " of " + Math.round(need.rate()) + " short";
        }
    }

    /** The result of assigning a company to a plan's needs. */
    public record Staffing(Map<Integer, Role> roles, List<Gap> gaps) {
        public Staffing {
            roles = new TreeMap<>(roles);
            gaps = List.copyOf(gaps);
        }

        /** Whether every need was met. */
        public boolean staffed() {
            return gaps.isEmpty();
        }

        /** The squads assigned to a role, in id order. */
        public List<Integer> squads(Role role) {
            List<Integer> out = new ArrayList<>();
            for (Map.Entry<Integer, Role> entry : roles.entrySet()) if (entry.getValue() == role) out.add(entry.getKey());
            return out;
        }

        /** The gaps as short sentences, for the command trace. */
        public List<String> describeGaps() {
            List<String> out = new ArrayList<>();
            for (Gap gap : gaps) out.add(gap.describe());
            return out;
        }
    }

    /**
     * Assigns squads to needs.
     *
     * @param needs   what the plan asks for
     * @param squads  each candidate squad's profile by squad id
     */
    public static Staffing assign(List<Need> needs, Map<Integer, CapabilityProfile> squads) {
        List<Need> order = new ArrayList<>();
        for (Need need : needs) if (need.rate() > 0f) order.add(need);
        order.sort(Comparator.<Need>comparingDouble(need -> scarcity(need, squads))
                .thenComparingInt(need -> need.role().ordinal()));
        Map<Integer, Role> roles = new TreeMap<>();
        List<Gap> gaps = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < order.size(); i++) {
            Need need = order.get(i);
            List<Need> later = order.subList(i + 1, order.size());
            List<Integer> candidates = new ArrayList<>();
            for (Map.Entry<Integer, CapabilityProfile> entry : squads.entrySet()) {
                if (!used.contains(entry.getKey()) && need.rateOf(entry.getValue()) > 0f) candidates.add(entry.getKey());
            }
            candidates.sort(Comparator.<Integer>comparingInt(id -> versatility(squads.get(id), later))
                    .thenComparing(Comparator.<Integer>comparingDouble(id -> need.rateOf(squads.get(id))).reversed())
                    .thenComparingInt(id -> id));
            float rate = 0f;
            float reserve = 0f;
            for (int id : candidates) {
                if (rate >= need.rate() && reserve >= need.reserve()) break;
                roles.put(id, need.role());
                used.add(id);
                rate += need.rateOf(squads.get(id));
                reserve += need.reserveOf(squads.get(id));
            }
            if (rate < need.rate() || reserve < need.reserve()) {
                gaps.add(new Gap(need, Math.max(0f, need.rate() - rate), Math.max(0f, need.reserve() - reserve)));
            }
        }
        shareBreachAndFire(squads, roles, gaps);
        return new Staffing(roles, gaps);
    }

    /**
     * The one pair of roles a single squad may hold in turn: opening the wall and then being the base of fire. A gun
     * that outranges the nests (a cannon, a rack of rockets) does the first before the second, because the wall comes
     * down while the nests still stand and the squad is the base of fire from the moment it is through. So a gap in one
     * of the two that the squads holding the other could fill is closed by letting those squads hold both, the breach
     * first. A squad that can do only one of them is untouched.
     */
    private static void shareBreachAndFire(Map<Integer, CapabilityProfile> squads,
                                           Map<Integer, Role> roles, List<Gap> gaps) {
        for (Gap gap : new ArrayList<>(gaps)) {
            Role role = gap.need().role();
            if (role != Role.BREACH && role != Role.BASE_OF_FIRE) continue;
            Role other = role == Role.BREACH ? Role.BASE_OF_FIRE : Role.BREACH;
            float rate = 0f;
            float reserve = 0f;
            List<Integer> lenders = new ArrayList<>();
            for (Map.Entry<Integer, Role> entry : roles.entrySet()) {
                if (entry.getValue() != other) continue;
                CapabilityProfile profile = squads.get(entry.getKey());
                if (gap.need().rateOf(profile) <= 0f) continue;
                lenders.add(entry.getKey());
                rate += gap.need().rateOf(profile);
                reserve += gap.need().reserveOf(profile);
            }
            if (rate < gap.rateMissing() || reserve < gap.reserveMissing()) continue;
            for (int id : lenders) roles.put(id, Role.BREACH);
            gaps.remove(gap);
        }
    }

    /** Supply over demand: the smaller, the less the company can spare for the need. */
    private static double scarcity(Need need, Map<Integer, CapabilityProfile> squads) {
        double supply = 0.0;
        for (CapabilityProfile profile : squads.values()) supply += need.rateOf(profile);
        return supply / need.rate();
    }

    /** How many of the needs still to be filled a squad could fill: the more, the more it is worth keeping free. */
    private static int versatility(CapabilityProfile profile, List<Need> later) {
        int n = 0;
        for (Need need : later) if (need.rateOf(profile) > 0f) n++;
        return n;
    }
}
