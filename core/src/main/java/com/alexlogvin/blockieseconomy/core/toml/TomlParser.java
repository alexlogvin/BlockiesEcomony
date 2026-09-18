package com.alexlogvin.blockieseconomy.core.toml;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the TOML subset this mod uses.
 *
 * <p>Comments are attached to the entry or table that follows them, so a rewritten
 * document keeps an admin's annotations in place.
 */
final class TomlParser {

    private final String[] lines;
    private int index;

    TomlParser(String text) {
        this.lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    }

    TomlDocument parse() {
        TomlDocument doc = new TomlDocument();
        TomlTable current = doc.root();
        List<String> pending = new ArrayList<String>();
        boolean seenContent = false;

        while (index < lines.length) {
            String raw = lines[index];
            int lineNo = index + 1;
            index++;

            String line = raw.trim();

            if (line.isEmpty()) {
                // A blank line ends a comment block that is not attached to anything,
                // which is how a file-level header is told apart from an entry's doc.
                if (!seenContent && !pending.isEmpty()) {
                    doc.headerComments().addAll(pending);
                    pending.clear();
                }
                continue;
            }

            if (line.charAt(0) == '#') {
                pending.add(stripComment(line));
                continue;
            }

            if (line.charAt(0) == '[') {
                int close = line.indexOf(']');
                if (close < 0) {
                    throw new TomlException("unterminated table header", lineNo);
                }
                String name = line.substring(1, close).trim();
                if (name.isEmpty()) {
                    throw new TomlException("empty table name", lineNo);
                }
                current = doc.table(unquoteKey(name, lineNo));
                current.comments().addAll(pending);
                pending.clear();
                seenContent = true;
                continue;
            }

            int eq = findAssignment(line);
            if (eq < 0) {
                throw new TomlException("expected a key = value pair but found: " + line, lineNo);
            }
            String key = unquoteKey(line.substring(0, eq).trim(), lineNo);
            String rest = line.substring(eq + 1).trim();

            String inline = null;
            int hash = findInlineComment(rest);
            if (hash >= 0) {
                inline = stripComment(rest.substring(hash));
                rest = rest.substring(0, hash).trim();
            }
            if (rest.isEmpty()) {
                throw new TomlException("missing value for key " + key, lineNo);
            }

            TomlEntry entry = current.put(key, parseValue(rest, lineNo));
            entry.comments().addAll(pending);
            entry.setInlineComment(inline);
            pending.clear();
            seenContent = true;
        }

        // Trailing comments with nothing after them belong to the file header when the
        // document is otherwise empty; otherwise they are dropped rather than misfiled.
        if (!seenContent && !pending.isEmpty()) {
            doc.headerComments().addAll(pending);
        }
        return doc;
    }

    private static String stripComment(String s) {
        String c = s.startsWith("#") ? s.substring(1) : s;
        return c.startsWith(" ") ? c.substring(1) : c;
    }

    /** Index of the assignment separating key from value, ignoring one inside a quoted key. */
    private static int findAssignment(String line) {
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == '=' && !inQuotes) {
                return i;
            }
        }
        return -1;
    }

    /** Index of a hash that starts a comment, ignoring one inside a string literal. */
    private static int findInlineComment(String s) {
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            } else if (c == '#' && !inQuotes) {
                return i;
            }
        }
        return -1;
    }

    private static String unquoteKey(String key, int lineNo) {
        if (key.length() >= 2 && key.charAt(0) == '"' && key.charAt(key.length() - 1) == '"') {
            return unescape(key.substring(1, key.length() - 1), lineNo);
        }
        if (key.isEmpty()) {
            throw new TomlException("empty key", lineNo);
        }
        return key;
    }

    private static TomlValue parseValue(String text, int lineNo) {
        char first = text.charAt(0);

        if (first == '"') {
            if (text.length() < 2 || text.charAt(text.length() - 1) != '"') {
                throw new TomlException("unterminated string: " + text, lineNo);
            }
            return TomlValue.parsed(TomlValue.Kind.STRING,
                    unescape(text.substring(1, text.length() - 1), lineNo), text);
        }

        if (first == '[') {
            if (text.charAt(text.length() - 1) != ']') {
                throw new TomlException("unterminated array: " + text, lineNo);
            }
            List<TomlValue> items = new ArrayList<TomlValue>();
            List<String> parts = splitArray(text.substring(1, text.length() - 1), lineNo);
            for (int i = 0; i < parts.size(); i++) {
                String trimmed = parts.get(i).trim();
                if (!trimmed.isEmpty()) {
                    items.add(parseValue(trimmed, lineNo));
                }
            }
            return TomlValue.parsed(TomlValue.Kind.ARRAY, items, text);
        }

        if ("true".equals(text) || "false".equals(text)) {
            return TomlValue.parsed(TomlValue.Kind.BOOLEAN, Boolean.valueOf("true".equals(text)), text);
        }

        String numeric = text.replace("_", "");
        try {
            if (numeric.indexOf('.') >= 0 || numeric.indexOf('e') >= 0 || numeric.indexOf('E') >= 0) {
                return TomlValue.parsed(TomlValue.Kind.FLOAT, Double.valueOf(numeric), text);
            }
            return TomlValue.parsed(TomlValue.Kind.INTEGER, Long.valueOf(numeric), text);
        } catch (NumberFormatException e) {
            throw new TomlException("unrecognised value: " + text, lineNo);
        }
    }

    private static List<String> splitArray(String body, int lineNo) {
        List<String> parts = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            }
            if (!inQuotes) {
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    parts.add(sb.toString());
                    sb.setLength(0);
                    continue;
                }
            }
            sb.append(c);
        }
        if (inQuotes) {
            throw new TomlException("unterminated string inside array", lineNo);
        }
        parts.add(sb.toString());
        return parts;
    }

    private static String unescape(String s, int lineNo) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (++i >= s.length()) {
                throw new TomlException("trailing backslash in string", lineNo);
            }
            char e = s.charAt(i);
            switch (e) {
                case 'n':
                    sb.append('\n');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case '"':
                    sb.append('"');
                    break;
                case '\\':
                    sb.append('\\');
                    break;
                case 'u':
                    if (i + 4 >= s.length()) {
                        throw new TomlException("truncated unicode escape", lineNo);
                    }
                    sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                    i += 4;
                    break;
                default:
                    throw new TomlException("unknown escape sequence: " + e, lineNo);
            }
        }
        return sb.toString();
    }
}
