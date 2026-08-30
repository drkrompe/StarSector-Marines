package com.dillon.starsectormarines.tools.roomauthoring;

import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageProvider;

/** Registers the room editor with the workbench. */
public final class RoomAuthoringPageProvider implements AuthoringPageProvider {

    @Override
    public String id() {
        return "rooms";
    }

    @Override
    public String label() {
        return "Rooms";
    }

    @Override
    public AuthoringPage create(AuthoringPageContext context) {
        return new RoomAuthoringPage(context);
    }
}
