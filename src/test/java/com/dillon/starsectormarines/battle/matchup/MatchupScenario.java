package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.scene.SceneWorld;

/**
 * A piece of ground, an objective and two orders: where a loadout matchup is fought. Each scenario stands the world
 * up fresh from the two kits and a seed, so a run depends on nothing but those three inputs.
 */
public interface MatchupScenario {

    /** Squad keys used by every scenario. */
    String ATTACKERS = "attackers";
    String DEFENDERS = "defenders";

    /** Stable kebab-case id, used on the command line and as the report row. */
    String id();

    /** One line saying what ground this is and what the attackers are ordered to do. */
    String label();

    /** Longest the fight is allowed to run, in ticks. A fight not decided by then is reported as unresolved. */
    int maxTicks();

    /** Stands up the world: attackers with {@code attackerKit} on the move, defenders with {@code defenderKit} holding. */
    SceneWorld build(MarineLoadout[] attackerKit, MarineLoadout[] defenderKit, long seed);
}
