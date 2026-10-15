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
 * pick, what a treatment costs and restores, and how it ends.
 */
public class SquadMedicSystemTest {

    private static final float DT = 1f / 30f;
    private static final SquadMedicine RANK_ONE = SquadMedicine.forRank(1);

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
    public void aHurtMedicAloneHasNobodyToTreat() {
        Ward ward = squad();
        ward.hp.put(3L, 5f);
        SquadMedic medic = medicFor(ward);
        assertEquals(0L, SquadMedicSystem.worstHurt(medic, ward));
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
    public void aFullTreatmentSpendsOneSupplyAndRestoresItsAuthoredAmount() {
        Ward ward = squad();
        ward.hp.put(4L, 5f);
        ward.armor.put(4L, 1f);
        SquadMedic medic = medicFor(ward);
        ward.x.put(4L, 2f + RANK_ONE.reachCells() * 0.5f);

        SquadMedicSystem.step(medic, ward, DT);
        assertEquals(SquadMedic.Phase.TREATING, medic.phase());
        assertEquals(4L, medic.patientId());
        assertEquals(RANK_ONE.supplies() - 1, medic.supplies(), "the supply is spent at the start");

        runUntilIdle(medic, ward);
        float expectedHealth = RANK_ONE.healthPerSecond() * RANK_ONE.channelSeconds();
        float expectedArmor = Math.min(4f, RANK_ONE.armorPerSecond() * RANK_ONE.channelSeconds());
        assertEquals(5f + expectedHealth, ward.hp.get(4L), 0.2f);
        assertEquals(1f + expectedArmor, ward.armor.get(4L), 0.2f);
        assertEquals(1, medic.completed());
        assertEquals(0, medic.interrupted());
        assertEquals(RANK_ONE.cooldownSeconds(), medic.cooldownTimer(), 0.05f);
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
        assertEquals(RANK_ONE.supplies() - 1, medic.supplies(), "an interrupted supply is gone");
    }

    @Test
    public void suppliesRecoverOneAtATimeToTheCapAndNoFurther() {
        Ward ward = squad();
        SquadMedic medic = medicFor(ward);
        medic.supplies = 0;
        int ticksPerSupply = Math.round(RANK_ONE.supplyRecoverySeconds() / DT);
        for (int i = 0; i < ticksPerSupply + 1; i++) SquadMedicSystem.step(medic, ward, DT);
        assertEquals(1, medic.supplies());
        for (int i = 0; i < ticksPerSupply * (RANK_ONE.supplies() + 2); i++) {
            SquadMedicSystem.step(medic, ward, DT);
        }
        assertEquals(RANK_ONE.supplies(), medic.supplies());
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
        assertEquals(RANK_ONE.supplies(), medic.supplies());
        assertEquals(0, medic.started());
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
        SquadMedic medic = new SquadMedic(RANK_ONE, false);
        medic.medicId = SquadMedicSystem.designate(ward);
        return medic;
    }

    private static void runUntilIdle(SquadMedic medic, Ward ward) {
        SquadMedicSystem.step(medic, ward, DT);
        for (int i = 0; i < 1000 && medic.phase() != SquadMedic.Phase.IDLE; i++) {
            SquadMedicSystem.step(medic, ward, DT);
        }
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
    }
}
