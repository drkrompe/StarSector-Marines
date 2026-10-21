package com.dillon.starsectormarines.battle.assault;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A compound building is a place to guard: the click's fallback onto a building's envelope
 * ({@link MachineGuards#envelopeUnderClick}), on a hand-built registry with one barracks and one trench.
 */
class MachineGuardEnvelopeTest {

    private static final int W = 100;
    private static final int H = 40;

    @Test
    void aClickInsideACompoundBuildingNamesItsEnvelopeAndNothingElseDoes() {
        List<Doodad> props = new ArrayList<>();
        DoodadDef def = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                1, 1, null, 0, null, FightingPositionRole.TRENCH);
        for (int x = 10; x <= 25; x++) props.add(new Doodad(x, 12, def));
        FightingPositionIndex index = FightingPositionIndex.build(W, H, props, (x, y) -> true);
        TacticalNode barracks = new TacticalNode(TacticalNode.Kind.BARRACKS, 47, 25, 40, 20, 55, 30,
                Faction.DEFENDER, 60, 0);
        GarrisonableRegistry places = GarrisonableRegistry.build(index, W, H, 8, new int[0],
                List.of(new GarrisonableRegistry.Envelope(barracks, new int[]{47, 21, 42, 21})));

        AssaultTarget target = MachineGuards.envelopeUnderClick(places, 50, 25);
        assertNotNull(target, "anywhere in the footprint is the building");
        assertEquals(AssaultTarget.Kind.HELD_PLACE, target.kind());
        assertSame(barracks, target.heldPlace());
        assertEquals(40, target.left);
        assertEquals(30, target.bottom);
        assertNull(MachineGuards.envelopeUnderClick(places, 50, 33), "outside the footprint");
        assertNull(MachineGuards.envelopeUnderClick(places, 20, 12), "a trench is a run, never an envelope");
        assertNull(MachineGuards.envelopeUnderClick(null, 50, 25), "no registry, no place");
    }
}
