package com.alexlogvin.blockieseconomy.fabric;

import com.alexlogvin.blockieseconomy.platform.ServerEvents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/**
 * Static hand-off between the advancement Mixin and the rest of the mod.
 *
 * <p>Fabric API has no advancement-earned event, so one has to be mixed in. A Mixin class
 * cannot hold mod state cleanly, so it calls {@link #fire} here instead.
 *
 * <p>The Mixin itself lives in {@code src/fabric-<mcversion>} because its target signature
 * changed: 1.20.1 has {@code award(Advancement, String)} while 1.21.1 has
 * {@code award(AdvancementHolder, String)}.
 */
public final class AdvancementBridge {

    private static final List<ServerEvents.AdvancementListener> LISTENERS =
            new ArrayList<ServerEvents.AdvancementListener>();

    private AdvancementBridge() {
    }

    public static void register(ServerEvents.AdvancementListener listener) {
        LISTENERS.add(listener);
    }

    /** Called from the Mixin once an advancement has actually been granted. */
    public static void fire(ServerPlayer player, String advancementId) {
        if (player == null || advancementId == null) {
            return;
        }
        for (int i = 0; i < LISTENERS.size(); i++) {
            LISTENERS.get(i).onEarned(player, advancementId);
        }
    }
}
