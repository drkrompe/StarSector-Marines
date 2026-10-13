package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.squad.Squad;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Which fort parts a squad does not shoot at in passing, and what lifts that. Every aspect core of a fort (the bunker,
 * magazine, power plant, control room and the shield emitter) is a place the fort's function is held in, and wrecking
 * one is permanent for both sides while capturing it is usually the better prize. A squad that merely sees a hostile
 * emitter used to fire on it like any contact and could end the dome for good before the commander had chosen between
 * taking the power, taking the control room and wrecking anything, so these cores are not opportunity targets.
 *
 * <p>What is restrained is only the fort's aspect cores. The battery platform (the guns), the wall nests and every unit
 * stay ordinary targets, so a fort still defends itself. The rule is symmetric: nothing here knows an attacker from a
 * defender, and it holds for whoever is hostile to the core.
 *
 * <p>The restraint is lifted for a squad, for a core, when the squad's executing order is within
 * {@link #LIFT_REACH_CELLS} of the core's cell and is either
 * <ul>
 *   <li>a fort task from the squad's commander that says wreck ({@link ObjectiveAssignment#wreckAuthorised()}), or</li>
 *   <li>an order the player gave it (a player pointing a squad at a building is asking for the building).</li>
 * </ul>
 * A core never shoots, so there is no "it is shooting us" case, and a core that is the only thing left to shoot at is
 * not an exception: the battle's own objectives decide when it ends, and a squad with nothing it may shoot simply
 * waits for the commander. Ship and aircraft fire is outside this: ships and aircraft choose their targets by their own
 * rules, as does splash from a grenade or round that lands near a core.
 *
 * <p>Off with {@code -Dbattle.fort.restraint=false} for a control run; the property is read when a battle is built.
 */
public final class FortRestraint {

    /** Turns the restraint off for a control run. On by default. */
    public static final String PROPERTY = "battle.fort.restraint";

    /**
     * How near, in cells, an order's destination must be to a core for the order to speak for it. A fort's approach
     * cell lies within four cells of the core's centre, so six covers it with some slack without reaching the next
     * building. UNTUNED first-pass figure.
     */
    public static final int LIFT_REACH_CELLS = 6;

    private final boolean enabled;
    private final LongOpenHashSet cores = new LongOpenHashSet();
    private final LongArrayList ordered = new LongArrayList();

    /** A restraint whose switch is read from {@link #PROPERTY}. */
    public FortRestraint() {
        this(Boolean.parseBoolean(System.getProperty(PROPERTY, "true")));
    }

    public FortRestraint(boolean enabled) {
        this.enabled = enabled;
    }

    /** Whether the restraint is in force at all. */
    public boolean enabled() {
        return enabled;
    }

    /** Records that {@code id} is the core of a fort aspect. Done as the fort is assembled, before play. */
    public void restrain(long id) {
        if (cores.add(id)) ordered.add(id);
    }

    /** Whether {@code id} is a fort core that no squad shoots at without being told to. */
    public boolean restrains(long id) {
        return enabled && !cores.isEmpty() && cores.contains(id);
    }

    /** Whether there is anything to restrain, so a caller can skip a loop. */
    public boolean any() {
        return enabled && !ordered.isEmpty();
    }

    /** The restrained cores in the order they were recorded. Valid to read during play: it only grows during setup. */
    public LongArrayList cores() {
        return ordered;
    }

    /** Whether {@code squad}'s executing order lifts the restraint on a core standing at the given cell. */
    public static boolean lifts(Squad squad, int coreCellX, int coreCellY) {
        if (squad == null) return false;
        return lifts(squad.assignmentForExecution(), squad.assignedAuthority, coreCellX, coreCellY);
    }

    /** As {@link #lifts(Squad, int, int)} for an order and the authority that gave it. */
    public static boolean lifts(ObjectiveAssignment order, CommandAuthority authority, int coreCellX, int coreCellY) {
        if (order == null || order.targetCellX() < 0 || order.targetCellY() < 0) return false;
        boolean permitted = order.wreckAuthorised() || authority == CommandAuthority.PLAYER_INTERVENTION;
        return permitted && Math.max(Math.abs(order.targetCellX() - coreCellX),
                Math.abs(order.targetCellY() - coreCellY)) <= LIFT_REACH_CELLS;
    }
}
