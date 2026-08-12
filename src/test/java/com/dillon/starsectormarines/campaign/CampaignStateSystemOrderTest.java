package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.CadreTrainingSystem;
import com.dillon.starsectormarines.campaign.systems.ContractLifecycleSystem;
import com.dillon.starsectormarines.campaign.systems.ContractRetainerSystem;
import com.dillon.starsectormarines.campaign.systems.DiscoveryPropagationSystem;
import com.dillon.starsectormarines.campaign.systems.AutonomousPromotionSystem;
import com.dillon.starsectormarines.campaign.systems.AutonomousChainCreationSystem;
import com.dillon.starsectormarines.campaign.systems.ChainAdvancementSystem;
import com.dillon.starsectormarines.campaign.systems.HouseAmbitionSystem;
import com.dillon.starsectormarines.campaign.systems.HouseConsolidationSystem;
import com.dillon.starsectormarines.campaign.systems.InternalFlipGarrisonSystem;
import com.dillon.starsectormarines.campaign.systems.StationingDefaultExtractionSystem;
import com.dillon.starsectormarines.campaign.systems.StationingDefaultSystem;
import com.dillon.starsectormarines.campaign.systems.StationingIncidentSystem;
import com.dillon.starsectormarines.campaign.systems.StakeDriftSystem;
import com.dillon.starsectormarines.campaign.systems.ThreatInterventionOfferSystem;
import com.dillon.starsectormarines.campaign.systems.VanillaRaidGarrisonSystem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignStateSystemOrderTest {

    @Test
    void defaultsPrecedePaymentsAndExpiryPrecedesExtractionSpawn() {
        List<CampaignSystem> systems = CampaignStateScript.defaultSystems();

        int defaults = indexOf(systems, StationingDefaultSystem.class);
        int ambitions = indexOf(systems, HouseAmbitionSystem.class);
        int drift = indexOf(systems, StakeDriftSystem.class);
        int promotions = indexOf(systems, AutonomousPromotionSystem.class);
        int chainCreation = indexOf(systems, AutonomousChainCreationSystem.class);
        int chainAdvancement = indexOf(systems, ChainAdvancementSystem.class);
        int consolidation = indexOf(systems, HouseConsolidationSystem.class);
        int contractGeneration = indexOf(systems,
                com.dillon.starsectormarines.campaign.systems.ContractGenerator.class);
        int discovery = indexOf(systems, DiscoveryPropagationSystem.class);
        int interventionOffers = indexOf(systems, ThreatInterventionOfferSystem.class);
        int raidDefense = indexOf(systems, VanillaRaidGarrisonSystem.class);
        int flipDefense = indexOf(systems, InternalFlipGarrisonSystem.class);
        int retainers = indexOf(systems, ContractRetainerSystem.class);
        int training = indexOf(systems, CadreTrainingSystem.class);
        int incidents = indexOf(systems, StationingIncidentSystem.class);
        int lifecycle = indexOf(systems, ContractLifecycleSystem.class);
        int extraction = indexOf(systems, StationingDefaultExtractionSystem.class);

        assertTrue(defaults < retainers);
        assertTrue(ambitions < drift);
        assertTrue(drift < promotions);
        assertTrue(chainCreation < chainAdvancement);
        assertTrue(chainAdvancement < consolidation);
        assertTrue(consolidation < contractGeneration);
        assertTrue(discovery < interventionOffers);
        assertTrue(raidDefense < defaults);
        assertTrue(flipDefense < defaults);
        assertTrue(defaults < training);
        assertTrue(retainers < lifecycle);
        assertTrue(training < lifecycle);
        assertTrue(incidents < lifecycle);
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
