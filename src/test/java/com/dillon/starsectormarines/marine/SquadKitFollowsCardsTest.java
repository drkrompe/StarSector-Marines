package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A marine's kit is what the squad's cards issue the seat they hold, and nothing else. */
class SquadKitFollowsCardsTest {

    private static final String WEAPONS = SquadEquipmentDoctrines.LUDDIC_PATH_ASSAULT_WEAPONS;
    private static final String SHEET = SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR;

    @Test void aNewSquadHoldsTheStarterCards() {
        MarineSquad squad = new MarineRoster().createSquad();
        assertEquals(SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS, squad.weaponDoctrineId());
        assertEquals(SHEET, squad.armorDoctrineId());
    }

    @Test void aReplacementTakesItsSeatsKitWithoutAnIssueOrder() {
        MarineRoster roster = carded();
        MarineSquad squad = lineSquad(roster);
        roster.applySoldierOutcome(Map.of(roster.manningMemberIds(squad).get(1), MarineSoldierStatus.KIA), 0f, 0f);
        roster.recruitToSquad(roster.reserveSquad().id(), 1);
        roster.fillVacancyFromReserve(squad.id());
        assertWearsItsCards(roster, squad);
    }

    @Test void aCasualtyMovesEveryoneUpASeatAndTheKitFollowsTheSeat() {
        MarineRoster roster = carded();
        MarineSquad squad = lineSquad(roster);
        String movesUp = roster.manningMemberIds(squad).get(1);
        roster.applySoldierOutcome(Map.of(roster.manningMemberIds(squad).get(0), MarineSoldierStatus.KIA), 0f, 0f);
        assertEquals(movesUp, roster.manningMemberIds(squad).get(0));
        assertWearsItsCards(roster, squad);
    }

    @Test void aSavedProfileBecomesTheSquadsCardsOnLoad() throws Exception {
        MarineRoster roster = carded();
        MarineSquad squad = lineSquad(roster);
        roster.applySquadEquipment(squad.id(), SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS, SHEET);
        SquadProfile profile = new SquadProfile("profile:old", "Old intent", WEAPONS, SHEET);
        field(MarineArmory.class, "squadProfiles").set(roster.armory(), new ArrayList<>(List.of(profile)));
        field(MarineSquad.class, "squadProfileId").set(squad, profile.id());

        MarineRoster loaded = roundTrip(roster);
        MarineSquad migrated = loaded.squadById(squad.id());
        assertEquals(WEAPONS, migrated.weaponDoctrineId());
        assertNull(migrated.legacySquadProfileId());
        assertNull(loaded.armory().legacySquadProfile(profile.id()));
        assertWearsItsCards(loaded, migrated);
    }

    private static MarineRoster carded() {
        MarineRoster roster = new MarineRoster();
        EquipmentTestFixtures.learnServiceKit(roster.armory());
        MarineSquad squad = roster.createSquad();
        roster.recruitToSquad(squad.id(), MarineSquad.CAPACITY);
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(squad.id(), WEAPONS, SHEET));
        return roster;
    }

    private static MarineSquad lineSquad(MarineRoster roster) {
        return roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow();
    }

    private static void assertWearsItsCards(MarineRoster roster, MarineSquad squad) {
        SquadEquipmentPreview cards = roster.previewSquadEquipment(
                squad.id(), squad.weaponDoctrineId(), squad.armorDoctrineId());
        List<String> seats = roster.manningMemberIds(squad);
        for (int seat = 0; seat < seats.size(); seat++) {
            MarineSoldier soldier = roster.soldierById(seats.get(seat));
            SquadEquipmentBillet billet = cards.billet(seat);
            assertEquals(billet.primaryId(), soldier.primaryId(), "seat " + seat);
            assertEquals(billet.specialEquipmentId(), soldier.specialEquipmentId(), "seat " + seat);
            assertEquals(billet.armorId(), soldier.armorId(), "seat " + seat);
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
