package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseAbilityPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.apache.log4j.Logger;

/**
 * Ability-bar entry point to the company's between-contracts home.
 *
 * <p>The ability bar is the campaign-only HUD element: it is present on the campaign
 * map and disappears with the rest of the HUD whenever a core tab, an interaction
 * dialog, or the pause menu takes over. That is the whole reason this is an ability
 * rather than a custom widget drawn through {@code CampaignUIRenderingListener} — the
 * "visible in campaign view, gone when other UI is open" requirement needs no
 * visibility gate of ours at all.
 *
 * <p>It is not really an ability. Nothing activates, nothing toggles, nothing has a
 * cooldown: {@link BaseAbilityPlugin} already defaults {@code isActive} to false,
 * {@code getProgressFraction} to zero and {@code getCooldownFraction} to one, so every
 * indicator stays dark on its own. {@link #activate}/{@link #deactivate} are stubbed
 * anyway so the engine's TOGGLE handling cannot fire an activation event or an on/off
 * sound behind our back. The only live hook is {@link #pressButton()}.
 *
 * <p>Lives in {@code ops} rather than {@code campaign} deliberately: it is a door into
 * an ops screen, and {@code ops} already depends on {@code campaign}. Putting it under
 * {@code campaign} would invert that.
 *
 * <p>Declared in {@code mod/data/campaign/abilities.csv} and granted by
 * {@code StarsectorMarinesModPlugin.ensureCompanyViewAbility}.
 */
public class CompanyViewAbility extends BaseAbilityPlugin {

    /** Must match the {@code id} column in {@code mod/data/campaign/abilities.csv}. */
    public static final String ABILITY_ID = "marines_company_view";

    private static final Logger LOG = Global.getLogger(CompanyViewAbility.class);

    @Override
    public void pressButton() {
        SectorAPI sector = Global.getSector();
        if (sector == null) return;
        CampaignUIAPI ui = sector.getCampaignUI();
        // The ability bar should not be reachable while a dialog is up, but
        // showInteractionDialog refuses in that case anyway; check rather than
        // rely on the HUD having been hidden.
        if (ui == null || ui.isShowingDialog()) return;

        // Null planet: this host is deliberately planet-free. CompanyHqScreen is the
        // only destination that tolerates it — see MarineOpsPanelPlugin's routing note.
        boolean shown = ui.showInteractionDialog(
                new MarineOpsDialogPlugin(null, ctx -> ctx.goTo(ScreenId.COMPANY_HQ)),
                sector.getPlayerFleet());
        if (!shown) {
            LOG.info("CompanyView: dialog refused; UI busy");
        }
    }

    /**
     * Stubbed so a TOGGLE spec cannot report an activation the player never made.
     * The base implementation calls {@code reportPlayerActivatedAbility}.
     */
    @Override
    public void activate() {
    }

    @Override
    public void deactivate() {
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle(Strings.get("companyAbilityTitle"));
        tooltip.addPara(Strings.get("companyAbilityTooltip"), 10f);
    }

    @Override
    public boolean isTooltipExpandable() {
        return false;
    }

    /** No cooldown exists; the abstract accessors are satisfied, not implemented. */
    @Override
    public void setCooldownLeft(float days) {
    }

    @Override
    public float getCooldownLeft() {
        return 0f;
    }
}
