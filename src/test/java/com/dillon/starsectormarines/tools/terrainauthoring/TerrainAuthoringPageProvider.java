package com.dillon.starsectormarines.tools.terrainauthoring;
import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageProvider;
public final class TerrainAuthoringPageProvider implements AuthoringPageProvider {
    public String id() { return "terrain"; }
    public String label() { return "Terrain"; }
    public AuthoringPage create(AuthoringPageContext context) throws Exception { return new TerrainAuthoringPage(context); }
}
