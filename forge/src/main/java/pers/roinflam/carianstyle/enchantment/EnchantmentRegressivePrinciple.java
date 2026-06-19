package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 回归法则附魔
 * <p>
 * 护甲附魔，清除周围实体的药水效果
 * 每tick有5%概率触发：
 * - 清除周围（等级×3格）内所有有药水效果的实体的所有药水
 * </p>
 *
 * <h3>性能安全上限（v2.1 新增）</h3>
 * <ul>
 *   <li>{@link #MAX_SEARCH_RADIUS}：AOE 搜索半径硬上限，防止高等级附魔（如 100 级）
 *       直接把等级×3 当半径，导致 300 格搜索扫过数千个实体。</li>
 *   <li>{@link #MAX_TARGETS}：单次触发最大命中目标数上限，防止密集怪物场景下
 *       对大量实体执行 removeAllEffects 导致事件风暴。</li>
 * </ul>
 *
 * <p>本附魔触发频率极高（每 tick 5% 概率 = 平均每秒触发 1 次），
 * 是服务端性能风险最大的附魔之一，必须双重封顶。</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "regressive_principle",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentRegressivePrinciple extends EnchantmentBase {

    /** AOE 搜索半径硬上限（方块）：不管等级多高，最多搜索半径 8 方块 */
    private static final int MAX_SEARCH_RADIUS = 8;

    /** 单次触发最大命中目标数：防止密集怪物场景下事件风暴 */
    private static final int MAX_TARGETS = 16;

    public EnchantmentRegressivePrinciple() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide) {
            return;
        }

        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        // 5%概率触发
        if (!RandomUtil.percentageChance(5)) {
            return;
        }

        Player player = evt.player;
        if (!player.isAlive()) {
            return;
        }

        Enchantment regressivePrinciple = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRegressivePrinciple.class);
        if (regressivePrinciple == null) {
            return;
        }

        // 从护甲累加附魔等级
        int totalLevel = 0;
        for (ItemStack armor : player.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(regressivePrinciple, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        // ⭐ v2.1：搜索半径硬上限，防止等级×3直接当半径
        // 原：totalLevel * 3（100级 = 300格，扫过整个区块区域）
        int searchRadius = Math.min(totalLevel * 3, MAX_SEARCH_RADIUS);

        List<LivingEntity> targets = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                player,
                searchRadius,
                entity -> !entity.getActiveEffects().isEmpty()
        );

        // ⭐ v2.1：命中数量硬上限，防止对上千个实体执行 removeAllEffects
        int hitCount = 0;
        for (LivingEntity target : targets) {
            if (hitCount >= MAX_TARGETS) {
                break;
            }
            target.removeAllEffects();
            hitCount++;
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
