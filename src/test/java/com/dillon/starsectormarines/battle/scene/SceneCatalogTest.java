package com.dillon.starsectormarines.battle.scene;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneCatalogTest {

    /**
     * A stand-in scene: the catalog only ever reads a scene's id and label, and
     * a service registration would leak a fake into the real catalog.
     */
    private record StubScene(String id) implements BehaviorScene {
        @Override public String label() { return "stub scene " + id; }
        @Override public List<SceneReport> play(FrameSink frames) { return List.of(); }
    }

    private static SceneCatalog catalogOf(String... ids) {
        return new SceneCatalog(List.of(ids).stream().map(StubScene::new).toList());
    }

    @Test
    void allOrdersScenesById() {
        List<String> ids = catalogOf("yield-freeze", "airfield-sortie", "killing-ground")
                .all().stream().map(BehaviorScene::id).toList();

        assertEquals(List.of("airfield-sortie", "killing-ground", "yield-freeze"), ids);
    }

    @Test
    void selectorAllTakesEveryScene() {
        SceneCatalog catalog = catalogOf("killing-ground", "yield-freeze");

        assertEquals(catalog.all(), catalog.select("all"));
        assertEquals(catalog.all(), catalog.select("  "));
    }

    @Test
    void selectorTakesNamedScenesInCatalogOrder() {
        List<String> ids = catalogOf("airfield-sortie", "killing-ground", "yield-freeze")
                .select(" yield-freeze , airfield-sortie ")
                .stream().map(BehaviorScene::id).toList();

        assertEquals(List.of("airfield-sortie", "yield-freeze"), ids);
    }

    @Test
    void unknownSceneNamesTheOnesThatExist() {
        SceneCatalog catalog = catalogOf("killing-ground", "yield-freeze");

        IllegalArgumentException problem = assertThrows(IllegalArgumentException.class,
                () -> catalog.select("killing-ground,no-such-scene"));

        assertTrue(problem.getMessage().contains("no-such-scene"), problem.getMessage());
        assertTrue(problem.getMessage().contains("killing-ground"), problem.getMessage());
        assertTrue(problem.getMessage().contains("yield-freeze"), problem.getMessage());
    }

    @Test
    void duplicateIdIsRefusedAtConstruction() {
        List<BehaviorScene> scenes = List.of(new StubScene("yield-freeze"),
                new StubScene("yield-freeze"));

        IllegalArgumentException problem = assertThrows(IllegalArgumentException.class,
                () -> new SceneCatalog(scenes));

        assertTrue(problem.getMessage().contains("yield-freeze"), problem.getMessage());
    }
}
