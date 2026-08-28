package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.render2d.DrawCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the durability bar's geometry contract, its shared armor/structure scale,
 * and its ownership coding. No GL and no simulation — {@link DurabilityBarDecor}
 * emits into a plain {@link DrawList}, so the emitted {@code SOLID_RECT}s are the
 * whole observable surface.
 *
 * <p>Assertions are structural (which rows a band occupies, where one material
 * hands off to the next, how many dividers survive) rather than a pinned palette:
 * the design owns the exact hues, but "armor and structure drain one continuous
 * run on one absolute scale" and "the four allegiances stay distinguishable" are
 * contracts a future restyle must keep.
 */
public class DurabilityBarDecorTest {

    private static final float CX = 100f;
    private static final float BASE_Y = 50f;
    private static final float WIDTH = 40f;

    /** A heavy emplacement — armor larger than structure, as the authored turrets are. */
    private static final float TURRET_STRUCTURE = 90f;
    private static final float TURRET_ARMOR = 145f;

    /** One emitted rect, in the corner form {@code setSolidRect} stores. */
    private record Rect(float x0, float y0, float x1, float y1,
                        float r, float g, float b, float a) {

        float width() {
            return x1 - x0;
        }

        boolean sameColorAs(Rect other) {
            return r == other.r && g == other.g && b == other.b;
        }
    }

    private static List<Rect> emit(Allegiance owner, float structure, float maxStructure) {
        DrawList out = new DrawList();
        DurabilityBarDecor.emit(out, RenderLayer.UNITS, owner, CX, BASE_Y, WIDTH,
                structure, maxStructure, 1f);
        return rects(out);
    }

    private static List<Rect> emit(Allegiance owner, float structure, float maxStructure,
                                   float armor, float maxArmor) {
        DrawList out = new DrawList();
        DurabilityBarDecor.emit(out, RenderLayer.UNITS, owner, CX, BASE_Y, WIDTH,
                structure, maxStructure, armor, maxArmor, 1f);
        return rects(out);
    }

    private static List<Rect> rects(DrawList out) {
        List<Rect> found = new ArrayList<>();
        DrawCommand[] buffer = out.buffer(RenderLayer.UNITS);
        for (int i = 0; i < out.count(RenderLayer.UNITS); i++) {
            DrawCommand command = buffer[i];
            assertSame(DrawCommand.Kind.SOLID_RECT, command.kind(),
                    "the bar stays inside the ordinary solid-rect vocabulary");
            found.add(new Rect(command.centerX(), command.centerY(),
                    command.width(), command.height(),
                    command.red(), command.green(), command.blue(), command.alpha()));
        }
        return found;
    }

    /** The plate is the first rect and encloses every other one. */
    private static Rect plate(List<Rect> emitted) {
        return emitted.get(0);
    }

    /** The drained track, painted before either material. */
    private static Rect track(List<Rect> emitted) {
        return emitted.get(1);
    }

    /**
     * Segment dividers are the only rects repainting the plate color at the
     * divider's own opacity, so color identifies them without pinning an index.
     */
    private static List<Rect> dividers(List<Rect> emitted) {
        Rect plate = plate(emitted);
        return emitted.stream()
                .skip(1)
                .filter(rect -> rect.sameColorAs(plate) && rect.a() != plate.a())
                .toList();
    }

    @Test
    void factionsResolveToTheFourOwnershipReadings() {
        assertEquals(Allegiance.PLAYER, Allegiance.of(Faction.MARINE));
        assertEquals(Allegiance.ENEMY, Allegiance.of(Faction.DEFENDER));
        assertEquals(Allegiance.NEUTRAL, Allegiance.of(Faction.CIVILIAN));
        assertEquals(Allegiance.ENEMY, Allegiance.of(null),
                "an unknown side must never style as friendly");
        assertTrue(Allegiance.PLAYER.friendly());
        assertFalse(Allegiance.ENEMY.friendly());
    }

    @Test
    void everyAllegianceEmitsAPlateOfItsDeclaredHeightCenteredOnTheCaller() {
        for (Allegiance owner : Allegiance.values()) {
            Rect plate = plate(emit(owner, 20f, 40f));
            assertEquals(BASE_Y, plate.y0(), 1e-4f, "plate bottom sits on baseY for " + owner);
            assertEquals(BASE_Y + DurabilityBarDecor.height(owner), plate.y1(), 1e-4f,
                    "plate height matches the advertised height for " + owner);
            assertEquals(CX, (plate.x0() + plate.x1()) / 2f, 1e-4f,
                    "plate is centered on cx for " + owner);
        }
    }

    @Test
    void armorSharesTheStructureBandInsteadOfStackingAboveIt() {
        List<Rect> bar = emit(Allegiance.ENEMY, TURRET_STRUCTURE, TURRET_STRUCTURE,
                TURRET_ARMOR, TURRET_ARMOR);
        Rect structure = bar.get(2);
        Rect armor = bar.get(3);

        assertEquals(structure.y0(), armor.y0(), 1e-4f, "one band, not two");
        assertEquals(structure.y1(), armor.y1(), 1e-4f, "one band, not two");
        assertEquals(structure.x1(), armor.x0(), 1e-4f,
                "armor continues from where structure ends — one uninterrupted run");
        assertFalse(structure.sameColorAs(armor),
                "the two materials stay tellable apart inside the shared band");
        assertEquals(DurabilityBarDecor.height(Allegiance.ENEMY),
                plate(emit(Allegiance.ENEMY, 20f, 40f)).y1() - BASE_Y, 1e-4f,
                "carrying armor no longer makes the bar taller");
    }

    @Test
    void bothMaterialsMeasureAgainstOneTotalDurabilityScale() {
        List<Rect> bar = emit(Allegiance.ENEMY, TURRET_STRUCTURE, TURRET_STRUCTURE,
                TURRET_ARMOR, TURRET_ARMOR);
        Rect track = track(bar);
        Rect structure = bar.get(2);
        Rect armor = bar.get(3);

        float total = TURRET_STRUCTURE + TURRET_ARMOR;
        assertEquals(track.width() * (TURRET_STRUCTURE / total), structure.width(), 1e-3f,
                "structure occupies its true share of total durability");
        assertEquals(track.width() * (TURRET_ARMOR / total), armor.width(), 1e-3f,
                "armor occupies its true share of total durability");
        assertEquals(track.x1(), armor.x1(), 1e-3f,
                "a fully intact entity fills its whole bar");
    }

    @Test
    void damageDrainsTheOneRunRightToLeftThroughArmorThenStructure() {
        float full = filledEdge(emit(Allegiance.ENEMY, TURRET_STRUCTURE, TURRET_STRUCTURE,
                TURRET_ARMOR, TURRET_ARMOR));
        float halfArmor = filledEdge(emit(Allegiance.ENEMY, TURRET_STRUCTURE, TURRET_STRUCTURE,
                TURRET_ARMOR / 2f, TURRET_ARMOR));
        float noArmor = filledEdge(emit(Allegiance.ENEMY, TURRET_STRUCTURE, TURRET_STRUCTURE,
                0f, TURRET_ARMOR));
        float halfStructure = filledEdge(emit(Allegiance.ENEMY, TURRET_STRUCTURE / 2f,
                TURRET_STRUCTURE, 0f, TURRET_ARMOR));

        assertTrue(full > halfArmor, "losing armor shortens the run");
        assertTrue(halfArmor > noArmor, "losing the rest of the armor shortens it further");
        assertTrue(noArmor > halfStructure, "structure loss keeps the same run shrinking");

        List<Rect> stripped = emit(Allegiance.ENEMY, TURRET_STRUCTURE, TURRET_STRUCTURE,
                0f, TURRET_ARMOR);
        assertEquals(3 + dividers(stripped).size(), stripped.size(),
                "a broken armor pool paints no armor rect at all");
    }

    /**
     * Right edge of the filled run — armor's edge when it survives, structure's
     * otherwise. Skips the dividers, which are laid over the band at the same rows
     * a full-height material rect occupies.
     */
    private static float filledEdge(List<Rect> emitted) {
        Rect plate = plate(emitted);
        Rect track = track(emitted);
        float edge = track.x0();
        for (Rect rect : emitted.subList(2, emitted.size())) {
            if (rect.sameColorAs(plate)) continue;
            if (rect.y0() == track.y0() && rect.y1() == track.y1()) {
                edge = Math.max(edge, rect.x1());
            }
        }
        return edge;
    }

    @Test
    void segmentDividersCutTheBandOnOneAbsoluteScale() {
        assertEquals(0, dividers(emit(Allegiance.ENEMY, 25f, 25f)).size(),
                "a single-segment body carries no divider");
        assertEquals(3, dividers(emit(Allegiance.ENEMY, 100f, 100f)).size(),
                "100 durability at 25 per segment is cut three times");
        assertEquals(3, dividers(emit(Allegiance.ENEMY, 40f, 40f, 60f, 60f)).size(),
                "the scale spans both pools, not each separately");

        // Every fifth divider is promoted, so a 250-point body shows eight minors
        // plus one major.
        List<Rect> emplacement = emit(Allegiance.ENEMY, 100f, 100f, 150f, 150f);
        List<Rect> ticks = dividers(emplacement);
        assertEquals(9, ticks.size());
        Rect band = track(emplacement);
        long majors = ticks.stream().filter(t -> t.y0() == band.y0()).count();
        assertEquals(1, majors, "one full-height divider every five segments");
    }

    @Test
    void aDividerTierTooDenseToReadIsDroppedWholeRatherThanSmeared() {
        // A heavy mech's 1500 points cannot show 59 minors in 40px, but its majors
        // still fit — density becomes the magnitude cue.
        List<Rect> mech = emit(Allegiance.PLAYER, 550f, 550f, 950f, 950f);
        List<Rect> ticks = dividers(mech);
        Rect band = track(mech);
        assertEquals(11, ticks.size(), "only the major tier survives at this density");
        assertTrue(ticks.stream().allMatch(t -> t.y0() == band.y0()),
                "every surviving divider is a major");

        assertTrue(dividers(emit(Allegiance.ENEMY, 1_000_000f, 1_000_000f)).isEmpty(),
                "an absurd pool drops both tiers instead of drawing a solid smear");
    }

    @Test
    void eachAllegianceIsCodedOnHueThicknessAndWidth() {
        Set<String> hues = new HashSet<>();
        for (Allegiance owner : Allegiance.values()) {
            Rect fill = emit(owner, 40f, 40f).get(2);
            hues.add(fill.r() + "/" + fill.g() + "/" + fill.b());
        }
        assertEquals(Allegiance.values().length, hues.size(),
                "no two allegiances share a structure hue");

        assertTrue(DurabilityBarDecor.height(Allegiance.PLAYER)
                        > DurabilityBarDecor.height(Allegiance.ENEMY),
                "the player's own bar is the thickest thing on the field");
        assertTrue(DurabilityBarDecor.height(Allegiance.NEUTRAL)
                        < DurabilityBarDecor.height(Allegiance.ENEMY),
                "non-combatants read quieter than combatants");
        assertTrue(plate(emit(Allegiance.NEUTRAL, 40f, 40f)).width()
                        < plate(emit(Allegiance.ENEMY, 40f, 40f)).width(),
                "neutral bars are narrower so they do not compete with the fight");
    }

    @Test
    void theKeelSeparatesFriendFromFoeWithoutRelyingOnHue() {
        assertEquals(1, keelRects(emit(Allegiance.PLAYER, 40f, 40f)).size(),
                "the player's keel is one unbroken underline");
        assertEquals(3, keelRects(emit(Allegiance.ALLY, 40f, 40f)).size(),
                "an ally's keel is broken into dashes");
        assertTrue(keelRects(emit(Allegiance.ENEMY, 40f, 40f)).isEmpty(),
                "hostile bars carry no keel");
        assertTrue(keelRects(emit(Allegiance.NEUTRAL, 40f, 40f)).isEmpty(),
                "neutral bars carry no keel");

        Rect playerKeel = keelRects(emit(Allegiance.PLAYER, 40f, 40f)).get(0);
        assertEquals(plate(emit(Allegiance.PLAYER, 40f, 40f)).width(), playerKeel.width(), 1e-4f,
                "the solid keel runs the full plate width");
    }

    /** Rects sharing the plate's bottom row, minus the plate itself. */
    private static List<Rect> keelRects(List<Rect> emitted) {
        return emitted.stream()
                .skip(1)
                .filter(rect -> rect.y0() == BASE_Y)
                .toList();
    }

    @Test
    void emplacementsWithholdTheirBarUntilTheyHaveTakenFire() {
        assertTrue(RenderAppearance.of(UnitType.TURRET).barsOnlyWhenUnderFire,
                "an untouched turret line reads as scenery");
        assertFalse(RenderAppearance.of(UnitType.MARINE).barsOnlyWhenUnderFire,
                "a marine's bar is always up");
        assertFalse(RenderAppearance.of(UnitType.HEAVY_MECH).barsOnlyWhenUnderFire);
    }

    @Test
    void callerAlphaScalesEveryRectAndADegenerateBarEmitsNothing() {
        List<Rect> solid = emit(Allegiance.PLAYER, 60f, 100f, 40f, 80f);
        DrawList half = new DrawList();
        DurabilityBarDecor.emit(half, RenderLayer.UNITS, Allegiance.PLAYER,
                CX, BASE_Y, WIDTH, 60f, 100f, 40f, 80f, 0.5f);
        List<Rect> halfAlpha = rects(half);
        assertEquals(solid.size(), halfAlpha.size());
        for (int i = 0; i < halfAlpha.size(); i++) {
            assertEquals(solid.get(i).a() * 0.5f, halfAlpha.get(i).a(), 1e-4f,
                    "the caller's fade multiplies every rect");
        }

        DrawList noWidth = new DrawList();
        DurabilityBarDecor.emit(noWidth, RenderLayer.UNITS, Allegiance.PLAYER,
                CX, BASE_Y, 0f, 10f, 10f, 1f);
        assertEquals(0, noWidth.count(RenderLayer.UNITS),
                "a bar with no width emits nothing at all");

        DrawList noPool = new DrawList();
        DurabilityBarDecor.emit(noPool, RenderLayer.UNITS, Allegiance.PLAYER,
                CX, BASE_Y, WIDTH, 0f, 0f, 1f);
        assertEquals(0, noPool.count(RenderLayer.UNITS),
                "an entity with no authored structure has nothing to gauge");
    }

    @Test
    void barHeightsAreScreenPixelsSoZoomDoesNotShrinkThem() {
        DrawList near = new DrawList();
        DurabilityBarDecor.emit(near, RenderLayer.UNITS, Allegiance.PLAYER,
                CX, BASE_Y, 96f, 20f, 40f, 1f);
        DrawList far = new DrawList();
        DurabilityBarDecor.emit(far, RenderLayer.UNITS, Allegiance.PLAYER,
                CX, BASE_Y, 12f, 20f, 40f, 1f);
        Rect nearPlate = plate(rects(near));
        Rect farPlate = plate(rects(far));
        assertEquals(nearPlate.y1() - nearPlate.y0(), farPlate.y1() - farPlate.y0(), 1e-4f);
        assertTrue(nearPlate.width() > farPlate.width());
    }
}
