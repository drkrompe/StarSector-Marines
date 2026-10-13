package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import java.util.LinkedHashMap;
import java.util.Map;

/** An explicit reward handoff before debrief controls, once per presented outcome. */
final class DebriefRewardPrompt {
    private final Runnable open, later;
    private final MutableSignal<Map<String, Object>> row;
    private MissionOutcome outcome, acknowledged;
    private boolean visible, claimed;

    DebriefRewardPrompt(Reactor reactor, Runnable open, Runnable later) {
        this.open = open; this.later = later;
        row = reactor.signal(hidden());
    }
    Signal<Map<String, Object>> row() { return row; }
    boolean visible() { return visible; }
    void present(MissionOutcome outcome, MarineRoster roster) {
        this.outcome = outcome;
        claimed = roster != null && roster.firstRefit().claimed();
        visible = outcome != null && outcome != acknowledged && !outcome.missionSource.isDebug()
                && FirstRefit.available(roster);
        refresh();
    }
    void dismiss() {
        if (!visible) return;
        acknowledge(); later.run();
    }
    private void choose() {
        if (!visible) return;
        acknowledge(); open.run();
    }
    private void acknowledge() { acknowledged = outcome; visible = false; refresh(); }
    void bind(UiDocument document, MarkupInstance markup, Runnable cancel) {
        TutorialModal.bind(document, markup, this::visible, "reward-modal", "mission-results-root", this::dismiss, cancel);
    }
    static Map<String, Object> hidden() {
        var props = new LinkedHashMap<String, Object>();
        props.put("classes", "reward-hidden"); props.put("title", ""); props.put("copy", "");
        props.put("actionLabel", Strings.get("openingDebriefRewardPromptChooseFreeTechnology")); props.put("lineIcon", "");
        props.put("scoutWeaponIcon", ""); props.put("scoutArmorIcon", "");
        props.put("open", (Runnable) () -> {});
        return props;
    }
    private void refresh() {
        var props = hidden();
        props.put("classes", visible ? "reward-backdrop" : "reward-hidden");
        props.put("title", claimed ? Strings.get("openingDebriefRewardPromptYourFreeEquipmentIsReady") : Strings.get("openingDebriefRewardPromptYouEarnedAFreeTechnologyTransfer"));
        props.put("copy", claimed
                ? Strings.get("openingDebriefRewardPromptCaptainTheSpecsAreOursLetS")
                : Strings.get("openingDebriefRewardPromptCaptainWaylineOwesUsOnePickA"));
        props.put("actionLabel", claimed ? Strings.get("openingDebriefRewardPromptFitFreeEquipment") : Strings.get("openingDebriefRewardPromptChooseFreeTechnology"));
        if (visible) {
            props.put("lineIcon", EquipmentIcons.primary(WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), EquipmentGrade.MILSPEC));
            props.put("scoutWeaponIcon", EquipmentIcons.primary(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), EquipmentGrade.MILSPEC));
            props.put("scoutArmorIcon", MarineArmorPattern.BLUE_SCOUT.iconPath);
        }
        props.put("open", (Runnable) this::choose);
        row.set(props);
    }
}
