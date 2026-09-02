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
 * Pins the durability bar's geometry contract, its per-capacity notch scales, and its
 * ownership coding. No GL and no simulation — {@link DurabilityBarDecor} emits into
 * a plain {@link DrawList}, so the emitted {@code SOLID_RECT}s are the whole
 * observable surface.
 *
 * <p>Assertions are structural (which rows a capacity occupies, how far its fill runs
 * against its own maximum, how many dividers survive) rather than a pinned palette:
 * the design owns the exact hues, but "each capacity fills its own row on its own
 * scale" and "the four allegiances stay distinguishable" are contracts a future
 * restyle must keep.
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

    /** The structure row's drained track — the first rect after the plate. */
    private static Rect structureTrack(List<Rect> emitted) {
        return emitted.get(1);
    }

    /** Every rect sharing the given track's rows, the track included. */
    private static List<Rect> rowOf(List<Rect> emitted, Rect track) {
        return emitted.stream()
                .filter(rect -> rect.y0() >= track.y0() && rect.y1() <= track.y1())
                .toList();
    }

    /** The armor row's drained track — the first rect painted above the structure row. */
    private static Rect armorTrack(List<Rect> emitted) {
        float structureTop = structureTrack(emitted).y1();
        return emitted.stream()
                .filter(rect -> rect.y0() > structureTop)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no armor row was painted"));
    }

    /**
     * Notch dividers are the only rects repainting the plate color at the divider's
     * own opacity, so color identifies them without pinning an index.
     */
    private static List<Rect> dividers(List<Rect> emitted, Rect track) {
        Rect plate = plate(emitted);
        return rowOf(emitted, track).stream()
                .filter(rect -> rect.sameColorAs(plate) && rect.a() != plate.a())
                .toList();
    }

    @Test
    void factionsResolveToTheFourOwnershipReadings() {
        assertEquals(Allegiance.PLAYER, Allegiance.of(Faction.MARINE));
        assertEquals(Allegiance.ENEMY, Allegiance.of(Faction.DEFENDER));
        assertEquals(Allegiance.NEUTRAL, Allegiance.of(Faction.CIVILIAN));
        assertEquals(Allegiance.ALLY, Allegiance.of(Faction.ALLY),
                "a friendly non-player side is the ally reading, not the enemy "
                        + "default — this is the one mapping that mis-styles "
                        + "silently rather than failing loud");
        assertEquals(Allegiance.ENEMY, Allegiance.of(null),
                "an unknown side must never style as friendly");
        assertTrue(Allegiance.PLAYER.friendly());
        assertTrue(Allegiance.ALLY.friendly());
        assertFalse(Allegiance.ENEMY.friendly());
        assertFalse(Allegiance.NEUTRAL.friendly());
    }

    @Test
    void everyAllegianceEmitsAPlateOfItsDeclaredHeightCenteredOnTheCaller() {
        for (Allegiance owner : Allegiance.values()) {
            Rect plate = plate(emit(owner, 20f, 40f));
            assertEquals(BASE_Y, plate.y0(), 1e-4f, "plate bottom sits on baseY for " + owner);
            assertEquals(BASE_Y + DurabilityBarDecor.height(owner, false), plate.y1(), 1e-4f,
                    "plate height matches the advertised height for " + owner);
            assertEquals(CX, (plate.x0() + plate.x1()) / 2f, 1e-4f,
                    "plate is centered on cx for " + owner);
        }
    }

    @Test
    void armorTakesItsOwnRowAboveStructureWithAGridlineBetween() {
        for (Allegiance owner : Allegiance.values()) {
            assertTrue(DurabilityBarDecor.height(owner, true)
                            > DurabilityBarDecor.height(owner, false),
                    "the armor row adds height for " + owner);

            List<Rect> bar = emit(owner, TURRET_STRUCTURE, TURRET_STRUCTURE,
                    TURRET_ARMOR, TURRET_ARMOR);
            Rect structure = structureTrack(bar);
            Rect armor = armorTrack(bar);
            assertTrue(armor.y0() > structure.y1(),
                    "the rows do not touch — a gridline separates them for " + owner);
            assertEquals(structure.x0(), armor.x0(), 1e-4f, "both rows span the same width");
            assertEquals(structure.x1(), armor.x1(), 1e-4f, "both rows span the same width");
            assertEquals(BASE_Y + DurabilityBarDecor.height(owner, true),
                    plate(bar).y1(), 1e-4f);
        }
    }

    @Test
    void eachRowFillsAgainstItsOwnCapacityRatherThanACombinedTotal() {
        // Armor half spent, hull untouched: the structure row must still read full.
        List<Rect> bar = emit(Allegiance.ENEMY, TURRET_STRUCTURE, TURRET_STRUCTURE,
                TURRET_ARMOR / 2f, TURRET_ARMOR);
        Rect structureTrack = structureTrack(bar);
        Rect structureFill = bar.get(2);
        Rect armorTrack = armorTrack(bar);
        Rect armorFill = rowOf(bar, armorTrack).get(1);

        assertEquals(structureTrack.width(), structureFill.width(), 1e-3f,
                "an untouched hull reads full however much armor is gone");
        assertEquals(armorTrack.width() / 2f, armorFill.width(), 1e-3f,
                "the armor row measures armor against armor");
        assertFalse(structureFill.sameColorAs(armorFill),
                "the two materials stay tellable apart between rows");
    }

    @Test
    void theTwoCapacitiesAreNotchedOnTheirOwnScales() {
        // 100 structure at 25 a notch is cut three times; 100 armor at 50 once.
        List<Rect> bar = emit(Allegiance.ENEMY, 100f, 100f, 100f, 100f);
        assertEquals(3, dividers(bar, structureTrack(bar)).size(),
                "structure notches are the finer scale");
        assertEquals(1, dividers(bar, armorTrack(bar)).size(),
                "armor notches are the coarser scale, because armor capacities run larger");

        // Every fifth divider is promoted, so a 250-point structure row shows eight
        // minors plus one major.
        List<Rect> emplacement = emit(Allegiance.ENEMY, 250f, 250f);
        Rect track = structureTrack(emplacement);
        List<Rect> notches = dividers(emplacement, track);
        assertEquals(9, notches.size());
        assertEquals(1, notches.stream().filter(n -> n.y0() == track.y0()).count(),
                "one full-height divider every five notches");
    }

    @Test
    void aDividerTierTooDenseToReadIsDroppedWholeRatherThanSmeared() {
        // A Bulwark's 550 structure cannot show 21 minors in 40px, but its majors
        // still fit — density becomes the magnitude cue.
        List<Rect> mech = emit(Allegiance.PLAYER, 550f, 550f, 950f, 950f);
        Rect track = structureTrack(mech);
        List<Rect> notches = dividers(mech, track);
        assertEquals(4, notches.size(), "only the major tier survives at this density");
        assertTrue(notches.stream().allMatch(n -> n.y0() == track.y0()),
                "every surviving divider is a major");

        List<Rect> absurd = emit(Allegiance.ENEMY, 1_000_000f, 1_000_000f);
        assertTrue(dividers(absurd, structureTrack(absurd)).isEmpty(),
                "an absurd capacity drops both tiers instead of drawing a solid smear");
    }

    @Test
    void aSurvivingSliverStaysVisibleAndAnEmptyCapacityKeepsItsTrack() {
        List<Rect> dead = emit(Allegiance.ENEMY, 0f, 100f);
        List<Rect> sliver = emit(Allegiance.ENEMY, 0.05f, 100f);
        assertEquals(dead.size() + 1, sliver.size(),
                "an empty capacity paints its track and notches but no fill");
        assertTrue(sliver.get(2).width() >= 1f,
                "a nearly-dead unit still shows at least one pixel of structure");

        List<Rect> broken = emit(Allegiance.ENEMY, TURRET_STRUCTURE, TURRET_STRUCTURE,
                0f, TURRET_ARMOR);
        Rect armorTrack = armorTrack(broken);
        assertEquals(armorTrack.width(), armorTrack.x1() - armorTrack.x0(), 1e-4f);
        assertTrue(rowOf(broken, armorTrack).stream()
                        .noneMatch(rect -> rect.sameColorAs(broken.get(2))),
                "a stripped armor row keeps its empty notches and paints no fill");
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

        assertTrue(DurabilityBarDecor.height(Allegiance.PLAYER, false)
                        > DurabilityBarDecor.height(Allegiance.ENEMY, false),
                "the player's own bar is the thickest thing on the field");
        assertTrue(DurabilityBarDecor.height(Allegiance.NEUTRAL, false)
                        < DurabilityBarDecor.height(Allegiance.ENEMY, false),
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

        DrawList noStructure = new DrawList();
        DurabilityBarDecor.emit(noStructure, RenderLayer.UNITS, Allegiance.PLAYER,
                CX, BASE_Y, WIDTH, 0f, 0f, 1f);
        assertEquals(0, noStructure.count(RenderLayer.UNITS),
                "an entity with no authored structure has nothing to gauge");

        assertEquals(emit(Allegiance.ENEMY, 40f, 40f).size(),
                emit(Allegiance.ENEMY, 40f, 40f, 0f, 0f).size(),
                "an authored-armorless entity gets no armor row");
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
