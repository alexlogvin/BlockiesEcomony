package com.alexlogvin.blockieseconomy.neoforge;

import net.minecraft.resources.ResourceLocation;

/**
 * Builds the game’s id type from a string.
 *
 * <p>NeoForge’s own APIs — payload types, GUI layers, reload listeners — are named by the
 * game’s id type, which 1.21.11 renamed from {@code ResourceLocation} to {@code Identifier}.
 * The shared NeoForge glue calls this instead, which lets it pass an id without naming its
 * type: the argument type is inferred from the return type here.
 *
 * <p>The pre-1.21.11 form.
 */
final class Ids {

    private Ids() {
    }

    /** The id for {@code id}, which callers pass as a literal that cannot fail to parse. */
    static ResourceLocation of(String id) {
        return ResourceLocation.tryParse(id);
    }
}
