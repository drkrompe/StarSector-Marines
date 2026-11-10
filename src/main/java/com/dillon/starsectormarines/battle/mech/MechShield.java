package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.turret.FluxShieldSpec;
import com.dillon.starsectormarines.battle.turret.ShieldTint;

/**
 * A mech's shield emitter ({@link MachineSystems#shieldEmitter}), the high-tech row's rule ({@code mech-lines.md}): a
 * frontal flux shield with no capacitors of its own, run on the mech's one flux pool ({@link MechFlux}) through the
 * hardpoint shield's rules ({@code FluxShield#pooled}). Every round it stops and every second it is held charges the
 * pool the guns fire from, so a mech that soaks fire has less left to shoot with, and one that fires hot vents with
 * its shield folded. Filling the pool overloads the mech, shield down and guns silent while the pool vents to empty.
 * The AI's guns already hold fire past the pool's warning mark, which leaves the rest to the shield.
 *
 * <p>Every figure is first-pass and UNTUNED, for the owner's playtest.
 */
public final class MechShield {

    private MechShield() { }

    /** The frontal emitter: a third of a circle, swinging to what threatens the mech. */
    public static final FluxShieldSpec FRONTAL = new FluxShieldSpec(
            1.6f,           // radius, cells: just clear of a heavy's shoulders
            120f,           // arc
            MechFlux.CAPACITY, // not read: a pooled shield's capacity is its pool's
            0f,             // not read: the pool sheds its own flux
            1.5f,           // upkeep a second, under the stock pool's dissipation of 6
            0.75f,          // flux per blocked point: the crawler's 150 pool blocks 200, its plate's trade (machine-budgets.md)
            0f,             // not read: the guns charge the pool themselves
            0.4f,           // raise
            0.3f,           // lower
            5f,             // overload: shield down, guns silent, while the pool vents to empty
            160f,           // turn, degrees a second
            2.5f);          // held this long after the last threat

    /** High-tech blue, as a high-tech emitter is drawn everywhere. */
    public static final ShieldTint TINT = ShieldTint.BLUE;
}
