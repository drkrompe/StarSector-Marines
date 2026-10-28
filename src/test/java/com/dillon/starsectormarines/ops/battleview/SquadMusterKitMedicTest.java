package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Without Field Medicine the squad card marks every ready marine whose suit carries a field-aid kit. */
public class SquadMusterKitMedicTest {

    @Test
    public void everyReadyKitWearerIsMarkedTheNcoIncludedAndNobodyElse() {
        List<SquadMuster.Billet> billets = new ArrayList<>();
        billets.add(billet(0, SquadMuster.State.READY, true));
        billets.add(billet(1, SquadMuster.State.READY, false));
        billets.add(billet(2, SquadMuster.State.WOUNDED, false));
        billets.add(billet(3, SquadMuster.State.VACANT, false));
        billets.add(billet(4, SquadMuster.State.READY, false));

        SquadMuster.markKitMedics(billets, b -> b.index() != 1);

        assertEquals(List.of(true, false, false, false, true),
                billets.stream().map(SquadMuster.Billet::medic).toList(),
                "the ready wearers; a wounded or vacant billet deploys nobody");
    }

    private static SquadMuster.Billet billet(int index, SquadMuster.State state, boolean leader) {
        return new SquadMuster.Billet(index, "m" + index, "Marine " + index, "Pvt", state, -1f, leader, false);
    }
}
