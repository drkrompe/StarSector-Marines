package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.setup.GroundRosterRegistry;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.polity.PolityDoctrineLedger;
import com.dillon.starsectormarines.campaign.polity.PolityRosterDerivation;
import com.dillon.starsectormarines.campaign.polity.ProductionSignals;
import com.dillon.starsectormarines.campaign.polity.ReleasedKit;
import com.dillon.starsectormarines.campaign.polity.VanillaProductionSignals;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.EnumSet;
import java.util.function.Supplier;

/**
 * Rebuilds the player faction's ground doctrine every day and registers it under the
 * player faction id ({@code polity-ground-doctrine.md}, law 1: the polity's roster is
 * derived and rebuilt from the live economy, never authored).
 *
 * <p>Three inputs, all read here and none of them decided here: what the company has
 * released ({@link ReleasedKit}), what the colonies can make
 * ({@link ProductionSignals}), and how the doctrine points are spent
 * ({@link PolityDoctrineLedger}). The derivation itself is pure; this is the daily
 * clock and the one write into the registry.
 *
 * <p>It rebuilds unconditionally rather than diffing its inputs. The derivation is
 * cheap and the economy moves under it in ways this system does not observe — an
 * industry finished, a deficit opened — so "has anything changed?" is a question with
 * more moving parts than the work it would save.
 *
 * <p>The registry is rebuilt from disk at application load, which drops any derived
 * profile with it. {@link #rebuildNow} is therefore called from game load as well as
 * from the colony panel, so a battle launched before the first daily tick already
 * resolves the player's own roster rather than the fallback.
 */
public final class PolityRosterSystem implements CampaignSystem {

    private static final Logger LOG = Global.getLogger(PolityRosterSystem.class);

    private final ProductionSignals signals;
    private final Supplier<GroundRosterRegistry> registry;

    public PolityRosterSystem() {
        this(new VanillaProductionSignals(), GroundRosterRegistry::installed);
    }

    PolityRosterSystem(ProductionSignals signals, Supplier<GroundRosterRegistry> registry) {
        this.signals = signals;
        this.registry = registry;
    }

    @Override
    public String name() {
        return "PolityRoster";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.RELEASED_KIT);
    }

    /**
     * The derived profile lands in the ground-roster registry, which is not one of
     * these tables; nothing on {@link CampaignState} is written.
     */
    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.noneOf(CampaignTable.class);
    }

    @Override
    public void tick(CampaignState state, int day) {
        rebuild(state, signals, registry.get());
    }

    /**
     * Rebuilds and installs immediately — for the colony panel, which must show the
     * result of an edit at once, and for game load, which starts with a registry that
     * holds no derived profile at all.
     *
     * @return the profile now standing under the player faction id, or null when there
     *         was no registry to install it into.
     */
    public static GroundRosterProfile rebuildNow(CampaignState state) {
        return rebuild(state, new VanillaProductionSignals(), GroundRosterRegistry.installed());
    }

    private static GroundRosterProfile rebuild(CampaignState state, ProductionSignals signals,
                                               GroundRosterRegistry registry) {
        if (state == null || signals == null || registry == null) return null;
        GroundRosterProfile profile = PolityRosterDerivation.derive(
                ReleasedKit.releasedCards(state), signals.bestQuality(),
                PolityDoctrineLedger.read(state));
        registry.replaceDerived(profile);
        LOG.debug("Polity ground roster rebuilt at " + signals.bestQuality());
        return profile;
    }
}
