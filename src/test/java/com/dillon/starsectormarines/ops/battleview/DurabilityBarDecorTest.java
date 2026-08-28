package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.DrawCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the durability bar's geometry contract and its ownership coding. No GL and
 * no simulation — {@link DurabilityBarDecor} emits into a plain {@link DrawList},
 * so the emitted {@code SOLID_RECT}s are the whole observable surface.
 *
 * <p>Assertions are structural (which band occupies which screen rows, how wide a
 * fill runs, whether an ownership channel differs) rather than a pinned palette:
 * the design owns the exact hues, but "the four allegiances are distinguishable"
 * and "armor sits above structure" are contracts a future restyle must keep.
 */
public class DurabilityBarDecorTest {

    private static final float CX = 100f;
    private static final float BASE_Y = 50f;
    private static final float WIDTH = 40f;

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

    private static List<Rect> emit(Allegiance owner, float hpFrac) {
        DrawList out = new DrawList();
        DurabilityBarDecor.emit(out, RenderLayer.UNITS, owner, CX, BASE_Y, WIDTH, hpFrac, 1f);
        return rects(out);
    }

    private static List<Rect> emitArmored(Allegiance owner, float hpFrac, float armorFrac) {
        DrawList out = new DrawList();
        DurabilityBarDecor.emit(out, RenderLayer.UNITS, owner, CX, BASE_Y, WIDTH,
                hpFrac, armorFrac, 1f);
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

    private static List<Rect> above(List<Rect> emitted, float y) {
        return emitted.stream().filter(rect -> rect.y0() >= y).toList();
    }

    @Test
    void factionsResolveToTheFourOwnershipReadings() {
        assertEquals(Allegiance.PLAYER, Allegiance.of(Faction.MARINE));
        assertEquals(Allegiance.ENEMY, Allegiance.of(Faction.DEFENDER));
        assertEquals(Allegiance.NEUTRAL, Allegiance.of(Faction.CIVILIAN));
        assertEquals(Allegiance.ENEMY, Allegiance.of(null),
                "an unknown side must never style as friendly");
        assertTrue(Allegiance.PLAYER.friendly());
        assertTrue(Allegiance.ALLY.friendly());
        assertFalse(Allegiance.NEUTRAL.friendly());
        assertFalse(Allegiance.ENEMY.friendly());
    }

    @Test
    void everyAllegianceEmitsAPlateOfItsDeclaredHeightCenteredOnTheCaller() {
        for (Allegiance owner : Allegiance.values()) {
            Rect plate = plate(emit(owner, 0.5f));
            assertEquals(BASE_Y, plate.y0(), 1e-4f, "plate bottom sits on baseY for " + owner);
            assertEquals(BASE_Y + DurabilityBarDecor.height(owner, false), plate.y1(), 1e-4f,
                    "plate height matches the advertised height for " + owner);
            assertEquals(CX, (plate.x0() + plate.x1()) / 2f, 1e-4f,
                    "plate is centered on cx for " + owner);
        }
    }

    @Test
    void armorAddsABandAboveStructureAndRaisesTheAdvertisedHeight() {
        for (Allegiance owner : Allegiance.values()) {
            float bare = DurabilityBarDecor.height(owner, false);
            float armored = DurabilityBarDecor.height(owner, true);
            assertTrue(armored > bare, "armor makes the bar taller for " + owner);

            List<Rect> withArmor = emitArmored(owner, 1f, 1f);
            List<Rect> withoutArmor = emit(owner, 1f);
            assertEquals(BASE_Y + armored, plate(withArmor).y1(), 1e-4f);
            assertTrue(withArmor.size() > withoutArmor.size(),
                    "the armor band is extra geometry for " + owner);

            // The structure band's top edge is the ceiling of the armorless bar's
            // interior; anything painted above it belongs to the armor band.
            float structureTop = plate(withoutArmor).y1() - 1f;
            assertFalse(above(withArmor, structureTop).isEmpty(),
                    "armor paints above structure for " + owner);
        }
    }

    @Test
    void structureFillTracksTheFractionAcrossTheBandInterior() {
        List<Rect> full = emit(Allegiance.PLAYER, 1f);
        List<Rect> half = emit(Allegiance.PLAYER, 0.5f);
        Rect fullTrack = full.get(1);
        Rect fullFill = full.get(2);
        Rect halfFill = half.get(2);

        assertEquals(fullTrack.width(), fullFill.width(), 1e-4f,
                "a full pool fills its whole band");
        assertEquals(fullTrack.width() / 2f, halfFill.width(), 1e-4f,
                "half a pool fills half the band");
        assertEquals(fullTrack.x0(), halfFill.x0(), 1e-4f, "fills are left-anchored");
        assertFalse(fullTrack.sameColorAs(fullFill),
                "the drained track is dimmer than the fill so loss is legible");
    }

    @Test
    void anEmptyPoolKeepsItsTrackAndASurvivingSliverStaysVisible() {
        List<Rect> dead = emit(Allegiance.ENEMY, 0f);
        List<Rect> sliver = emit(Allegiance.ENEMY, 0.001f);
        assertEquals(dead.size() + 1, sliver.size(),
                "an empty pool paints its track but no fill");
        assertTrue(sliver.get(2).width() >= 1f,
                "a nearly-dead unit still shows at least one pixel of structure");

        List<Rect> brokenArmor = emitArmored(Allegiance.ENEMY, 1f, 0f);
        assertTrue(brokenArmor.size() > emit(Allegiance.ENEMY, 1f).size(),
                "broken armor still paints its empty track — armor loss stays readable");
    }

    @Test
    void eachAllegianceIsCodedOnHueThicknessAndWidth() {
        Set<String> hues = new HashSet<>();
        for (Allegiance owner : Allegiance.values()) {
            Rect fill = emit(owner, 1f).get(2);
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
        assertTrue(plate(emit(Allegiance.NEUTRAL, 1f)).width()
                        < plate(emit(Allegiance.ENEMY, 1f)).width(),
                "neutral bars are narrower so they do not compete with the fight");
    }

    @Test
    void theKeelSeparatesFriendFromFoeWithoutRelyingOnHue() {
        // The keel repaints the plate's bottom frame row, so it is the only
        // geometry sharing the plate's bottom edge.
        assertEquals(1, keelRects(emit(Allegiance.PLAYER, 1f)).size(),
                "the player's keel is one unbroken underline");
        assertEquals(3, keelRects(emit(Allegiance.ALLY, 1f)).size(),
                "an ally's keel is broken into dashes");
        assertTrue(keelRects(emit(Allegiance.ENEMY, 1f)).isEmpty(),
                "hostile bars carry no keel");
        assertTrue(keelRects(emit(Allegiance.NEUTRAL, 1f)).isEmpty(),
                "neutral bars carry no keel");

        Rect playerKeel = keelRects(emit(Allegiance.PLAYER, 1f)).get(0);
        assertEquals(plate(emit(Allegiance.PLAYER, 1f)).width(), playerKeel.width(), 1e-4f,
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
    void callerAlphaScalesEveryRectAndADegenerateWidthEmitsNothing() {
        List<Rect> solid = emitArmored(Allegiance.PLAYER, 0.6f, 0.6f);

        DrawList half = new DrawList();
        DurabilityBarDecor.emit(half, RenderLayer.UNITS, Allegiance.PLAYER,
                CX, BASE_Y, WIDTH, 0.6f, 0.6f, 0.5f);
        List<Rect> halfAlpha = rects(half);
        for (int i = 0; i < halfAlpha.size(); i++) {
            assertEquals(solid.get(i).a() * 0.5f, halfAlpha.get(i).a(), 1e-4f,
                    "the caller's fade multiplies every rect");
        }

        DrawList empty = new DrawList();
        DurabilityBarDecor.emit(empty, RenderLayer.UNITS, Allegiance.PLAYER,
                CX, BASE_Y, 0f, 1f, 1f);
        assertEquals(0, empty.count(RenderLayer.UNITS),
                "a bar with no width emits nothing at all");
    }

    @Test
    void barHeightsAreScreenPixelsSoZoomDoesNotShrinkThem() {
        // Same allegiance, two very different world widths: the plate's height is
        // identical, only its span changes. This is what keeps a pulled-back camera
        // legible.
        DrawList near = new DrawList();
        DurabilityBarDecor.emit(near, RenderLayer.UNITS, Allegiance.PLAYER,
                CX, BASE_Y, 96f, 0.5f, 1f);
        DrawList far = new DrawList();
        DurabilityBarDecor.emit(far, RenderLayer.UNITS, Allegiance.PLAYER,
                CX, BASE_Y, 12f, 0.5f, 1f);
        Rect nearPlate = plate(rects(near));
        Rect farPlate = plate(rects(far));
        assertEquals(nearPlate.y1() - nearPlate.y0(), farPlate.y1() - farPlate.y0(), 1e-4f);
        assertNotEquals(nearPlate.width(), farPlate.width());
    }
}
