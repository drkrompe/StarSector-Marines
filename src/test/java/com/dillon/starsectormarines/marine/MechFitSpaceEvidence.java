package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechHeat;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The mech fit space as readings, not assertions: per chassis, every footprint-legal fit against
 * the engine budget (how many fit, the stock fit, the full internal refit, the best of everything,
 * what a full plate and the top engine leave for weapons, the heaviest weapons a full refit can
 * carry), and the Hound's weapons' autonomous duty cycle under the production {@link MechHeat}
 * gates at a range of cooling rates.
 *
 * <p>Pure arithmetic, but a design-pass report rather than a unit: it lives under
 * {@code generatedSpaceEvidence} and is run on its own with
 * {@code gradlew.bat generatedSpaceEvidence --tests *MechFitSpaceEvidence}. It writes
 * {@code build/reports/mech-fits/fit-space.md}. The laws it reads are pinned separately and
 * cheaply by {@code MechFitLawsTest}.</p>
 */
@Tag("generated-space-evidence")
class MechFitSpaceEvidence {

    private static final float SECONDS = 60f;
    private static final float DT = 1f / 30f;

    @Test
    void report() throws IOException {
        StringBuilder md = new StringBuilder("# Mech fit space\n\n");
        budgetTable(md);
        for (MechVariant variant : MechVariant.values()) chassis(md, variant);
        heat(md);
        String text = md.toString();
        System.out.println(text);
        Path out = Path.of("build", "reports", "mech-fits", "fit-space.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, text, StandardCharsets.UTF_8);
    }

    private static void budgetTable(StringBuilder md) {
        md.append("## Masses\n\n| Part | Tier | Footprint | Mass (H/S/B) | Output x | Speed f | Armor f | Heat | Cooling |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (MechInternal i : MechInternal.values()) {
            md.append(String.format(Locale.ROOT, "| %s | %s | %dx%d | %s/%s/%s | %.2f | %.2f | %.2f | %.0f | %+.1f |\n",
                    i.name(), i.tier, i.footprintColumns, i.footprintRows,
                    f(MechFrameBudget.mass(i, MechVariant.HOUND)), f(MechFrameBudget.mass(i, MechVariant.SIROCCO)),
                    f(MechFrameBudget.mass(i, MechVariant.BULWARK)), i.outputFactor, i.speedFraction,
                    i.armorFraction, i.heatCapacity, i.cooling));
        }
        md.append("\n| Weapon | Tier | Footprint | Mass | Heat/trigger | Cycle s | Heat/s at full rate |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- |\n");
        for (MechWeaponComponent w : MechWeaponComponent.values()) {
            md.append(String.format(Locale.ROOT, "| %s | %s | %dx%d | %s | %.0f | %.2f | %.1f |\n", w.name(),
                    MechPattern.of(w).accessTier(), w.footprintColumns, w.footprintRows, f(MechFrameBudget.mass(w)),
                    w.heatPerTrigger(), w.cycleSeconds(), w.heatPerTrigger() / w.cycleSeconds()));
        }
        md.append('\n');
    }

    private static void chassis(StringBuilder md, MechVariant v) {
        md.append("## ").append(v.name()).append("\n\n");
        md.append(String.format(Locale.ROOT, "Rated output %s, plating unit %s; engines give %s / %s / %s.\n\n",
                f(MechFrameBudget.ratedOutput(v)), f(MechFrameBudget.platingUnit(v)),
                f(MechFrameBudget.ratedOutput(v) * MechInternal.INDUSTRIAL_ENGINE.outputFactor),
                f(MechFrameBudget.ratedOutput(v) * MechInternal.UPRATED_ENGINE.outputFactor),
                f(MechFrameBudget.ratedOutput(v) * MechInternal.MILITARY_ENGINE.outputFactor)));
        for (MechMountSlot slot : MechMountSlot.values()) {
            List<String> names = new ArrayList<>();
            for (MechWeaponComponent w : MechFitSpace.weaponsFor(v, slot)) names.add(w.name() + " " + f(MechFrameBudget.mass(w)));
            md.append("- ").append(slot).append(": ").append(names.isEmpty() ? "(no socket)" : String.join(", ", names)).append('\n');
        }
        for (MechInternal.Role role : MechInternal.Role.values()) {
            List<String> names = new ArrayList<>();
            for (MechInternal i : MechFitSpace.internalsFor(v, role)) names.add(i == null ? "empty" : i.name());
            md.append("- ").append(role).append(": ").append(names.isEmpty() ? "(no socket)" : String.join(", ", names)).append('\n');
        }
        List<MechFrameBudget.Fit> all = MechFitSpace.all(v);
        long fits = all.stream().filter(fit -> !fit.overBudget()).count();
        long full = all.stream().filter(MechFitSpaceEvidence::allMountsFilled).count();
        long fullFits = all.stream().filter(MechFitSpaceEvidence::allMountsFilled).filter(fit -> !fit.overBudget()).count();
        md.append(String.format(Locale.ROOT, "\nLegal fits: %d, under budget %d (%.0f%%). With every mount filled: %d, under budget %d (%.0f%%).\n\n",
                all.size(), fits, 100.0 * fits / all.size(), full, fullFits, 100.0 * fullFits / Math.max(1, full)));

        md.append("| Fit | Mass | Output | Headroom | Armor | Speed | Heat | Cooling | Fits |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        row(md, "stock", MechFitSpace.stock(v));
        row(md, "full internal refit, stock weapons", MechFitSpace.fullInternalRefit(v));
        row(md, "best buildable refit, stock weapons", MechFitSpace.bestRefitAroundStockWeapons(v));
        row(md, "best of everything", MechFitSpace.bestOfEverything(v));
        md.append(String.format(Locale.ROOT, "| pre-cut reference | | | | %.0f | %.2f | %.0f | %.1f | |\n\n",
                MechFitSpace.preCut(v).armorCapacity(), MechFitSpace.preCut(v).moveSpeed(),
                MechFitSpace.preCut(v).heatCapacity(), MechFitSpace.preCut(v).coolingPerSecond()));

        MechInternal topEngine = MechInternal.MILITARY_ENGINE;
        float plateAndEngine = MechFrameBudget.ratedOutput(v) * topEngine.outputFactor
                - MechFrameBudget.mass(topEngine, v) - MechFrameBudget.mass(MechInternal.HEAVY_PLATING, v);
        MechInternal topSink = top(MechFitSpace.internalsFor(v, MechInternal.Role.HEAT_SINK));
        float stockWeapons = MechFrameBudget.mass(v.arms) + MechFrameBudget.mass(v.leftShoulder) + MechFrameBudget.mass(v.rightShoulder);
        md.append(String.format(Locale.ROOT, "Heavy plate + military engine leave **%s** for weapons and the rest of the frame"
                        + " (%s after the top sink %s); the stock weapons weigh %s.\n\n",
                f(plateAndEngine), f(plateAndEngine - MechFrameBudget.mass(topSink, v)), topSink.name(), f(stockWeapons)));

        // The heaviest weapon sets each frame can carry.
        md.append("Heaviest weapon sets that fit, by frame:\n\n| Frame | Weapons | Weapon mass | Headroom left |\n| --- | --- | --- | --- |\n");
        for (String label : List.of("stock frame", "full internal refit", "heavy plate + military engine, stock sink")) {
            MechFrameBudget.Fit base = switch (label) {
                case "stock frame" -> MechFitSpace.stock(v);
                case "full internal refit" -> MechFitSpace.fullInternalRefit(v);
                default -> MechFitSpace.stock(v).withInternal(MechInternal.Role.ENGINE, MechInternal.MILITARY_ENGINE)
                        .withInternal(MechInternal.Role.PLATING, MechInternal.HEAVY_PLATING);
            };
            MechFrameBudget.Fit best = heaviestWeaponsOn(v, base);
            if (best == null) {
                md.append("| ").append(label).append(" | nothing fits | | |\n");
            } else {
                md.append(String.format(Locale.ROOT, "| %s | %s | %s | %s |\n", label, weapons(best),
                        f(weaponMass(best)), f(best.output() - best.fittedMass())));
            }
        }
        md.append('\n');

        // Sensible builds: every all-mounts-filled weapon set, with the best frame it can carry.
        md.append("Every filled weapon set with the strongest frame that fits around it (armor, then speed, then cooling):\n\n")
                .append("| Weapons | Weapon mass | Engine | Plating | Sink | Cooler | Armor | Speed | Cooling | Headroom |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        List<MechWeaponComponent> arms = MechFitSpace.weaponsFor(v, MechMountSlot.ARMS);
        List<MechWeaponComponent> left = MechFitSpace.weaponsFor(v, MechMountSlot.LEFT_SHOULDER);
        List<MechWeaponComponent> right = MechFitSpace.weaponsFor(v, MechMountSlot.RIGHT_SHOULDER);
        if (right.isEmpty()) right = Collections.singletonList(null);
        List<MechFrameBudget.Fit> rows = new ArrayList<>();
        for (MechWeaponComponent a : arms) {
            for (MechWeaponComponent l : left) {
                for (MechWeaponComponent r : right) {
                    if (r != null && l != null && r.ordinal() < l.ordinal()
                            && MechFitSpace.weaponsFor(v, MechMountSlot.LEFT_SHOULDER).contains(r)
                            && MechFitSpace.weaponsFor(v, MechMountSlot.RIGHT_SHOULDER).contains(l)) continue;
                    MechFitSpace.internalFits(v, a, l, r).stream().filter(fit -> !fit.overBudget())
                            .max(MechFitSpace.FRAME_ORDER).ifPresent(rows::add);
                }
            }
        }
        rows.sort(Comparator.comparingDouble(MechFitSpaceEvidence::weaponMass).reversed());
        for (MechFrameBudget.Fit fit : rows) {
            md.append(String.format(Locale.ROOT, "| %s | %s | %s | %s | %s | %s | %.0f | %.2f | %.1f | %s |\n",
                    weapons(fit), f(weaponMass(fit)), name(fit.engine()), name(fit.plating()), name(fit.heatSink()),
                    name(fit.cooler()), fit.armorCapacity(), fit.moveSpeed(), fit.coolingPerSecond(),
                    f(fit.output() - fit.fittedMass())));
        }
        md.append('\n');
    }

    private static MechFrameBudget.Fit heaviestWeaponsOn(MechVariant v, MechFrameBudget.Fit base) {
        MechFrameBudget.Fit best = null;
        for (MechWeaponComponent a : MechFitSpace.weaponsFor(v, MechMountSlot.ARMS)) {
            for (MechWeaponComponent l : MechFitSpace.weaponsFor(v, MechMountSlot.LEFT_SHOULDER)) {
                List<MechWeaponComponent> rights = new ArrayList<>(MechFitSpace.weaponsFor(v, MechMountSlot.RIGHT_SHOULDER));
                if (rights.isEmpty()) rights.add(null);
                for (MechWeaponComponent r : rights) {
                    MechFrameBudget.Fit fit = base.withWeapon(MechMountSlot.ARMS, a)
                            .withWeapon(MechMountSlot.LEFT_SHOULDER, l).withWeapon(MechMountSlot.RIGHT_SHOULDER, r);
                    if (fit.overBudget()) continue;
                    if (best == null || weaponMass(fit) > weaponMass(best)) best = fit;
                }
            }
        }
        return best;
    }

    private static void row(StringBuilder md, String label, MechFrameBudget.Fit fit) {
        md.append(String.format(Locale.ROOT, "| %s: %s; %s %s %s %s | %s | %s | %s | %.0f | %.2f | %.0f | %.1f | %s |\n",
                label, weapons(fit), name(fit.engine()), name(fit.plating()), name(fit.heatSink()), name(fit.cooler()),
                f(fit.fittedMass()), f(fit.output()), f(fit.output() - fit.fittedMass()), fit.armorCapacity(),
                fit.moveSpeed(), fit.heatCapacity(), fit.coolingPerSecond(), fit.overBudget() ? "**no**" : "yes"));
    }

    // ---- heat ----

    private static void heat(StringBuilder md) {
        md.append("## Autonomous duty cycle (production MechHeat gates, AI fires every mount whenever ready and permitted)\n\n")
                .append("Duty is triggers fired over 60 s against the mechanical maximum. First gate is the second the AI first stops to cool.\n\n")
                .append("| Chassis | Weapons | Heat/s demand | Reservoir | Cooling | First gate s | Duty | Triggers/min per mount |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");
        List<MechWeaponComponent[]> hound = new ArrayList<>();
        hound.add(new MechWeaponComponent[]{MechWeaponComponent.NOSE_CHAINGUN, MechWeaponComponent.SRM_5});
        hound.add(new MechWeaponComponent[]{MechWeaponComponent.NOSE_CHAINGUN});
        hound.add(new MechWeaponComponent[]{MechWeaponComponent.SRM_5});
        hound.add(new MechWeaponComponent[]{MechWeaponComponent.DEMOLITION_CANNON, MechWeaponComponent.SRM_5});
        hound.add(new MechWeaponComponent[]{MechWeaponComponent.NOSE_MUSTER_AUTOGUN, MechWeaponComponent.LRM_5});
        hound.add(new MechWeaponComponent[]{MechWeaponComponent.QUARRY_BREAKER_CANNON, MechWeaponComponent.PIONEER_ROCKET_CRADLE});
        for (MechWeaponComponent[] set : hound) {
            for (float[] frame : new float[][]{{100f, 6f}, {100f, 7f}, {100f, 8f}, {120f, 7f}, {120f, 7.5f}, {120f, 8f}}) {
                heatRow(md, "HOUND", set, frame[0], frame[1]);
            }
        }
        for (MechVariant v : new MechVariant[]{MechVariant.SIROCCO, MechVariant.BULWARK}) {
            MechWeaponComponent[] set = v.rightShoulder != null
                    ? new MechWeaponComponent[]{v.arms, v.leftShoulder, v.rightShoulder}
                    : new MechWeaponComponent[]{v.arms, v.leftShoulder};
            MechFrameBudget.Fit refit = MechFitSpace.bestRefitAroundStockWeapons(v);
            heatRow(md, v.name(), set, 100f, 6f);
            heatRow(md, v.name(), set, 100f, 8f);
            heatRow(md, v.name(), set, refit.heatCapacity(), refit.coolingPerSecond());
        }
        md.append('\n');
    }

    private static void heatRow(StringBuilder md, String chassis, MechWeaponComponent[] set, float capacity, float cooling) {
        MechHeat heat = new MechHeat(capacity, cooling);
        float[] ready = new float[set.length];
        int[] triggers = new int[set.length];
        float firstGate = -1f;
        float demand = 0f;
        for (MechWeaponComponent w : set) demand += w.heatPerTrigger() / w.cycleSeconds();
        for (float t = 0f; t < SECONDS; t += DT) {
            for (int i = 0; i < set.length; i++) {
                ready[i] -= DT;
                if (ready[i] > 0f) continue;
                float cost = set[i].heatPerTrigger();
                if (heat.permits(cost, true)) {
                    heat.commit(cost);
                    triggers[i]++;
                    ready[i] = set[i].cycleSeconds();
                }
            }
            if (firstGate < 0f && heat.aiCooling()) firstGate = t;
            heat.advance(DT);
        }
        float duty = 0f;
        List<String> per = new ArrayList<>();
        for (int i = 0; i < set.length; i++) {
            float max = SECONDS / set[i].cycleSeconds();
            duty += triggers[i] / max / set.length;
            per.add(set[i].name() + " " + triggers[i] + "/" + Math.round(max));
        }
        List<String> names = new ArrayList<>();
        for (MechWeaponComponent w : set) names.add(w.name());
        md.append(String.format(Locale.ROOT, "| %s | %s | %.1f | %.0f | %.1f | %s | %.0f%% | %s |\n", chassis,
                String.join(" + ", names), demand, capacity, cooling,
                firstGate < 0f ? "never" : String.format(Locale.ROOT, "%.1f", firstGate), duty * 100f, String.join(", ", per)));
    }

    // ---- helpers ----

    private static boolean allMountsFilled(MechFrameBudget.Fit fit) {
        for (MechMountSlot slot : MechMountSlot.values()) {
            if (!MechFitSpace.weaponsFor(fit.variant(), slot).isEmpty() && fit.weaponAt(slot) == null) return false;
        }
        return true;
    }

    private static float weaponMass(MechFrameBudget.Fit fit) {
        return MechFrameBudget.mass(fit.arms()) + MechFrameBudget.mass(fit.leftShoulder()) + MechFrameBudget.mass(fit.rightShoulder());
    }

    private static String weapons(MechFrameBudget.Fit fit) {
        List<String> names = new ArrayList<>();
        for (MechMountSlot slot : MechMountSlot.values()) {
            if (fit.weaponAt(slot) != null) names.add(fit.weaponAt(slot).name());
        }
        return String.join(" + ", names);
    }

    private static MechInternal top(List<MechInternal> legal) {
        MechInternal best = null;
        for (MechInternal i : legal) {
            if (i != null && (best == null || i.tier.ordinal() > best.tier.ordinal())) best = i;
        }
        return best;
    }

    private static String name(MechInternal internal) {
        return internal == null ? "-" : internal.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String f(float value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
