package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BarracksEquipmentIssueTest {
    private final MarineRoster roster = new MarineRoster();

    private List<MarineSquad> squads(int count) {
        roster.bootstrapInitialComplement(count * MarineSquad.CAPACITY);
        return roster.squads().stream().filter(s -> !s.reserve()).toList();
    }

    private void station(MarineSquad squad) {
        var captain = new MarineCaptain("Station commander", null, Rank.CAPTAIN, 0f);
        roster.add(captain);
        assertTrue(roster.bindStationing(1001L, captain.id(), List.of(squad.id())));
    }

    private SquadProfile profile() {
        return roster.armory().saveSquadProfile("Security", SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
    }

    @Test void assigningIntentPreservesIssuedKitAndRejectsStationedSelectionAtomically() {
        var squads = squads(2);
        var first = squads.get(0);
        String weapons = first.weaponDoctrineId();
        String armor = roster.soldierById(first.memberIds().get(0)).armorId();
        roster.applySoldierOutcome(Map.of(first.memberIds().get(0), MarineSoldierStatus.WIA), 10f, 2f);
        var profile = profile();
        assertTrue(roster.assignSquadProfile(Set.of(first.id()), profile.id()));
        assertEquals(weapons, first.weaponDoctrineId());
        assertEquals(armor, roster.soldierById(first.memberIds().get(0)).armorId());
        station(squads.get(1));
        var another = profile();
        assertFalse(roster.assignSquadProfile(Set.of(first.id(), squads.get(1).id()), another.id()));
        assertEquals(profile.id(), first.squadProfileId());
        assertNull(squads.get(1).squadProfileId());
    }

    @Test void combinedBillIsPaidOnceAndShortageCannotPartiallyIssue() {
        var squads = squads(2);
        var ids = Set.of(squads.get(0).id(), squads.get(1).id());
        var profile = profile();
        roster.assignSquadProfile(ids, profile.id());
        var cargo = new Cargo();
        var issue = new BarracksEquipmentIssue(roster, cargo);
        var bill = issue.preview(ids).cost();
        assertFalse(bill.isZero());
        cargo.available = roster.previewSquadEquipment(squads.get(0).id(), profile.weaponDoctrineId(),
                profile.armorDoctrineId(), EquipmentIssueResources.UNLIMITED).issueCost();
        var original = squads.stream().map(MarineSquad::armorDoctrineId).toList();
        assertFalse(issue.apply(ids).succeeded());
        assertEquals(0, cargo.payments);
        assertEquals(original, squads.stream().map(MarineSquad::armorDoctrineId).toList());
        cargo.available = bill;
        cargo.accept = false;
        assertFalse(issue.apply(ids).succeeded());
        assertEquals(original, squads.stream().map(MarineSquad::armorDoctrineId).toList());
        cargo.accept = true;
        assertEquals(new BarracksEquipmentIssue.Result(2, 0, true), issue.apply(ids));
        assertEquals(1, cargo.payments);
        assertEquals(bill, cargo.paid);
        assertTrue(issue.preview(ids).cost().isZero());
        assertTrue(squads.stream().allMatch(s -> profile.id().equals(s.squadProfileId())));
        assertTrue(issue.apply(ids).succeeded());
        assertEquals(1, cargo.payments, "conforming equipment incurs no further charge");
    }

    @Test void recoveringSquadsReceiveKitWhileStationedAndStaleSelectionsRemainBlocked() {
        var squads = squads(3);
        var ids = Set.of(squads.get(0).id(), squads.get(1).id(), squads.get(2).id());
        roster.assignSquadProfile(ids, profile().id());
        String wounded = squads.get(1).memberIds().get(0);
        roster.applySoldierOutcome(Map.of(wounded, MarineSoldierStatus.WIA), 10f, 2f);
        station(squads.get(2));
        var issue = new BarracksEquipmentIssue(roster, EquipmentIssueResources.UNLIMITED);
        assertFalse(issue.apply(Set.of(squads.get(0).id(), "missing")).succeeded());
        assertEquals(new BarracksEquipmentIssue.Result(2, 1, true), issue.apply(ids));
        assertEquals(MarineSoldierStatus.WIA, roster.soldierById(wounded).status());
        assertTrue(squads.get(1).memberIds().contains(wounded));
    }

    @Test void profileAndAssignmentSurviveSaveAndOlderArmoriesStartEmpty() throws Exception {
        var squad = squads(1).get(0);
        var profile = profile();
        roster.assignSquadProfile(Set.of(squad.id()), profile.id());
        MarineRoster loaded = roundTrip(roster);
        assertEquals(profile.id(), loaded.squadById(squad.id()).squadProfileId());
        assertEquals(profile.weaponDoctrineId(), loaded.armory().squadProfile(profile.id()).weaponDoctrineId());
        Field field = MarineArmory.class.getDeclaredField("squadProfiles");
        field.setAccessible(true);
        field.set(roster.armory(), null);
        assertTrue(roundTrip(roster).armory().squadProfiles().isEmpty());
    }

    @Test void profileProtectsItsLoadoutAndDifferentManualIssueClearsItsAssignment() {
        var squad = squads(1).get(0);
        var clone = roster.armory().cloneWeaponDoctrine(SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS);
        var profile = roster.armory().saveSquadProfile("Custom security", clone.id(), SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
        assertNotNull(profile);
        assertFalse(roster.deleteWeaponDoctrine(clone.id()));
        roster.assignSquadProfile(Set.of(squad.id()), profile.id());
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(squad.id(),
                SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS, SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR));
        assertNull(squad.squadProfileId());
    }

    @SuppressWarnings("unchecked") private static <T> T roundTrip(T object) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(object); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return (T) in.readObject(); }
    }

    private static final class Cargo implements EquipmentIssueResources {
        EquipmentTemplateCost available = EquipmentIssueResources.UNLIMITED.available();
        EquipmentTemplateCost paid = EquipmentTemplateCost.ZERO;
        int payments;
        boolean accept = true;
        public EquipmentTemplateCost available() { return available; }
        public boolean spend(EquipmentTemplateCost cost) {
            if (!accept || !canAfford(cost)) return false;
            payments++; paid = paid.plus(cost);
            available = new EquipmentTemplateCost(available.supplies() - cost.supplies(),
                    available.heavyArmaments() - cost.heavyArmaments(),
                    available.heavyMachinery() - cost.heavyMachinery(), available.food() - cost.food());
            return true;
        }
    }
}
