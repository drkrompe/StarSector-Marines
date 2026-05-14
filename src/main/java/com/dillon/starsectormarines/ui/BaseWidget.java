package com.dillon.starsectormarines.ui;

/**
 * Convenience base with a pixel-space bounding rectangle and no-op defaults.
 * Subclasses set {@link #x}, {@link #y}, {@link #w}, {@link #h} and override
 * what they need.
 */
public abstract class BaseWidget implements Widget {

    public float x;
    public float y;
    public float w;
    public float h;

    @Override
    public boolean contains(int px, int py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    @Override
    public void render(float alphaMult) {
    }

    @Override
    public void advance(float dt) {
    }

    @Override
    public void onMouseMove(int px, int py) {
    }

    @Override
    public boolean onMouseDown(int px, int py) {
        return false;
    }

    @Override
    public boolean onMouseUp(int px, int py) {
        return false;
    }
}
