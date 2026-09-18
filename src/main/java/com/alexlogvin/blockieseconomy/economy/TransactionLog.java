package com.alexlogvin.blockieseconomy.economy;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.config.ConfigPaths;
import com.alexlogvin.blockieseconomy.core.ledger.TransactionResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Append-only audit log of balance changes.
 *
 * <p>Off by default, because most servers do not need it and it writes on every trade.
 * When a dupe or exploit is suspected it is the only way to reconstruct what happened, so
 * it is worth having the switch.
 *
 * <p>Operator commands are recorded regardless of the setting: an admin quietly setting
 * someone's balance is exactly the thing another admin will later need to find.
 */
public final class TransactionLog {

    /** Rotated once it passes this size, so one long-running server cannot fill a disk. */
    private static final long MAX_BYTES = 16L * 1024L * 1024L;

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final boolean enabled;

    public TransactionLog(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Appends one entry.
     *
     * @param playerName the player's name if known, for readability; the UUID is always
     *                   written because names change and UUIDs do not
     */
    public void record(UUID player, String playerName, TransactionResult result, String detail) {
        if (!enabled && !result.type().isAdmin()) {
            return;
        }
        if (!result.succeeded()) {
            return;
        }

        String line = TIMESTAMP.format(Instant.now())
                + " | " + result.type()
                + " | " + player
                + " | " + (playerName == null ? "-" : playerName)
                + " | " + (result.delta() >= 0 ? "+" : "") + result.delta()
                + " | balance " + result.balanceAfter()
                + " | " + (detail == null ? "" : detail)
                + System.lineSeparator();

        append(line);
    }

    private void append(String line) {
        Path path = ConfigPaths.transactionLog();
        try {
            Files.createDirectories(path.getParent());
            rotateIfLarge(path);
            Files.write(path, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // A failed audit write must never take a trade down with it.
            BlockiesEconomy.LOGGER.warn("Could not write to the transaction log: {}", e.toString());
        }
    }

    private static void rotateIfLarge(Path path) throws IOException {
        if (!Files.exists(path) || Files.size(path) < MAX_BYTES) {
            return;
        }
        Path rotated = path.resolveSibling(path.getFileName() + ".1");
        Files.deleteIfExists(rotated);
        Files.move(path, rotated);
        BlockiesEconomy.LOGGER.info("Rotated the transaction log to {}", rotated.getFileName());
    }
}
