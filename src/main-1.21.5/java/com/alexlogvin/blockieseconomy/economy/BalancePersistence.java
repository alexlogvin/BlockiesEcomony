package com.alexlogvin.blockieseconomy.economy;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.core.ledger.Ledger;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Balance persistence for Minecraft 1.21.5.
 *
 * <p>Where saved data stopped serialising itself. {@code SavedData.Factory} is gone,
 * {@code save} is no longer an override, and a store now declares a
 * {@link SavedDataType} carrying a {@link Codec}. The reading and writing below is
 * unchanged from the earlier eras; it is reached through a codec built on
 * {@code CompoundTag.CODEC} rather than by overriding, which keeps the bytes on disk
 * byte-identical to what the older jars wrote. A world moved between them reads either way.
 *
 * <p>The NBT accessors changed at the same time: the getters return {@link java.util.Optional}
 * rather than a default, so this reads through their {@code ...Or} and {@code ...OrEmpty}
 * siblings, which mean exactly what the old getters did.
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
        // The ledger and the advancement guard are the live objects the rest of the mod
        // reads, so decoding writes straight into them rather than into a copy the caller
        // would then have to merge.
        Codec<BalanceData> codec = CompoundTag.CODEC.xmap(
                tag -> BalanceData.load(tag, ledger, awarded),
                data -> data.save(new CompoundTag()));

        SavedDataType<BalanceData> type = new SavedDataType<>(
                DATA_NAME, () -> new BalanceData(ledger, awarded), codec, null);
        return server.overworld().getDataStorage().computeIfAbsent(type);
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

            CompoundTag balances = tag.getCompoundOrEmpty(BALANCES_KEY);
            for (String key : balances.keySet()) {
                UUID uuid = parseUuid(key);
                if (uuid != null) {
                    ledger.load(uuid, balances.getLongOr(key, 0L));
                    data.loadedBalances++;
                }
            }

            CompoundTag history = tag.getCompoundOrEmpty(AWARDED_KEY);
            for (String key : history.keySet()) {
                UUID uuid = parseUuid(key);
                if (uuid == null) {
                    continue;
                }
                ListTag ids = history.getListOrEmpty(key);
                Set<String> claimed = new HashSet<String>(ids.size());
                for (int i = 0; i < ids.size(); i++) {
                    // An entry that is not a string reads as absent rather than as an
                    // empty id, which would otherwise be claimed as an advancement.
                    ids.getString(i).ifPresent(claimed::add);
                }
                awarded.load(uuid, claimed);
            }

            return data;
        }

        CompoundTag save(CompoundTag tag) {
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
