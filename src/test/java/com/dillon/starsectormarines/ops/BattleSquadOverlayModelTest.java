package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleSquadOverlayModelTest {

    @Test
    void playerMechSquadUsesItsDedicatedOverlay() {
        Squad infantry = new Squad(1, Faction.MARINE);
        infantry.aliveMembers = 4;
        Squad mechs = new Squad(2, Faction.MARINE);
        mechs.aliveMembers = 2;
        mechs.mechSquad = true;

        assertTrue(BattleSquadOverlayModel.isPlayerInfantrySquad(infantry));
        assertFalse(BattleSquadOverlayModel.isPlayerInfantrySquad(mechs));
    }

    @Test
    void mlxProjectsThreeFireTeamsAndHoveredLoadout() throws Exception {
        Reactor reactor = new Reactor();
        AtomicInteger backs = new AtomicInteger();
        BattleSquadOverlayModel model = new BattleSquadOverlayModel(
                reactor, backs::incrementAndGet);
        model.updateProjected(new BattleSquadOverlayModel.SquadState(
                "Squad 20", 11, 12, 0.72f, members()));

        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                Path.of("mod").resolve(path)), List.of(BattleSquadOverlay.COMPONENT_PATH));
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, BattleSquadOverlay.COMPONENT, model.props())) {
            UiDocument document = document(instance);

            assertEquals("SQUAD 20", instance.requireElement("battle-squad-title").text());
            assertEquals("11/12", instance.requireElement("battle-squad-strength").text());
            assertEquals(3, instance.requireElement("battle-squad-fireteams").childCount());
            for (int team = 0; team < 3; team++) {
                assertEquals(4, instance.requireElement(
                        "battle-squad-fireteam-" + team + "-members").childCount());
            }
            assertTrue(instance.requireElement("battle-squad-member-2-3")
                    .hasClass("member-empty"));
            var edgeCard = instance.requireElement("battle-squad-member-2-2");
            var edgeSpecial = instance.requireElement(
                    "battle-squad-member-2-2-special");
            assertTrue(edgeCard.box().contentBox().right()
                    - edgeSpecial.box().contentBox().right() >= 2f);
            assertTrue(instance.requireElement("battle-squad-tooltip")
                    .hasClass("squad-tooltip-hidden"));

            model.hover("battle-squad-member-0-0");
            instance.flush();
            document.advance(0f);
            assertFalse(instance.requireElement("battle-squad-tooltip")
                    .hasClass("squad-tooltip-hidden"));
            assertEquals("Arden Vale · SQUAD LEADER",
                    instance.requireElement("battle-squad-tooltip-title").text());
            assertEquals("PRIMARY · PLS-3 Lancer",
                    instance.requireElement("battle-squad-tooltip-primary").text());
            assertEquals("SPECIAL · Smoke Grenade · 2 LEFT",
                    instance.requireElement("battle-squad-tooltip-special").text());
            assertEquals("SUIT SYSTEM · Kestrel Sweep · READY",
                    instance.requireElement("battle-squad-tooltip-system").text());

            var back = instance.requireElement("battle-squad-back").box().borderBox();
            assertTrue(document.pointerDown(back.x() + back.width() * 0.5f,
                    back.y() + back.height() * 0.5f));
            assertTrue(document.pointerUp(back.x() + back.width() * 0.5f,
                    back.y() + back.height() * 0.5f));
            assertEquals(1, backs.get());
        }
    }

    private static UiDocument document(MarkupInstance instance) {
        BattleSquadOverlay.wireLayout(instance);
        UiDocument document = new UiDocument(instance.root());
        for (var style : instance.styles()) document.addStyleSheet(style);
        document.theme(MarineOpsThemes.standard());
        document.layout(BattleSquadOverlay.DOCUMENT_WIDTH,
                BattleSquadOverlay.DOCUMENT_HEIGHT);
        return document;
    }

    private static List<BattleSquadOverlayModel.MemberState> members() {
        List<BattleSquadOverlayModel.MemberState> members = new ArrayList<>();
        for (int team = 0; team < 3; team++) {
            int count = team == 2 ? 3 : 4;
            for (int slot = 0; slot < count; slot++) {
                boolean leader = team == 0 && slot == 0;
                members.add(new BattleSquadOverlayModel.MemberState(
                        team * 10L + slot + 1L, team, leader,
                        leader ? "Arden Vale" : "Marine " + (team * 4 + slot + 1),
                        leader ? 82f : 100f, 100f, 24f, 40f, 12f,
                        "PLS-3 Lancer", "RIF-III", new Color(0x78, 0xD4, 0x94),
                        leader ? "Smoke Grenade" : null,
                        leader ? "SMK" : null, leader ? "2 LEFT" : "",
                        leader ? "Kestrel Sweep" : null,
                        leader ? "READY" : "", "Veteran", "Gifted",
                        UnitRole.COMBATANT));
            }
        }
        return members;
    }
}
