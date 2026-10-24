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

    /** A hull that could escort: where it stands and the redoubt whose sector it holds. */
    record Hull(long id, float x, float y, int sector) { }

    /** Machines each manned redoubt keeps on its posts: one hull in open country and towns, a mech pair in a city. */
    static int coverFloor(CordonPlan.Density density) {
        return density == CordonPlan.Density.URBAN ? 2 : 1;
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
     * The hulls an escort at {@code (x, y)} may take, nearest first, up to {@code want}: only from sectors above
     * their floor, at most one from any one sector, never a calling redoubt's last machine. Fewer than {@code want}
     * means the escort goes short, and the radio says it is not Escorted.
     */
    static List<Hull> escorts(float x, float y, int want, List<Hull> free, List<Post> posts, int floor) {
        Map<Integer, Post> bySector = new HashMap<>();
        for (Post p : posts) bySector.put(p.number(), p);
        List<Hull> nearest = new ArrayList<>(free);
        nearest.sort(Comparator.comparingDouble(h -> Math.hypot(h.x() - x, h.y() - y)));
        List<Hull> out = new ArrayList<>();
        Map<Integer, Integer> taken = new HashMap<>();
        for (Hull h : nearest) {
            if (out.size() >= want) break;
            Post p = bySector.get(h.sector());
            if (p != null && p.manned()) {
                if (taken.containsKey(h.sector())) continue;
                if (p.machines() <= floor) continue;
                if (p.calling() && p.machines() <= 1) continue;
            }
            taken.put(h.sector(), 1);
            out.add(h);
        }
        return out;
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
     * Whether the line asks for an ammunition truck to a redoubt now: it is manned, a gun of its is dry (firing
     * rationed) or the rounds its guns and tubes hold together have fallen under {@code threshold} of full, and no
     * truck asked for it before is still on the road.
     */
    static boolean wantsAmmunition(boolean manned, float share, float threshold, int dryGuns, int onRoad) {
        return manned && (share < threshold || dryGuns > 0) && onRoad == 0;
    }

    /**
     * Whether a redoubt has help coming (the radio's Help coming): a carrier with a squad aboard bound for it, or an
     * allied column marching to it.
     */
    static boolean helpComing(boolean carrierBound, boolean columnBound) {
        return carrierBound || columnBound;
    }
}
