package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
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
 * 灾厄附魔（诅咒）
 * <p>v2.2：LivingHurt受击者视角入口接入怪物附魔触发开关。
 * onPlayerTick 玩家专属，无需检查。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "calamity",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST},
        isCurse = true
)
@Mod.EventBusSubscriber
public class EnchantmentCalamity extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.calamity.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "calamity";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 灾祸生效时的伤害倍率
     * <p>默认 1.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    1.5D, 0.0D, 10.0D);

    /**
     * 每 tick 的触发概率（百分比）
     * <p>默认 2.0，允许范围 0.0 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_CHANCE =
            EnchantmentValues.define(VALUE_ID, "trigger_chance",
                    2.0D, 0.0D, 100.0D);

    public EnchantmentCalamity() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    private static int getArmorLevel(LivingEntity entity) {
        Enchantment calamity = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCalamity.class);
        if (calamity == null) {
            return 0;
        }

        // v-cache：走中央装备缓存
        int totalLevel = EnchantmentEventHandler.armorTotal(entity, calamity);
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, LEVEL_CAP.getInt());
        }
        return totalLevel;
    }

    private static int getTotalLevel(LivingEntity entity) {
        Enchantment calamity = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCalamity.class);
        if (calamity == null) {
            return 0;
        }

        // v-cache：走中央装备缓存（主手 + 四个护甲槽）
        int totalLevel = EnchantmentEventHandler.armorAndMainHand(entity, calamity);

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, LEVEL_CAP.getInt());
        }
        return totalLevel;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        int totalLevel = getArmorLevel(victim);
        if (totalLevel > 0) {
            evt.setAmount(evt.getAmount() * (float) DAMAGE_MULTIPLIER.get());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide) {
            return;
        }

        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        if (!RandomUtil.percentageChance(TRIGGER_CHANCE.get())) {
            return;
        }

        Player player = evt.player;
        if (!player.isAlive()) {
            return;
        }

        int totalLevel = getTotalLevel(player);
        if (totalLevel <= 0) {
            return;
        }

        List<Mob> nearbyMobs = EntityUtil.getNearbyEntities(
                Mob.class,
                player,
                32
        );

        for (Mob mob : nearbyMobs) {
            LivingEntity currentTarget = mob.getTarget();

            if (currentTarget == null || !currentTarget.isAlive()) {
                if (RandomUtil.percentageChance(25)) {
                    mob.setTarget(player);
                }
            } else if (!currentTarget.equals(player)) {
                if (RandomUtil.percentageChance(50)) {
                    mob.setTarget(player);
                }
            }
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
