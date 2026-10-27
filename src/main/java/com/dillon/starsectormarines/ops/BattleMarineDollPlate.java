package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.combat.BodyPart;
import com.dillon.starsectormarines.battle.infantry.CarriedSpecials;
import com.dillon.starsectormarines.battle.infantry.IntegralSystemService;
import com.dillon.starsectormarines.battle.infantry.SquadMedic;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.ui.panel.Ordnance;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.ArmorClass;
import com.dillon.starsectormarines.ops.BattleDirectControlHudModel.RoundIcon;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.SpecialActivation;
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
 * equipment in both slots, hand and belt, integral system, and the squad's medic kit when this is the medic) with what is left of
 * each, and two flags beneath, LEG WOUND and ARM WOUND. Display only: it takes no input.
 */
final class BattleMarineDollPlate {

    static final String COMPONENT = "battle-marine-doll";
    static final String COMPONENT_PATH = "data/ui/components/battle/" + COMPONENT + ".mlx";
    static final float DOCUMENT_WIDTH = BattleDamageDollPlate.DOCUMENT_WIDTH;
    static final float DOCUMENT_HEIGHT = BattleDamageDollPlate.DOCUMENT_HEIGHT;
    /** Kit rows the plate has room for: two special slots, the integral system and the medic kit. */
    static final int KIT_ROWS = 4;

    private static final String FLAG_OFF = "flag flag-off";
    private static final String FLAG_ON = "flag flag-on";
    private static final String ROW = "kit-row";
    private static final String ROW_SPENT = "kit-row kit-row-spent";
    private static final String ROW_HIDDEN = "kit-row kit-row-hidden";

    /** Rounds a kit row draws as icons rather than a count; more than this keeps its number. */
    static final int MAX_KIT_ROUNDS = 6;
    private static final int MISSILE_WIDTH = 6;
    private static final int RIFLE_WIDTH = 8;
    private static final int ROUND_GAP = 2;

    /**
     * One item a Marine carries: its name and what is left of it ({@code "2/3"}, {@code "ready"},
     * {@code "12s"}). {@code round} names the icon a short load draws, one a round ({@code missile} for
     * a rocket, {@code rifle} for a precision rifle's cartridges, {@code grenade} and {@code smoke} for
     * throws), or is null for a load kept as a count. A cooldown item with an icon ({@code shield}) draws
     * it once, beside its clock: lit when ready ({@code left} 1), dimmed while it recharges.
     */
    record Kit(String name, String count, boolean spent, String round, int left, int capacity, boolean clocked) {
        Kit(String name, String count, boolean spent) {
            this(name, count, spent, null, 0, 0, false);
        }

        Kit(String name, String count, boolean spent, String round, int left, int capacity) {
            this(name, count, spent, round, left, capacity, false);
        }

        /** Whether the row draws its load as icons. */
        boolean drawsRounds() {
            return round != null && capacity > 0 && capacity <= MAX_KIT_ROUNDS;
        }

        /** Whether the row keeps its text: a load not drawn, or a clock beside its icon. */
        boolean showsCount() {
            return clocked || !drawsRounds();
        }

        /** The icon a special's load is drawn with: rockets as missiles, a precision rifle's cartridges, throws as themselves. */
        static String round(SpecialEquipmentDef special) {
            if (!special.usesAmmunition()) return null;
            if (special.isThrownGrenade()) return "grenade";
            if (special.isSmokeThrow()) return "smoke";
            if (!special.isDirectFireWeapon()) return null;
            if (Ordnance.of(special.weaponDef()) == Ordnance.MISSILE) return "missile";
            return special.activation() == SpecialActivation.DIRECT_PRECISION ? "rifle" : null;
        }
    }

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
            for (CarriedSpecials.Slot slot : specialOrder(world, id)) {
                SpecialEquipmentDef special = CarriedSpecials.spec(world, id, slot);
                int left = CarriedSpecials.ammo(world, id, slot);
                kit.add(special.usesAmmunition()
                        ? new Kit(special.displayName(), left + "/" + special.startingAmmo(), left <= 0,
                                Kit.round(special), left, special.startingAmmo())
                        : new Kit(special.displayName(), clock(CarriedSpecials.cooldown(world, id, slot), copy), false));
            }
            IntegralSystemService systems = sim.integralSystems();
            IntegralSystemDef system = systems.has(id) ? systems.spec(id) : null;
            // A field-aid kit's row is the medic's pool, below.
            if (system != null && system.fieldAid() == null) {
                int left = systems.ammo(id);
                float cooldown = systems.cooldownRemaining(id);
                kit.add(system.usesAmmunition()
                        ? new Kit(system.displayName(), left + "/" + system.startingAmmo(), left <= 0)
                        : system.grantsMitigation()
                        ? new Kit(system.displayName(), clock(cooldown, copy), false, "shield", cooldown > 0f ? 0 : 1, 1, true)
                        : new Kit(system.displayName(), clock(cooldown, copy), false));
            }
            Squad squad = sim.squad().hasSquad(id) ? sim.getSquad(sim.squad().squadId(id)) : null;
            SquadMedic medic = squad != null ? squad.medicOf(id) : null;
            if (medic != null) {
                int left = (int) Math.floor(medic.energy());
                kit.add(new Kit(copy.apply("battleMarineDollMedic"), left + "/" + Math.round(medic.medicine.energyMax()),
                        left < medic.medicine.minStartEnergy()));
            }
            return new MarineDoll(sim.identity().armorClass(id) == ArmorClass.POWER_SUIT, armored,
                    armored ? fraction(world.armor(id), world.maxArmor(id)) : 0f,
                    fraction(world.hp(id), world.maxHp(id)),
                    sim.wounds().wounds(id, BodyPart.ARMS), sim.wounds().wounds(id, BodyPart.LEGS), kit);
        }

        /**
         * The slots holding an item, in a fixed order whichever slot an item sits in: anything not thrown first,
         * then smoke, then a hand grenade. A belt throw swaps its item into the hand and back, and the rows hold
         * still through it.
         */
        static List<CarriedSpecials.Slot> specialOrder(World world, long id) {
            List<CarriedSpecials.Slot> slots = new ArrayList<>(2);
            if (!world.hasSecondaryWeapon(id)) return slots;
            if (world.specialEquipment(id) != null) slots.add(CarriedSpecials.Slot.HAND);
            if (world.hasBelt(id) && world.beltEquipment(id) != null) slots.add(CarriedSpecials.Slot.BELT);
            slots.sort((a, b) -> Integer.compare(rank(CarriedSpecials.spec(world, id, a)),
                    rank(CarriedSpecials.spec(world, id, b))));
            return slots;
        }

        private static int rank(SpecialEquipmentDef special) {
            return special.isSmokeThrow() ? 1 : special.isThrownGrenade() ? 2 : 0;
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
    private final List<MutableSignal<List<RoundIcon>>> rowRounds = new ArrayList<>(KIT_ROWS);
    private final List<MutableSignal<String>> rowRoundsStyles = new ArrayList<>(KIT_ROWS);
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
            rowRounds.add(reactor.signal(List.of()));
            rowRoundsStyles.add(reactor.signal(roundsStyle(0, 0)));
            props.put("kit" + i + "Rounds", rowRounds.get(i));
            props.put("kit" + i + "RoundsStyle", rowRoundsStyles.get(i));
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
            boolean icons = kit != null && kit.drawsRounds();
            rowCounts.get(i).set(kit == null || !kit.showsCount() ? "" : kit.count());
            rowRounds.get(i).set(icons ? rounds(i, kit) : List.of());
            rowRoundsStyles.get(i).set(icons ? roundsStyle(kit.capacity(), width(kit.round())) : roundsStyle(0, 0));
        }
        armClasses.set(doll.armWounds() > 0 ? FLAG_ON : FLAG_OFF);
        legClasses.set(doll.legWounds() > 0 ? FLAG_ON : FLAG_OFF);
        markup.flush();
    }

    /** A row's load, first to leave on the left, spent rounds as outlines; a clocked item recharging dimmed. */
    private static List<RoundIcon> rounds(int row, Kit kit) {
        List<RoundIcon> rounds = new ArrayList<>(kit.capacity());
        for (int i = 0; i < kit.capacity(); i++) {
            String state = i < kit.left() ? "live" : kit.clocked() ? "loading" : "spent";
            rounds.add(new RoundIcon("marine-doll-kit-" + row + "-round-" + i, "kit-round kit-round-" + kit.round(),
                    "graphics/ui/direct-control/round-" + kit.round() + "-" + state + ".svg"));
        }
        return rounds;
    }

    private static int width(String round) {
        return switch (round) {
            case "missile" -> MISSILE_WIDTH;
            case "rifle" -> RIFLE_WIDTH;
            default -> BattleDirectControlHudModel.itemWidth(round);
        };
    }

    // This layout sizes a row to its own width rather than its children's, so the strip is told it.
    private static String roundsStyle(int count, int width) {
        return "width: " + (count > 0 ? count * width + (count - 1) * ROUND_GAP : 0) + "px;";
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
