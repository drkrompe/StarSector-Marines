package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one write a rebuilt profile makes into an otherwise authored catalog.
 *
 * <p>Every case here works on a registry of its own rather than the installed one, so
 * a derived profile from a test never outlives the test that made it.
 */
class GroundRosterRegistryReplaceDerivedTest {

    private static final String DERIVED_ID = "roster.test-derived";

    @Test
    void aDerivedProfileResolvesUnderItsFactionInsteadOfTheFallback() throws Exception {
        GroundRosterRegistry registry = authored();

        registry.replaceDerived(profile(DERIVED_ID, "player", UnitType.MILITIA));

        assertEquals(DERIVED_ID, registry.profileFor("player").id());
        assertEquals("roster.hegemony", registry.profileFor("hegemony").id(),
                "the authored catalog around it is untouched");
    }

    /**
     * The daily rebuild sends the same id and the same faction claim again. The
     * authored path would refuse both, correctly; this is what makes a rebuild a
     * replacement rather than a collision.
     */
    @Test
    void aRebuildReplacesRatherThanCollides() throws Exception {
        GroundRosterRegistry registry = authored();
        int authoredCount = registry.size();

        registry.replaceDerived(profile(DERIVED_ID, "player", UnitType.MILITIA));
        registry.replaceDerived(profile(DERIVED_ID, "player", UnitType.MARINE_RED));
        registry.replaceDerived(profile(DERIVED_ID, "player", UnitType.MILITIA));

        assertEquals(authoredCount + 1, registry.size(), "one derived profile, not three");
        assertEquals(UnitType.MILITIA,
                registry.profileFor("player").unitType(GroundRosterProfile.ForceTier.BULK),
                "the newest rebuild is what stands");
    }

    /** A faction the previous rebuild claimed and this one dropped must not be stranded. */
    @Test
    void aFactionTheRebuildDroppedStopsResolvingToIt() throws Exception {
        GroundRosterRegistry registry = authored();
        registry.replaceDerived(profile(DERIVED_ID, "player", UnitType.MILITIA));

        registry.replaceDerived(profile(DERIVED_ID, "some_other_polity", UnitType.MILITIA));

        assertEquals("roster.independent", registry.profileFor("player").id(),
                "the dropped claim falls back rather than pointing at a profile that is gone");
        assertEquals(DERIVED_ID, registry.profileFor("some_other_polity").id());
    }

    /** A derived roster may not quietly take a faction an authored profile owns. */
    @Test
    void anAuthoredFactionClaimIsStillRefused() throws Exception {
        GroundRosterRegistry registry = authored();

        assertThrows(IllegalStateException.class,
                () -> registry.replaceDerived(profile(DERIVED_ID, "hegemony", UnitType.MILITIA)));
        assertEquals("roster.hegemony", registry.profileFor("hegemony").id());
        assertThrows(IllegalArgumentException.class, () -> registry.replaceDerived(null));
    }

    private static GroundRosterRegistry authored() throws Exception {
        GroundRosterRegistry registry = new GroundRosterRegistry();
        for (String path : GroundRosterRegistry.BUILTIN_CATALOGS) {
            registry.ingest(new JSONObject(Files.readString(Paths.get("mod", path))));
        }
        registry.validateCompleteness();
        assertNotNull(registry.profileFor("hegemony"));
        return registry;
    }

    private static GroundRosterProfile profile(String id, String factionId, UnitType bulk) {
        return GroundRosterProfile.builder(id)
                .factionId(factionId)
                .bulk(issue(bulk))
                .elite(issue(UnitType.MARINE_RED))
                .build();
    }

    private static GroundRosterProfile.Issue issue(UnitType unitType) {
        GroundRosterProfile.Issue.Builder builder = GroundRosterProfile.Issue.builder(unitType);
        builder.primary(WeaponRegistry.require("weapon.field-rifle"), 10);
        for (RiskLevel risk : RiskLevel.values()) {
            builder.grade(risk, EquipmentGrade.SURPLUS, 10);
            builder.armor(risk, MarineArmorCatalogRegistry.require("armor.militia"), 10);
            builder.special(risk, null, 10);
        }
        return builder.build();
    }

    /** Guards against the sanity of the fixture itself rather than the code under test. */
    @Test
    void theFixtureRegistryIsTheAuthoredCatalog() throws Exception {
        GroundRosterRegistry registry = authored();

        assertTrue(registry.size() > 1);
        assertSame(registry.profileFor("some_unknown_faction"),
                registry.profileFor("independent"),
                "an unclaimed faction falls back");
    }
}
