package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.CampaignMechSquad;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated deck draws through the game's own renderer, and its berths hold
 * the company's own machines.
 *
 * <p>Guards the seams rather than the picture. A world layer may emit a command
 * that only a live GL context can replay — the decal pass does — and asking for
 * such a layer fails loudly at draw time. That is the right behaviour, but until
 * this test existed the only thing that exercised it was running the snapshot
 * task by hand, so the failure surfaced as a broken evidence run instead of a
 * broken build.
 */
final class ShipDeckBattleSceneTest {

    private static final long SEED = 42L;
    private static final int CELL_PX = 16;
    /** Long and shallow, the way a hull that carries people and machines is. */
    private static final float TRANSPORT_ASPECT = 0.28f;

    /**
     * Constructing the headless drain installs the tile catalogs, which the deck
     * scene needs before it can build a sim. Shared so neither test depends on
     * the other having run first.
     */
    private static HeadlessUiRenderer drain;

    @BeforeAll
    static void installCatalogs() {
        Path modRoot = Paths.get("mod").toAbsolutePath().normalize();
        drain = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(modRoot), modRoot);
    }

    @Test
    void deckDrawsThroughTheBattleRendererWithoutALiveContext() {
        MapResult deck = deck();

        BufferedImage image;
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck, SEED)) {
            int across = 24;
            int down = 16;
            image = drain.renderHostPass(
                    scene.pass(ShipDeckBattleScene.DeckView.over(
                            10, 6, across, down, CELL_PX)),
                    across * CELL_PX, down * CELL_PX);
        }

        assertNotNull(image, "the deck view produced no image");
        assertTrue(painted(image) > 0.5f,
                "the deck view came out mostly blank, so the world layers drew nothing");
    }

    /**
     * The bay stands the company's own machines, not a chosen number of them.
     *
     * <p>A home deck's vehicle bay is the Mech Lab, so what it holds is whatever
     * the campaign says the player owns. A new company owns one mech, and the
     * berths past it stay empty on purpose — filling them to make the room look
     * busy would be showing the player equipment they do not have.
     */
    @Test
    void berthsHoldTheCompanysOwnLance() {
        CampaignMechSquad squad = new MechBay().activeSquad();
        List<MechVariant> lance = squad.mechs().stream().map(CampaignMech::variant).toList();
        assertTrue(!lance.isEmpty(), "a new company should start with at least one machine");

        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck(), SEED)) {
            assertTrue(!scene.gantries().isEmpty(), "the deck authored no berths");
            assertEquals(Math.min(lance.size(), scene.gantries().size()),
                    scene.occupyGantries(lance),
                    "the bay berthed a different number of machines than the company owns");
        }
    }

    /**
     * A troop transport's deck, sized to its own program.
     *
     * <p>Deliberately not a fixed width and height. A vehicle bay is a large
     * room, and forcing it onto an arbitrary deck means it sometimes fails to
     * place — which turns a berth test into a test that quietly skips. Sizing
     * the deck from the program is also what the game does.
     */
    private static MapResult deck() {
        return new ShipDeckGenerator().generateDeck(
                DeckSizing.planFor(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                        10, 250, 50, TRANSPORT_ASPECT),
                SEED, null);
    }

    /** Fraction of pixels the renderer actually put something opaque into. */
    private static float painted(BufferedImage image) {
        int opaque = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) > 0x80) opaque++;
            }
        }
        return opaque / (float) (image.getWidth() * image.getHeight());
    }
}
