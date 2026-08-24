package com.dillon.starsectormarines.tools.turretauthoring;

import com.dillon.starsectormarines.battle.turret.DefensePostLayoutDef;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.turret.preview.HeadlessTurretCatalogPreviewRenderer;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretAuthoringDocumentTest {

    @TempDir Path temporary;

    @Test
    void crossCatalogEditsValidateSaveAndReloadTogether() throws Exception {
        copyCatalogs(temporary);
        String preservedCatalogLine = "\"catalog\": { \"displayName\": \"Vulcan Cannon\", \"modelName\": \"Vulcan\", \"designation\": \"VUL\", \"designationTiered\": false }";
        TurretAuthoringDocument document = TurretAuthoringDocument.load(temporary);
        assertEquals(8, document.selections().size());
        assertEquals(9, document.layoutObjects().size());

        TurretAuthoringDocument.TurretSelection hephaestus =
                document.selection("structure.turret-hephaestus");
        hephaestus.weapon().getJSONObject("sim").put("damage", 52.0)
                .put("penetration", 6.0);
        hephaestus.weapon().getJSONObject("sim").getJSONObject("contact")
                .put("damage", 124.0).put("penetration", 28.0);
        hephaestus.mount().getJSONObject("render")
                .put("visualCells", 2.35).put("muzzleOffsetCells", 0.66);
        hephaestus.structure().getJSONObject("durability")
                .put("structure", 98.0).put("armorPool", 155.0)
                .put("armorRating", 16.0);
        hephaestus.weapon().getJSONObject("fx").getJSONArray("impact")
                .getJSONObject(0).put("radius", 1.42);

        TurretMountDef preview = document.previewMount(hephaestus.structureId());
        assertEquals(52f, preview.weapon.damage, 0f);
        assertEquals(6f, preview.weapon.penetration, 0f);
        assertEquals(124f, preview.weapon.contactDamage, 0f);
        assertEquals(2.35f, preview.visualCells, 0f);
        assertEquals(0.66f, preview.muzzleOffsetCells, 0f);
        assertTrue(document.validate().isEmpty());
        assertTrue(document.dirty());

        document.save();
        assertFalse(document.dirty());
        assertTrue(Files.readString(temporary.resolve(TurretAuthoringDocument.WEAPON_PATH))
                .contains(preservedCatalogLine),
                "scalar save must preserve untouched compact source formatting");
        TurretAuthoringDocument reloaded = TurretAuthoringDocument.load(temporary);
        TurretMountDef saved = reloaded.previewMount(hephaestus.structureId());
        assertEquals(52f, saved.weapon.damage, 0f);
        assertEquals(155f, reloaded.validateCatalogs().turrets()
                .getStructure(hephaestus.structureId()).armorPool, 0f);
    }

    @Test
    void historyRestoresCompleteCatalogSnapshotAndNewLargeVariant() throws Exception {
        copyCatalogs(temporary);
        TurretAuthoringDocument document = TurretAuthoringDocument.load(temporary);
        TurretAuthoringHistory history = new TurretAuthoringHistory();
        String before = document.snapshot();

        JSONObject copy = document.duplicateLargeLayout(
                "layout.defense-post-large-line-h", "authoring-test");
        copy.getJSONObject("bounds").put("minOffset",
                new JSONArray().put(-3).put(-2));
        copy.getJSONObject("bounds").put("maxOffset",
                new JSONArray().put(3).put(2));
        String after = document.snapshot();
        history.record(before, after);

        assertEquals(10, document.layoutObjects().size());
        assertTrue(document.validate().isEmpty());
        history.undo(document);
        assertEquals(9, document.layoutObjects().size());
        history.redo(document);
        assertEquals(10, document.layoutObjects().size());
        assertEquals("authoring-test", document.layoutObject(
                "layout.defense-post-large-authoring-test").getString("variant"));
    }

    @Test
    void rearrangedThreeTurretLayoutSavesReloadsAndResolvesForRuntime() throws Exception {
        copyCatalogs(temporary);
        TurretAuthoringDocument document = TurretAuthoringDocument.load(temporary);
        JSONObject triangle = document.layoutObject("layout.defense-post-large-triangle");
        triangle.getJSONArray("turrets").getJSONObject(2)
                .put("offset", new JSONArray().put(-1).put(0));

        assertTrue(document.validate().isEmpty());
        document.save();

        TurretAuthoringDocument reloaded = TurretAuthoringDocument.load(temporary);
        DefensePostLayoutDef runtime = reloaded.validateCatalogs().layouts()
                .get("layout.defense-post-large-triangle");
        assertEquals(new DefensePostLayoutDef.Offset(-1, 0),
                runtime.turrets.get(2).offset());
        assertEquals(3, runtime.turrets.size());
    }

    @Test
    void currentInMemoryFxChangeProducesANewDeterministicPreview() throws Exception {
        String install = System.getProperty("starsectorDir");
        Assumptions.assumeTrue(install != null && !install.isBlank());
        Path core = Path.of(install, "starsector-core");
        Assumptions.assumeTrue(Files.isRegularFile(
                core.resolve("graphics/fx/particlealpha64linear.png")));

        TurretAuthoringDocument document = TurretAuthoringDocument.load(Path.of("."));
        HeadlessTurretCatalogPreviewRenderer renderer =
                new HeadlessTurretCatalogPreviewRenderer(Path.of("mod"), core);
        String id = "structure.turret-hephaestus";
        byte[] before = png(renderer.render(document.previewMount(id)).image());
        document.selection(id).weapon().getJSONObject("fx").getJSONArray("impact")
                .getJSONObject(0).put("radius", 2.0);
        byte[] after = png(renderer.render(document.previewMount(id)).image());

        assertFalse(Arrays.equals(before, after));
        assertEquals(Arrays.hashCode(after), Arrays.hashCode(
                png(renderer.render(document.previewMount(id)).image())));
    }

    @Test
    void providerIsDiscoverableFromRootTestServices() {
        boolean found = ServiceLoader.load(AuthoringPageProvider.class).stream()
                .anyMatch(provider -> provider.type() == TurretAuthoringPageProvider.class);
        assertTrue(found, "Turrets must be a visible authoring workbench page");
    }

    private static void copyCatalogs(Path root) throws Exception {
        for (Path relative : new Path[]{TurretAuthoringDocument.WEAPON_PATH,
                TurretAuthoringDocument.TURRET_PATH, TurretAuthoringDocument.LAYOUT_PATH}) {
            Path target = root.resolve(relative);
            Files.createDirectories(target.getParent());
            Files.writeString(target, Files.readString(relative, StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);
        }
    }

    private static byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "PNG", bytes));
        return bytes.toByteArray();
    }
}
