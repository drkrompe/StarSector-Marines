package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Which compound a place fronts: a barracks (x 20..39, y 20..34), a parapet three cells off its south face, another
 * far out in the open, and a third beside a fort's centre.
 */
class CompoundGroundTest {

    private static final int W = 120, H = 60;

    private static GarrisonableRegistry registry(int... courses) {
        List<Doodad> props = new ArrayList<>();
        DoodadDef bag = new DoodadDef("doodad.test.bag", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f, 1, 1, null, 0, null,
                FightingPositionRole.PARAPET);
        for (int i = 0; i + 1 < courses.length; i += 2) {
            for (int x = courses[i]; x < courses[i] + 6; x++) props.add(new Doodad(x, courses[i + 1], bag));
        }
        return GarrisonableRegistry.build(FightingPositionIndex.build(W, H, props, (x, y) -> true), W, H, 8,
                new int[]{3, 100, 50});
    }

    private static ConquestCommandFacts.Compound barracks() {
        TacticalNode node = new TacticalNode(TacticalNode.Kind.BARRACKS, 30, 27, 20, 20, 39, 34, Faction.DEFENDER, 60, 0);
        return new ConquestCommandFacts.Compound(CompoundService.CompoundState.DEFENDER_HELD, node, 30, 27, 1, new int[0]);
    }

    @Test
    void anApronOffAFaceFrontsItsBuilding() {
        GarrisonableRegistry places = registry(27, 17);
        ConquestCommandFacts.Compound compound = barracks();
        assertSame(compound, CompoundGround.frontedBy(places, places.idAt(28, 17), List.of(compound)));
    }

    @Test
    void groundFarFromAnyBuildingFrontsNothing() {
        GarrisonableRegistry places = registry(60, 5);
        assertNull(CompoundGround.frontedBy(places, places.idAt(61, 5), List.of(barracks())));
    }

    @Test
    void aFortsBeltFrontsTheFortNotACompound() {
        // Inside the fort's reach the registry reads the run as that fort's belt, whatever building is near.
        GarrisonableRegistry places = registry(97, 45);
        TacticalNode node = new TacticalNode(TacticalNode.Kind.BARRACKS, 100, 50, 95, 46, 105, 55, Faction.DEFENDER, 60, 0);
        ConquestCommandFacts.Compound near = new ConquestCommandFacts.Compound(
                CompoundService.CompoundState.DEFENDER_HELD, node, 100, 50, 1, new int[0]);
        assertNull(CompoundGround.frontedBy(places, places.idAt(98, 45), List.of(near)));
    }
}
