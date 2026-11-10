package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.ruins.RuinsPressure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;

/**
 * The facility's wake (`derelict-watch.md`, Waking): at a derelict site the stir ({@link RuinsPressure}) is also the
 * plant coming up, a stage at a time, never back. Each stage brings up stock the site always had and had shut down;
 * nothing is built or grown.
 *
 * <ul>
 *   <li>{@link Stage#STIRRING}: the service ducts come up ({@link ServiceDucts#open()}).</li>
 *   <li>{@link Stage#ROUSED}: each online zone's sealed reserve bay puts up its flock, the deepest zones first.</li>
 *   <li>{@link Stage#HUNTING}: the security bulkheads open, and every online zone goes alert where it stands.</li>
 *   <li>{@link Stage#CONTINGENCY}: the vault is breached; everything left wakes ({@link SecurityNetwork#breachVault}).</li>
 * </ul>
 *
 * A reserve bay destroyed while shut never opens; one whose zone is dark or has lost its node stays shut until the
 * zone is online again at that stage or later. A bay opens once. Its flock is out to be seen: while its zone sleeps it
 * sweeps the zone, a corner of the perimeter at a time ({@link #sweepPoint}), and once the zone wakes it is tasked as
 * any flock of the zone.
 */
public final class FacilityWake {

    public enum Stage {
        QUIET, STIRRING, ROUSED, HUNTING, CONTINGENCY;

        /** Whether the service ducts are up. */
        public boolean ventsOpen() { return ordinal() >= STIRRING.ordinal(); }

        /** Whether the reserve bays open. */
        public boolean baysOpen() { return ordinal() >= ROUSED.ordinal(); }

        /** Whether the security bulkheads are open and every zone alert. */
        public boolean bulkheadsOpen() { return ordinal() >= HUNTING.ordinal(); }

        /** The comms line key a stage is announced with, or null for one that says nothing. */
        public String commsKey() {
            return switch (this) {
                case QUIET, CONTINGENCY -> null;
                case STIRRING -> "derelictWakeVents";
                case ROUSED -> "derelictWakeBays";
                case HUNTING -> "derelictWakeBulkheads";
            };
        }
    }

    /** How long a reserve flock's sweep holds each corner, ticks (10 s). UNTUNED. */
    public static final int SWEEP_TICKS = 600;
    /** How far toward the perimeter's corner a sweep reaches, as a share of the perimeter. */
    static final float SWEEP_REACH = 0.6f;

    private Stage stage = Stage.QUIET;
    private boolean sounded;
    private final Set<Integer> released = new TreeSet<>();

    /** The stage a site stands at: the stir's level, or the contingency once the vault is breached. */
    public static Stage of(RuinsPressure.Level level, boolean contingency) {
        if (contingency) return Stage.CONTINGENCY;
        return switch (level) {
            case QUIET -> Stage.QUIET;
            case STIRRING -> Stage.STIRRING;
            case ROUSED -> Stage.ROUSED;
            case HUNTING -> Stage.HUNTING;
        };
    }

    public Stage stage() {
        return stage;
    }

    /**
     * The site reads at {@code seen}: the stage rises to it, never falls.
     *
     * @return whether the stage rose
     */
    public boolean advance(Stage seen) {
        if (seen.ordinal() <= stage.ordinal()) return false;
        stage = seen;
        return true;
    }

    /**
     * The zones whose reserve bays open now, deepest first (zone 0 keeps the vault): the stage opens bays, the bay still
     * stands, its zone is online, and it has not opened before. Each is marked opened.
     *
     * @param bays      the zones that have a reserve bay
     * @param bayStands whether a zone's bay still stands
     */
    public List<Integer> release(SecurityNetwork network, List<Integer> bays, IntPredicate bayStands) {
        if (!stage.baysOpen()) return List.of();
        List<Integer> order = new ArrayList<>(bays);
        Collections.sort(order);
        List<Integer> out = new ArrayList<>();
        for (int zoneId : order) {
            if (released.contains(zoneId) || !bayStands.test(zoneId)) continue;
            SecurityZone zone = network.zone(zoneId);
            if (zone == null || !zone.online()) continue;
            released.add(zoneId);
            out.add(zoneId);
        }
        return out;
    }

    /**
     * Where a released reserve flock sweeps at {@code tick} while its zone sleeps: a corner of the zone's perimeter, drawn
     * in toward the node, going round counter-clockwise from the north-east a corner every {@link #SWEEP_TICKS}.
     */
    public static int[] sweepPoint(SecurityZone zone, int tick) {
        int corner = Math.floorMod(tick / SWEEP_TICKS, 4);
        int reach = Math.max(1, Math.round(zone.perimeterCells * SWEEP_REACH));
        int[][] corners = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
        return new int[] {zone.nodeCellX + corners[corner][0] * reach, zone.nodeCellY + corners[corner][1] * reach};
    }

    /** Whether a zone's reserve bay has opened. */
    public boolean released(int zoneId) {
        return released.contains(zoneId);
    }

    /**
     * The hunting stage's alarm, once: every online zone still dormant goes alert where it stands.
     *
     * @return whether it sounded now
     */
    public boolean sound(SecurityNetwork network) {
        if (sounded || !stage.bulkheadsOpen()) return false;
        sounded = true;
        network.alertAll();
        return true;
    }
}
