package com.alexlogvin.blockieseconomy.economy;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.core.ledger.Ledger;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Balance persistence for Minecraft 1.20.5 — 1.21.1.
 *
 * <p>Identical in intent to every other era, and forks only where 1.20.5 threaded
 * registries through saved data: {@code save} takes a {@link HolderLookup.Provider}, and
 * the {@link SavedData.Factory} loader is handed one too.
 *
 * <p>See the 1.20.1 class for why balances sit on the overworld and are keyed by UUID
 * rather than attached to the player entity.
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
        SavedData.Factory<BalanceData> factory = new SavedData.Factory<>(
                () -> new BalanceData(ledger, awarded),
                (tag, registries) -> BalanceData.load(tag, ledger, awarded),
                null);
        return server.overworld().getDataStorage().computeIfAbsent(factory, DATA_NAME);
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
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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
