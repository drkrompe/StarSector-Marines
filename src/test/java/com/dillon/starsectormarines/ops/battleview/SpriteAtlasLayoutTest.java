package com.dillon.starsectormarines.ops.battleview;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one piece of layout arithmetic {@link GroundAtlasTest} does not already
 * cover: choosing how wide the atlas is.
 *
 * <p>A ground atlas holds six sheets whose sizes are stable, so one width was
 * enough for it. A unit atlas holds every image a body is composed from, and
 * that set grows with the art — a mech livery is sixteen more images and there
 * is one per faction. So {@link SpriteAtlas} is handed a ladder of widths and
 * takes the first that holds everything without becoming taller than it is
 * wide. Getting that wrong is silent in both directions: too small and the plan
 * fails and every body goes back to being its own texture bind, too large and
 * the battle pays for a texture it does not fill.
 */
class SpriteAtlasLayoutTest {

    /** A {@link SpriteAtlas} whose contents are stated rather than loaded. */
    private static final class Fixture extends SpriteAtlas {
        Fixture(int... sides) {
            super("test atlas", true, sides);
        }

        boolean layOut(List<Source> sources, int minimum) {
            return plan(sources, minimum);
        }
    }

    @Test
    void takesTheSmallestWidthThatHoldsEverything() {
        Fixture atlas = new Fixture(1024, 2048, 4096);
        // Sixty 200x200 plates: three rows of four hundred at 1024 wide is over
        // a thousand tall, which is taller than it is wide; at 2048 it is not.
        assertTrue(atlas.layOut(plates(60, 200, 200), 4));
        assertEquals(2048, atlas.width());
        assertTrue(atlas.height() <= 2048 && atlas.height() > 0,
                "an atlas no taller than it is wide; got " + atlas.height());
    }

    @Test
    void staysSmallWhenTheArtIsSmall() {
        Fixture atlas = new Fixture(1024, 2048, 4096);
        assertTrue(atlas.layOut(plates(8, 64, 64), 4));
        assertEquals(1024, atlas.width(),
                "eight thumbnails do not need a two-thousand-texel texture");
    }

    @Test
    void aSetTooLargeForEveryWidthLeavesEveryImageOnItsOwnPath() {
        Fixture atlas = new Fixture(1024);
        // One plate wider than the only width offered: nothing to lay out, and
        // the caller keeps drawing images one at a time.
        assertFalse(atlas.layOut(plates(2, 2000, 200), 1));
        assertFalse(atlas.isPlanned());
        assertNull(atlas.placement(plates(1, 8, 8).get(0).sheet()));
    }

    @Test
    void tooFewImagesIsNotWorthCompositing() {
        Fixture atlas = new Fixture(1024);
        assertFalse(atlas.layOut(plates(3, 64, 64), 4));
        assertFalse(atlas.isPlanned());
    }

    @Test
    void aPlacementReportsWhereAnImageWentAndHowBigItIs() {
        Fixture atlas = new Fixture(1024);
        List<SpriteAtlas.Source> sources = plates(6, 70, 130);
        assertTrue(atlas.layOut(sources, 4));
        for (SpriteAtlas.Source source : sources) {
            SpriteAtlas.Placement at = atlas.placement(source.sheet());
            assertNotNull(at, "every image offered has a slot");
            assertEquals(70, at.width());
            assertEquals(130, at.height());
            assertTrue(at.x() >= SpriteAtlas.GUTTER_PX && at.y() >= SpriteAtlas.GUTTER_PX,
                    "and it sits inside the gutter");
            assertEquals((at.x() << 16) | at.y(), atlas.origin(source.sheet()),
                    "the packed origin is the same slot the placement names");
        }
    }

    private static List<SpriteAtlas.Source> plates(int count, int w, int h) {
        List<SpriteAtlas.Source> sources = new ArrayList<>();
        for (int i = 0; i < count; i++) sources.add(new SpriteAtlas.Source(token(), w, h));
        return sources;
    }

    /** A distinct {@link SpriteAPI} identity; the layout never draws through it. */
    private static SpriteAPI token() {
        return (SpriteAPI) Proxy.newProxyInstance(
                SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "token";
                    default -> null;
                });
    }
}
