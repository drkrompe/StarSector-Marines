package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The company commander's rules as pure decisions ({@code cordon-allied-command.md}, The company commander's rules):
 * what wants squads, who goes, where the uncommitted wait, and when a lift is worth asking for. {@link CordonCommand}
 * freezes the picture and issues what these return.
 */
public final class CordonCompanyRules {

    private CordonCompanyRules() { }

    /** Squads a relief, an uncovered sector's posts and the base's apron each take. */
    static final int DETACHMENT = 2;
    /** Squads that clear the road ahead of a halted convoy. */
    static final int ROAD_SQUADS = 2;
    /** Squads that walk with a convoy no allied armour is with. */
    static final int ESCORT_SQUADS = 3;
    /** Cells of walk past which a duty is worth a lift. */
    static final int LIFT_WORTH = 100;
    /** How many times a squad's walking pace a carrier covers the ground, counted cautiously. */
    static final float RIDE_PACE = 3f;
    /** Cells across a town's hinge squads stand, and a hammer's. */
    static final int POST_SPACING = 8;
    /** Cells from a duty's cell inside which a squad is on it, and not still walking there. */
    static final int ON_DUTY = 12;
    /** Cells a squad walking to its duty must cover by each {@link #STALL_TICKS}, or it has stalled. */
    static final int PROGRESS = 10;
    /** Ticks a walking squad may go without covering {@link #PROGRESS} cells: thirty seconds. */
    static final int STALL_TICKS = Math.round(30f / BattleSimulation.TICK_DT);
    /** Ticks a squad that stalled on a duty is kept off that duty: a minute. */
    static final int BARRED_TICKS = Math.round(60f / BattleSimulation.TICK_DT);

    /**
     * What wants squads, in the order a short company serves them. {@link #floorBound} duties are the line's and
     * the rear's (rules 3 to 7): they wait rather than take the reserve's floor outside a tide; the road's do not.
     * {@link #infantryFirst} duties walk with someone or fetch them (a convoy's escort, a beat): the nearest free
     * infantry go before any machine, which keeps to the roads and cannot lead people out.
     */
    enum Kind {
        ROAD_BLOCK(false, false), CONVOY(false, true), GATE(false, true), TOWN(false, true), BREACHED(true, false),
        UNCOVERED(true, false),
        BLED(true, false), BEAT(true, true), BASE(true, false);

        final boolean floorBound;
        final boolean infantryFirst;

        Kind(boolean floorBound, boolean infantryFirst) {
            this.floorBound = floorBound;
            this.infantryFirst = infantryFirst;
        }
    }

    /** One duty: a key that stays the same while it stands, so a squad on it keeps it. */
    record Duty(String key, Kind kind, int x, int y, int squads) { }

    /**
     * A squad as the rules see it: where it stands, the duty it is on, by key, or {@code null}, whether it is a
     * machine, and the duty it stalled on and is kept off for now, or {@code null}.
     */
    record Squad(int id, float x, float y, String duty, boolean machine, String barred) {
        Squad(int id, float x, float y, String duty) {
            this(id, x, y, duty, false, null);
        }

        boolean barredFrom(Duty duty) {
            return duty.key().equals(barred);
        }
    }

    /**
     * The duty a squad counts as on, which it keeps while it stands: {@code now}, its own, while it {@code stands}; else {@code left}, the
     * one a more urgent call drew it from, while that stands; else none. A road's call drew the gate's squads to a
     * truck the swarm reached, the gate was filled from the line a hundred cells back, and when the call ended a
     * minute later the squads at the gate walked back to the reserve as the line's walked out to replace them.
     */
    static String dutyOf(String now, String left, Predicate<String> stands) {
        if (now != null && stands.test(now)) return now;
        return left != null && stands.test(left) ? left : null;
    }

    /**
     * Whether each squad walking to its duty is getting anywhere (rule 11): one that has not moved {@link #PROGRESS}
     * cells from where it stood {@link #STALL_TICKS} ago, while still more than {@link #ON_DUTY} cells off its duty, has
     * stalled. Its own going, not the way left: a convoy's escort cell runs ahead of the trucks, so a squad walking
     * after it never closes on it, and judged by that every escort was taken off as stalled and the next sent, none
     * ever reaching the trucks (town Road, arid 8). A squad fighting on its way is not excused: one held in a fight
     * ninety cells off the stragglers kept their call all battle (Line town arid 10). A squad on its duty, or riding to
     * it, is never stalled; a new duty starts its watch afresh.
     */
    static final class Progress {
        private record Watch(String key, float x, float y, int since) { }

        private final Map<Integer, Watch> watches = new HashMap<>();

        /**
         * One look at {@code squad}, standing at {@code x, y}, {@code away} cells from its duty {@code key}; true once
         * it has stalled.
         */
        boolean stalled(int squad, String key, float x, float y, double away, boolean riding, int tick) {
            Watch w = watches.get(squad);
            if (w == null || !w.key().equals(key) || riding || away <= ON_DUTY
                    || Math.hypot(x - w.x(), y - w.y()) >= PROGRESS) {
                watches.put(squad, new Watch(key, x, y, tick));
                return false;
            }
            if (tick - w.since() < STALL_TICKS) return false;
            watches.remove(squad);
            return true;
        }

        /** Forgets a squad that is off duty. */
        void forget(int squad) {
            watches.remove(squad);
        }
    }

    /**
     * The ordered duties: the road's (unchanged), then each redoubt's (breached, uncovered, bled without help), the
     * beats, and the base when it is overrun.
     *
     * @param beats each {x, y, squads}, and the beat's own number where the call carries one
     * @param base  the apron's cell while the base is overrun, else {@code null}
     */
    static List<Duty> duties(List<CordonRadio> radio, List<int[]> roadBlocks, List<int[]> convoys,
                             List<int[]> beats, int[] base) {
        return duties(radio, roadBlocks, convoys, List.of(), beats, base);
    }

    /**
     * As {@link #duties(List, List, List, List, int[])}, with the compound's gate held after the convoys' escorts
     * (rule 2): {@link #DETACHMENT} squads for each gate a convoy is coming to or loading at.
     *
     * @param gates each gate's cell
     */
    static List<Duty> duties(List<CordonRadio> radio, List<int[]> roadBlocks, List<int[]> convoys,
                             List<int[]> gates, List<int[]> beats, int[] base) {
        return duties(radio, roadBlocks, convoys, gates, null, beats, base);
    }

    /**
     * As {@link #duties(List, List, List, List, List, int[])}, with the Road's cut-off town after the gate (rule 2):
     * {@link #DETACHMENT} squads to the compound while it calls (the swarm inside its wall or not long gone, or half
     * its defenders down, since nobody else is coming for it). The town is the Road's score, so it is not held to the
     * reserve's floor.
     *
     * @param town the town's radio, or {@code null} where there is none
     */
    static List<Duty> duties(List<CordonRadio> radio, List<int[]> roadBlocks, List<int[]> convoys,
                             List<int[]> gates, CordonRadio town, List<int[]> beats, int[] base) {
        List<Duty> out = new ArrayList<>();
        for (int i = 0; i < roadBlocks.size(); i++) {
            int[] b = roadBlocks.get(i);
            out.add(new Duty("road-" + i, Kind.ROAD_BLOCK, b[0], b[1], ROAD_SQUADS));
        }
        for (int i = 0; i < convoys.size(); i++) {
            int[] c = convoys.get(i);
            out.add(new Duty("convoy-" + i, Kind.CONVOY, c[0], c[1], ESCORT_SQUADS));
        }
        for (int i = 0; i < gates.size(); i++) {
            int[] g = gates.get(i);
            out.add(new Duty("gate-" + i, Kind.GATE, g[0], g[1], DETACHMENT));
        }
        if (town != null && town.wantsRelief()) out.add(new Duty("town", Kind.TOWN, town.x(), town.y(), DETACHMENT));
        for (CordonRadio r : radio) {
            if (r.manned() && r.unsettled()) out.add(new Duty("relieve-" + r.number(), Kind.BREACHED, r.x(), r.y(), DETACHMENT));
        }
        for (CordonRadio r : radio) {
            if (r.manned() && r.uncovered()) out.add(new Duty("cover-" + r.number(), Kind.UNCOVERED, r.x(), r.y(), DETACHMENT));
        }
        for (CordonRadio r : radio) {
            if (r.manned() && !r.unsettled() && r.bled() && !r.helpComing()) {
                out.add(new Duty("relieve-" + r.number(), Kind.BLED, r.x(), r.y(), DETACHMENT));
            }
        }
        // By the beat, not its cell: a beat's call moves with its truck.
        for (int i = 0; i < beats.size(); i++) {
            int[] b = beats.get(i);
            out.add(new Duty("beat-" + (b.length > 3 ? b[3] : i), Kind.BEAT, b[0], b[1], b[2]));
        }
        if (base != null) out.add(new Duty("base", Kind.BASE, base[0], base[1], DETACHMENT));
        return out;
    }

    /** Squads the reserve keeps outside a tide: a third of the company. In a tide, none. */
    static int reserveFloor(int company, boolean tide) {
        return tide ? 0 : company / 3;
    }

    /**
     * Who goes where. In duty order, each duty keeps the squads already on it (up to its count, the nearest first, so
     * one that came back to it outranks the one sent in its place), then takes the nearest free ones, infantry before
     * machines for an {@link Kind#infantryFirst} duty. A squad never keeps or
     * takes the duty it is barred from. A floor-bound duty never takes the last {@code floor} free squads; the beats
     * together never take more than half the company.
     *
     * @return squad id to its duty; a squad not in the map is uncommitted
     */
    static Map<Integer, Duty> assign(List<Duty> duties, List<Squad> squads, int floor) {
        Map<Integer, Duty> out = new LinkedHashMap<>();
        Set<Integer> free = new HashSet<>();
        for (Squad s : squads) free.add(s.id());
        int beatCap = squads.size() / 2, beatsTaken = 0;
        for (Duty duty : duties) {
            int want = duty.squads();
            if (duty.kind() == Kind.BEAT) want = Math.min(want, beatCap - beatsTaken);
            int allowed = duty.kind().floorBound ? Math.max(0, free.size() - floor) : free.size();
            want = Math.min(want, allowed);
            if (want <= 0) continue;
            int taken = 0;
            Comparator<Squad> near = Comparator.comparingDouble(s -> Math.hypot(s.x() - duty.x(), s.y() - duty.y()));
            List<Squad> on = new ArrayList<>();
            for (Squad s : squads) {
                if (free.contains(s.id()) && duty.key().equals(s.duty()) && !s.barredFrom(duty)) on.add(s);
            }
            on.sort(near);
            for (int k = 0; taken < want && k < on.size(); k++, taken++) {
                out.put(on.get(k).id(), duty);
                free.remove(on.get(k).id());
            }
            List<Squad> nearest = new ArrayList<>();
            for (Squad s : squads) if (free.contains(s.id()) && !s.barredFrom(duty)) nearest.add(s);
            nearest.sort(duty.kind().infantryFirst ? Comparator.comparing(Squad::machine).thenComparing(near) : near);
            for (int k = 0; taken < want && k < nearest.size(); k++, taken++) {
                out.put(nearest.get(k).id(), duty);
                free.remove(nearest.get(k).id());
            }
            if (duty.kind() == Kind.BEAT) beatsTaken += taken;
        }
        return out;
    }

    /**
     * Where {@code count} uncommitted squads wait ({@code cordon-allied-command.md}, rule 9). Rural: in a row at the
     * reserve. Town: the hinge between the middle two redoubts, set back like the reserve, or up in the line's gap in
     * a tide. Urban: two hammers, each behind a pair of redoubts. {@code redoubts} are the redoubts' centres in order
     * along the line; the reserve cell is behind the middle of it.
     */
    static List<int[]> reservePosts(CordonPlan.Density density, List<int[]> redoubts, int reserveX, int reserveY,
                                    int count, boolean tide) {
        List<int[]> out = new ArrayList<>();
        if (count <= 0) return out;
        int n = redoubts.size();
        float lineY = 0;
        for (int[] r : redoubts) lineY += r[1];
        lineY = n == 0 ? reserveY : lineY / n;
        int back = Math.round(reserveY - lineY);
        if (density == CordonPlan.Density.URBAN && n >= 4) {
            int[] a = mid(redoubts.get(0), redoubts.get(1)), b = mid(redoubts.get(n - 2), redoubts.get(n - 1));
            int first = (count + 1) / 2;
            row(out, a[0], a[1] + back, first);
            row(out, b[0], b[1] + back, count - first);
            return out;
        }
        if (density == CordonPlan.Density.TOWN && n >= 2) {
            int[] hinge = mid(redoubts.get(n / 2 - 1), redoubts.get(n / 2));
            row(out, hinge[0], hinge[1] + (tide ? 0 : back), count);
            return out;
        }
        row(out, reserveX, reserveY, count);
        return out;
    }

    /** Where {@code count} squads stand at the Road's rally place once the town is finished: a row across it. */
    static List<int[]> rallyPosts(int x, int y, int count) {
        List<int[]> out = new ArrayList<>();
        row(out, x, y, count);
        return out;
    }

    private static int[] mid(int[] a, int[] b) {
        return new int[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2};
    }

    private static void row(List<int[]> out, int x, int y, int count) {
        for (int i = 0; i < count; i++) out.add(new int[]{x + (i - (count - 1) / 2) * POST_SPACING, y});
    }

    /**
     * Whether a squad should ask the base for a lift to a duty (rule 10): only outside a city, only for a duty more
     * than {@link #LIFT_WORTH} cells off, and only where walking to the carrier and riding from there to the duty at
     * {@link #RIDE_PACE} times its pace beats walking. A carrier merely nearer the squad than the duty sent squads at
     * the line fifty cells back to the base for a ride a hundred and fifty cells out, two minutes for what they
     * could have walked (town Road, arid 8).
     */
    static boolean liftWorth(CordonPlan.Density density, float squadX, float squadY, int dutyX, int dutyY,
                             float carrierX, float carrierY) {
        if (density == CordonPlan.Density.URBAN) return false;
        double walk = Math.hypot(dutyX - squadX, dutyY - squadY);
        double toCarrier = Math.hypot(carrierX - squadX, carrierY - squadY);
        double ride = Math.hypot(dutyX - carrierX, dutyY - carrierY);
        return walk > LIFT_WORTH && toCarrier + ride / RIDE_PACE < walk;
    }
}
