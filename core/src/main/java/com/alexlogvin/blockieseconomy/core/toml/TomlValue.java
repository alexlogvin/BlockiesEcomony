package com.alexlogvin.blockieseconomy.core.toml;

import java.util.Collections;
import java.util.List;

/**
 * A single TOML value.
 *
 * <p>The original source text is kept alongside the parsed value so that rewriting a
 * document only reformats the entries that were actually changed. Config files in this
 * mod are hand-edited by server admins, so preserving their formatting matters.
 */
public final class TomlValue {

    public enum Kind { STRING, INTEGER, FLOAT, BOOLEAN, ARRAY }

    private final Kind kind;
    private final Object value;
    private final String raw;

    private TomlValue(Kind kind, Object value, String raw) {
        this.kind = kind;
        this.value = value;
        this.raw = raw;
    }

    public static TomlValue of(String s) {
        return new TomlValue(Kind.STRING, s, null);
    }

    public static TomlValue of(long l) {
        return new TomlValue(Kind.INTEGER, Long.valueOf(l), null);
    }

    public static TomlValue of(double d) {
        return new TomlValue(Kind.FLOAT, Double.valueOf(d), null);
    }

    public static TomlValue of(boolean b) {
        return new TomlValue(Kind.BOOLEAN, Boolean.valueOf(b), null);
    }

    public static TomlValue ofArray(List<TomlValue> items) {
        return new TomlValue(Kind.ARRAY, Collections.unmodifiableList(items), null);
    }

    static TomlValue parsed(Kind kind, Object value, String raw) {
        return new TomlValue(kind, value, raw);
    }

    public Kind kind() {
        return kind;
    }

    /** The source text this value was parsed from, or {@code null} if it was built in code. */
    public String raw() {
        return raw;
    }

    public String asString() {
        if (kind != Kind.STRING) {
            throw new TomlException("expected a string but found " + kind);
        }
        return (String) value;
    }

    public long asLong() {
        if (kind == Kind.INTEGER) {
            return ((Long) value).longValue();
        }
        if (kind == Kind.FLOAT) {
            return (long) ((Double) value).doubleValue();
        }
        throw new TomlException("expected an integer but found " + kind);
    }

    public double asDouble() {
        if (kind == Kind.FLOAT) {
            return ((Double) value).doubleValue();
        }
        if (kind == Kind.INTEGER) {
            return ((Long) value).doubleValue();
        }
        throw new TomlException("expected a number but found " + kind);
    }

    public boolean asBoolean() {
        if (kind != Kind.BOOLEAN) {
            throw new TomlException("expected a boolean but found " + kind);
        }
        return ((Boolean) value).booleanValue();
    }

    @SuppressWarnings("unchecked")
    public List<TomlValue> asArray() {
        if (kind != Kind.ARRAY) {
            throw new TomlException("expected an array but found " + kind);
        }
        return (List<TomlValue>) value;
    }

    /** True for an empty string, which this mod uses to mean "blacklisted". */
    public boolean isEmptyString() {
        return kind == Kind.STRING && ((String) value).isEmpty();
    }

    @Override
    public String toString() {
        return raw != null ? raw : String.valueOf(value);
    }
}
