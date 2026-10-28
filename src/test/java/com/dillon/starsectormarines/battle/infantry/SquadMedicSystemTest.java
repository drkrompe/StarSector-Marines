package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.BodyPart;
import com.dillon.starsectormarines.battle.combat.WoundService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The treatment rules on a squad of plain numbers: who the medic is, who they
 * pick, what a treatment costs and restores, and how it ends. The medicine is
 * built here rather than read from the shipped table, so these rules do not move
 * when the numbers are retuned.
 */
public class SquadMedicSystemTest {

    private static final float DT = 1f / 30f;
    private static final SquadMedicine RANK_ONE = new SquadMedicine(30f, 0.2f, 1f, 7.5f, 0.05f, 3f,
            2.5f, 1f, 1.5f, InfantryCohesion.COHESION_RADIUS, 8f, 0.7f, 0.5f, 2f);

    @Test
    public void theMedicIsTheFirstMiddleTeamSquadmateAndNeverTheLeader() {
        Ward ward = new Ward(1L);
        ward.add(1L, 0, 0f);
        ward.add(2L, 0, 1f);
        ward.add(3L, 1, 2f);
        ward.add(4L, 1, 3f);
        assertEquals(3L, SquadMedicSystem.designate(ward));

        Ward noMiddleTeam = new Ward(1L);
        noMiddleTeam.add(1L, 0, 0f);
        noMiddleTeam.add(2L, 2, 1f);
        assertEquals(2L, SquadMedicSystem.designate(noMiddleTeam));
    }

    @Test
    public void aHurtMedicAloneTreatsThemselvesWithoutAnApproach() {
        Ward ward = squad();
        ward.hp.put(3L, 5f);
        SquadMedic medic = medicFor(ward);
        assertEquals(3L, SquadMedicSystem.worstHurt(medic, ward));

        SquadMedicSystem.step(medic, ward, DT);
        assertEquals(SquadMedic.Phase.TREATING, medic.phase());
        assertEquals(3L, medic.patientId());
        runUntilIdle(medic, ward);
        assertEquals(25f, ward.hp.get(3L), 1e-4f);
        assertEquals(1, medic.completed());
    }

    @Test
    public void aWorseHurtSquadmateIsTreatedBeforeTheMedic() {
        Ward ward = squad();
        ward.hp.put(3L, 12f);
        ward.hp.put(4L, 5f);
        assertEquals(4L, SquadMedicSystem.worstHurt(medicFor(ward), ward));
    }

    @Test
    public void aMedicDoesNotTreatThemselvesWhileAnotherMedicHasThem() {
        Ward ward = squad();
        ward.hp.put(3L, 5f);
        SquadMedic kit = new SquadMedic(RANK_ONE, 2L);
        kit.patientId = 3L;
        ward.held.put(3L, kit);
        assertEquals(0L, SquadMedicSystem.worstHurt(medicFor(ward), ward));
    }

    @Test
    public void healthOutranksArmorAndTheWorstHurtWins() {
        Ward ward = squad();
        ward.armor.put(2L, 0f);
        ward.hp.put(4L, 15f);
        ward.hp.put(5L, 10f);
        SquadMedic medic = medicFor(ward);
        assertEquals(5L, SquadMedicSystem.worstHurt(medic, ward));

        ward.hp.put(4L, 25f);
        ward.hp.put(5L, 25f);
        assertEquals(2L, SquadMedicSystem.worstHurt(medic, ward),
                "with nobody below the health threshold, broken armor qualifies");
    }

    @Test
    public void aPatientOutsideTheLeashIsNotChosen() {
        Ward ward = squad();
        ward.hp.put(4L, 5f);
        ward.x.put(4L, 2f + RANK_ONE.leashCells() + 1f);
        assertEquals(0L, SquadMedicSystem.worstHurt(medicFor(ward), ward));
    }

    @Test
    public void aTreatmentPaysForEveryPointItRestoresAndRunsUntilThePatientIsWhole() {
        Ward ward = squad();
        ward.hp.put(4L, 5f);
        ward.armor.put(4L, 1f);
        SquadMedic medic = medicFor(ward);
        ward.x.put(4L, 2f + RANK_ONE.reachCells() * 0.5f);

        SquadMedicSystem.step(medic, ward, DT);
        assertEquals(SquadMedic.Phase.TREATING, medic.phase());
        assertEquals(4L, medic.patientId());

        int ticks = 1 + runUntilIdle(medic, ward);
        assertEquals(25f, ward.hp.get(4L), 1e-4f);
        assertEquals(5f, ward.armor.get(4L), 1e-4f);
        assertEquals(1, medic.completed());
        assertEquals(0, medic.interrupted());
        float spent = medic.healthRestored() + medic.armorRestored();
        assertEquals(24f, spent, 1e-3f);
        assertEquals(RANK_ONE.energyMax() - spent + RANK_ONE.energyRegenPerSecond() * ticks * DT,
                medic.energy(), 0.05f, "the pool regenerates throughout and pays one per point");
    }

    @Test
    public void belowTheStartGateTheMedicDoesNotSetOut() {
        Ward ward = squad();
        ward.hp.put(4L, 5f);
        ward.x.put(4L, 2.5f);
        SquadMedic medic = medicFor(ward);
        medic.energy = RANK_ONE.minStartEnergy() - 1f;
        SquadMedicSystem.step(medic, ward, DT);
        assertEquals(SquadMedic.Phase.IDLE, medic.phase());
        assertEquals(5f, ward.hp.get(4L), 1e-6f);
    }

    @Test
    public void atTheGateATreatmentRunsUntilThePoolIsEmptyAndHealingStopsThere() {
        Ward ward = squad();
        ward.hp.put(4L, 5f);
        ward.x.put(4L, 2.5f);
        SquadMedic medic = medicFor(ward);
        medic.energy = RANK_ONE.minStartEnergy();
        SquadMedicSystem.step(medic, ward, DT);
        assertEquals(SquadMedic.Phase.TREATING, medic.phase());
        runUntilIdle(medic, ward);
        assertEquals(1, medic.exhausted(), "the pool ran dry before the patient was whole");
        assertEquals(0, medic.completed());
        float healed = ward.hp.get(4L);
        assertTrue(healed > 5f + RANK_ONE.minStartEnergy() - 1e-3f && healed < 25f,
                "it ran past the gate on regeneration, to empty: " + healed);

        for (int i = 0; i < 30; i++) SquadMedicSystem.step(medic, ward, DT);
        assertEquals(SquadMedic.Phase.IDLE, medic.phase(), "an empty pool waits for the gate again");
        assertEquals(healed, ward.hp.get(4L), 1e-6f, "no healing from an empty pool");
    }

    @Test
    public void aTreatmentNeverRestoresMoreThanThePoolPays() {
        Ward ward = squad();
        ward.hp.put(4L, 1f);
        ward.armor.put(4L, 0f);
        ward.x.put(4L, 2.5f);
        SquadMedic medic = medicFor(ward);
        medic.energy = 10f;
        int ticks = 1 + runUntilIdle(medic, ward);
        float paidFor = 10f + RANK_ONE.energyRegenPerSecond() * ticks * DT;
        assertTrue(medic.healthRestored() + medic.armorRestored() <= paidFor + 1e-3f);
    }

    @Test
    public void aSmallPoolStillTopsOffANearlyWholePatient() {
        Ward ward = squad();
        ward.hp.put(4L, 23f);
        SquadMedic medic = medicFor(ward);
        medic.energy = 3f;
        assertTrue(SquadMedicSystem.mayStart(medic, ward, 4L), "the pool covers the whole wound");
        medic.energy = 1f;
        assertTrue(!SquadMedicSystem.mayStart(medic, ward, 4L), "and not when it does not");
        ward.hp.put(4L, 24.5f);
        medic.energy = RANK_ONE.energyMax();
        assertTrue(!SquadMedicSystem.mayStart(medic, ward, 4L), "a scratch under the wound floor");
    }

    @Test
    public void restorationIsClampedAndEndsWhenThePatientIsWhole() {
        Ward ward = squad();
        ward.armor.put(4L, 4.9f);
        ward.hp.put(4L, 17f);
        SquadMedic medic = medicFor(ward);
        ward.x.put(4L, 2.5f);
        runUntilIdle(medic, ward);
        assertEquals(25f, ward.hp.get(4L), 1e-4f);
        assertEquals(5f, ward.armor.get(4L), 1e-4f);
        assertEquals(1, medic.completed());
        assertTrue(medic.healthRestored() <= 8f + 1e-3f, "never more than the deficit");
    }

    @Test
    public void aPatientWhoLeavesReachEndsTheTreatmentButKeepsWhatLanded() {
        Ward ward = squad();
        ward.hp.put(4L, 5f);
        SquadMedic medic = medicFor(ward);
        ward.x.put(4L, 2.5f);
        for (int i = 0; i < 30; i++) SquadMedicSystem.step(medic, ward, DT);
        float partial = ward.hp.get(4L);
        assertTrue(partial > 5f);

        ward.x.put(4L, 2f + RANK_ONE.reachCells() + 1f);
        SquadMedicSystem.step(medic, ward, DT);
        assertEquals(SquadMedic.Phase.IDLE, medic.phase());
        assertEquals(1, medic.interrupted());
        assertEquals(partial, ward.hp.get(4L), 1e-5f);
        float spent = medic.healthRestored() + medic.armorRestored();
        assertEquals(partial - 5f, spent, 1e-4f, "only what landed was paid for");
    }

    @Test
    public void reachingZeroThenRestoringCountsOneReplating() {
        Ward ward = squad();
        ward.armor.put(4L, 0f);
        SquadMedic medic = medicFor(ward);
        ward.x.put(4L, 2.5f);
        runUntilIdle(medic, ward);
        assertEquals(1, medic.armorReplated());
        assertTrue(ward.armor.get(4L) > 0f);
    }

    @Test
    public void aMedicUnderHeavyFireDoesNotSetOut() {
        Ward ward = squad();
        ward.hp.put(4L, 5f);
        ward.pressure.put(3L, RANK_ONE.heavyFirePressure());
        SquadMedic medic = medicFor(ward);
        SquadMedicSystem.step(medic, ward, DT);
        assertEquals(SquadMedic.Phase.IDLE, medic.phase());
    }

    @Test
    public void anApproachThatNeverArrivesCostsNothing() {
        Ward ward = squad();
        ward.hp.put(4L, 5f);
        ward.x.put(4L, 8f);
        SquadMedic medic = medicFor(ward);
        SquadMedicSystem.step(medic, ward, DT);
        assertEquals(SquadMedic.Phase.APPROACHING, medic.phase());
        for (int i = 0; i < Math.round(RANK_ONE.approachSeconds() / DT) + 2; i++) {
            SquadMedicSystem.step(medic, ward, DT);
        }
        assertEquals(SquadMedic.Phase.IDLE, medic.phase());
        assertEquals(RANK_ONE.energyMax(), medic.energy(), 1e-6f);
        assertEquals(0, medic.started());
        assertTrue(medic.retryTimer > 0f, "the medic waits before setting out again");
    }

    @Test
    public void aDeadMedicIsReplacedAndTheirTreatmentCountsAsInterrupted() {
        Ward ward = squad();
        ward.hp.put(4L, 5f);
        SquadMedic medic = medicFor(ward);
        ward.x.put(4L, 2.5f);
        SquadMedicSystem.step(medic, ward, DT);
        assertEquals(SquadMedic.Phase.TREATING, medic.phase());

        ward.hp.put(3L, 0f);
        SquadMedicSystem.step(medic, ward, DT);
        assertEquals(1, medic.interrupted());
        assertEquals(4L, medic.medicId(), "the next middle-team marine takes the billet");
    }

    @Test
    public void aCompletedTreatmentDressesThePatientsWounds() {
        Ward ward = squad();
        ward.hp.put(4L, 5f);
        ward.wounds.wound(4L, BodyPart.LEGS);
        ward.wounds.wound(4L, BodyPart.ARMS);
        SquadMedic medic = medicFor(ward);
        ward.x.put(4L, 2.5f);
        runUntilIdle(medic, ward);
        assertEquals(1, medic.completed());
        assertEquals(0, ward.wounds.wounds(4L, BodyPart.LEGS));
        assertEquals(0, ward.wounds.wounds(4L, BodyPart.ARMS));
    }

    @Test
    public void anInterruptedTreatmentLeavesTheWounds() {
        Ward ward = squad();
        ward.hp.put(4L, 5f);
        ward.wounds.wound(4L, BodyPart.LEGS);
        SquadMedic medic = medicFor(ward);
        ward.x.put(4L, 2.5f);
        for (int i = 0; i < 30; i++) SquadMedicSystem.step(medic, ward, DT);
        ward.x.put(4L, 2f + RANK_ONE.reachCells() + 1f);
        SquadMedicSystem.step(medic, ward, DT);
        assertEquals(1, medic.interrupted());
        assertEquals(1, ward.wounds.wounds(4L, BodyPart.LEGS));
    }

    @Test
    public void aDeadKitMedicIsNotReplaced() {
        Ward ward = squad();
        SquadMedic kit = new SquadMedic(RANK_ONE, 2L);
        ward.hp.put(2L, 0f);
        SquadMedicSystem.step(kit, ward, DT);
        assertEquals(2L, kit.medicId(), "the kit dies with its wearer");
        assertEquals(SquadMedic.Phase.IDLE, kit.phase());
    }

    @Test
    public void twoMedicsDoNotSetOutForTheSamePatient() {
        Ward ward = squad();
        ward.hp.put(4L, 5f);
        ward.hp.put(5L, 10f);
        SquadMedic first = medicFor(ward);
        first.patientId = 4L;
        ward.held.put(4L, first);
        SquadMedic second = new SquadMedic(RANK_ONE, 2L);
        ward.x.put(2L, 6.5f);
        assertEquals(5L, SquadMedicSystem.worstHurt(second, ward),
                "the worst hurt is in the first medic's hands, so the second takes the next");
    }

    /** Leader 1 and marine 2 in team 0; 3 (the medic), 4 and 5 in team 1; all whole. */
    private static Ward squad() {
        Ward ward = new Ward(1L);
        ward.add(1L, 0, 0f);
        ward.add(2L, 0, 1f);
        ward.add(3L, 1, 2f);
        ward.add(4L, 1, 6f);
        ward.add(5L, 1, 7f);
        return ward;
    }

    private static SquadMedic medicFor(Ward ward) {
        SquadMedic medic = new SquadMedic(RANK_ONE);
        medic.medicId = SquadMedicSystem.designate(ward);
        return medic;
    }

    /** Steps until the medic is idle again; returns the steps taken. */
    private static int runUntilIdle(SquadMedic medic, Ward ward) {
        SquadMedicSystem.step(medic, ward, DT);
        int steps = 1;
        for (int i = 0; i < 1000 && medic.phase() != SquadMedic.Phase.IDLE; i++) {
            SquadMedicSystem.step(medic, ward, DT);
            steps++;
        }
        return steps;
    }

    /** Marines on one line: distance is the difference of their x. */
    private static final class Ward implements SquadMedicSystem.Ward {
        final long leader;
        final long[] order = new long[8];
        int count;
        final Map<Long, Integer> team = new HashMap<>();
        final Map<Long, Float> x = new HashMap<>();
        final Map<Long, Float> hp = new HashMap<>();
        final Map<Long, Float> armor = new HashMap<>();
        final Map<Long, Float> pressure = new HashMap<>();
        final WoundService wounds = new WoundService();
        /** Patients and the medic holding them. */
        final Map<Long, SquadMedic> held = new HashMap<>();

        Ward(long leader) { this.leader = leader; }

        void add(long id, int fireTeam, float position) {
            order[count++] = id;
            team.put(id, fireTeam);
            x.put(id, position);
            hp.put(id, 25f);
            armor.put(id, 5f);
        }

        @Override public int memberCount() { return count; }
        @Override public long memberAt(int index) { return order[index]; }
        @Override public long leader() { return leader; }
        @Override public int fireTeamIndex(long member) { return team.get(member); }
        @Override public boolean alive(long member) { return hp.getOrDefault(member, 0f) > 0f; }
        @Override public boolean mayAct(long member) { return true; }
        @Override public float incomingPressure(long member) { return pressure.getOrDefault(member, 0f); }
        @Override public float distance(long a, long b) { return Math.abs(x.get(a) - x.get(b)); }
        @Override public float hp(long member) { return hp.get(member); }
        @Override public float maxHp(long member) { return 25f; }
        @Override public void setHp(long member, float value) { hp.put(member, value); }
        @Override public float armor(long member) { return armor.get(member); }
        @Override public float maxArmor(long member) { return 5f; }
        @Override public void setArmor(long member, float value) { armor.put(member, value); }
        @Override public void dressWounds(long member) { wounds.dress(member); }
        @Override public boolean inOtherHands(SquadMedic self, long member) {
            SquadMedic holder = held.get(member);
            return holder != null && holder != self;
        }
    }
}
