package com.dillon.starsectormarines.ops.playrules;

import java.util.List;

/**
 * What {@code playBattle}'s planet menu tells its stand-in commands: the market state its toggles set, and where a
 * command reports what the game's own command would have done. This package is listed first among the rule command
 * packages ({@code PlayRuleLines}), so a class here shadows the game command of the same name.
 */
public final class StandInMarket {

    /** The menu now open, or null. */
    public static volatile State current;

    /** Every stand-in command, named so the planet menu's compile pulls them in; they are looked up by name. */
    public static final List<Class<?>> COMMANDS = List.of(MarketCMD.class, Nex_MarketCMD.class,
            ShowDefaultVisual.class);

    private StandInMarket() {}

    public interface State {
        /** A station (a star fortress) holds the orbit. */
        boolean station();

        /** The defending fleets are strong enough to stop a raid. */
        boolean defenders();

        /** The market's faction is already hostile, so a raid needs no confirmation. */
        boolean hostile();

        /** The raid cooldown is running. */
        boolean cooldown();

        void cooldown(boolean running);

        /** Writes one line of what the game's command would commit. */
        void report(String line);
    }

    static State state() {
        State state = current;
        if (state == null) throw new IllegalStateException("no playBattle planet menu is open");
        return state;
    }
}
