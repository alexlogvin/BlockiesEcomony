package com.alexlogvin.blockieseconomy.core.toml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A TOML table: the root table, or one introduced by a {@code [header]}. */
public final class TomlTable {

    private final String name;
    private final List<String> comments = new ArrayList<String>();
    private final Map<String, TomlEntry> entries = new LinkedHashMap<String, TomlEntry>();

    TomlTable(String name) {
        this.name = name;
    }

    /** Table name, or {@code ""} for the root table. */
    public String name() {
        return name;
    }

    /** Comment lines immediately above the {@code [header]}. */
    public List<String> comments() {
        return comments;
    }

    public Collection<TomlEntry> entries() {
        return entries.values();
    }

    public boolean has(String key) {
        return entries.containsKey(key);
    }

    public TomlEntry get(String key) {
        return entries.get(key);
    }

    public TomlEntry put(String key, TomlValue value) {
        TomlEntry existing = entries.get(key);
        if (existing != null) {
            existing.setValue(value);
            return existing;
        }
        TomlEntry entry = new TomlEntry(key, value);
        entries.put(key, entry);
        return entry;
    }

    public TomlEntry remove(String key) {
        return entries.remove(key);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
