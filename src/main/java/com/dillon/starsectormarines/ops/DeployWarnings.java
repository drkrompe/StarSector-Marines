package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.spec.SpecSheet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The briefing's warning lamps beside Deploy: four standing checks on the force as it would launch. A lit
 * lamp never blocks the launch; its hover says what is wrong and how it clears.
 */
final class DeployWarnings {
    private DeployWarnings() { }

    enum Kind {
        UNDERMANNED("graphics/ui/briefing/infantry.svg"),
        CASUALTIES("graphics/ui/briefing/casualty.svg"),
        DAMAGE("graphics/ui/briefing/mech.svg"),
        LIFT("graphics/ui/briefing/weight-none.svg");

        final String icon;

        Kind(String icon) {
            this.icon = icon;
        }

        String id() {
            return "mission-lamp-" + name().toLowerCase(Locale.ROOT);
        }
    }

    /** One lamp: dark when its check passes, lit amber when it does not. */
    record Lamp(Kind kind, boolean lit, SpecSheet hint) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> kind.id();
                case "classes" -> "deploy-lamp" + (lit ? " deploy-lamp-lit" : "");
                case "iconId" -> kind.id() + ":icon";
                case "icon" -> kind.icon;
                case "labelId" -> kind.id() + ":label";
                case "label" -> Strings.upper(Strings.get("deployLamp" + kindKey(kind)));
                default -> null;
            };
        }
    }

    private static String kindKey(Kind kind) {
        String name = kind.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Whole hours until a marine wounded until {@code untilDay} is fit, counted from {@code day}. */
    static int hoursToFit(float untilDay, float day) {
        return Math.max(0, (int) Math.ceil((untilDay - day) * 24f - 1e-3f));
    }

    static Lamp undermanned(int ready, int recommended) {
        boolean lit = ready < recommended;
        String key = "deployLampUndermanned";
        return new Lamp(Kind.UNDERMANNED, lit, new SpecSheet(Strings.get(key + "Title"),
                Strings.format("deployLampUndermannedCount", ready, recommended), null, "command", List.of(),
                List.of(Strings.get(lit ? key + "Lit" : key + "Clear"))));
    }

    /**
     * Wounded and empty billets in the selected squads. A wounded marine keeps the billet and returns on
     * a clock, so the hover gives hours; an empty billet only fills by hand, so it says what Replenish can draw.
     *
     * @param replenishable reserves plus cargo Marines a refill could draw on
     */
    static Lamp casualties(MarineRoster roster, Collection<String> squadIds, float day, int replenishable) {
        List<String> notes = new ArrayList<>();
        int vacant = 0;
        boolean lit = false;
        if (roster != null && squadIds != null) {
            for (String id : squadIds) {
                MarineSquad squad = roster.squadById(id);
                if (squad == null || squad.reserve()) continue;
                int wounded = 0;
                float latest = day;
                for (MarineSoldier soldier : roster.squadMembers(squad)) {
                    if (soldier.status() != MarineSoldierStatus.WIA) continue;
                    wounded++;
                    latest = Math.max(latest, soldier.unavailableUntilDay());
                }
                int open = roster.vacancies(squad);
                vacant += open;
                if (wounded > 0) notes.add(Strings.format("deployLampCasualtiesWounded", squad.name(), wounded,
                        hoursToFit(latest, day)));
                if (open > 0) notes.add(Strings.format("deployLampCasualtiesVacant", squad.name(), open));
                lit |= wounded > 0 || open > 0;
            }
        }
        if (vacant > 0) notes.add(Strings.format(replenishable >= vacant ? "deployLampCasualtiesReplenish"
                : replenishable > 0 ? "deployLampCasualtiesShort" : "deployLampCasualtiesNone",
                Math.min(vacant, replenishable), vacant));
        if (!lit) notes.add(Strings.get("deployLampCasualtiesClear"));
        return new Lamp(Kind.CASUALTIES, lit, new SpecSheet(Strings.get("deployLampCasualtiesTitle"), "", null,
                "command", List.of(), notes));
    }

    /** The active lance's mechs still under repair, each with the hours left on its clock. */
    static Lamp damage(Collection<CampaignMech> mechs, float day) {
        List<String> notes = new ArrayList<>();
        if (mechs != null) {
            for (CampaignMech mech : mechs) {
                float days = mech.repairDaysRemaining(day);
                if (days > 0f) notes.add(Strings.format("deployLampDamageMech", mech.displayName(),
                        hoursToFit(mech.repairUntilDay(), day)));
            }
        }
        boolean lit = !notes.isEmpty();
        notes.add(Strings.get(lit ? "deployLampDamageLit" : "deployLampDamageClear"));
        return new Lamp(Kind.DAMAGE, lit, new SpecSheet(Strings.get("deployLampDamageTitle"), "", null,
                "command", List.of(), notes));
    }

    /** Every bay machine that stays aboard, and why. */
    static Lamp lift(BriefingLift.Readout readout) {
        List<String> notes = new ArrayList<>();
        if (readout != null) {
            for (BriefingLift.Machine machine : readout.machines()) {
                if (!machine.status().drops) notes.add(Strings.format("deployLampLiftMachine", machine.name(),
                        BriefingLift.statusLine(machine)));
            }
        }
        boolean lit = !notes.isEmpty();
        if (lit) {
            notes.add(Strings.get("briefingLiftWhy" + readout.machines().stream()
                    .filter(m -> !m.status().drops).findFirst().orElseThrow().status().name()));
        } else {
            notes.add(Strings.get("deployLampLiftClear"));
        }
        return new Lamp(Kind.LIFT, lit, new SpecSheet(Strings.get("deployLampLiftTitle"), "", null,
                "command", List.of(), notes));
    }
}
