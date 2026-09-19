package com.alexlogvin.blockieseconomy.platform;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * Whether a command source is allowed to run the admin half of {@code /shop}.
 *
 * <p>The level itself is a server config value, so that an admin can decide whether the
 * economy commands sit with the ordinary operator commands or above them. Only the check
 * forks: 1.21.11 replaced the plain level test with a permission set holding named
 * permissions, of which "has at least this command level" is one.
 *
 * <p>The 1.21.11 form. Permissions became objects rather than a number, and the one asking
 * for a command level is what the older test meant.
 */
public final class CommandPermissions {

    private CommandPermissions() {
    }

    /** Whether {@code source} holds at least operator level {@code level}. */
    public static boolean atLeast(CommandSourceStack source, int level) {
        return source.permissions().hasPermission(
                new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }
}
