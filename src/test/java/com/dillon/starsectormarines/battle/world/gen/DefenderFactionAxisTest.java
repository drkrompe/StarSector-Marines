package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.GroundRosterRegistry;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The defending faction is an axis of its own: it selects the ground roster and
 * nothing else, so swapping it is a controlled comparison rather than a reroll.
 * These pin both halves of that claim — the override reaches roster selection,
 * and it does not reach the map.
 */
class DefenderFactionAxisTest {

    /** One market's read, as {@code TargetProfileResolver} would hand it over. */
    private static final TargetProfile MARKET = new TargetProfile(6, 5, 3, 1, "hegemony",
            EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.MILITARY),
            SurfacePalette.ROCK);

    private static final int WIDTH = 128;
    private static final int HEIGHT = 96;
    private static final long SEED = 20260828L;
    private static final TraversalAxis AXIS = TraversalAxis.WEST_TO_EAST;

    @Test
    void noOverrideLeavesTheProfileExactlyAsTheMarketGaveIt() {
        assertSame(MARKET, MARKET.withFactionId(null),
                "a null override is the no-override case and must not rebuild the profile");
    }

    @Test
    void anOverrideReplacesTheFactionIdAndNothingElse() {
        TargetProfile swapped = MARKET.withFactionId("pirates");

        assertEquals("pirates", swapped.factionId());
        assertEquals(MARKET.marketSize(), swapped.marketSize());
        assertEquals(MARKET.stability(), swapped.stability());
        assertEquals(MARKET.defenseLevel(), swapped.defenseLevel());
        assertEquals(MARKET.spaceportTier(), swapped.spaceportTier());
        assertEquals(MARKET.functions(), swapped.functions());
        assertEquals("hegemony", MARKET.factionId(), "the source profile is immutable");
    }

    @Test
    void theOverriddenFactionIdSelectsThatFactionsGroundRoster() {
        assertEquals("roster.hegemony",
                GroundRosterRegistry.resolve(MARKET.factionId()).id());
        assertEquals("roster.pirates",
                GroundRosterRegistry.resolve(MARKET.withFactionId("pirates").factionId()).id());
    }

    @Test
    void swappingTheDefenderLeavesTheGeneratedMapIdentical() {
        BspCityGenerator generator = new BspCityGenerator();

        String asOwned = mapDigest(generator.generate(WIDTH, HEIGHT, SEED, AXIS, MARKET));
        String asSwapped = mapDigest(generator.generate(
                WIDTH, HEIGHT, SEED, AXIS, MARKET.withFactionId("pirates")));
        assertEquals(asOwned, asSwapped,
                "who defends must not reach map generation — the two launches have to be"
                        + " the same battlefield for the comparison to mean anything");

        // Control: the same digest, over the same seed, does discriminate — a
        // profile field generation genuinely reads moves it. Without this the
        // assertion above would also pass on a digest that saw nothing.
        String asFortified = mapDigest(generator.generate(WIDTH, HEIGHT, SEED, AXIS,
                new TargetProfile(MARKET.marketSize(), MARKET.stability(), 7,
                        MARKET.spaceportTier(), MARKET.factionId(), MARKET.functions(),
                        MARKET.surface())));
        assertNotEquals(asOwned, asFortified,
                "defense level drives the overwatch line, so this digest must move with it");
    }

    /**
     * A whole-map identity: every cell's passability, opacity and ground kind,
     * plus the spawns, the placed content counts, and each defense post with the
     * turret it mounts.
     */
    private static String mapDigest(MapResult map) {
        NavigationGrid grid = map.grid;
        CellTopology topology = map.topology;
        StringBuilder sb = new StringBuilder(grid.getWidth() * grid.getHeight() * 3 + 256);
        sb.append(grid.getWidth()).append('x').append(grid.getHeight())
                .append("|marine=").append(map.marineSpawnX).append(',').append(map.marineSpawnY)
                .append("|defender=").append(map.defenderSpawnX).append(',').append(map.defenderSpawnY);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                sb.append(grid.isWalkable(x, y) ? '.' : '#');
                sb.append(grid.blocksLineOfSight(x, y) ? 'o' : '-');
                CellTopology.GroundKind kind = topology.getGroundKind(x, y);
                sb.append(kind == null ? '?' : (char) ('A' + kind.ordinal()));
            }
        }
        sb.append("|poi=").append(map.pointsOfInterest.size())
                .append("|doodads=").append(map.doodads.size())
                .append("|pads=").append(map.landingPads.size());
        for (DefensePost post : map.defensePosts) {
            sb.append("|post=").append(post.tier)
                    .append('@').append(post.anchorX).append(',').append(post.anchorY);
            for (DefensePost.TurretSpec turret : post.turrets) {
                sb.append('/').append(turret.structureId);
            }
        }
        return sha256(sb.toString());
    }

    private static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
