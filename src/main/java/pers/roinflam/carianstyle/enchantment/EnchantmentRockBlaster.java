package pers.roinflam.carianstyle.enchantment;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentRockBlaster extends RaryBase {

    public EnchantmentRockBlaster(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "rock_blaster");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.ROCK_BLASTER;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(@Nonnull BlockEvent.BreakEvent evt) {
        if (!evt.getPlayer().world.isRemote) {
            EntityPlayer entityPlayer = evt.getPlayer();
            if (entityPlayer.swingingHand != null) {
                @Nonnull ItemStack itemStack = entityPlayer.getHeldItem(entityPlayer.swingingHand);
                if (!itemStack.isEmpty() && itemStack.getItem() instanceof ItemPickaxe) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    if (ConfigLoader.levelLimit) {
                        bonusLevel = Math.min(bonusLevel, 10);
                    }
                    if (bonusLevel > 0) {
                        @Nonnull List<BlockPos> blockPosList = new ArrayList<>();
                        int radius = 1 + bonusLevel / 2;
                        radius = Math.min(radius, ConfigLoader.rockBlasterMaxRange);
                        for (int x = -radius; x <= radius; x++) {
                            for (int z = -radius; z <= radius; z++) {
                                for (int y = -radius; y <= radius; y++) {
                                    BlockPos blockPos = evt.getPos();
                                    blockPosList.add(new BlockPos(blockPos.getX() + x, blockPos.getY() + y, blockPos.getZ() + z));
                                }
                            }
                        }
                        World world = entityPlayer.world;
                        for (@Nonnull BlockPos blockPos : blockPosList) {
                            if (itemStack.isItemStackDamageable()) {
                                if (itemStack.getItemDamage() >= itemStack.getMaxDamage() - 1) {
                                    return;
                                }
                            }
                            @Nonnull IBlockState blockState = world.getBlockState(blockPos);
                            @Nonnull Block block = blockState.getBlock();
                            if (blockState.getBlock().equals(evt.getState().getBlock())) {
                                if (!entityPlayer.capabilities.isCreativeMode) {
                                    block.onBlockHarvested(world, blockPos, blockState, entityPlayer);
                                    if (block.removedByPlayer(blockState, world, blockPos, entityPlayer, true)) {
                                        world.playEvent(2001, blockPos, Block.getStateId(blockState));
                                        block.harvestBlock(world, entityPlayer, blockPos, blockState, world.getTileEntity(blockPos), itemStack);
                                        block.onBlockDestroyedByPlayer(world, blockPos, blockState);
                                        block.dropXpOnBlockBreak(world, blockPos, block.getExpDrop(blockState, world, blockPos, EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, itemStack)) / 2);
                                        if (itemStack.isItemStackDamageable()) {
                                            itemStack.attemptDamageItem(1, world.rand, (EntityPlayerMP) entityPlayer);
                                        }
                                    }
                                } else {
                                    world.destroyBlock(blockPos, false);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 35) * ConfigLoader.enchantingDifficulty);
    }


    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.UNBREAKING);
    }

}
