package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.OfficerSpecialty;
import com.dillon.starsectormarines.marine.Rank;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The squad card marks the medic billet only when the home officer trains Field Medicine. */
public class SquadMusterMedicTest {

    @Test
    public void theFirstReadyBravoMarineIsMarkedOnlyUnderFieldMedicine() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        MarineSquad squad = roster.squads().stream().filter(q -> !q.reserve()).findFirst().orElseThrow();
        MarineCaptain captain = new MarineCaptain("Medic Officer", "portrait.png", Rank.CAPTAIN, 0f);
        roster.add(captain);
        roster.assignCaptainToSquad(captain.id(), squad.id());

        assertEquals(-1, medicIndex(SquadMuster.from(roster, squad.id(), 0f)), "untrained: no medic");

        captain.train(OfficerSpecialty.FIELD_MEDICINE);
        assertEquals(MarineSquad.TEAM_SIZE, medicIndex(SquadMuster.from(roster, squad.id(), 0f)));

        String firstBravo = roster.manningMemberIds(squad).get(MarineSquad.TEAM_SIZE);
        roster.applySoldierOutcome(Map.of(firstBravo, MarineSoldierStatus.WIA), 0f, 5f);
        SquadMuster muster = SquadMuster.from(roster, squad.id(), 0f);
        int index = medicIndex(muster);
        assertEquals(SquadMuster.State.READY, muster.billets().get(index).state(),
                "a wounded marine does not deploy and cannot carry the medic billet");
        assertEquals(1, muster.billets().get(index).teamIndex());
    }

    private static int medicIndex(SquadMuster muster) {
        assertNotNull(muster);
        int found = -1;
        for (SquadMuster.Billet billet : muster.billets()) {
            if (!billet.medic()) continue;
            assertEquals(-1, found, "one medic per squad");
            found = billet.index();
        }
        return found;
    }
}
