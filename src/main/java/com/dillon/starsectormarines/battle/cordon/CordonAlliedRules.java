package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.allied.AlliedTool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The allied line command's rules as pure decisions ({@code cordon-allied-command.md}, The allied line command's
 * rules): the cover each redoubt keeps, where the next machine goes, which hulls an escort may take, where a carrier
 * takes a waiting squad, what the allies say over the radio about a sector, and when the line asks its producer
 * ({@code allied-tools.md}) for a tool. {@link CordonArmour}, {@link CordonLogistics} and {@link CordonLineCommand}
 * freeze the line, call these and issue the orders.
 */
public final class CordonAlliedRules {

    private CordonAlliedRules() { }

    /** A redoubt as the allied command sees it. */
    record Post(int number, boolean manned, boolean calling, int standing, int machines, boolean hadMachines) { }
    /** What could escort a road hull: a hull, one of a sector's mechs, or a street mouth's rifle team. */
    enum Kind {
        HULL, MECH, TEAM;

        /**
         * What it counts toward its sector's floor: a machine one, a rifle team none. A team may go while the
         * sector's machines stand at their floor; it is never a sector's last cover, since the machines stay.
         */
        int cover() {
            return this == TEAM ? 0 : 1;
        }

        /** Whether it walks: it escorts a road hull at a walker's pace. */
        boolean walks() {
            return this != HULL;
        }
    }

    /** Something that could escort: where it stands, the redoubt whose sector it holds, and what it is. */
    record Escort(long id, float x, float y, int sector, Kind kind) {
        /** A hull. */
        Escort(long id, float x, float y, int sector) {
            this(id, x, y, sector, Kind.HULL);
        }
    }

    /** Machines each manned redoubt keeps on its posts: one hull in open country and towns, a mech pair in a city. */
    static int coverFloor(CordonPlan.Density density) {
        return density == CordonPlan.Density.URBAN ? 2 : 1;
    }

    /**
     * Whether the line's hulls keep to the streets (rule 5): in a town, where armour cannot leave them. Open country
     * is driven anywhere, and a city's sectors are held by mechs. Not on the Road: there the town's main street is the
     * convoys' road, and a hull parked on the streets beside it stood in their way and the dozer's (town Road held 0
     * of 12 with the streets kept, 2 of 12 without).
     */
    static boolean roadbound(CordonPlan.Density density, CordonVariant variant) {
        return density == CordonPlan.Density.TOWN && variant != CordonVariant.ROAD;
    }

    /**
     * Whether a road hull may take a walking escort (a mech of a sector's pair, a street mouth's rifle team) when no
     * hull is free: in a city, which fields no hulls and whose trucks otherwise went alone. Elsewhere hulls escort, as
     * before, and the company walks where none is free.
     */
    static boolean walkingEscorts(CordonPlan.Density density) {
        return density == CordonPlan.Density.URBAN;
    }

    /** A sector had machines and has none left: the radio's Uncovered. */
    static boolean uncovered(Post post) {
        return post.manned() && post.hadMachines() && post.machines() == 0;
    }

    /**
     * Where the next machine off the shed or gantry goes: the manned redoubt furthest under its floor, an uncovered
     * one first, a calling one breaking a tie, then the fewest machines. {@code -1} with no manned redoubt.
     */
    static int nextPost(List<Post> posts, int floor) {
        Post best = null;
        for (Post p : posts) {
            if (!p.manned()) continue;
            if (best == null || NEED.compare(p, best, floor) < 0) best = p;
        }
        return best == null ? -1 : best.number();
    }

    private interface Need {
        int compare(Post a, Post b, int floor);
    }

    private static final Need NEED = (a, b, floor) -> {
        int c = Integer.compare(Math.max(0, floor - b.machines()), Math.max(0, floor - a.machines()));
        if (c != 0) return c;
        c = Boolean.compare(uncovered(b), uncovered(a));
        if (c != 0) return c;
        c = Boolean.compare(b.calling(), a.calling());
        if (c != 0) return c;
        return Integer.compare(a.machines(), b.machines());
    };

    /**
     * What an escort at {@code (x, y)} may take, nearest first, up to {@code want}: hulls, mechs and street teams
     * alike, only out of the surplus. Taking one must leave its sector's machines at or above the floor (a machine
     * counts one, a rifle team none: {@link Kind#cover}), and a calling redoubt keeps at least one machine; at most
     * one from any one sector. Fewer than {@code want} means the escort goes short, and the radio says it is not
     * Escorted.
     */
    static List<Escort> escorts(float x, float y, int want, List<Escort> free, List<Post> posts, int floor) {
        Map<Integer, Post> bySector = new HashMap<>();
        for (Post p : posts) bySector.put(p.number(), p);
        List<Escort> nearest = new ArrayList<>(free);
        nearest.sort(Comparator.comparingDouble(h -> Math.hypot(h.x() - x, h.y() - y)));
        List<Escort> out = new ArrayList<>();
        Map<Integer, Integer> taken = new HashMap<>();
        for (Escort h : nearest) {
            if (out.size() >= want) break;
            Post p = bySector.get(h.sector());
            if (p != null && p.manned()) {
                int left = p.machines() - h.kind().cover();
                if (taken.containsKey(h.sector())) continue;
                if (left < floor) continue;
                if (p.calling() && left < 1) continue;
            }
            taken.put(h.sector(), 1);
            out.add(h);
        }
        return out;
    }

    /**
     * Whether a convoy is Escorted, the company's cue to leave it to the armour (rule 3): the armour gave it a hull for
     * every one of its posts, and one of them is alongside a truck. A convoy that went short is not, whatever armour
     * stands near it: a sector's own hull beside the road at the gap is not going with it, and one technical at the
     * compound's gate is not an escort of three. Reading any armed hull near a truck as its escort kept the company at
     * the hinge while a convoy loaded alone in the swarm's country (town Road, arid 7: the trucks eaten at the gate,
     * the company 116 cells back).
     *
     * @param wanted    the convoy's escort posts
     * @param taken     the hulls the armour sent to them
     * @param alongside whether an armed allied hull is within reach of one of its trucks
     */
    static boolean convoyEscorted(int wanted, int taken, boolean alongside) {
        return wanted > 0 && taken >= wanted && alongside;
    }

    /**
     * Where a carrier takes a waiting allied squad: the neediest manned redoubt (calling first, then fewest
     * standing). {@code -1} with none manned.
     */
    static int carrierDuty(List<Post> posts) {
        Post best = null;
        for (Post p : posts) {
            if (!p.manned()) continue;
            if (best == null || p.calling() && !best.calling()
                    || p.calling() == best.calling() && p.standing() < best.standing()) best = p;
        }
        return best == null ? -1 : best.number();
    }

    /** The tool an uncovered sector asks for: a Hound pair in a city, an armour pair of the density's hull elsewhere. */
    static AlliedTool coverTool(CordonPlan.Density density) {
        return density == CordonPlan.Density.URBAN ? AlliedTool.MECH_LANCE : AlliedTool.ARMOUR_PAIR;
    }

    /**
     * Whether the line asks its producer for cover for a sector now: it is {@link #uncovered}, and nothing asked for
     * it before is still on the road (at most one on the road per sector).
     */
    static boolean wantsCover(boolean uncovered, int onRoad) {
        return uncovered && onRoad == 0;
    }

    /**
     * Whether the line asks for an ammunition truck to a redoubt now: it is manned, a truck has a way to it (a supply
     * road, or a way through the streets, that a truck can drive), a gun of its is dry (firing rationed) or the rounds
     * its guns and tubes hold together have fallen under {@code threshold} of full, and no truck asked for it before
     * is still on the road. A truck sent where none can drive stood where its way ran out, delivering nothing.
     */
    static boolean wantsAmmunition(boolean manned, boolean reachable, float share, float threshold, int dryGuns,
                                   int onRoad) {
        return manned && reachable && (share < threshold || dryGuns > 0) && onRoad == 0;
    }

    /**
     * Whether a redoubt has help coming (the radio's Help coming): a carrier with a squad aboard bound for it, or an
     * allied column marching to it.
     */
    static boolean helpComing(boolean carrierBound, boolean columnBound) {
        return carrierBound || columnBound;
    }

    /**
     * Whether the line replaces a redoubt's lost teams from the base by need ({@code cordon-nouns.md}, Replacements):
     * on the Road, which runs on long after the timetable's last column. The Line keeps its timetable alone until the
     * owner says otherwise.
     */
    static boolean replacesLosses(CordonVariant variant) {
        return variant == CordonVariant.ROAD;
    }

    /** The allied defenses a replacement squad could be sent to ({@code cordon-nouns.md}, Replacements). */
    enum Defense {
        /** A redoubt's garrison. */
        REDOUBT,
        /** The safe camp's two guard teams. */
        CAMP,
        /**
         * A picket line team: it falls back to a redoubt once half its team is down, before it is ever a team's worth
         * short, and a stretch it left is given up.
         */
        PICKET_LINE,
        /** The outer picket, out in front of the line to be lost. */
        OUTER_PICKET,
        /** The town compound outside the wire (owner: "Not for the outside the wire settlement though."). */
        TOWN
    }

    /**
     * Whether a defense is re-garrisoned from the base's replacement pool: the posts behind the line that hold until
     * they fall, the redoubts and the camp. Never the town compound outside the wire, and never a picket, which falls
     * back rather than holding short.
     */
    static boolean regarrisoned(Defense defense) {
        return defense == Defense.REDOUBT || defense == Defense.CAMP;
    }

    /** The camp's number in the replacement rule: the redoubts number from one. */
    static final int CAMP = 0;

    /**
     * A post as the replacement rule sees it (a redoubt by its number, the camp as {@link #CAMP}): whether it is still
     * manned, whether it is calling, the men it was manned with at setup, the men standing in its garrison now, and
     * the squads already on their way to it.
     */
    record Garrison(int number, boolean manned, boolean calling, int establishment, int standing, int coming) { }

    /**
     * Teams a post is short: the whole teams' worth of men it has lost against its establishment, less the squads
     * already coming to it. A fallen or given-up post is short of nothing: it is a hole the next column cannot fill.
     */
    static int teamsShort(Garrison g, int teamSize) {
        if (!g.manned() || teamSize <= 0) return 0;
        int men = g.establishment() - g.standing() - g.coming() * teamSize;
        return Math.max(0, men / teamSize);
    }

    /**
     * The manned post most teams short, a calling one breaking a tie and then the earlier in {@code garrisons} (the
     * redoubts west to east, then the camp); {@code null} when none is short.
     */
    static Garrison mostShort(List<Garrison> garrisons, int teamSize) {
        Garrison best = null;
        int bestShort = 0;
        for (Garrison g : garrisons) {
            int s = teamsShort(g, teamSize);
            if (s <= 0) continue;
            if (best == null || s > bestShort || s == bestShort && g.calling() && !best.calling()) {
                best = g;
                bestShort = s;
            }
        }
        return best;
    }

    /**
     * The one call for replacements the line makes now, {@code {post, squads}}, or {@code null}: the post most teams
     * short ({@link #mostShort}), sent up to {@code perCall} squads and no more than the line still has room for in
     * flight. Nothing while the last call's cooldown runs or the line already has {@code maxInFlight} squads on the
     * way; the caps are the line's, shared by every post. The base's pool and the swarm on its apron are the
     * producer's to refuse.
     */
    static int[] replacement(List<Garrison> garrisons, int teamSize, int perCall, int inFlight, int maxInFlight,
                             boolean cooling) {
        if (cooling || inFlight >= maxInFlight) return null;
        Garrison best = mostShort(garrisons, teamSize);
        if (best == null) return null;
        return new int[]{best.number(), Math.min(teamsShort(best, teamSize), Math.min(perCall, maxInFlight - inFlight))};
    }
}
