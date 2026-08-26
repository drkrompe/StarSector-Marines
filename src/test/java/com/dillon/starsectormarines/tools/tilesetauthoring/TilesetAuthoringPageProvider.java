package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageProvider;

/** Discoverable contribution for turning raw art sheets into loadable tilesets. */
public final class TilesetAuthoringPageProvider implements AuthoringPageProvider {

    @Override public String id() { return "tilesets"; }

    @Override public String label() { return "Tilesets"; }

    @Override
    public AuthoringPage create(AuthoringPageContext context) throws Exception {
        return new TilesetAuthoringPage(context);
    }
}
