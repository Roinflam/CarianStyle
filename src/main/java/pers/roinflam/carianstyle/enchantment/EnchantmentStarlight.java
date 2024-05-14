package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.UncommonBase;
import pers.roinflam.carianstyle.block.light.HideLight;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tileentity.MoveLight;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class EnchantmentStarlight extends UncommonBase {

    public EnchantmentStarlight(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "starlight");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.STARLIGHT;
    }

    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        EntityLivingBase entityLivingBase = evt.getEntityLiving();
        int bonusLevel = 0;
        for (@Nonnull ItemStack itemStack : entityLivingBase.getArmorInventoryList()) {
            if (!itemStack.isEmpty()) {
                bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
            }
        }
        
                if (bonusLevel > 0) {
            World world = entityLivingBase.world;
            int blockX = MathHelper.floor(entityLivingBase.posX);
            int blockY = MathHelper.floor(entityLivingBase.posY - 0.2D - entityLivingBase.getYOffset());
            int blockZ = MathHelper.floor(entityLivingBase.posZ);
            BlockPos blockPos = new BlockPos(blockX, blockY + 1, blockZ);
            if (world.isAirBlock(blockPos)) {
                if (world.getTileEntity(blockPos) instanceof MoveLight) {
                    MoveLight moveLight = (MoveLight) world.getTileEntity(blockPos);
                    moveLight.retime();
                } else if (world.getBlockState(blockPos).getBlock() instanceof HideLight) {
                    world.setBlockToAir(blockPos);
                }
                HideLight hideLight = CarianStyleBlocks.HIDE_LIGHT;
                hideLight.setLightLevel(0.5f + bonusLevel * 0.1f);
                world.setBlockState(blockPos, CarianStyleBlocks.HIDE_LIGHT.getDefaultState());
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((23 + (enchantmentLevel - 1) * 9) * ConfigLoader.enchantingDifficulty);
    }

}
