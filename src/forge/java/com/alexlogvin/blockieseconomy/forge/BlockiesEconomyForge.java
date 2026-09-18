package com.alexlogvin.blockieseconomy.forge;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import net.minecraftforge.fml.common.Mod;

@Mod(BlockiesEconomy.MOD_ID)
public final class BlockiesEconomyForge {
    public BlockiesEconomyForge() {
        BlockiesEconomy.init();
    }
}
