package com.dillon.starsectormarines.render2d.texture;

import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.ops.battleview.BattleSprites;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.UnitSpriteCache;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * The same through the shipping loaders: a session sprite set loaded as the company deck
     * loads it, and a battle's loaded under its own lease as the battle screen loads it.
     */
    @Test
    void aBattlesSpriteSetLeavesWithItsLeaseAndTheDeckKeepsDrawing() throws Exception {
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
                runSpriteSets(store);
            } finally {
                Global.setSettings(previous);
            }
        }
    }

    /** What {@code CompanyDeck.ensureSceneSprites} loads. */
    private static final Consumer<BattleSprites> DECK = s -> {
        s.ensureTileSheet();
        s.ensureRoadSheet();
        s.ensureDoodadSheet();
        s.ensureLayeredUnitSprites();
        s.ensureLayeredMechSprites();
        s.ensureMarineSecondarySprites();
        s.ensureAirframeSprites();
    };

    /** What the battle screen's load queue loads. */
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

    private static void runSpriteSets(GameLikeTextureStore store) throws Exception {
        TextureLedger ledger = TextureLedger.shared();
        DECK.accept(new BattleSprites());
        Set<String> deckPaths = new HashSet<>(ledger.session().paths());
        Map<String, Long> deckPixels = new LinkedHashMap<>();
        for (String path : deckPaths) deckPixels.put(path, checksum(store.texture(path)));
        long beforeBattle = ledger.residentBytes();

        TextureLease lease = ledger.lease("battle");
        BATTLE.accept(new BattleSprites(lease));
        long duringBattle = ledger.residentBytes();
        Map<String, Integer> firstIds = new LinkedHashMap<>();
        Map<String, Long> firstPixels = new LinkedHashMap<>();
        for (String path : lease.paths()) {
            GameLikeTextureStore.Texture texture = store.texture(path);
            firstIds.put(path, texture.id());
            firstPixels.put(path, checksum(texture));
        }
        Set<String> battleOnly = new HashSet<>(lease.paths());
        battleOnly.removeAll(deckPaths);
        assertTrue(battleOnly.size() > 0, "the battle loads art the deck does not");

        TextureLedger.Release release = lease.release();

        int foreign = 0;
        for (String path : battleOnly) {
            if (store.texture(path) != null) {
                // Only the game's own art, already resident, may outlive the battle.
                foreign++;
                assertEquals(firstIds.get(path), store.texture(path).id());
                continue;
            }
            assertFalse(glIsTexture(firstIds.get(path)), path + " is deleted on the card");
        }
        assertEquals(battleOnly.size() - foreign, release.unloaded());
        for (String path : deckPaths) {
            GameLikeTextureStore.Texture texture = store.texture(path);
            assertNotNull(texture, path + " survives: the deck still draws it");
            assertTrue(glIsTexture(texture.id()), path + " has a live texture");
            assertEquals(deckPixels.get(path), checksum(texture), path + " still holds its image");
        }
        assertEquals(beforeBattle, ledger.residentBytes(), "releasing the battle returns the card to where it was");

        TextureLease againLease = ledger.lease("battle");
        BattleSprites again = new BattleSprites(againLease);
        BATTLE.accept(again);
        assertEquals(firstPixels.keySet(), againLease.paths(), "the second battle loads the same set");
        for (String path : againLease.paths()) {
            GameLikeTextureStore.Texture texture = store.texture(path);
            assertTrue(glIsTexture(texture.id()), path + " has a live texture in the second battle");
            assertEquals(firstPixels.get(path), checksum(texture), path + " draws the same image as before");
        }
        // And the sprites the second set hands out are bound to those textures, not to the first battle's.
        assertEquals(store.texture(TileManifest.SHEET).id(), again.tileSheet().getTextureId());
        for (UnitSpriteCache cache : again.convoySprites().values()) {
            assertTrue(glIsTexture(cache.sheet.getTextureId()), "a convoy sheet sprite draws a live texture");
        }
        TextureLedger.Release second = againLease.release();

        System.out.printf("[shader-evidence] battle sprite set: %.1f MB of mod textures before the battle, %.1f during,"
                        + " %.1f after; the battle held %d textures, released %d (%.1f MB), %d still drawn by the deck"
                        + " or the game; second battle released %.1f MB%n",
                mb(beforeBattle), mb(duringBattle), mb(ledger.residentBytes()), firstIds.size(),
                release.unloaded(), mb(release.bytesFreed()), release.kept(), mb(second.bytesFreed()));
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
