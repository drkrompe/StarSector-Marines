package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.dillon.starsectormarines.campaign.personnel.CaptainDiscoverySalvageListener;
import com.dillon.starsectormarines.marine.CaptainCandidate;
import com.dillon.starsectormarines.marine.CaptainCandidateState;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Trait;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.Misc.TokenType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptainDiscoveryCMDTest {

    @Test
    void showPresentsFrozenCandidateAndAllResolutionChoices() {
        Fixture fixture = new Fixture();

        assertTrue(fixture.execute("show"));

        assertTrue(fixture.paragraphs.stream().anyMatch(text -> text.contains("cryo-pod")));
        assertTrue(fixture.paragraphs.stream().anyMatch(text -> text.contains("Mara Venn")));
        assertEquals(List.of("graphics/portraits/portrait_mercenary01.png"),
                fixture.images);
        assertEquals("Offer Mara Venn a commission",
                fixture.options.get(CaptainDiscoveryCMD.OPTION_ACCEPT));
        assertTrue(fixture.options.containsKey(CaptainDiscoveryCMD.OPTION_DECLINE));
        assertTrue(fixture.options.containsKey(CaptainDiscoveryCMD.OPTION_LATER));
        assertFalse(fixture.disabled.contains(CaptainDiscoveryCMD.OPTION_ACCEPT));
    }

    @Test
    void fullRosterDisablesAndRejectsAcceptanceWithoutConsumingOffer() {
        Fixture fixture = new Fixture();
        fixture.roster.setCapacity(1);
        fixture.roster.add(new MarineCaptain("Incumbent", null, Rank.LIEUTENANT, 0f));

        assertTrue(fixture.execute("show"));
        assertTrue(fixture.disabled.contains(CaptainDiscoveryCMD.OPTION_ACCEPT));
        assertTrue(fixture.tooltips.get(CaptainDiscoveryCMD.OPTION_ACCEPT)
                .contains("roster is full"));

        assertTrue(fixture.execute("accept"));
        assertEquals(1, fixture.roster.size());
        assertEquals(CaptainCandidateState.AVAILABLE, fixture.candidate.state());
        assertTrue(fixture.paragraphs.stream()
                .anyMatch(text -> text.contains("Every captain berth is occupied")));
        assertTrue(fixture.memoryValues.containsKey(
                CaptainDiscoverySalvageListener.PENDING_SOURCE_MEMORY_KEY));
    }

    @Test
    void acceptanceAddsCaptainOnceThenDoneClearsContinuationMemory() {
        Fixture fixture = new Fixture();

        assertTrue(fixture.execute("accept"));
        assertEquals(1, fixture.roster.size());
        assertEquals(CaptainCandidateState.ACCEPTED, fixture.candidate.state());
        assertEquals(List.of(CaptainDiscoveryCMD.OPTION_DONE),
                new ArrayList<>(fixture.options.keySet()));
        assertFalse(fixture.dismissed);

        assertTrue(fixture.execute("done"));
        assertTrue(fixture.dismissed);
        assertNull(fixture.memoryValues.get(
                CaptainDiscoverySalvageListener.PENDING_SOURCE_MEMORY_KEY));
        assertNull(fixture.memoryValues.get(
                CaptainDiscoverySalvageListener.KEEP_SALVAGE_DIALOG_MEMORY_KEY));
    }

    @Test
    void declineResolvesWithoutAddingCaptain() {
        Fixture fixture = new Fixture();

        assertTrue(fixture.execute("decline"));

        assertEquals(0, fixture.roster.size());
        assertEquals(CaptainCandidateState.DECLINED, fixture.candidate.state());
        assertTrue(fixture.paragraphs.stream().anyMatch(text -> text.contains("rescue service")));
        assertEquals(List.of(CaptainDiscoveryCMD.OPTION_DONE),
                new ArrayList<>(fixture.options.keySet()));
    }

    @Test
    void decideLaterKeepsCandidateAvailableAndDismissesCleanly() {
        Fixture fixture = new Fixture();

        assertTrue(fixture.execute("later"));

        assertEquals(CaptainCandidateState.AVAILABLE, fixture.candidate.state());
        assertEquals(0, fixture.roster.size());
        assertTrue(fixture.dismissed);
        assertTrue(fixture.memoryValues.isEmpty());
    }

    @Test
    void staleResolvedOptionCannotDuplicateAcceptedCaptain() {
        Fixture fixture = new Fixture();
        fixture.roster.acceptCaptainCandidate(fixture.candidate.sourceKey());

        assertFalse(fixture.execute("accept"));

        assertEquals(1, fixture.roster.size());
        assertTrue(fixture.dismissed);
        assertTrue(fixture.memoryValues.isEmpty());
    }

    private static final class Fixture {
        final MarineRoster roster = new MarineRoster();
        final CaptainCandidate candidate = roster.discoverCaptainCandidate(
                "derelict:test-wreck", "Mara Venn",
                "graphics/portraits/portrait_mercenary01.png",
                Rank.CAPTAIN, Trait.FIELD_MEDIC, 61f);
        final Map<String, Object> memoryValues = new HashMap<>();
        final Map<Object, String> options = new LinkedHashMap<>();
        final Map<Object, String> tooltips = new HashMap<>();
        final List<Object> disabled = new ArrayList<>();
        final List<String> paragraphs = new ArrayList<>();
        final List<String> images = new ArrayList<>();
        final MemoryAPI memory;
        final InteractionDialogAPI dialog;
        final CaptainDiscoveryCMD command;
        boolean dismissed;

        Fixture() {
            memoryValues.put(CaptainDiscoverySalvageListener.PENDING_SOURCE_MEMORY_KEY,
                    candidate.sourceKey());
            memoryValues.put(CaptainDiscoverySalvageListener.KEEP_SALVAGE_DIALOG_MEMORY_KEY,
                    true);
            memory = proxy(MemoryAPI.class, (method, args) -> switch (method.getName()) {
                case "contains" -> memoryValues.containsKey((String) args[0]);
                case "get", "getString" -> memoryValues.get((String) args[0]);
                case "set" -> {
                    memoryValues.put((String) args[0], args[1]);
                    yield null;
                }
                case "unset" -> memoryValues.remove((String) args[0]);
                default -> defaultValue(method.getReturnType());
            });
            OptionPanelAPI optionPanel = proxy(OptionPanelAPI.class,
                    (method, args) -> optionCall(method, args));
            TextPanelAPI textPanel = proxy(TextPanelAPI.class,
                    (method, args) -> textCall(method, args));
            dialog = proxy(InteractionDialogAPI.class, (method, args) -> switch (method.getName()) {
                case "getOptionPanel" -> optionPanel;
                case "getTextPanel" -> textPanel;
                case "dismiss" -> {
                    dismissed = true;
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            });
            command = new CaptainDiscoveryCMD(() -> roster);
        }

        boolean execute(String action) {
            Token token = new Token(action, TokenType.LITERAL);
            return command.execute("test", dialog, List.of(token),
                    Map.of(MemKeys.ENTITY, memory));
        }

        private Object optionCall(Method method, Object[] args) {
            return switch (method.getName()) {
                case "clearOptions" -> {
                    options.clear();
                    disabled.clear();
                    tooltips.clear();
                    yield null;
                }
                case "addOption" -> {
                    options.put(args[1], (String) args[0]);
                    yield null;
                }
                case "setEnabled" -> {
                    if (!((Boolean) args[1])) disabled.add(args[0]);
                    yield null;
                }
                case "setTooltip" -> tooltips.put(args[0], (String) args[1]);
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object textCall(Method method, Object[] args) {
            return switch (method.getName()) {
                case "addParagraph", "addPara" -> {
                    paragraphs.add((String) args[0]);
                    yield null;
                }
                case "addImage" -> {
                    images.add((String) args[0]);
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            };
        }
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
