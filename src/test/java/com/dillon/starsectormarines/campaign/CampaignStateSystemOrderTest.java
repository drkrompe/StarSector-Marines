package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.CadreTrainingSystem;
import com.dillon.starsectormarines.campaign.systems.ContractLifecycleSystem;
import com.dillon.starsectormarines.campaign.systems.ContractRetainerSystem;
import com.dillon.starsectormarines.campaign.systems.StationingDefaultExtractionSystem;
import com.dillon.starsectormarines.campaign.systems.StationingDefaultSystem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignStateSystemOrderTest {

    @Test
    void defaultsPrecedePaymentsAndExpiryPrecedesExtractionSpawn() {
        List<CampaignSystem> systems = CampaignStateScript.defaultSystems();

        int defaults = indexOf(systems, StationingDefaultSystem.class);
        int retainers = indexOf(systems, ContractRetainerSystem.class);
        int training = indexOf(systems, CadreTrainingSystem.class);
        int lifecycle = indexOf(systems, ContractLifecycleSystem.class);
        int extraction = indexOf(systems, StationingDefaultExtractionSystem.class);

        assertTrue(defaults < retainers);
        assertTrue(defaults < training);
        assertTrue(retainers < lifecycle);
        assertTrue(training < lifecycle);
        assertTrue(lifecycle < extraction);
    }

    private static int indexOf(List<CampaignSystem> systems,
                               Class<? extends CampaignSystem> type) {
        for (int i = 0; i < systems.size(); i++) {
            if (type.isInstance(systems.get(i))) return i;
        }
        return Integer.MAX_VALUE;
    }
}
