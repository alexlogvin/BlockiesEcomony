package com.alexlogvin.blockieseconomy.api;

/**
 * Implemented by a mod that wants to price its own items in code.
 *
 * <p>Declare it as a service in
 * {@code META-INF/services/com.alexlogvin.blockieseconomy.api.PriceProvider}, or hand an
 * instance to {@link BlockiesEconomyAPI#register(PriceProvider)} from your initialiser.
 *
 * <p>If your prices are simply a list, prefer a datapack file — see
 * {@link BlockiesEconomyAPI}. It needs no compile-time dependency on this mod and keeps
 * working when this mod is absent.
 *
 * <p>Example:
 *
 * <pre>{@code
 * public final class MyModPrices implements PriceProvider {
 *     @Override
 *     public void declarePrices(BlockiesEconomyAPI.PriceRegistry registry) {
 *         registry.price("mymod:copper_gear", 180);
 *         registry.priceTag("c:raw_materials", 60);
 *         registry.blacklist("mymod:creative_generator");
 *     }
 * }
 * }</pre>
 */
public interface PriceProvider {

    /**
     * Declares prices.
     *
     * <p>Called once per price build, which happens when a world loads and on
     * {@code /shop rebuild}. It runs on the server thread before the solver starts, so keep
     * it quick and do not block on anything.
     */
    void declarePrices(BlockiesEconomyAPI.PriceRegistry registry);
}
