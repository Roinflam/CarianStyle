package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
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

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.regressive_principle.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "regressive_principle";

    /**
     * AOE 搜索半径上限（格）
     * <p>默认 8，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    8, 1, 64);

    /**
     * 单次触发最大命中目标数
     * <p>默认 16，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    16, 1, 200);

    /**
     * 每次触发的概率（百分比）
     * <p>默认 5.0，允许范围 0.0 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_CHANCE =
            EnchantmentValues.define(VALUE_ID, "trigger_chance",
                    5.0D, 0.0D, 100.0D);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

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
        if (!RandomUtil.percentageChance(TRIGGER_CHANCE.get())) {
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

        // 从护甲累加附魔等级（v-cache：走中央装备缓存）
        int totalLevel = EnchantmentEventHandler.armorTotal(player, regressivePrinciple);

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, LEVEL_CAP.getInt());
        }

        if (totalLevel <= 0) {
            return;
        }

        // ⭐ v2.1：搜索半径硬上限，防止等级×3直接当半径
        // 原：totalLevel * 3（100级 = 300格，扫过整个区块区域）
        int searchRadius = Math.min(totalLevel * 3, MAX_SEARCH_RADIUS.getInt());

        List<LivingEntity> targets = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                player,
                searchRadius,
                entity -> !entity.getActiveEffects().isEmpty()
        );

        // ⭐ v2.1：命中数量硬上限，防止对上千个实体执行 removeAllEffects
        int hitCount = 0;
        for (LivingEntity target : targets) {
            if (hitCount >= MAX_TARGETS.getInt()) {
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
