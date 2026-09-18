package com.alexlogvin.blockieseconomy.core.toml;

import java.util.ArrayList;
import java.util.List;

/** One {@code key = value} line, together with the comment lines above it. */
public final class TomlEntry {

    private final String key;
    private TomlValue value;
    private final List<String> comments = new ArrayList<String>();
    private String inlineComment;

    TomlEntry(String key, TomlValue value) {
        this.key = key;
        this.value = value;
    }

    public String key() {
        return key;
    }

    public TomlValue value() {
        return value;
    }

    public void setValue(TomlValue value) {
        this.value = value;
    }

    /** Comment lines immediately above this entry, without the leading {@code #}. */
    public List<String> comments() {
        return comments;
    }

    public String inlineComment() {
        return inlineComment;
    }

    public void setInlineComment(String inlineComment) {
        this.inlineComment = inlineComment;
    }
}
