package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.FactionEquipmentCatalog;
import com.dillon.starsectormarines.marine.MarineArmory;
import com.dillon.starsectormarines.marine.SquadLoadoutPresentationRegistry;
import com.dillon.starsectormarines.battle.mech.MechCatalog;
import com.dillon.starsectormarines.battle.setup.GroundRosterRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Auto-registered JUnit extension that installs the disk-loaded catalogs before
 * any test runs — mirroring what {@code onApplicationLoad} does in-game. The
 * map-generation chain comes from {@link DiskRegistries} and the hull geometry
 * from {@link InstalledHullSpecs}, both shared with the authoring workbench and
 * the snapshot suites; the campaign-side catalogs below
 * are the ones only a test needs. Without the tile registry, gen code
 * under test takes its {@code installed() == null} path: {@code NatureZoneFiller}
 * skips overlay scatter, which diverges the gen RNG stream (and therefore every
 * preview PNG) from production. Without the mapping registry, fillers that read
 * {@code GenMappingRegistry.doodadPool(theme)} (moddable-tilesets Phase 2) fail.
 * Without the weapon registry, every {@code WeaponDef} stat read throws;
 * without special equipment, persisted loadout ids and utility presentation
 * cannot resolve. Both catalogs deliberately fail loud rather than degrading.
 * The mech catalog is the same kind of thing for chassis and their components:
 * without it every mech spec sheet throws rather than reading blank.
 *
 * <p>Registered globally via {@code META-INF/services/org.junit.jupiter.api.extension.Extension}
 * + {@code junit.jupiter.extensions.autodetection.enabled=true} in
 * {@code junit-platform.properties}. Idempotent — each registry loads only once.
 */
public final class TileRegistryTestInstaller implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        // Shared with the authoring workbench, which needs the same two and had
        // nothing to install them: a page that opened on rooms it could not
        // furnish was how that came to light.
        DiskRegistries.installMapGeneration();
        // The other half of "what the game would have loaded": hull geometry,
        // which comes from the install rather than from the mod folder. Without
        // it every aircraft under test is the same size as every other one.
        InstalledHullSpecs.install();
        if (SpecialEquipmentRegistry.installed() == null) {
            SpecialEquipmentRegistry equipment = new SpecialEquipmentRegistry();
            for (String path : SpecialEquipmentRegistry.BUILTIN_CATALOGS) {
                equipment.ingest(new JSONObject(Files.readString(Paths.get("mod", path))));
            }
            equipment.validateReferences();
            SpecialEquipmentRegistry.install(equipment);
        }
        if (MarineArmorCatalogRegistry.installed() == null) {
            MarineArmorCatalogRegistry armor = new MarineArmorCatalogRegistry();
            for (String path : MarineArmorCatalogRegistry.BUILTIN_CATALOGS) {
                armor.ingest(new JSONObject(Files.readString(Paths.get("mod", path))));
            }
            armor.validateCompleteness();
            MarineArmorCatalogRegistry.install(armor);
        }
        if (EquipmentTemplateCatalog.installed() == null) {
            EquipmentTemplateCatalog templates = new EquipmentTemplateCatalog();
            templates.ingest(new JSONObject(Files.readString(Paths.get("mod", "data",
                    "marines", "equipment-templates.template.json"))));
            templates.validateCompleteness();
            EquipmentTemplateCatalog.install(templates);
        }
        if (FactionEquipmentCatalog.installed() == null) {
            FactionEquipmentCatalog factionEquipment = new FactionEquipmentCatalog();
            factionEquipment.ingest(new JSONObject(Files.readString(Paths.get("mod", "data",
                    "marines", "faction-equipment.faction-equipment.json"))));
            factionEquipment.validateCompleteness();
            factionEquipment.validateReachability(
                    new MarineArmory().ownedEquipmentTemplateIds());
            FactionEquipmentCatalog.install(factionEquipment);
        }
        if (SquadLoadoutPresentationRegistry.installed() == null) {
            SquadLoadoutPresentationRegistry loadouts =
                    new SquadLoadoutPresentationRegistry();
            for (String path : SquadLoadoutPresentationRegistry.BUILTIN_CATALOGS) {
                loadouts.ingest(new JSONObject(Files.readString(Paths.get("mod", path))));
            }
            loadouts.validateBuiltins();
            SquadLoadoutPresentationRegistry.install(loadouts);
        }
        if (MechCatalog.installed() == null) {
            MechCatalog.install(MechCatalog.parse(new JSONObject(
                    Files.readString(Paths.get("mod", MechCatalog.CONTENT_PATH)))));
        }
        if (GroundRosterRegistry.installed() == null) {
            GroundRosterRegistry rosters = new GroundRosterRegistry();
            for (String path : GroundRosterRegistry.BUILTIN_CATALOGS) {
                rosters.ingest(new JSONObject(Files.readString(Paths.get("mod", path))));
            }
            rosters.validateCompleteness();
            GroundRosterRegistry.install(rosters);
        }
    }
}
