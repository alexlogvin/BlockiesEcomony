package com.alexlogvin.blockieseconomy.net;

/**
 * Every channel the mod uses, and which way each one travels.
 *
 * <p>The lists are not documentation: NeoForge's payload registrar wants each channel
 * declared with its direction at registration time, before any handler exists. Keeping the
 * declaration here means a new channel is added in one place rather than in four loader
 * classes that would otherwise drift apart.
 */
public final class Channels {

    /** C2S, sent once on join: "this is the price table I already have cached". */
    public static final String HELLO = "hello";

    /** C2S: buy or sell. Carries no prices — the server has those and does not trust ours. */
    public static final String TRADE = "trade";

    /** S2C: the price table, as unchanged, delta or full. Chunked. */
    public static final String PRICES = "prices";

    /** S2C: this player's balance, after any change. */
    public static final String BALANCE = "balance";

    /** S2C: the outcome of a trade, so the shop screen can show it without a chat message. */
    public static final String RESULT = "result";

    public static final String[] SERVERBOUND = {HELLO, TRADE};

    /** S2C: open the shop screen, sent in answer to {@code /shop ui}. */
    public static final String OPEN = "open";

    public static final String[] CLIENTBOUND = {PRICES, BALANCE, RESULT, OPEN};

    /**
     * Bumped when any payload layout changes.
     *
     * <p>Sent in the hello so a server can tell an out-of-date client apart from a broken
     * one, and decline to send it a table it would misparse.
     */
    public static final int PROTOCOL_VERSION = 1;

    private Channels() {
    }
}
