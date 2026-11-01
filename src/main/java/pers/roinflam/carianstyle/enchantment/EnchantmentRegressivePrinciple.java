package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 回归法则附魔
 *
 * 护甲附魔，清除周围实体的药水效果
 * 每tick有5%概率触发：
 * - 清除周围（等级×3格，至多 8 格）内有药水效果的生物（含自身）的所有药水，一次至多 16 个
 * <p>
 * 以前半径和目标数都没有上限，等级和 10 时要扫 30 格。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "regressive_principle",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentRegressivePrinciple extends EnchantmentBase {

    /** 搜索半径上限（格） */
    private static final int MAX_SEARCH_RADIUS = 8;
    /** 单次触发至多清除的目标数 */
    private static final int MAX_TARGETS = 16;
    /** 每 tick 的触发概率（%） */
    private static final double TRIGGER_CHANCE = 5.0D;
    /** 每级的搜索半径（格） */
    private static final int RADIUS_PER_LEVEL = 3;
    /** 参与计算的等级上限（开了等级上限时生效） */
    private static final int LEVEL_CAP = 10;

    public EnchantmentRegressivePrinciple() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 每tick概率清除周围实体的药水效果
     * 由于需要累加护甲等级，保留静态监听器
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote) {
            return;
        }

        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        // 5%概率触发
        if (!RandomUtil.percentageChance(TRIGGER_CHANCE)) {
            return;
        }

        EntityPlayer player = evt.player;
        if (!player.isEntityAlive()) {
            return;
        }

        Enchantment regressivePrinciple = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRegressivePrinciple.class);
        if (regressivePrinciple == null) {
            return;
        }

        // 从护甲累加附魔等级
        int totalLevel = 0;
        for (ItemStack armor : player.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(regressivePrinciple, armor);
            }
        }

        totalLevel = capLevel(totalLevel, LEVEL_CAP);

        if (totalLevel <= 0) {
            return;
        }

        int searchRadius = Math.min(totalLevel * RADIUS_PER_LEVEL, MAX_SEARCH_RADIUS);

        List<EntityLivingBase> targets = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                player,
                searchRadius,
                entity -> entity != null && !entity.getActivePotionEffects().isEmpty()
        );

        int hitCount = 0;
        for (EntityLivingBase target : targets) {
            if (hitCount >= MAX_TARGETS) {
                break;
            }
            target.clearActivePotions();
            hitCount++;
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}