package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * One preview card per vault-access variant ({@code ruins-works.md}, Vault access), each on a real empty scattered site
 * that drew it: the look preset at 1920x1080 with roofs lifted, framing the chain from the power hall to the vault's
 * door, its steps numbered on the ground, the power's path drawn, the door's own prompt text in a panel, and the door
 * inset at play zoom once powered. Seeds from {@code -Dsnapshot.ruinsVaultAccess.firstSeed} (default 1) are tried in
 * turn until every variant is found. Badges, lines and panels are a review overlay. Opt-in:
 * {@code -Psnapshot=ruins-vault-access-look}.
 */
public final class RuinsVaultAccessLookSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final int SEEDS = 60;

    @Override public String id() { return "ruins-vault-access-look"; }

    @Override public String label() { return "Ruins works: one card per vault-access variant, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        long first = Long.getLong("snapshot.ruinsVaultAccess.firstSeed", 1L);
        Map<VaultAccess, SnapshotArtifact> cards = new EnumMap<>(VaultAccess.class);
        EnumSet<VaultAccess> wanted = EnumSet.of(VaultAccess.TWO_KEYS, VaultAccess.RECORDS_CODE,
                VaultAccess.CROSSED_FEED);
        for (long seed = first; seed < first + SEEDS && !cards.keySet().containsAll(wanted); seed++) {
            try (BattleSimulation sim = BattleSetup.createRuins(seed, BattleSetup.defaultManifest(), false,
                    OperationTier.FIRST_CONTRACT, RiskLevel.MEDIUM, profile(), FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                    RuinsGrade.SCATTERED, RuinsOccupant.NOBODY)) {
                VaultAccess access = sim.getRuinsWorks().access();
                if (!wanted.contains(access) || cards.containsKey(access)) continue;
                cards.put(access, card(context, sim, seed));
            }
        }
        if (!cards.keySet().containsAll(wanted)) {
            throw new IllegalStateException("no site drew " + EnumSet.complementOf(EnumSet.copyOf(cards.keySet())));
        }
        return new ArrayList<>(cards.values());
    }

    private static SnapshotArtifact card(SnapshotContext context, BattleSimulation sim, long seed) throws Exception {
        for (int t = 0; t < Math.round(3f / BattleSimulation.TICK_DT); t++) sim.advance(BattleSimulation.TICK_DT);
        RuinsWorks works = sim.getRuinsWorks();
        VaultAccess access = works.access();
        SealDoor seal = null;
        for (SealDoor door : works.doors()) if (door.id().equals(RuinsWorks.VAULT_SEAL)) seal = door;
        if (seal == null) throw new IllegalStateException("the site's works have no vault seal");
        int last = works.junctions().size() - 1;
        RuinsWorks.Site junction = works.junctions().get(last);
        float[] door = {seal.middle()[0] + .5f, seal.middle()[1] + .5f};
        float[] hall = at(works.hall()), box = at(junction);
        float[] records = works.records() == null ? null : at(works.records());
        float[] keypad = works.keypad() == null ? null : at(works.keypad());

        float minX = Math.min(hall[0], Math.min(box[0], door[0])), maxX = Math.max(hall[0], Math.max(box[0], door[0]));
        float minY = Math.min(hall[1], Math.min(box[1], door[1])), maxY = Math.max(hall[1], Math.max(box[1], door[1]));
        if (records != null) {
            minX = Math.min(minX, records[0]);
            maxX = Math.max(maxX, records[0]);
            minY = Math.min(minY, records[1]);
            maxY = Math.max(maxY, records[1]);
        }
        float zoom = Math.max(maxX - minX + 30f, (maxY - minY + 34f) * W / H);
        BattleReviewFrameRenderer wide = BattleReviewFrameRenderer.look(context.modRoot(), W, H, (minX + maxX) / 2f,
                (minY + maxY) / 2f - 4f, zoom).withoutRoofs();
        BufferedImage base = wide.render(sim, "");
        BattleCamera camera = wide.cameraOf(sim);
        String shutText = works.vaultStatus(false) + "  " + works.vaultDetail(false);

        // Power the door the variant's way, for the inset and the powered prompt.
        works.graph().restart();
        if (access == VaultAccess.CROSSED_FEED) works.graph().throwJunction(last, Branch.STORE);
        sim.advance(BattleSimulation.TICK_DT);
        BufferedImage inset = BattleReviewFrameRenderer.look(context.modRoot(), 480, 300, door[0], door[1],
                BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 0.3f).withoutRoofs().render(sim, "");
        int sealIndex = works.doors().indexOf(seal);
        String poweredText = works.isOpen(sealIndex) ? "OPEN: the door opens as soon as it is powered."
                : works.vaultStatus(true) + "  " + works.vaultDetail(true);

        String name;
        String file;
        ReviewCard card;
        switch (access) {
            case TWO_KEYS -> {
                name = "Two keys at once";
                file = "a-two-keys";
                card = new ReviewCard(base, camera, inset, "Vault access: two keys at once (site seed " + seed + ")");
                card.power(hall, box, door);
                card.step(hall, 1, "Restart the power hall", "hold 6 s");
                card.step(box, 2, junction.name() + ": feed the way on", "hold 2 s");
                card.pair(records, keypad);
                card.step(records, 3, "Squad 1: key at the records", "hold together 3 s");
                card.step(keypad, 3, "Squad 2: key at the vault keypad", "hold together 3 s");
            }
            case RECORDS_CODE -> {
                name = "The records code";
                file = "b-records-code";
                card = new ReviewCard(base, camera, inset, "Vault access: the records code (site seed " + seed + ")");
                card.power(hall, box, door);
                card.step(records, 1, "Read the records: the vault code", "hold 4 s");
                card.step(hall, 2, "Restart the power hall", "hold 6 s");
                card.step(box, 3, junction.name() + ": feed the way on", "hold 2 s");
                card.step(keypad, 4, "Enter the code at the vault keypad", "hold 3 s, powered");
            }
            default -> {
                name = "Crossed feed";
                file = "c-crossed-feed";
                card = new ReviewCard(base, camera, inset, "Vault access: crossed feed (site seed " + seed + ")");
                card.power(hall, box, door);
                card.step(hall, 1, "Restart the power hall", "hold 6 s");
                card.step(box, 2, junction.name() + ": way on, get the company in", "the bunker opens");
                card.step(door, 3, "Throw " + junction.name() + " to its store side", "the vault door powers");
            }
        }
        card.panel(name, "Shut: " + shutText, "Powered: " + poweredText);
        return new SnapshotArtifact("ruins-vault-access/" + file + ".png", card.done());
    }

    private static float[] at(RuinsWorks.Site site) { return new float[]{site.x() + .5f, site.y() + .5f}; }

    private static TargetProfile profile() {
        return new TargetProfile(0, 0, 0, 0, "", EnumSet.noneOf(EconomicFunction.class), SurfacePalette.ARID,
                SettlementLink.NONE, 0f, 0f, false, false, false, GroundDefences.NONE);
    }
}
