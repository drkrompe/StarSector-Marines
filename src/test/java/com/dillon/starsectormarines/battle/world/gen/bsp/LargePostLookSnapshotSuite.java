package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.DefensePostLayoutDef;
import com.dillon.starsectormarines.battle.turret.DefensePostLayoutRegistry;
import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Opt-in look study for the large defence post's layouts (`ground-turret-tiers.md`): each shipped LARGE layout, and
 * any draft layout written here, stamped by the production stamper onto real generated ground with a marine squad
 * beside it for scale, its guns spawned through the production post path. Drafts are whole layout entries in the
 * catalog's own form, so one that is chosen moves into the catalog as it stands.
 * {@code -Dsnapshot.largePosts=} names the layouts to draw (variant keys, comma-separated; default all).
 */
public final class LargePostLookSnapshotSuite implements SnapshotSuite {

    /** Draft layouts not in the catalog yet, by variant key. */
    private static final Map<String, String> DRAFTS = new LinkedHashMap<>();
    static {
        // The wedge with the gaps in its 5x3 box paved, so the post reads as one built piece.
        DRAFTS.put("wedge-paved", """
                {"id":"layout.draft-wedge-paved","tier":"large","variant":"wedge-paved","anchor":[0,0],
                 "bounds":{"minOffset":[-2,-1],"maxOffset":[2,1]},
                 "cells":[
                  {"offset":[-2,1],"kind":"barrier","appearance":"concrete","facing":[-1,1]},
                  {"offset":[-1,1],"kind":"barrier","appearance":"concrete","facing":[0,1]},
                  {"offset":[0,1],"kind":"barrier","appearance":"concrete","facing":[0,1]},
                  {"offset":[1,1],"kind":"barrier","appearance":"concrete","facing":[0,1]},
                  {"offset":[2,1],"kind":"barrier","appearance":"concrete","facing":[1,1]},
                  {"offset":[-1,0],"kind":"barrier","appearance":"concrete","facing":[-1,0]},
                  {"offset":[1,0],"kind":"barrier","appearance":"concrete","facing":[1,0]},
                  {"offset":[0,-1],"kind":"barrier","appearance":"concrete","facing":[0,-1]},
                  {"offset":[0,0],"kind":"pad"},
                  {"offset":[-2,0],"kind":"apron"},{"offset":[2,0],"kind":"apron"},
                  {"offset":[-2,-1],"kind":"apron"},{"offset":[-1,-1],"kind":"apron"},
                  {"offset":[1,-1],"kind":"apron"},{"offset":[2,-1],"kind":"apron"}],
                 "turrets":[{"offset":[0,0],"structure":"structure.turret-hephaestus"}]}
                """);
        // A horseshoe: parapets on the front and both flanks, the back open and paved for the crew's way in.
        DRAFTS.put("horseshoe", """
                {"id":"layout.draft-horseshoe","tier":"large","variant":"horseshoe","anchor":[0,0],
                 "bounds":{"minOffset":[-1,-1],"maxOffset":[1,1]},
                 "cells":[
                  {"offset":[-1,1],"kind":"barrier","appearance":"concrete","facing":[-1,1]},
                  {"offset":[0,1],"kind":"barrier","appearance":"concrete","facing":[0,1]},
                  {"offset":[1,1],"kind":"barrier","appearance":"concrete","facing":[1,1]},
                  {"offset":[-1,0],"kind":"barrier","appearance":"concrete","facing":[-1,0]},
                  {"offset":[1,0],"kind":"barrier","appearance":"concrete","facing":[1,0]},
                  {"offset":[0,0],"kind":"pad"},
                  {"offset":[-1,-1],"kind":"apron"},{"offset":[0,-1],"kind":"apron"},{"offset":[1,-1],"kind":"apron"}],
                 "turrets":[{"offset":[0,0],"structure":"structure.turret-hephaestus"}]}
                """);
    }

    @Override public String id() { return "large-post-look"; }

    @Override public String label() { return "Large defence post layouts (look study)"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        Map<String, DefensePostLayoutDef> layouts = new LinkedHashMap<>();
        for (DefensePostLayoutDef layout : DefensePostLayoutRegistry.requireInstalled().layoutsFor(DefensePostKind.LARGE)) {
            if (!layout.variant.startsWith("aa-")) layouts.put(layout.variant, layout);
        }
        JSONArray drafts = new JSONArray();
        for (String json : DRAFTS.values()) drafts.put(new JSONObject(json));
        DefensePostLayoutRegistry draftRegistry = new DefensePostLayoutRegistry();
        draftRegistry.ingest(new JSONObject().put("layouts", drafts), TurretCatalogRegistry.installed());
        for (String key : DRAFTS.keySet()) layouts.put(key, draftRegistry.require(DefensePostKind.LARGE, key));

        String selected = System.getProperty("snapshot.largePosts", "");
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        for (Map.Entry<String, DefensePostLayoutDef> entry : layouts.entrySet()) {
            if (!selected.isBlank() && !List.of(selected.split(",")).contains(entry.getKey())) continue;
            artifacts.addAll(LookReview.record(context.modRoot(), shot(entry.getKey(), entry.getValue())));
        }
        return artifacts;
    }

    /** A 30x18 window with the post on the open field east of its middle and a six-marine squad standing south-west of it for scale. */
    private static LookReview.Shot shot(String name, DefensePostLayoutDef layout) {
        LookGround ground = LookGround.of(SurfacePalette.ARID, 7L);
        return new LookReview.Shot(name, ground, 30, 18, placement -> {
            float[] at = placement.openNear(21.5f, 7f);
            DefensePost post = DefensePostStamper.stampPost(placement.map().grid, placement.map().topology,
                    placement.map().doodads, layout, (int) at[0], (int) at[1], EmplacementStyle.BRUTALIST);
            SceneBuilder builder = placement.builder().posts(List.of(post));
            float[] squad = placement.openNear(7f, 13f);
            builder.squad("squad").faction(Faction.MARINE).size(6)
                    .at((int) squad[0] - placement.site().x0(), (int) squad[1] - placement.site().y0())
                    .primary("weapon.field-rifle").stationary().done();
            placement.holdouts(builder);
            return builder.build().sim();
        }, placement -> List.of(
                LookReview.View.play(placement.site().centreX(), placement.site().centreY()),
                LookReview.View.close("close", placement.site().centreX(), placement.site().centreY(), 22f)),
                1, 2, 1);
    }
}
