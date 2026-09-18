package com.alexlogvin.blockieseconomy.core.config;

/**
 * Where the balance sits on screen.
 *
 * <p>Nine positions rather than four corners, because HUD real estate is contested: other
 * mods park things in the corners, and a player who needs to dodge one of them should not
 * have to fight the offset to do it.
 */
public enum HudAnchor {
    TOP_LEFT(0.0f, 0.0f),
    TOP_CENTER(0.5f, 0.0f),
    TOP_RIGHT(1.0f, 0.0f),
    MIDDLE_LEFT(0.0f, 0.5f),
    MIDDLE_CENTER(0.5f, 0.5f),
    MIDDLE_RIGHT(1.0f, 0.5f),
    BOTTOM_LEFT(0.0f, 1.0f),
    BOTTOM_CENTER(0.5f, 1.0f),
    BOTTOM_RIGHT(1.0f, 1.0f);

    private final float horizontal;
    private final float vertical;

    HudAnchor(float horizontal, float vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    /** 0 at the left edge, 1 at the right. */
    public float horizontal() {
        return horizontal;
    }

    /** 0 at the top edge, 1 at the bottom. */
    public float vertical() {
        return vertical;
    }

    /** True when the element should be right-aligned against its anchor point. */
    public boolean isRightAligned() {
        return horizontal == 1.0f;
    }

    public boolean isCentered() {
        return horizontal == 0.5f;
    }

    /**
     * Screen x for an element of the given width, before the configured offset.
     *
     * @param screenWidth  the scaled screen width
     * @param elementWidth the rendered width of the balance text
     * @param margin       padding kept away from the screen edge
     */
    public int x(int screenWidth, int elementWidth, int margin) {
        if (isCentered()) {
            return (screenWidth - elementWidth) / 2;
        }
        return isRightAligned() ? screenWidth - elementWidth - margin : margin;
    }

    /** Screen y for an element of the given height, before the configured offset. */
    public int y(int screenHeight, int elementHeight, int margin) {
        if (vertical == 0.5f) {
            return (screenHeight - elementHeight) / 2;
        }
        return vertical == 1.0f ? screenHeight - elementHeight - margin : margin;
    }

    /** Parses a config value, falling back to {@code fallback} for anything unrecognised. */
    public static HudAnchor parse(String name, HudAnchor fallback) {
        if (name == null) {
            return fallback;
        }
        String normalised = name.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        for (HudAnchor anchor : values()) {
            if (anchor.name().equals(normalised)) {
                return anchor;
            }
        }
        return fallback;
    }
}
