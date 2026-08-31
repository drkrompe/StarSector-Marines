package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which sheds keep aircraft: the ones on the field that owns the strip.
 *
 * <p>A map has more sheds than airfields. Every airbase lot builds them,
 * including the civil landing sites scattered through a city, and an aircraft
 * based in one of those has nowhere to lift off and nothing within reach to
 * roll on — measured on conquest maps it taxied a hundred and thirty cells
 * across the city to the only runway there was. "The map has a strip" and
 * "this shed has one" are different questions and the first one used to be
 * asked.
 */
class ShedsOnTheFieldWithTest {

    /** The station: a strip along the front and its sheds along the back. */
    private static final TacticalNode STATION = new TacticalNode(
            TacticalNode.Kind.AIRBASE, 40, 20, 16, 8, 64, 32,
            Faction.DEFENDER, 65, 3);

    /** A landing site the other side of the city, with a shed and no strip. */
    private static final TacticalNode ELSEWHERE = new TacticalNode(
            TacticalNode.Kind.AIRBASE, 150, 100, 144, 94, 160, 108,
            Faction.DEFENDER, 65, 3);

    private static final Runway STRIP = new Runway(20f, 11f, 60f, 11f, 4f);

    private static Gantry shed(int x, int y) {
        return new Gantry(x, y, 2, 2, Gantry.Facing.SOUTH);
    }

    @Test
    void onlyTheShedsOfTheFieldTheStripBelongsTo() {
        Gantry near = shed(28, 28);
        Gantry alsoNear = shed(44, 28);
        Gantry farAway = shed(152, 101);

        assertEquals(List.of(near, alsoNear),
                BattleSetup.shedsOnTheFieldWith(STRIP,
                        new TacticalMap(List.of(STATION, ELSEWHERE)),
                        List.of(near, farAway, alsoNear)),
                "a shed on another airfield is not this field's, whatever the map has");
    }

    /**
     * A strip nobody's field contains bases nothing, rather than everything.
     *
     * <p>Both come out of the same lot, so this is a map that has not been
     * built yet. The safe reading of it is a field that flies off its apron —
     * the behaviour before anything was based in sheds at all — and not one
     * that adopts every shed on the map.
     */
    @Test
    void anUnclaimedStripBasesNothing() {
        assertTrue(BattleSetup.shedsOnTheFieldWith(STRIP,
                        new TacticalMap(List.of(ELSEWHERE)),
                        List.of(shed(28, 28))).isEmpty(),
                "no field owns this strip, so no shed on the map keeps an aircraft for it");
    }
}
