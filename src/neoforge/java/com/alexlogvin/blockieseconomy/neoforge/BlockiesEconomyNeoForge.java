package com.alexlogvin.blockieseconomy.neoforge;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import net.neoforged.fml.common.Mod;

@Mod(BlockiesEconomy.MOD_ID)
public final class BlockiesEconomyNeoForge {
    public BlockiesEconomyNeoForge() {
        BlockiesEconomy.init();
    }
}
