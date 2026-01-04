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
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.block.light.HideLight;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.tileentity.MoveLight;

import javax.annotation.Nonnull;

/**
 * 星光附魔
 *
 * 护甲附魔，移动时发光
 * 效果：
 * - 在脚下放置隐形光源
 * - 光源亮度 = 0.5 + 等级 × 0.1
 */
@AutoRegisterEnchantment(
        id = "starlight",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentStarlight extends EnchantmentBase {

    public EnchantmentStarlight() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 移动时在脚下放置光源
     * 由于 LivingUpdateEvent 没有模板方法，且需要累加护甲等级，保留静态监听器
     */
    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        EntityLivingBase entity = evt.getEntityLiving();

        Enchantment starlight = EnchantmentRegistry.getEnchantmentByClass(EnchantmentStarlight.class);
        if (starlight == null) {
            return;
        }

        // 注意：原代码没有客户端检查，也没有等级上限检查
        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(starlight, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        World world = entity.world;
        int blockX = MathHelper.floor(entity.posX);
        int blockY = MathHelper.floor(entity.posY - 0.2D - entity.getYOffset());
        int blockZ = MathHelper.floor(entity.posZ);
        BlockPos blockPos = new BlockPos(blockX, blockY + 1, blockZ);

        if (!world.isAirBlock(blockPos)) {
            return;
        }

        // 处理已存在的光源
        if (world.getTileEntity(blockPos) instanceof MoveLight) {
            MoveLight moveLight = (MoveLight) world.getTileEntity(blockPos);
            moveLight.retime();
        } else if (world.getBlockState(blockPos).getBlock() instanceof HideLight) {
            world.setBlockToAir(blockPos);
        }

        // 放置新光源
        HideLight hideLight = CarianStyleBlocks.HIDE_LIGHT;
        hideLight.setLightLevel(0.5f + totalLevel * 0.1f);
        world.setBlockState(blockPos, CarianStyleBlocks.HIDE_LIGHT.getDefaultState());
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((23 + (enchantmentLevel - 1) * 9) * ConfigLoader.enchantingDifficulty);
    }
}