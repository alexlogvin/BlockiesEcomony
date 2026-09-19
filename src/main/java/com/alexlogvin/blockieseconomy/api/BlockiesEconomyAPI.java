package com.alexlogvin.blockieseconomy.api;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.config.PriceDeclarations;
import com.alexlogvin.blockieseconomy.core.price.PriceSource;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * How another mod declares prices for its own items.
 *
 * <p>Three routes exist, and a mod author should pick whichever suits them:
 *
 * <ol>
 *   <li><b>A datapack file</b> — {@code data/<yourmod>/blockies_economy/prices.json} inside
 *       your jar. No code, no compile-time dependency on this mod, and it works even if
 *       this mod is not installed. This is the recommended route for most mods.</li>
 *   <li><b>A {@link PriceProvider} service</b> — declared in
 *       {@code META-INF/services/com.alexlogvin.blockieseconomy.api.PriceProvider}. Use
 *       this when prices are computed rather than written down: derived from your own
 *       config, say, or from a tier system.</li>
 *   <li><b>{@link #register(PriceProvider)}</b> — call it from your own initialiser. The
 *       fallback for when service discovery does not reach you, which can happen on
 *       loaders that isolate mods into separate module layers.</li>
 * </ol>
 *
 * <p>Whichever route, declarations rank <em>below</em> the server admin's own files. An
 * admin who overrides your price has decided something about their server that you do not
 * get to override back.
 *
 * <p>Prices are whole Blockies. Declaring a price for an item whose recipe would also price
 * it is fine and normal: the declaration wins, and everything crafted from it is recomputed
 * against the number you gave.
 */
public final class BlockiesEconomyAPI {

    private static final List<PriceProvider> REGISTERED = new ArrayList<PriceProvider>();

    private static boolean servicesLoaded;

    private BlockiesEconomyAPI() {
    }

    /**
     * Registers a provider directly.
     *
     * <p>Safe to call at any point before the first price build, which happens when a world
     * loads. Calling it later is allowed too — it takes effect on the next
     * {@code /shop rebuild}.
     */
    public static synchronized void register(PriceProvider provider) {
        if (provider != null && !REGISTERED.contains(provider)) {
            REGISTERED.add(provider);
            BlockiesEconomy.LOGGER.info("Registered price provider {}.",
                    provider.getClass().getName());
        }
    }

    /**
     * Asks every provider for its prices.
     *
     * <p>Called once per price build. A provider that throws is skipped with a log line
     * naming it: another mod's mistake should cost that mod its prices, not the shop.
     */
    public static synchronized PriceDeclarations collect() {
        loadServicesOnce();

        PriceDeclarations declarations =
                new PriceDeclarations(PriceSource.API, "Java API");
        Registry registry = new Registry(declarations);

        for (int i = 0; i < REGISTERED.size(); i++) {
            PriceProvider provider = REGISTERED.get(i);
            try {
                provider.declarePrices(registry);
            } catch (RuntimeException e) {
                BlockiesEconomy.LOGGER.warn("Price provider {} failed and was skipped: {}",
                        provider.getClass().getName(), e.toString());
            }
        }

        if (!declarations.isEmpty()) {
            BlockiesEconomy.LOGGER.info("Collected {} price declarations from {} provider(s).",
                    declarations.size(), REGISTERED.size());
        }
        return declarations;
    }

    /**
     * Discovers providers declared as services.
     *
     * <p>Once, and never again: a provider found here stays registered, so a rebuild does
     * not accumulate duplicates. Discovery uses this class's own loader, which is the one
     * every loader in scope shares — a mod isolated behind its own module layer would not
     * be found, which is exactly what {@link #register(PriceProvider)} is there for.
     */
    private static void loadServicesOnce() {
        if (servicesLoaded) {
            return;
        }
        servicesLoaded = true;
        try {
            ServiceLoader<PriceProvider> services = ServiceLoader.load(
                    PriceProvider.class, BlockiesEconomyAPI.class.getClassLoader());
            for (PriceProvider provider : services) {
                register(provider);
            }
        } catch (RuntimeException | java.util.ServiceConfigurationError e) {
            // Usually a broken services file in someone else's jar. Logged rather than
            // thrown: one mod's typo must not cost the shop every other mod's prices.
            BlockiesEconomy.LOGGER.warn("Could not scan for price providers: {}", e.toString());
        }
    }

    /** What a provider writes into. */
    public interface PriceRegistry {

        /** Prices one item. {@code price} is in whole Blockies and must not be negative. */
        void price(String itemId, long price);

        /**
         * Prices every member of an item tag.
         *
         * <p>Ranked below explicit item ids, including your own, so a tag rule fills in
         * what nothing else has claimed rather than overwriting it.
         */
        void priceTag(String tagId, long price);

        /** Removes an item from the shop entirely. Not the same as pricing it at zero. */
        void blacklist(String itemId);
    }

    /** The implementation handed to providers. */
    private static final class Registry implements PriceRegistry {

        private final PriceDeclarations target;

        Registry(PriceDeclarations target) {
            this.target = target;
        }

        @Override
        public void price(String itemId, long price) {
            if (itemId == null || price < 0L) {
                return;
            }
            target.putItem(itemId, price);
        }

        @Override
        public void priceTag(String tagId, long price) {
            if (tagId == null || price < 0L) {
                return;
            }
            target.putTag(tagId.startsWith("#") ? tagId.substring(1) : tagId, price);
        }

        @Override
        public void blacklist(String itemId) {
            if (itemId != null) {
                target.blacklist(itemId);
            }
        }
    }
}
