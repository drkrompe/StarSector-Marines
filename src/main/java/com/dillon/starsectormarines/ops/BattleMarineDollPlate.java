package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.combat.BodyPart;
import com.dillon.starsectormarines.battle.infantry.IntegralSystemService;
import com.dillon.starsectormarines.battle.infantry.SquadMedic;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.ArmorClass;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.ops.battleview.MarineDollCanvas;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.markup.MarkupSource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.Global;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A controlled Marine's doll as a small read-only plate, the counterpart of a machine's
 * {@link BattleDamageDollPlate} and the same size, so the status panel stands beside either in the
 * same place: the {@link MarineDollCanvas} figure, the kit the Marine carries beside it (special
 * equipment, integral system, and the squad's medic kit when this is the medic) with what is left of
 * each, and two flags beneath, LEG WOUND and ARM WOUND. Display only: it takes no input.
 */
final class BattleMarineDollPlate {

    static final String COMPONENT = "battle-marine-doll";
    static final String COMPONENT_PATH = "data/ui/components/battle/" + COMPONENT + ".mlx";
    static final float DOCUMENT_WIDTH = BattleDamageDollPlate.DOCUMENT_WIDTH;
    static final float DOCUMENT_HEIGHT = BattleDamageDollPlate.DOCUMENT_HEIGHT;
    /** Kit rows the plate has room for. */
    static final int KIT_ROWS = 3;

    private static final String FLAG_OFF = "flag flag-off";
    private static final String FLAG_ON = "flag flag-on";
    private static final String ROW = "kit-row";
    private static final String ROW_SPENT = "kit-row kit-row-spent";
    private static final String ROW_HIDDEN = "kit-row kit-row-hidden";

    /** One item a Marine carries: its name and what is left of it ({@code "2/3"}, {@code "ready"}, {@code "12s"}). */
    record Kit(String name, String count, boolean spent) { }

    /**
     * What the plate shows of one Marine: a power suit or not, armor and health as fractions of their
     * pools, the wounds on each limb, and the kit carried.
     */
    record MarineDoll(boolean powerSuit, boolean hasArmor, float armor, float health,
                      int armWounds, int legWounds, List<Kit> kit) {
        MarineDoll {
            kit = List.copyOf(kit);
        }

        /** Reads Marine {@code id} out of the battle. */
        static MarineDoll read(BattleSimulation sim, long id, Function<String, String> copy) {
            World world = sim.world();
            boolean armored = world.hasArmor(id);
            List<Kit> kit = new ArrayList<>(KIT_ROWS);
            SpecialEquipmentDef special = world.hasSecondaryWeapon(id) ? world.specialEquipment(id) : null;
            if (special != null) {
                int left = world.secondaryAmmo(id);
                kit.add(special.usesAmmunition()
                        ? new Kit(special.displayName(), left + "/" + special.startingAmmo(), left <= 0)
                        : new Kit(special.displayName(), clock(world.secondaryCooldownTimer(id), copy), false));
            }
            IntegralSystemService systems = sim.integralSystems();
            IntegralSystemDef system = systems.has(id) ? systems.spec(id) : null;
            if (system != null) {
                int left = systems.ammo(id);
                kit.add(system.usesAmmunition()
                        ? new Kit(system.displayName(), left + "/" + system.startingAmmo(), left <= 0)
                        : new Kit(system.displayName(), clock(systems.cooldownRemaining(id), copy), false));
            }
            Squad squad = sim.squad().hasSquad(id) ? sim.getSquad(sim.squad().squadId(id)) : null;
            SquadMedic medic = squad != null ? squad.medic : null;
            if (medic != null && medic.medicId() == id) {
                int left = medic.supplies();
                kit.add(new Kit(copy.apply("battleMarineDollMedic"), left + "/" + medic.medicine.supplies(), left <= 0));
            }
            return new MarineDoll(sim.identity().armorClass(id) == ArmorClass.POWER_SUIT, armored,
                    armored ? fraction(world.armor(id), world.maxArmor(id)) : 0f,
                    fraction(world.hp(id), world.maxHp(id)),
                    sim.wounds().wounds(id, BodyPart.ARMS), sim.wounds().wounds(id, BodyPart.LEGS), kit);
        }

        private static String clock(float remaining, Function<String, String> copy) {
            return remaining > 0f ? (int) Math.ceil(remaining) + "s" : copy.apply("battleMarineDollReady");
        }

        private static float fraction(float value, float max) {
            return max > 0f ? Math.max(0f, Math.min(1f, value / max)) : 0f;
        }
    }

    private final Reactor reactor = new Reactor();
    private final boolean showKit;
    private final MutableSignal<String> padClasses;
    private final MutableSignal<String> kitClasses;
    private final List<MutableSignal<String>> rowClasses = new ArrayList<>(KIT_ROWS);
    private final List<MutableSignal<String>> rowNames = new ArrayList<>(KIT_ROWS);
    private final List<MutableSignal<String>> rowCounts = new ArrayList<>(KIT_ROWS);
    private final MutableSignal<String> armClasses = reactor.signal(FLAG_OFF);
    private final MutableSignal<String> legClasses = reactor.signal(FLAG_OFF);
    private final MarineDollCanvas.View view = new MarineDollCanvas.View();
    private final MarkupInstance markup;
    private final UiDocument document;
    private MarineDoll shown;

    BattleMarineDollPlate() {
        this(path -> Global.getSettings().loadText(path), Strings::get, true);
    }

    /** @param showKit whether the kit column stands beside the figure; without it the figure is centred */
    BattleMarineDollPlate(MarkupSource source, Function<String, String> copy, boolean showKit) {
        this.showKit = showKit;
        padClasses = reactor.signal(showKit ? "pad pad-hidden" : "pad");
        kitClasses = reactor.signal(showKit ? "kit" : "kit kit-hidden");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("padClasses", padClasses);
        props.put("kitClasses", kitClasses);
        for (int i = 0; i < KIT_ROWS; i++) {
            rowClasses.add(reactor.signal(ROW_HIDDEN));
            rowNames.add(reactor.signal(""));
            rowCounts.add(reactor.signal(""));
            props.put("kit" + i + "Classes", rowClasses.get(i));
            props.put("kit" + i + "Name", rowNames.get(i));
            props.put("kit" + i + "Count", rowCounts.get(i));
        }
        props.put("arm", reactor.signal(copy.apply("battleMarineDollArmWound")));
        props.put("leg", reactor.signal(copy.apply("battleMarineDollLegWound")));
        props.put("armClasses", armClasses);
        props.put("legClasses", legClasses);
        MarkupLoader loader = new MarkupLoader(source, List.of(COMPONENT_PATH));
        markup = loader.reloadAndBuild(reactor, COMPONENT, props);
        document = BattleDirectControlOverlay.document(markup);
        document.layout(DOCUMENT_WIDTH, DOCUMENT_HEIGHT);
        document.canvases().set(markup.requireElement("marine-doll-canvas"), new MarineDollCanvas(view));
    }

    /** Shows {@code doll}, or hides the plate when null. */
    void update(MarineDoll doll) {
        if (doll == null) {
            clear();
            return;
        }
        if (doll.equals(shown)) return;
        shown = doll;
        view.read(doll.powerSuit(), doll.hasArmor(), doll.armor(), doll.health(), doll.armWounds(), doll.legWounds());
        for (int i = 0; i < KIT_ROWS; i++) {
            Kit kit = showKit && i < doll.kit().size() ? doll.kit().get(i) : null;
            rowClasses.get(i).set(kit == null ? ROW_HIDDEN : kit.spent() ? ROW_SPENT : ROW);
            rowNames.get(i).set(kit == null ? "" : kit.name());
            rowCounts.get(i).set(kit == null ? "" : kit.count());
        }
        armClasses.set(doll.armWounds() > 0 ? FLAG_ON : FLAG_OFF);
        legClasses.set(doll.legWounds() > 0 ? FLAG_ON : FLAG_OFF);
        markup.flush();
    }

    void clear() {
        shown = null;
        view.clear();
    }

    boolean visible() {
        return view.present();
    }

    UiDocument document() {
        return document;
    }

    MarkupInstance markup() {
        return markup;
    }

    void render(UiViewport viewport, float alphaMult) {
        if (view.present() && viewport != null) document.render(viewport, alphaMult);
    }

    BattleHudSurface surface(UiViewport viewport) {
        return view.present() && viewport != null ? new BattleHudSurface(document, viewport) : null;
    }
}
