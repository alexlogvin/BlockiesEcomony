package com.alexlogvin.blockieseconomy.config;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.core.toml.TomlDocument;
import com.alexlogvin.blockieseconomy.core.toml.TomlException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reading and writing the mod's TOML files.
 *
 * <p>Two rules throughout: a malformed or unreadable file never crashes the server, and a
 * write never destroys a file it failed to finish. A broken config should cost the admin a
 * log line, not a world.
 */
public final class TomlFiles {

    private TomlFiles() {
    }

    /**
     * Reads a document, or returns an empty one if the file is missing or malformed.
     *
     * <p>A malformed file is reported and left untouched so the admin can fix their typo,
     * rather than being silently overwritten with defaults.
     */
    public static TomlDocument read(Path path) {
        if (!Files.isRegularFile(path)) {
            return new TomlDocument();
        }
        try {
            return TomlDocument.parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        } catch (TomlException e) {
            BlockiesEconomy.LOGGER.error(
                    "Could not parse {} ({}). Using defaults for this file; your file has "
                            + "been left alone so you can correct it.",
                    path.getFileName(), e.getMessage());
            return new TomlDocument();
        } catch (IOException e) {
            BlockiesEconomy.LOGGER.error("Could not read {}: {}", path, e.toString());
            return new TomlDocument();
        }
    }

    /**
     * Writes a document atomically: to a temporary file first, then moved into place.
     *
     * <p>A crash partway through a write would otherwise leave a truncated config, and the
     * generated price table can be large enough for that window to be real.
     */
    public static boolean write(Path path, TomlDocument document) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(temp, document.write().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                // Some filesystems (and Windows, when the target is open) refuse an atomic
                // move. A plain replace is still better than writing in place.
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            BlockiesEconomy.LOGGER.error("Could not write {}: {}", path, e.toString());
            return false;
        }
    }

    /** Writes the file only if it does not exist yet, so first run seeds the defaults. */
    public static boolean writeIfAbsent(Path path, TomlDocument document) {
        if (Files.exists(path)) {
            return false;
        }
        BlockiesEconomy.LOGGER.info("Creating {}", path.getFileName());
        return write(path, document);
    }

    /**
     * Every {@code .toml} file in a drop-in directory, sorted by name.
     *
     * <p>Sorted so the merge order is deterministic: two packs pricing the same item must
     * resolve the same way on every server, not by directory-listing luck.
     */
    public static List<Path> listDropIns(Path directory) {
        if (!Files.isDirectory(directory)) {
            return Collections.emptyList();
        }
        List<Path> found = new ArrayList<Path>();
        try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                            .endsWith(".toml"))
                    .forEach(found::add);
        } catch (IOException e) {
            BlockiesEconomy.LOGGER.error("Could not list {}: {}", directory, e.toString());
            return Collections.emptyList();
        }
        Collections.sort(found);
        return found;
    }

    /** Creates the mod's directory tree, including the drop-in and generated folders. */
    public static void ensureDirectories() {
        try {
            Files.createDirectories(ConfigPaths.priceDropIns());
            Files.createDirectories(ConfigPaths.generated());
        } catch (IOException e) {
            BlockiesEconomy.LOGGER.error("Could not create config directories: {}", e.toString());
        }
    }
}
