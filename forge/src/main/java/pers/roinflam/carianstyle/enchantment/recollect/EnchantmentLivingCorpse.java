package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import java.util.UUID;

/**
 * 死诞者附魔
 * <p>
 * 死亡时满血复活，但开始持续失血直到再次死亡
 * 冷却4800tick
 * </p>
 * <p>
 * v2.1新增: onLivingDeath 入口接入怪物附魔触发开关，
 * 怪物身上的"濒死复活+流血"效果可由配置 allowMobTriggerDeathEnchantments 控制
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "living_corpse",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST}
)
@Mod.EventBusSubscriber
public class EnchantmentLivingCorpse extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.living_corpse.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "living_corpse";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 复活的冷却时间（tick）
     * <p>默认 4800，允许范围 20 ~ 144000。</p>
     */
    private static final EnchantmentValues.Handle REVIVE_COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "revive_cooldown",
                    4800, 20, 144000);

    /**
     * 复活后每秒流失的最大生命占比基数
     * <p>默认 0.01，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle DRAIN_RATIO_PER_SECOND =
            EnchantmentValues.define(VALUE_ID, "drain_ratio_per_second",
                    0.01D, 0.0D, 1.0D);


    private static final String REVIVE_COOLDOWN_KEY = "living_corpse_cooldown";
    private static final String BLEEDING_STATE_KEY = "living_corpse_bleeding";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentLivingCorpse() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    /**
     * 获取实体装备的死诞者附魔总等级
     *
     * @param entity 实体
     * @return 附魔总等级
     */
    private static int getTotalLevel(LivingEntity entity) {
        Enchantment livingCorpse = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLivingCorpse.class);
        if (livingCorpse == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(livingCorpse, armor);
            }
        }
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, LEVEL_CAP.getInt());
        }
        return totalLevel;
    }

    /**
     * 监听生物死亡事件 - 触发复活机制
     * <p>v2.1新增：怪物附魔触发开关（濒死类）拦截</p>
     *
     * @param evt 死亡事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity holder = evt.getEntity();

        // ⭐ v2.1：怪物附魔触发开关 —— 死诞者属于濒死复活类，怪物身上不触发
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, true)) {
            return;
        }

        UUID uuid = holder.getUUID();

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        if (EnchantmentDataManager.isOnCooldown(REVIVE_COOLDOWN_KEY, uuid)) {
            return;
        }

        // 取消死亡事件
        evt.setCanceled(true);

        // 满血复活
        holder.setHealth(holder.getMaxHealth());
        holder.invulnerableTime = 20;

        // 设置冷却和流血状态
        EnchantmentDataManager.setCooldown(REVIVE_COOLDOWN_KEY, uuid, REVIVE_COOLDOWN.getInt());
        EnchantmentDataManager.setData(BLEEDING_STATE_KEY, uuid, true);

        DamageSource originalSource = evt.getSource();

        // 启动持续失血任务
        new SynchronizationTask(1, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (!holder.isAlive()) {
                    this.cancel();
                    return;
                }

                float baseDamage = holder.getMaxHealth() * (float) DRAIN_RATIO_PER_SECOND.get() / 20;
                float damage = baseDamage * 5 + baseDamage * ++tick / 75;

                if (holder.getHealth() - damage * 2 > 0) {
                    EntityLivingUtil.damageHealthDirectly(holder, damage);
                } else {
                    EntityLivingUtil.kill(holder, originalSource);
                    EnchantmentDataManager.removeData(BLEEDING_STATE_KEY, uuid);
                    this.cancel();
                }
            }
        }.start();
    }

    /**
     * 监听实体加入世界事件 - 恢复失血状态
     * <p>注意：此事件用于玩家重新登录时恢复未完结的流血状态，
     * 不属于"触发亡语"，未接入怪物附魔开关。
     * 若 onLivingDeath 被拦截，BLEEDING_STATE_KEY 本来就不会被设置，
     * 本方法不会对怪物产生影响。</p>
     *
     * @param evt 实体加入事件
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(@NotNull EntityJoinLevelEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity holder = (LivingEntity) evt.getEntity();
        UUID uuid = holder.getUUID();

        Boolean bleeding = EnchantmentDataManager.getData(BLEEDING_STATE_KEY, uuid);
        if (bleeding == null || !bleeding) {
            return;
        }

        // 恢复失血任务
        new SynchronizationTask(1, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (!holder.isAlive()) {
                    this.cancel();
                    return;
                }

                float baseDamage = holder.getMaxHealth() * (float) DRAIN_RATIO_PER_SECOND.get() / 20;
                float damage = baseDamage * 6 + baseDamage * ++tick / 30;

                if (holder.getHealth() - damage * 2 > 0) {
                    EntityLivingUtil.damageHealthDirectly(holder, damage);
                } else {
                    EntityLivingUtil.kill(holder, holder.damageSources().fellOutOfWorld());
                    EnchantmentDataManager.removeData(BLEEDING_STATE_KEY, uuid);
                    this.cancel();
                }
            }
        }.start();
    }

    @Override
    protected boolean checkCompatibility(Enchantment ench) {
        if (isDeadEnchantment(ench)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    /**
     * 判断是否为死亡类附魔（互斥）
     *
     * @param ench 待检查的附魔
     * @return 是否为死亡类附魔
     */
    private boolean isDeadEnchantment(Enchantment ench) {
        return ench instanceof EnchantmentFullMoon || ench instanceof EnchantmentTimeReversal;
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
