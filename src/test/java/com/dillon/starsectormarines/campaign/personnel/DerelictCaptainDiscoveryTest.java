package com.dillon.starsectormarines.campaign.personnel;

import com.dillon.starsectormarines.marine.CaptainCandidate;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Trait;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerelictCaptainDiscoveryTest {

    @Test
    void eligibleWreckPublishesDeterministicFrozenProfile() {
        String entityId = findEntityId(true);
        SectorEntityToken wreck = wreck(entityId, Set.of(Tags.SALVAGEABLE));
        MarineRoster firstRoster = new MarineRoster();
        MarineRoster secondRoster = new MarineRoster();

        CaptainCandidate first = DerelictCaptainDiscovery.publish(firstRoster, wreck, 42f);
        CaptainCandidate second = DerelictCaptainDiscovery.publish(secondRoster, wreck, 77f);

        assertEquals("derelict:" + entityId, first.sourceKey());
        assertEquals(first.id(), second.id());
        assertEquals(first.name(), second.name());
        assertEquals(first.portraitSprite(), second.portraitSprite());
        assertEquals(first.startingRank(), second.startingRank());
        assertEquals(first.startingTrait(), second.startingTrait());
        assertEquals(42f, first.discoveredAtDay());
        assertEquals(77f, second.discoveredAtDay());
        assertTrue(Set.of(Rank.PRIVATE, Rank.CORPORAL, Rank.SERGEANT)
                .contains(first.startingRank()));
        assertTrue(first.startingTrait() == null
                || Set.of(Trait.FIELD_MEDIC, Trait.NATURAL_LEADER, Trait.SALVAGE_EXPERT)
                .contains(first.startingTrait()));
        assertTrue(first.portraitSprite().startsWith("graphics/portraits/portrait_mercenary"));
    }

    @Test
    void repeatedLootCallbackReturnsOriginalCandidateWithoutDuplication() {
        String entityId = findEntityId(true);
        SectorEntityToken wreck = wreck(entityId, Set.of(Tags.SALVAGEABLE));
        MarineRoster roster = new MarineRoster();

        CaptainCandidate first = DerelictCaptainDiscovery.publish(roster, wreck, 42f);
        CaptainCandidate replay = DerelictCaptainDiscovery.publish(roster, wreck, 99f);

        assertSame(first, replay);
        assertEquals(1, roster.captainCandidates().size());
        assertEquals(42f, replay.discoveredAtDay());
    }

    @Test
    void stableRarityRollSelectsRoughlyOneInEightSources() {
        int eligible = 0;
        for (int i = 0; i < 800; i++) {
            String source = DerelictCaptainDiscovery.sourceKey("wreck-" + i);
            boolean first = DerelictCaptainDiscovery.isEligibleSource(source);
            assertEquals(first, DerelictCaptainDiscovery.isEligibleSource(source));
            if (first) eligible++;
        }

        assertTrue(eligible >= 75 && eligible <= 125,
                "expected a stable one-in-eight-sized cohort, got " + eligible);
    }

    @Test
    void ineligibleOrNonWreckLootDoesNotPublishCandidate() {
        MarineRoster roster = new MarineRoster();
        String ineligibleId = findEntityId(false);
        String eligibleId = findEntityId(true);

        assertNull(DerelictCaptainDiscovery.publish(
                roster, wreck(ineligibleId, Set.of(Tags.SALVAGEABLE)), 1f));
        assertNull(DerelictCaptainDiscovery.publish(
                roster, entity(eligibleId, null, Set.of(Tags.SALVAGEABLE)), 1f));
        assertNull(DerelictCaptainDiscovery.publish(
                roster, wreck(eligibleId, Set.of()), 1f));
        assertNull(DerelictCaptainDiscovery.publish(roster, null, 1f));
        assertTrue(roster.captainCandidates().isEmpty());
    }

    @Test
    void missionTaggedWrecksAreExcluded() {
        String entityId = findEntityId(true);
        MarineRoster roster = new MarineRoster();

        assertFalse(DerelictCaptainDiscovery.isOrdinarySalvageableWreck(
                wreck(entityId, Set.of(Tags.SALVAGEABLE, Tags.MISSION_ITEM))));
        assertFalse(DerelictCaptainDiscovery.isOrdinarySalvageableWreck(
                wreck(entityId, Set.of(Tags.SALVAGEABLE, Tags.MISSION_LOCATION))));
        assertFalse(DerelictCaptainDiscovery.isOrdinarySalvageableWreck(
                wreck(entityId, Set.of(Tags.SALVAGEABLE, Tags.NOT_RANDOM_MISSION_TARGET))));
        assertTrue(roster.captainCandidates().isEmpty());
    }

    @Test
    void listenerObservesTargetWithoutTouchingVanillaLoot() {
        String entityId = findEntityId(true);
        SectorEntityToken wreck = wreck(entityId, Set.of(Tags.SALVAGEABLE));
        MarineRoster roster = new MarineRoster();
        AtomicInteger cargoCalls = new AtomicInteger();
        CargoAPI cargo = proxy(CargoAPI.class, (method, args) -> {
            cargoCalls.incrementAndGet();
            return defaultValue(method.getReturnType());
        });
        InteractionDialogAPI dialog = proxy(InteractionDialogAPI.class,
                (method, args) -> method.getName().equals("getInteractionTarget")
                        ? wreck : defaultValue(method.getReturnType()));
        CaptainDiscoverySalvageListener listener = new CaptainDiscoverySalvageListener(
                () -> roster, () -> 61f);

        listener.reportAboutToShowLootToPlayer(cargo, dialog);
        listener.reportAboutToShowLootToPlayer(cargo, dialog);

        assertEquals(0, cargoCalls.get());
        assertEquals(1, roster.captainCandidates().size());
        assertEquals(61f, roster.captainCandidates().get(0).discoveredAtDay());
    }

    private static String findEntityId(boolean eligible) {
        for (int i = 0; i < 10_000; i++) {
            String id = "test-wreck-" + i;
            if (DerelictCaptainDiscovery.isEligibleSource(
                    DerelictCaptainDiscovery.sourceKey(id)) == eligible) {
                return id;
            }
        }
        throw new AssertionError("no deterministic fixture found");
    }

    private static SectorEntityToken wreck(String id, Set<String> tags) {
        return entity(id, new DerelictShipEntityPlugin(), tags);
    }

    private static SectorEntityToken entity(
            String id, CustomCampaignEntityPlugin plugin, Set<String> tags) {
        Set<String> copiedTags = new HashSet<>(tags);
        return proxy(SectorEntityToken.class, (method, args) -> switch (method.getName()) {
            case "getId" -> id;
            case "getCustomPlugin" -> plugin;
            case "hasTag" -> copiedTags.contains((String) args[0]);
            case "getTags" -> copiedTags;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static <T> T proxy(Class<T> type, ProxyCall call) {
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (ignored, method, args) -> call.invoke(method, args));
        return type.cast(proxy);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    @FunctionalInterface
    private interface ProxyCall {
        Object invoke(Method method, Object[] args) throws Throwable;
    }
}
