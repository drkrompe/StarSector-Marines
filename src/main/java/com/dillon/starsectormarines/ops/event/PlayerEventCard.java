package com.dillon.starsectormarines.ops.event;

import com.dillon.starsectormarines.campaign.PlayerEventNotice;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ops.StationingResponseLaunch;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.PanelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.text.MessageFormat;
import java.util.List;

/**
 * The event card: what happened, who is standing there, how long the player has, and
 * the three things they can do about it.
 *
 * <p>Deliberately not a {@code Screen} on {@code MarineOpsPanelPlugin}'s router — that
 * router is planet-scoped by construction and this card must render with the fleet
 * anywhere in the sector.
 */
public final class PlayerEventCard extends BaseCustomUIPanelPlugin {

    private static final Color HEADER = new Color(0xFF, 0xD0, 0x70);
    private static final Color VALUE = new Color(0xE0, 0xE8, 0xFF);
    private static final Color URGENT = new Color(0xFF, 0x80, 0x80);
    private static final Color ACCEPT = new Color(0xC8, 0xFF, 0xE0);
    private static final float PAD = 26f;
    private static final float ROW = 34f;
    private static final float BTN_H = 36f;
    /** Days left at which the countdown itself turns red. */
    private static final int URGENT_DAYS = 2;

    private final WidgetRoot widgets = new WidgetRoot();
    private final PlayerEventNotice notice;
    private final int currentDay;
    private final Runnable dismiss;

    private PositionAPI position;
    private boolean confirmingWriteOff;

    public PlayerEventCard(PlayerEventNotice notice, int currentDay, Runnable dismiss) {
        this.notice = notice;
        this.currentDay = currentDay;
        this.dismiss = dismiss;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.position = position;
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        if (position == null) return;

        float x = position.getX() + PAD;
        float width = position.getWidth() - 2f * PAD;
        float top = position.getY() + position.getHeight() - PAD;

        widgets.add(new PanelWidget(position.getX(), position.getY(),
                position.getWidth(), position.getHeight()));

        widgets.add(new LabelWidget(Fonts.ORBITRON_24_BOLD,
                Strings.get(notice.headerKey), x, top, HEADER));

        float y = top - 52f;
        String location = PlayerEventTarget.displayName(notice);
        if (location != null) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    MessageFormat.format(Strings.get("eventPopupLocationFmt"), location),
                    x, y, VALUE));
            y -= ROW;
        }

        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get(notice.labelKey), x, y, VALUE));
        y -= ROW;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                MessageFormat.format(Strings.get("stationingIncidentDetachment"),
                        captainName(), notice.committedMarines), x, y, VALUE));
        y -= ROW;

        int daysLeft = notice.daysRemaining(currentDay);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                daysLeft <= 0
                        ? Strings.get("eventPopupDeadlineToday")
                        : MessageFormat.format(Strings.get("eventPopupDeadlineFmt"), daysLeft),
                x, y, daysLeft <= URGENT_DAYS ? URGENT : VALUE));
        y -= ROW;

        if (notice.activeSeats == 0) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("eventPopupNoForce"), x, y, URGENT));
            y -= ROW;
        }

        if (confirmingWriteOff) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("eventPopupWriteOffWarning"), x, y, URGENT));
            addButtonRow(x, width,
                    Strings.get("eventPopupConfirm"), URGENT, this::onWriteOffConfirmed,
                    Strings.get("eventPopupCancel"), VALUE, this::onCancelWriteOff,
                    null, null, null);
            return;
        }

        PlanetAPI planet = PlayerEventTarget.planet(notice);
        boolean canDeploy = planet != null;
        if (!canDeploy) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("eventPopupDeployUnavailable"), x, y, URGENT));
        }
        addButtonRow(x, width,
                Strings.get("eventPopupDeploy"), canDeploy ? ACCEPT : URGENT,
                canDeploy ? this::onDeploy : null,
                Strings.get("eventPopupHold"), VALUE, this::onHold,
                Strings.get("eventPopupWriteOff"), URGENT, this::onWriteOff);
    }

    /** Two or three equal buttons pinned to the bottom edge; a null action disables one. */
    private void addButtonRow(float x, float width,
                              String labelA, Color colorA, Runnable actionA,
                              String labelB, Color colorB, Runnable actionB,
                              String labelC, Color colorC, Runnable actionC) {
        int count = labelC != null ? 3 : 2;
        float gap = 10f;
        float buttonW = (width - (count - 1) * gap) / count;
        float y = position.getY() + PAD;
        addButton(x, y, buttonW, labelA, colorA, actionA);
        addButton(x + buttonW + gap, y, buttonW, labelB, colorB, actionB);
        if (count == 3) {
            addButton(x + 2f * (buttonW + gap), y, buttonW, labelC, colorC, actionC);
        }
    }

    private void addButton(float x, float y, float w, String label, Color color,
                           Runnable action) {
        widgets.add(new ButtonWidget(x, y, w, BTN_H, action));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, label,
                x + 14f, y + BTN_H - 8f, color));
    }

    private void onDeploy() {
        PlayerEventPresenter.requestDeployment(notice);
        dismiss.run();
    }

    private void onHold() {
        dismiss.run();
    }

    private void onWriteOff() {
        confirmingWriteOff = true;
        rebuild();
    }

    private void onCancelWriteOff() {
        confirmingWriteOff = false;
        rebuild();
    }

    private void onWriteOffConfirmed() {
        StationingResponseLaunch.writeOff(notice.contractId);
        dismiss.run();
    }

    private String captainName() {
        MarineCaptain captain = captain();
        return captain != null ? captain.name() : Strings.get("stationingUnknownCaptain");
    }

    private MarineCaptain captain() {
        if (notice.captainId == null) return null;
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster().byId(notice.captainId) : null;
    }

    @Override
    public void advance(float amount) {
        widgets.advance(amount);
    }

    @Override
    public void render(float alphaMult) {
        widgets.render(alphaMult);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }
}
