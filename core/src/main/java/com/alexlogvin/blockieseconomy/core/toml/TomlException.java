package com.alexlogvin.blockieseconomy.core.toml;

/** Thrown when a TOML document cannot be parsed or a value has the wrong type. */
public class TomlException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TomlException(String message) {
        super(message);
    }

    public TomlException(String message, int line) {
        super("line " + line + ": " + message);
    }
}
