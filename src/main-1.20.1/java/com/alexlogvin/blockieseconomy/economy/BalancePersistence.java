package com.alexlogvin.blockieseconomy.economy;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.core.ledger.Ledger;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Balance persistence for Minecraft 1.20.1.
 *
 * <p>Balances and the advancement payout guard live in a single {@link SavedData} on the
 * <em>overworld</em>, keyed by player UUID. Two deliberate choices there:
 *
 * <ul>
 *   <li><b>Overworld, always.</b> {@code getDataStorage()} is per-dimension, so reading it
 *       from wherever the player happens to be would fork everyone's balance per dimension.</li>
 *   <li><b>Keyed by UUID, not attached to the player entity.</b> Attached data has to be
 *       copied across death and dimension changes, and the three loaders each offer a
 *       different mechanism for it. A SavedData is one implementation that works everywhere
 *       and survives respawn for free.</li>
 * </ul>
 *
 * <p>The 1.21.1 twin of this class differs only in the two signatures Mojang changed in
 * 1.20.5: {@code save} gained a registry lookup, and {@code computeIfAbsent} took a
 * {@code SavedData.Factory}.
 */
public final class BalancePersistence {

    private static final String DATA_NAME = "blockies_economy_balances";
    private static final String BALANCES_KEY = "Balances";
    private static final String AWARDED_KEY = "AwardedAdvancements";

    private BalancePersistence() {
    }

    public static void load(MinecraftServer server, Ledger ledger, AwardedAdvancements awarded) {
        BalanceData data = get(server, ledger, awarded);
        BlockiesEconomy.LOGGER.info(
                "Loaded {} player balances and advancement history for {} players.",
                data.loadedBalances, awarded.trackedPlayers());
    }

    /** Flags the store dirty so the world save writes it out. */
    public static void markDirty(MinecraftServer server, Ledger ledger,
                                 AwardedAdvancements awarded) {
        get(server, ledger, awarded).setDirty();
    }

    private static BalanceData get(MinecraftServer server, Ledger ledger,
                                   AwardedAdvancements awarded) {
        return server.overworld().getDataStorage().computeIfAbsent(
                tag -> BalanceData.load(tag, ledger, awarded),
                () -> new BalanceData(ledger, awarded),
                DATA_NAME);
    }

    private static final class BalanceData extends SavedData {

        private final Ledger ledger;
        private final AwardedAdvancements awarded;
        private int loadedBalances;

        BalanceData(Ledger ledger, AwardedAdvancements awarded) {
            this.ledger = ledger;
            this.awarded = awarded;
        }

        static BalanceData load(CompoundTag tag, Ledger ledger, AwardedAdvancements awarded) {
            BalanceData data = new BalanceData(ledger, awarded);

            CompoundTag balances = tag.getCompound(BALANCES_KEY);
            for (String key : balances.getAllKeys()) {
                UUID uuid = parseUuid(key);
                if (uuid != null) {
                    ledger.load(uuid, balances.getLong(key));
                    data.loadedBalances++;
                }
            }

            CompoundTag history = tag.getCompound(AWARDED_KEY);
            for (String key : history.getAllKeys()) {
                UUID uuid = parseUuid(key);
                if (uuid == null) {
                    continue;
                }
                ListTag ids = history.getList(key, Tag.TAG_STRING);
                Set<String> claimed = new HashSet<String>(ids.size());
                for (int i = 0; i < ids.size(); i++) {
                    claimed.add(ids.getString(i));
                }
                awarded.load(uuid, claimed);
            }

            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag balances = new CompoundTag();
            for (Map.Entry<UUID, Long> entry : ledger.snapshot().entrySet()) {
                balances.putLong(entry.getKey().toString(), entry.getValue().longValue());
            }
            tag.put(BALANCES_KEY, balances);

            CompoundTag history = new CompoundTag();
            for (Map.Entry<UUID, Set<String>> entry : awarded.snapshot().entrySet()) {
                ListTag ids = new ListTag();
                for (String id : entry.getValue()) {
                    ids.add(StringTag.valueOf(id));
                }
                history.put(entry.getKey().toString(), ids);
            }
            tag.put(AWARDED_KEY, history);

            BlockiesEconomy.LOGGER.debug("Wrote {} player balances to the world save.",
                    balances.size());
            return tag;
        }
    }

    /** A corrupt key costs one player's record, not the whole world. */
    private static UUID parseUuid(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            BlockiesEconomy.LOGGER.warn("Skipping malformed saved-data entry {}.", key);
            return null;
        }
    }
}
