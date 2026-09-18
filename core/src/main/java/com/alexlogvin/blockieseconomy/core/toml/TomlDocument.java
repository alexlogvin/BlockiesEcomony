package com.alexlogvin.blockieseconomy.core.toml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An ordered, comment-preserving TOML document.
 *
 * <p>Supports the subset this mod needs: tables, bare and quoted keys, strings,
 * integers, floats, booleans and arrays. Deliberately hand-written rather than
 * pulled from a library, because the mod ships with no runtime dependencies and
 * NeoForge already has its own Night Config on the classpath.
 */
public final class TomlDocument {

    private final List<String> headerComments = new ArrayList<String>();
    private final Map<String, TomlTable> tables = new LinkedHashMap<String, TomlTable>();

    public TomlDocument() {
        tables.put("", new TomlTable(""));
    }

    /** Comment block at the very top of the file, above any key or table. */
    public List<String> headerComments() {
        return headerComments;
    }

    public TomlTable root() {
        return tables.get("");
    }

    public TomlTable table(String name) {
        TomlTable t = tables.get(name);
        if (t == null) {
            t = new TomlTable(name);
            tables.put(name, t);
        }
        return t;
    }

    public TomlTable findTable(String name) {
        return tables.get(name);
    }

    public Collection<TomlTable> tables() {
        return tables.values();
    }

    public boolean hasTable(String name) {
        return tables.containsKey(name);
    }

    // ---- convenience accessors -------------------------------------------------

    /** Reads {@code key} from {@code table} ({@code ""} for root), or returns {@code null}. */
    public TomlValue value(String table, String key) {
        TomlTable t = tables.get(table);
        if (t == null) {
            return null;
        }
        TomlEntry e = t.get(key);
        return e == null ? null : e.value();
    }

    public String getString(String table, String key, String fallback) {
        TomlValue v = value(table, key);
        return v == null ? fallback : v.asString();
    }

    public long getLong(String table, String key, long fallback) {
        TomlValue v = value(table, key);
        return v == null ? fallback : v.asLong();
    }

    public double getDouble(String table, String key, double fallback) {
        TomlValue v = value(table, key);
        return v == null ? fallback : v.asDouble();
    }

    public boolean getBoolean(String table, String key, boolean fallback) {
        TomlValue v = value(table, key);
        return v == null ? fallback : v.asBoolean();
    }

    public static TomlDocument parse(String text) {
        return new TomlParser(text).parse();
    }

    public String write() {
        return new TomlWriter().write(this);
    }

    @Override
    public String toString() {
        return write();
    }
}
