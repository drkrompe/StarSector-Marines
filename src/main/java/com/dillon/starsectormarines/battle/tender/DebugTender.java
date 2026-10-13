package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.warship.DebugWarship;
import com.dillon.starsectormarines.battle.warship.Warship;

/**
 * The briefing's WARSHIP DEBUG tender: a drone tender put over a real mission so
 * its drones and its building can be judged against units on the ground.
 *
 * <p>Nothing here is production play. It reuses {@link DebugWarship}'s placement
 * (the same edge, facing and station inset) and its panel; the panel asks
 * {@link #of} whether the debug ship is a tender and, if so, its function keys
 * order a build, recall or deploy the defence drones, and cancel.
 */
@DebugOnly
public final class DebugTender {

    private DebugTender() {}

    /**
     * Fits and places a tender for {@code spec} and puts it over the battle. It
     * holds its station until it is ordered to build; its defence drones launch
     * at once.
     *
     * @return the tender's hull, which is also what the debug panel selects
     */
    public static Warship install(BattleSimulation sim, DebugWarship.Spec spec) throws Exception {
        Tender probe = Tender.fit(spec.side(), 0f, 0f, 0f);
        DebugWarship.Placement p = DebugWarship.place(spec.side(), DebugWarship.axisOf(sim),
                sim.getGrid().getWidth(), sim.getGrid().getHeight(), probe.ship().lengthCells);
        Tender tender = Tender.fit(spec.side(), p.x(), p.y(), p.facingDegrees());
        tender.ship().stationTo(p.stationX(), p.stationY());
        sim.tenders().add(sim, tender);
        return tender.ship();
    }

    /** The tender whose hull is {@code ship}, or null when the debug ship is an ordinary warship. */
    public static Tender of(BattleSimulation sim, Warship ship) {
        return sim == null || ship == null ? null : sim.tenders().of(ship);
    }

    /** Lays {@code option} at the cell; false when the tender refuses the site. */
    public static boolean buildAt(BattleSimulation sim, Tender tender, BuildOption option, float cellX, float cellY) {
        return tender.lay(option, cellX, cellY, sim.getGrid()) != null;
    }

    /** The option after {@code index} in {@link BuildOption#ALL}, wrapping at the end. */
    public static int nextOption(int index) {
        return (index + 1) % BuildOption.ALL.size();
    }

    /** Recalls the defence drones into the bay, or sends them back out. */
    public static void toggleDefence(Tender tender) {
        tender.defenceOut(!tender.defenceOut());
    }

    /** Hints for the panel's second line. */
    public static final String HINT =
            "F6 build at cursor   F7 cancel builds   F8 recall/deploy defence drones   F10 go to cursor   F11 add to route   F12 next build option";
}
