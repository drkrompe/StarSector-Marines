package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.gen.nature.MineralFormation;
import java.nio.file.Path;

/** The irradiated world's husks: two columns (husks, husk spires), rows intact, damaged, destroyed. */
public final class HuskFormationTemplate {
    private HuskFormationTemplate() {}
    public static void main(String[] args) throws Exception {
        PlanetFormationTemplate.write(Path.of(args[0]),"irradiated-formations",
                PlanetFormationTemplate.sheet(MineralFormation.Material.HUSK),true,
                "HuskFormationTemplate: full footprint cuts, two columns, intact/damaged/destroyed rows. MineralFormation polygons own collision and fog masks. No ground backing.");
    }
}
