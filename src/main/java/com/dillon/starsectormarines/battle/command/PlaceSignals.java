package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * One party's pulse as its {@link PlaceDirectives} read it (`garrisonables.md`, place directives): the tick, the
 * stages and flags the mission discloses to that party, and the party's own belief — where its own squads stand, the
 * believed hostiles heavy enough to wake a place, and its hostile pressure field. Built from the party's own frame and
 * nothing else, so a trigger cannot read what the party does not know.
 */
public final class PlaceSignals {

    /** Believed hostile pressure on a block of the party's influence lattice. */
    @FunctionalInterface
    public interface Pressure {
        float at(int blockX, int blockY);
    }

    private final int tick;
    private final float[] ownXY;
    private final int ownCount;
    private final int[] contactXY;
    private final int contactCount;
    private final Pressure pressure;
    private final int blockSize;
    private final int blockWidth;
    private final int blockHeight;
    private final Map<String, Integer> stages = new TreeMap<>();
    private final Set<String> flags = new HashSet<>();

    private PlaceSignals(int tick, float[] ownXY, int ownCount, int[] contactXY, int contactCount, Pressure pressure,
                         int blockSize, int blockWidth, int blockHeight) {
        this.tick = tick;
        this.ownXY = ownXY;
        this.ownCount = ownCount;
        this.contactXY = contactXY;
        this.contactCount = contactCount;
        this.pressure = pressure;
        this.blockSize = Math.max(1, blockSize);
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
    }

    /** The party's own pulse: its alive squads' centroids, its believed contacts and its pressure field. */
    public static PlaceSignals of(CommandFrame frame) {
        float[] own = new float[2 * frame.squads().size()];
        int n = 0;
        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            own[2 * n] = squad.centroidX();
            own[2 * n + 1] = squad.centroidY();
            n++;
        }
        CommanderInfluenceSnapshot influence = frame.influence();
        if (influence == null) return new PlaceSignals(frame.tick(), own, n, new int[0], 0, null, 1, 0, 0);
        int[] contacts = new int[2 * influence.contacts().size()];
        int c = 0;
        for (CommanderContact contact : influence.contacts()) {
            if (contact.confidence() * contact.strength() < GarrisonDirector.MIN_CONTACT_WEIGHT) continue;
            contacts[2 * c] = contact.cellX();
            contacts[2 * c + 1] = contact.cellY();
            c++;
        }
        return new PlaceSignals(frame.tick(), own, n, contacts, c, influence::hostileAt, influence.blockSize(),
                influence.width(), influence.height());
    }

    /** A pulse with no belief at all; stages and flags only. */
    public static PlaceSignals at(int tick) {
        return new PlaceSignals(tick, new float[0], 0, new int[0], 0, null, 1, 0, 0);
    }

    /**
     * A pulse from packed own-squad centroids, packed believed contact cells and a pressure field on a lattice of
     * {@code blockWidth} by {@code blockHeight} blocks of {@code blockSize} cells; the seam unit tests use.
     */
    public static PlaceSignals of(int tick, float[] ownXY, int[] contactXY, Pressure pressure, int blockSize,
                                  int blockWidth, int blockHeight) {
        return new PlaceSignals(tick, Arrays.copyOf(ownXY, ownXY.length), ownXY.length / 2,
                Arrays.copyOf(contactXY, contactXY.length), contactXY.length / 2, pressure, blockSize, blockWidth,
                blockHeight);
    }

    /** Discloses a stage to this pulse. */
    public PlaceSignals stage(String name, int value) {
        stages.put(name, value);
        return this;
    }

    /** Raises a flag on this pulse when {@code raised}. */
    public PlaceSignals flag(String name, boolean raised) {
        if (raised) flags.add(name);
        return this;
    }

    /** Raises a flag on this pulse. */
    public PlaceSignals raise(String name) {
        return flag(name, true);
    }

    public int tick() { return tick; }

    /** The disclosed stage, or {@link Integer#MIN_VALUE}. */
    public int stage(String name) {
        Integer value = stages.get(name);
        return value == null ? Integer.MIN_VALUE : value;
    }

    public boolean flag(String name) { return flags.contains(name); }

    /** How the party believes the region stands: own squads' centroids in it, believed contacts in it. */
    public PlaceHold hold(PlaceTarget target) {
        boolean own = false;
        for (int i = 0; i < ownCount && !own; i++) own = target.contains(ownXY[2 * i], ownXY[2 * i + 1]);
        boolean hostile = false;
        for (int i = 0; i < contactCount && !hostile; i++) hostile = target.contains(contactXY[2 * i], contactXY[2 * i + 1]);
        return PlaceHold.of(own, hostile);
    }

    /** The most believed hostile pressure on any block the region covers; zero with no field. */
    public float pressure(PlaceTarget target) {
        if (pressure == null || blockWidth <= 0 || blockHeight <= 0) return 0f;
        int x0 = clamp(Math.floorDiv(target.x() - target.radius(), blockSize), blockWidth);
        int x1 = clamp(Math.floorDiv(target.x() + target.radius(), blockSize), blockWidth);
        int y0 = clamp(Math.floorDiv(target.y() - target.radius(), blockSize), blockHeight);
        int y1 = clamp(Math.floorDiv(target.y() + target.radius(), blockSize), blockHeight);
        float best = 0f;
        for (int by = y0; by <= y1; by++) for (int bx = x0; bx <= x1; bx++) best = Math.max(best, pressure.at(bx, by));
        return best;
    }

    private static int clamp(int v, int size) {
        return Math.max(0, Math.min(size - 1, v));
    }
}
