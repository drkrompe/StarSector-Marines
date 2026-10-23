package com.dillon.starsectormarines.render2d.texture;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.ship.TestHulls;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ops.battleview.ArmoryPreviewAssets;
import com.dillon.starsectormarines.ops.battleview.BattleTextureSet;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.TextureLoadQueue;
import com.dillon.starsectormarines.ops.battleview.BattleSprites;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.GameLikeTextureStore;
import com.dillon.starsectormarines.testsupport.HeadlessGl;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glGetTexImage;
import static org.lwjgl.opengl.GL11.glIsTexture;

/**
 * A battle's textures leave the card when it ends, the ones another screen shares do not, and a
 * second battle gets its pictures back ({@code battle-textures.md}, Evidence).
 *
 * <p>On a real driver, because the failures are a deleted texture that is still drawn and a
 * texture that was never deleted, and neither shows up anywhere but on the card. The store under
 * the ledger is {@link GameLikeTextureStore}, which does what the game's does: it pads to power
 * of two, and its unload forgets the path without deleting anything. A texture's content is read
 * back with {@code glGetTexImage} and compared with what the same path held the first time, so a
 * texture that is present but holds another image — what a reused id gives a stale sprite — fails
 * as surely as one that is gone.
 */
@Tag("shader-evidence")
class TextureLifecycleGlEvidence {

    /** Drawn by the company deck as well as by battles: held on the session lease. */
    private static final List<String> SHARED = List.of(TileManifest.SHEET, TileManifest.DOODAD_SHEET);
    /** Drawn by a battle and nothing else. */
    private static final List<String> BATTLE_ONLY = List.of(
            "graphics/battle/vehicles/army-tank.png",
            "graphics/structures/swarm-ooze.png",
            "graphics/decals/decals.png");
    /** The game's own art, already on the card before the mod asked for it. */
    private static final String FOREIGN = "graphics/fx/engineflame32.png";

    @Test
    void aBattleReleasesItsTexturesKeepsSharedOnesAndReloadsCleanly() throws Exception {
        try (HeadlessGl gl = HeadlessGl.createOrNull(64, 64)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            List<Path> roots = new ArrayList<>();
            roots.add(Path.of("mod").toAbsolutePath());
            String install = System.getProperty("starsectorDir");
            if (install != null) roots.add(Path.of(install, "starsector-core"));
            GameLikeTextureStore store = new GameLikeTextureStore(roots);
            SettingsAPI previous = Global.getSettings();
            Global.setSettings(store.settings());
            try {
                run(store, install != null && Files.isRegularFile(Path.of(install, "starsector-core", FOREIGN)));
            } finally {
                Global.setSettings(previous);
            }
        }
    }

    /**
     * The same through the shipping loaders and the real company deck, as the ops dialog drives
     * it: the campaign map with the dialog closed, the deck drawn, the Armory opened from it, the
     * player gone to a battle, back aboard afterwards, and the dialog closed again.
     */
    @Test
    void theDeckHoldsOnlyWhatItDrawsAndGivesItBackWhenTheDialogCloses() throws Exception {
        try (HeadlessGl gl = HeadlessGl.createOrNull(64, 64)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            DiskRegistries.install();
            HeadlessArmoryPreviewRenderer.installCatalogs(Path.of("mod"));
            List<Path> roots = new ArrayList<>();
            roots.add(Path.of("mod").toAbsolutePath());
            String install = System.getProperty("starsectorDir");
            if (install != null) roots.add(Path.of(install, "starsector-core"));
            GameLikeTextureStore store = new GameLikeTextureStore(roots);
            SettingsAPI previous = Global.getSettings();
            Global.setSettings(store.settings());
            try {
                runDeck(store);
            } finally {
                Global.setSettings(previous);
            }
        }
    }

    /** What the battle screen's load queue loads for a battle that names every family, livery and line. */
    private static final Consumer<BattleSprites> BATTLE = s -> {
        s.ensureTileSheet();
        s.ensureRoadSheet();
        s.ensureFloorsSheet();
        s.ensureNatureSheet();
        s.ensureWaterSheet();
        s.ensureUrbanTile3Sheet();
        s.ensureDoodadSheet();
        s.ensureDecalSheet();
        s.ensureUnitSheets();
        s.ensureLayeredUnitSprites();
        s.ensureLayeredMechSprites();
        s.ensureTurretSprites();
        s.ensureMarineSecondarySprites();
        s.ensureAirframeSprites();
        s.ensureDroneSprite();
        s.ensureDroneHubSprite();
        s.ensureEngineFxSprites();
        s.ensureConvoySprites();
        s.ensureObjectiveIcons();
        s.ensureSmokeSprites();
        s.ensureSatchelSprite();
    };

    private static final long READY_TIMEOUT_MS = 120_000L;

    private static void runDeck(GameLikeTextureStore store) throws Exception {
        TextureLedger ledger = TextureLedger.shared();
        long closed = ledger.residentBytes();

        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        List<MarineSoldier> company = roster.soldiers();
        CompanyDeck deck = CompanyDeck.home(TestHulls.transport(), 42L, null, List::of, () -> company,
                () -> new boolean[]{true}).leasedFrom(ledger);
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        while (!deck.ready()) {
            assertTrue(System.currentTimeMillis() < deadline, "the deck was never got ready");
            Thread.sleep(10L);
        }
        assertFalse(deck.holdsTextures(), "a deck nobody has drawn holds nothing");
        int frames = drawAboard(deck);
        long deckOpen = ledger.residentBytes();
        Set<String> deckPaths = new HashSet<>(deck.sprites().lease().paths());
        Map<String, Integer> deckIds = new LinkedHashMap<>();
        Map<String, Long> deckPixels = new LinkedHashMap<>();
        Map<String, Long> byFolder = new TreeMap<>();
        Map<String, Long> sizes = new LinkedHashMap<>();
        for (String path : deckPaths) {
            GameLikeTextureStore.Texture texture = store.texture(path);
            deckIds.put(path, texture.id());
            deckPixels.put(path, checksum(texture));
            byFolder.merge(folder(path), cardBytes(texture), Long::sum);
            sizes.put(path, cardBytes(texture));
        }

        // The Armory, opened from the deck: its previews hold the families they show, and give them back.
        ArmoryPreviewAssets armory = new ArmoryPreviewAssets();
        Set<LayeredArmorFamily> shown = new HashSet<>();
        for (MarineSoldier soldier : company) {
            LayeredArmorFamily family = soldier.armorDef().appearanceFamily();
            if (family != null && shown.add(family)) assertNotNull(armory.layered(family), family + " previews in the Armory");
        }
        long armoryOpen = ledger.residentBytes();
        armory.release();
        assertEquals(deckOpen, ledger.residentBytes(), "the Armory gives its previews back");

        // Off to a battle: the panel releases the deck, and the battle loads what it draws itself.
        deck.releaseTextures();
        assertFalse(deck.holdsTextures());
        for (String path : deckPaths) {
            if (store.texture(path) != null) continue;
            assertFalse(glIsTexture(deckIds.get(path)), path + " is deleted on the card");
        }
        long betweenScreens = ledger.residentBytes();
        assertEquals(closed, betweenScreens, "a deck no screen draws holds nothing");
        // A small battle, loaded the way the battle screen's queue loads it: only its own set.
        long inBattle;
        TextureLedger.Release battleRelease;
        var world = SceneBuilder.openGround(40, 30)
                .squad("infantry").size(4).inFile(4, 4, 1, 0).done()
                .squad("lance").size(1).mech(MechVariant.HOUND, MechRole.BALANCED).inFile(10, 10, 1, 0).done()
                .unit("enemy", Faction.DEFENDER, UnitType.MARINE, 36, 26).build();
        try (BattleSimulation sim = world.sim()) {
            TextureLease battle = ledger.lease("battle");
            BattleSprites battleSprites = new BattleSprites(battle);
            for (TextureLoadQueue.Step step : battleSprites.battleLoadSteps(BattleTextureSet.of(sim), key -> key)) {
                step.work().run();
            }
            inBattle = ledger.residentBytes();
            battleRelease = battle.release();
        }
        assertEquals(closed, ledger.residentBytes(), "a battle leaves nothing behind");
        // And the worst case, a battle that names every family, livery and vehicle line.
        TextureLease everything = ledger.lease("battle");
        BATTLE.accept(new BattleSprites(everything));
        long inWorstBattle = ledger.residentBytes();
        everything.release();
        long afterBattle = ledger.residentBytes();
        assertEquals(closed, afterBattle, "a battle leaves nothing behind");

        // Back aboard: the same textures, loaded afresh, and a renderer drawing live ones.
        int framesAgain = drawAboard(deck);
        assertEquals(deckPaths, deck.sprites().lease().paths(), "back aboard loads the same set");
        for (String path : deckPaths) {
            GameLikeTextureStore.Texture texture = store.texture(path);
            assertTrue(glIsTexture(texture.id()), path + " has a live texture back aboard");
            assertEquals(deckPixels.get(path), checksum(texture), path + " draws the same image as before");
        }
        assertEquals(store.texture(TileManifest.SHEET).id(), deck.sprites().tileSheet().getTextureId(),
                "the deck's sprites are bound to the reloaded sheet, not the released one");
        long backAboard = ledger.residentBytes();

        // And the dialog closes.
        deck.dismiss();
        long dialogClosed = ledger.residentBytes();
        assertEquals(closed, dialogClosed, "closing the dialog returns the card to the campaign map's");

        StringBuilder folders = new StringBuilder();
        for (Map.Entry<String, Long> entry : byFolder.entrySet()) {
            folders.append(String.format("%n    %-56s %7.1f MB", entry.getKey(), mb(entry.getValue())));
        }
        folders.append(String.format("%n  the largest:"));
        deckPaths.stream()
                .sorted((a, b) -> Long.compare(sizes.get(b), sizes.get(a)))
                .limit(12)
                .forEach(path -> folders.append(String.format("%n    %-72s %7.1f MB", path, mb(sizes.get(path)))));
        System.out.printf("[shader-evidence] company deck: %.1f MB of mod textures on the campaign map with the dialog closed,"
                        + " %.1f with the deck drawn (%d textures, %d frames to load), %.1f with the Armory's previews,"
                        + " %.1f between screens, %.1f in a small battle (released %.1f MB; %.1f in a battle that names"
                        + " everything), %.1f after the battle,"
                        + " %.1f back aboard (%d frames), %.1f with the dialog closed%n  the deck's textures by folder:%s%n",
                mb(closed), mb(deckOpen), deckPaths.size(), frames, mb(armoryOpen), mb(betweenScreens), mb(inBattle),
                mb(battleRelease.bytesFreed()), mb(inWorstBattle), mb(afterBattle), mb(backAboard), framesAgain, mb(dialogClosed), folders);
    }

    /** Draws the deck the way a canvas does, a frame at a time, until she can be drawn; the frames it took. */
    private static int drawAboard(CompanyDeck deck) {
        int frames = 1;
        while (deck.drawable() == null) {
            frames++;
            assertTrue(frames < 10_000, "the deck's textures never finished loading");
        }
        assertTrue(deck.drawable().hasRenderer(), "a drawn deck has a renderer");
        return frames;
    }

    /** Bytes the texture holds on the card, padding included. */
    private static long cardBytes(GameLikeTextureStore.Texture texture) {
        return (long) texture.texWidth() * texture.texHeight() * 4L;
    }

    /** The image's folder, up to three levels deep, for the inventory. */
    private static String folder(String path) {
        String[] parts = path.split("/");
        return String.join("/", Arrays.copyOf(parts, Math.min(parts.length - 1, 4)));
    }

    private static void run(GameLikeTextureStore store, boolean haveForeign) throws Exception {
        if (haveForeign) Global.getSettings().loadTexture(FOREIGN);
        TextureLedger ledger = new TextureLedger(new GameTextureBackend());
        for (String path : SHARED) ledger.session().acquire(path);
        long beforeBattle = ledger.residentBytes();

        TextureLease battle = ledger.lease("battle");
        List<String> all = new ArrayList<>(SHARED);
        all.addAll(BATTLE_ONLY);
        if (haveForeign) all.add(FOREIGN);
        for (String path : all) battle.acquire(path);
        long duringBattle = ledger.residentBytes();
        Map<String, Integer> firstIds = new LinkedHashMap<>();
        Map<String, Long> firstPixels = new LinkedHashMap<>();
        for (String path : all) {
            GameLikeTextureStore.Texture texture = store.texture(path);
            assertNotNull(texture, path + " is on the card while the battle holds it");
            firstIds.put(path, texture.id());
            firstPixels.put(path, checksum(texture));
        }

        TextureLedger.Release release = battle.release();
        long afterBattle = ledger.residentBytes();

        for (String path : BATTLE_ONLY) {
            assertFalse(glIsTexture(firstIds.get(path)), path + " is deleted on the card");
            assertNull(store.texture(path), path + " is forgotten by the store");
        }
        for (String path : SHARED) {
            GameLikeTextureStore.Texture texture = store.texture(path);
            assertNotNull(texture, path + " survives: the session lease still holds it");
            assertEquals(firstIds.get(path), texture.id(), path + " is the same texture");
            assertEquals(firstPixels.get(path), checksum(texture), path + " still holds its image");
        }
        if (haveForeign) {
            assertTrue(glIsTexture(firstIds.get(FOREIGN)), "the game's own texture is never deleted");
        }
        assertEquals(BATTLE_ONLY.size(), release.unloaded());
        assertEquals(beforeBattle, afterBattle, "releasing a battle returns the card to where it was");

        TextureLease again = ledger.lease("battle");
        for (String path : all) again.acquire(path);
        for (String path : all) {
            GameLikeTextureStore.Texture texture = store.texture(path);
            assertNotNull(texture, path + " is back for the second battle");
            assertTrue(glIsTexture(texture.id()), path + " has a live texture");
            assertEquals(firstPixels.get(path), checksum(texture), path + " draws the same image as before");
        }
        TextureLedger.Release second = again.release();
        assertEquals(BATTLE_ONLY.size(), second.unloaded());

        System.out.printf("[shader-evidence] texture lifecycle: %.1f MB held before the battle, %.1f during, %.1f after;"
                        + " released %d textures, %.1f MB; second battle released %.1f MB%n",
                mb(beforeBattle), mb(duringBattle), mb(afterBattle),
                release.unloaded(), mb(release.bytesFreed()), mb(second.bytesFreed()));
    }

    /** CRC of the image's own texels, read back off the card; the padding is left out. */
    private static long checksum(GameLikeTextureStore.Texture texture) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(texture.texWidth() * texture.texHeight() * 4);
        glBindTexture(GL_TEXTURE_2D, texture.id());
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindTexture(GL_TEXTURE_2D, 0);
        CRC32 crc = new CRC32();
        byte[] row = new byte[texture.width() * 4];
        for (int y = 0; y < texture.height(); y++) {
            pixels.position(y * texture.texWidth() * 4);
            pixels.get(row);
            crc.update(row);
        }
        return crc.getValue();
    }

    private static double mb(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }
}
