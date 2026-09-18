package com.alexlogvin.blockieseconomy.config;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.platform.Services;
import java.nio.file.Path;

/**
 * Where the mod's files live.
 *
 * <pre>
 * config/blockies_economy/
 *   server.toml            economy rules, limits, permissions
 *   client.toml            HUD placement and appearance
 *   prices.toml            hand-authored prices; the admin has the final say
 *   advancements.toml      advancement prize overrides
 *   whitelist.toml         optional; empty means no whitelist applies
 *   prices.d/              drop-in price add-ons, e.g. create.toml
 *   generated/             machine-written output, safe to delete
 *     prices.toml          the solved table
 *     transactions.log     audit log, when enabled
 * </pre>
 *
 * <p>Generated output sits in its own folder deliberately. Putting it beside the
 * hand-edited files invites an admin to edit it and lose the work on the next rebuild.
 */
public final class ConfigPaths {

    public static final String SERVER_FILE = "server.toml";
    public static final String CLIENT_FILE = "client.toml";
    public static final String PRICES_FILE = "prices.toml";
    public static final String ADVANCEMENTS_FILE = "advancements.toml";
    public static final String WHITELIST_FILE = "whitelist.toml";
    public static final String PRICES_DROPIN_DIR = "prices.d";
    public static final String GENERATED_DIR = "generated";
    public static final String GENERATED_PRICES_FILE = "prices.toml";
    public static final String TRANSACTION_LOG_FILE = "transactions.log";

    private ConfigPaths() {
    }

    public static Path root() {
        return Services.PLATFORM.configDir().resolve(BlockiesEconomy.MOD_ID);
    }

    public static Path server() {
        return root().resolve(SERVER_FILE);
    }

    public static Path client() {
        return root().resolve(CLIENT_FILE);
    }

    public static Path prices() {
        return root().resolve(PRICES_FILE);
    }

    public static Path advancements() {
        return root().resolve(ADVANCEMENTS_FILE);
    }

    public static Path whitelist() {
        return root().resolve(WHITELIST_FILE);
    }

    public static Path priceDropIns() {
        return root().resolve(PRICES_DROPIN_DIR);
    }

    public static Path generated() {
        return root().resolve(GENERATED_DIR);
    }

    public static Path generatedPrices() {
        return generated().resolve(GENERATED_PRICES_FILE);
    }

    public static Path transactionLog() {
        return generated().resolve(TRANSACTION_LOG_FILE);
    }
}
