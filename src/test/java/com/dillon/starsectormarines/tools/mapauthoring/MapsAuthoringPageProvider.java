package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageProvider;

/** The Maps page: the editor for owned mission maps. */
public final class MapsAuthoringPageProvider implements AuthoringPageProvider {
    @Override public String id() { return "maps"; }
    @Override public String label() { return "Maps"; }
    @Override public AuthoringPage create(AuthoringPageContext context) throws Exception { return new MapsAuthoringPage(context); }
}
