package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.AutonomousPromotionSystem;
import com.dillon.starsectormarines.campaign.systems.ChainAdvancementSystem;
import com.dillon.starsectormarines.campaign.systems.ContractLifecycleSystem;
import com.dillon.starsectormarines.campaign.systems.DiscoveryPropagationSystem;
import com.dillon.starsectormarines.campaign.systems.RelationshipInteractionSystem;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sector-attached holder for the campaign-tier {@link CampaignState}. Registered
 * once via {@code Global.getSector().addScript(this)} so xstream walks the
 * SoA tables transitively — same persistence pattern as
 * {@link com.dillon.starsectormarines.marine.MarineRosterScript}.
 *
 * <p>The state is persisted; the {@link CampaignSystem} list is not. Systems
 * are pure behavior, reconstructed from {@link #defaultSystems()} on every
 * game load. See <code>roadmap/campaign/architecture.md</code> §2.
 *
 * <p>{@link #advance(float)} fires the daily tick when the sector clock crosses
 * a day boundary, then walks the systems list in registration order. A future
 * scheduler can use each system's {@link CampaignSystem#reads()} /
 * {@link CampaignSystem#writes()} declarations to run conflict-free systems in
 * parallel; for now everything runs serially.
 */
public class CampaignStateScript implements EveryFrameScript {

    private final CampaignState state = new CampaignState();

    /** Behavior, not data — reconstructed on every game load via {@link #systems()}. */
    private transient List<CampaignSystem> systems;

    public CampaignState state() {
        return state;
    }

    public List<CampaignSystem> systems() {
        if (systems == null) {
            systems = defaultSystems();
        }
        return systems;
    }

    /** Default per-tick system order. Plug new systems in here. */
    public static List<CampaignSystem> defaultSystems() {
        return new ArrayList<>(Arrays.asList(
                new AutonomousPromotionSystem(),
                new RelationshipInteractionSystem(),
                new ChainAdvancementSystem(),
                new ContractLifecycleSystem(),
                new DiscoveryPropagationSystem()
        ));
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector() == null) return;
        int day = (int) Global.getSector().getClock().getDay();
        if (day == state.lastTickDay) return;
        state.lastTickDay = day;
        onDailyTick(day);
    }

    private void onDailyTick(int day) {
        List<CampaignSystem> list = systems();
        // Serial execution. Future: schedule by reads()/writes() conflict
        // matrix once profiling justifies parallel.
        for (int i = 0; i < list.size(); i++) {
            list.get(i).tick(state, day);
        }
    }

    /** Finds the registered campaign-state script, or null if not yet installed. */
    public static CampaignStateScript getInstance() {
        if (Global.getSector() == null) return null;
        for (EveryFrameScript script : Global.getSector().getScripts()) {
            if (script instanceof CampaignStateScript) {
                return (CampaignStateScript) script;
            }
        }
        return null;
    }
}
