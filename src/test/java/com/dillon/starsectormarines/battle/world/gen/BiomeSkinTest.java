package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.BiomeSkin.Adaptation;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Box;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Cell;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.DropRole;
import com.dillon.starsectormarines.battle.world.model.NaturalRise;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiomeSkinTest {

    private static final Climate TOXIC_AIR = new Climate(EnumSet.of(Climate.Hazard.TOXIC));

    private static BiomeSkin skin(SurfacePalette palette) {
        return new BiomeSkin(palette, null, null, null);
    }

    private static OwnedMap.Settlement town(String terraceInto) {
        return new OwnedMap.Settlement("town", "market-town", OwnedMap.SettlementRole.TOWN, new Box(4, 4, 20, 20),
                List.of(), List.of(), List.of(), terraceInto);
    }

    private static OwnedMap map(OwnedMap.Settlement settlement) {
        OwnedMap.Drop gorge = new OwnedMap.Drop("gorge", DropRole.RIFT, List.of(new Cell(0, 24), new Cell(31, 24)), 4,
                List.of(), 0, List.of());
        return new OwnedMap("test", 32, 32, null, null, null, new Cell(1, 1), new Cell(30, 30), List.of(), List.of(),
                List.of(), List.of(settlement), List.of(), List.of(), List.of(gorge), List.of(), List.of());
    }

    @Test
    void aDropsRolePicksTheStyleOnlyOnAVolcanicWorld() {
        assertEquals(NaturalVoidZoning.LAVA_RIVER, skin(SurfacePalette.VOLCANIC).drop(DropRole.RIVER));
        assertEquals(NaturalVoidZoning.LAVA_RIFT, skin(SurfacePalette.VOLCANIC).drop(DropRole.RIFT));
        for (SurfacePalette p : SurfacePalette.values()) {
            if (p == SurfacePalette.VOLCANIC) continue;
            assertEquals(NaturalVoidZoning.forPalette(p), skin(p).drop(DropRole.RIVER), p.name());
            assertEquals(NaturalVoidZoning.forPalette(p), skin(p).drop(DropRole.RIFT), p.name());
        }
    }

    @Test
    void aShareOfTheBandIsMetresWithinItAndAFixedDropKeepsItsFloor() {
        BiomeSkin rock = skin(SurfacePalette.ROCK);
        NaturalVoidZoning.Drop dry = NaturalVoidZoning.DRY_GORGE;
        assertEquals(dry.shallowest(), rock.floorMeters(DropRole.RIFT, 0f), 1e-4);
        assertEquals(dry.deepest(), rock.floorMeters(DropRole.RIFT, 1f), 1e-4);
        assertEquals((dry.shallowest() + dry.deepest()) / 2f, rock.floorMeters(DropRole.RIFT, .5f), 1e-4);
        BiomeSkin verdant = skin(SurfacePalette.VERDANT);
        assertEquals(verdant.floorMeters(DropRole.RIFT, 0f), verdant.floorMeters(DropRole.RIFT, 1f), 1e-4);
    }

    @Test
    void theAirDecidesASealedPerimeterBeforeAnythingElse() {
        // toxic_cold reads as frozen ground, and its air still seals the town.
        BiomeSkin toxicCold = BiomeSkin.forPlanetType("toxic_cold", TOXIC_AIR);
        assertEquals(SurfacePalette.FROZEN, toxicCold.palette());
        assertEquals(Adaptation.SEALED_PERIMETER, toxicCold.adaptation(map(town("gorge")), town("gorge")));
        BiomeSkin airless = new BiomeSkin(SurfacePalette.ROCK, null, null, new Climate(EnumSet.of(Climate.Hazard.AIRLESS)));
        assertEquals(Adaptation.SEALED_PERIMETER, airless.adaptation(map(town(null)), town(null)));
        assertEquals(Habitat.SEALED, airless.habitat());
    }

    @Test
    void anIrradiatedOrToxicWorldWithNoClimateStillSealsItsTowns() {
        assertEquals(Habitat.SHIELDED, skin(SurfacePalette.IRRADIATED).habitat());
        assertEquals(Habitat.SEALED, skin(SurfacePalette.TOXIC).habitat());
        assertEquals(Adaptation.SEALED_PERIMETER, skin(SurfacePalette.IRRADIATED).adaptation(map(town(null)), town(null)));
        assertEquals(Adaptation.SEALED_PERIMETER, skin(SurfacePalette.TOXIC).adaptation(map(town(null)), town(null)));
    }

    @Test
    void aFrozenTownTerracesIntoACrevasseOnlyWhereTheMapLetsIt() {
        BiomeSkin frozen = skin(SurfacePalette.FROZEN);
        assertTrue(frozen.dropsTakeTerraces(DropRole.RIFT));
        assertEquals(Adaptation.CREVASSE_TERRACE, frozen.adaptation(map(town("gorge")), town("gorge")));
        assertEquals(Adaptation.INSULATED, frozen.adaptation(map(town(null)), town(null)));
        // The same map on a world whose drop is no crevasse builds its town in the open.
        assertFalse(skin(SurfacePalette.ARID).dropsTakeTerraces(DropRole.RIFT));
        assertEquals(Adaptation.OPEN, skin(SurfacePalette.ARID).adaptation(map(town("gorge")), town("gorge")));
    }

    @Test
    void aBreathableLivingWorldBuildsInTheOpen() {
        for (String type : List.of("jungle", "terran", "tundra", "water")) {
            BiomeSkin living = BiomeSkin.forPlanetType(type, Climate.NONE);
            assertEquals(SurfacePalette.VERDANT, living.palette(), type);
            assertEquals(Adaptation.OPEN, living.adaptation(map(town(null)), town(null)), type);
        }
        assertEquals(LivingWorld.JUNGLE, BiomeSkin.forPlanetType("jungle", Climate.NONE).living());
    }

    @Test
    void aBankIsIceOnAFrozenWorldAndRockElsewhere() {
        assertEquals(NaturalRise.Style.ICE_WALL, skin(SurfacePalette.FROZEN).riseStyle());
        assertEquals(NaturalRise.Style.RAVINE_WALL, skin(SurfacePalette.ARID).riseStyle());
    }

    @Test
    void onlyALivingWorldNamesWhichOneItIs() {
        assertEquals(LivingWorld.TEMPERATE, new BiomeSkin(SurfacePalette.ARID, LivingWorld.JUNGLE, null, null).living());
    }
}
