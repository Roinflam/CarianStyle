package pers.roinflam.carianstyle.enchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.block.light.HideLight;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.tileentity.MoveLight;

/**
 * 星光附魔
 * <p>
 * 护甲附魔,移动时发光
 * 效果:
 * - 在脚下放置隐形光源
 * - 光源固定为最大亮度(15级光照)
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "starlight",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
@Mod.EventBusSubscriber
public class EnchantmentStarlight extends EnchantmentBase {

    public EnchantmentStarlight() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    @SubscribeEvent
    public static void onLivingUpdate(@NotNull LivingEvent.LivingTickEvent evt) {
        LivingEntity entity = evt.getEntity();

        Enchantment starlight = EnchantmentRegistry.getEnchantmentByClass(EnchantmentStarlight.class);
        if (starlight == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(starlight, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        Level world = entity.level();
        int blockX = Mth.floor(entity.getX());
        // 修复：去掉 getMyRidingOffset() 调用
        // 原代码中 Shulker 等实体调用 getVehicle() 可能返回 null，导致 NPE
        int blockY = Mth.floor(entity.getY() - 0.2D);
        int blockZ = Mth.floor(entity.getZ());
        BlockPos blockPos = new BlockPos(blockX, blockY + 1, blockZ);

        if (!world.isEmptyBlock(blockPos)) {
            return;
        }

        // 处理已存在的光源
        if (world.getBlockEntity(blockPos) instanceof MoveLight) {
            MoveLight moveLight = (MoveLight) world.getBlockEntity(blockPos);
            moveLight.retime();
            return;  // 已经有光源了,刷新时间即可
        } else if (world.getBlockState(blockPos).getBlock() instanceof HideLight) {
            world.removeBlock(blockPos, false);
        }

        world.setBlock(blockPos, CarianStyleBlocks.HIDE_LIGHT.get().defaultBlockState(), 3);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((23 + (enchantmentLevel - 1) * 9) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}