package com.alexlogvin.blockieseconomy.core.toml;

import java.util.Iterator;
import java.util.List;

/**
 * Serialises a {@link TomlDocument} back to text, keeping comments and entry order.
 *
 * <p>Values that were parsed and never modified are written back from their original
 * source text, so reformatting does not churn an admin's file.
 */
final class TomlWriter {

    private static final String NEWLINE = "\n";

    String write(TomlDocument doc) {
        StringBuilder sb = new StringBuilder(1024);

        writeComments(sb, doc.headerComments());
        if (!doc.headerComments().isEmpty() && !doc.root().isEmpty()) {
            sb.append(NEWLINE);
        }

        writeEntries(sb, doc.root());

        Iterator<TomlTable> it = doc.tables().iterator();
        while (it.hasNext()) {
            TomlTable table = it.next();
            if (table.name().isEmpty()) {
                continue;
            }
            // Exactly one blank line between blocks, never two.
            if (sb.length() > 0) {
                if (sb.charAt(sb.length() - 1) != NEWLINE.charAt(0)) {
                    sb.append(NEWLINE);
                }
                sb.append(NEWLINE);
            }
            writeComments(sb, table.comments());
            sb.append('[').append(escapeKey(table.name())).append(']').append(NEWLINE);
            writeEntries(sb, table);
        }

        return sb.toString();
    }

    private static void writeEntries(StringBuilder sb, TomlTable table) {
        Iterator<TomlEntry> it = table.entries().iterator();
        boolean first = true;
        while (it.hasNext()) {
            TomlEntry entry = it.next();
            // A commented entry gets a blank line above it so blocks stay readable.
            if (!first && !entry.comments().isEmpty()) {
                sb.append(NEWLINE);
            }
            first = false;
            writeComments(sb, entry.comments());
            sb.append(escapeKey(entry.key())).append(" = ").append(render(entry.value()));
            if (entry.inlineComment() != null) {
                sb.append("  # ").append(entry.inlineComment());
            }
            sb.append(NEWLINE);
        }
    }

    private static void writeComments(StringBuilder sb, List<String> comments) {
        for (int i = 0; i < comments.size(); i++) {
            String c = comments.get(i);
            if (c.isEmpty()) {
                sb.append('#').append(NEWLINE);
            } else {
                sb.append("# ").append(c).append(NEWLINE);
            }
        }
    }

    private static String render(TomlValue value) {
        if (value.raw() != null) {
            return value.raw();
        }
        switch (value.kind()) {
            case STRING:
                return quote(value.asString());
            case INTEGER:
                return Long.toString(value.asLong());
            case FLOAT:
                return renderDouble(value.asDouble());
            case BOOLEAN:
                return Boolean.toString(value.asBoolean());
            case ARRAY: {
                StringBuilder sb = new StringBuilder("[");
                List<TomlValue> items = value.asArray();
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(render(items.get(i)));
                }
                return sb.append(']').toString();
            }
            default:
                throw new TomlException("cannot render value of kind " + value.kind());
        }
    }

    /** Keeps whole-number doubles looking like TOML floats (1.0, not 1). */
    private static String renderDouble(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d) + ".0";
        }
        return Double.toString(d);
    }

    private static String escapeKey(String key) {
        boolean bare = !key.isEmpty();
        for (int i = 0; i < key.length() && bare; i++) {
            char c = key.charAt(i);
            bare = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-';
        }
        return bare ? key : quote(key);
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
