package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

import java.util.UUID;

/**
 * 尸山血海附魔
 * <p>v2.2：击杀计数器（killer视角）入口接入怪物附魔触发开关。
 * 死亡者计数衰减为清理逻辑，无需开关。
 * onDamageAsAttackerHighest 走中央分发器，已被 scanEntity 拦截。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "corpse_piler",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true,
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentCorpsePiler extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.corpse_piler.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "corpse_piler";

    /**
     * 每层击杀数每级提供的额外伤害倍率
     * <p>默认 0.01，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_KILL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_kill_per_level",
                    0.01D, 0.0D, 1.0D);

    /**
     * 每层击杀数每级提供的最大生命回复占比
     * <p>默认 0.0005，允许范围 0.0 ~ 0.1。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_KILL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_kill_per_level",
                    0.0005D, 0.0D, 0.1D);

    /**
     * 击杀层数上限
     * <p>默认 50，允许范围 1 ~ 999。</p>
     */
    private static final EnchantmentValues.Handle MAX_KILL_COUNT =
            EnchantmentValues.define(VALUE_ID, "max_kill_count",
                    50, 1, 999);

    /**
     * 击杀层数的保持时长（tick）
     * <p>默认 6000，允许范围 20 ~ 72000。</p>
     */
    private static final EnchantmentValues.Handle COUNT_DURATION_TICKS =
            EnchantmentValues.define(VALUE_ID, "count_duration_ticks",
                    6000, 20, 72000);


    private static final String KILL_COUNT_KEY = "corpse_piler_kills";

    public EnchantmentCorpsePiler() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerHighest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        UUID uuid = attacker.getUUID();

        int killCount = EnchantmentDataManager.getCounter(KILL_COUNT_KEY, uuid);
        if (killCount <= 0) {
            return;
        }

        ctx.addDamage(ctx.getDamage() * killCount * level * (float) DAMAGE_PER_KILL_PER_LEVEL.get());
        attacker.heal(attacker.getMaxHealth() * killCount * level * (float) HEAL_PER_KILL_PER_LEVEL.get());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity dead = evt.getEntity();

        Enchantment corpsePiler = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCorpsePiler.class);
        if (corpsePiler == null) {
            return;
        }

        // 击杀者增加计数
        if (evt.getSource().getDirectEntity() instanceof LivingEntity) {
            LivingEntity killer = (LivingEntity) evt.getSource().getDirectEntity();

            // ⭐ v2.2：怪物附魔触发开关（击杀者视角）
            if (!EnchantmentEventHandler.shouldBlockMobTrigger(killer, false)) {
                ItemStack heldItem = killer.getItemInHand(InteractionHand.MAIN_HAND);
                if (!heldItem.isEmpty()) {
                    int level = EnchantmentHelper.getItemEnchantmentLevel(corpsePiler, heldItem);

                    if (level > 0) {
                        if (killer.level().random.nextBoolean()) {
                            int current = EnchantmentDataManager.getCounter(KILL_COUNT_KEY, killer.getUUID());
                            int newCount = Math.min(current + 1, MAX_KILL_COUNT.getInt());
                            EnchantmentDataManager.setCounter(KILL_COUNT_KEY, killer.getUUID(), newCount, COUNT_DURATION_TICKS.getInt());
                        }
                    }
                }
            }
        }

        // 死亡者计数衰减（清理逻辑，无需开关）
        int deadCount = EnchantmentDataManager.getCounter(KILL_COUNT_KEY, dead.getUUID());
        if (deadCount > 0) {
            EnchantmentDataManager.setCounter(KILL_COUNT_KEY, dead.getUUID(), deadCount / 2, COUNT_DURATION_TICKS.getInt());
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((35 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
