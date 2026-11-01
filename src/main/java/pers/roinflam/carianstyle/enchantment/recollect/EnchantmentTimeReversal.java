package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 普拉顿桑克斯的回溯附魔
 *
 * 死亡时触发（300 秒冷却）：取消死亡、生命设为 1 并获得 1 秒无敌，进入 5 秒逆转状态。
 * 逆转状态：免疫伤害并把这一下原样反击给来源，累积受到的伤害；持续灼烧和流血不扣血，也不计入累积。
 * 逆转结束：回复累积伤害的 25%。
 * <p>
 * 以前复活后没有无敌时间；两个都在逆转中的实体互相打时，反击会再触发对方的反击，来回递归。
 * 现在反击期间置位，被反击的一方不再反击回来（免疫和累积照常）。怪物持有时按死亡类附魔的开关处理。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "time_reversal",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentTimeReversal extends EnchantmentBase {

    private static final String REVERSAL_COOLDOWN_KEY = "time_reversal_cooldown";
    private static final String REVERSAL_STATE_KEY = "time_reversal_state";
    private static final String REVERSAL_DAMAGE_KEY = "time_reversal_damage";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    /** 冷却（tick） */
    private static final int REVERSAL_COOLDOWN = 6000;
    /** 逆转持续时间（tick） */
    private static final int ACCUMULATE_WINDOW = 100;
    /** 结束时回复累积伤害的比例 */
    private static final float HEAL_RATIO = 0.25F;
    /** 复活后的无敌时间（tick） */
    private static final int REVIVE_INVULNERABLE_TICKS = 20;

    /**
     * 正在结算反击时置位：反击伤害进入对方的回溯时，对方只免疫、累积，不再反击回来
     */
    private static boolean processingReflect = false;

    public EnchantmentTimeReversal() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    /**
     * 实体是否正处于逆转状态
     * <p>
     * 逆转靠 {@link #onLivingAttack} 取消伤害，而持续灼烧、出血、切腹这些直接扣血的不走这个事件，
     * 持有者会在「无敌」里被烧死。这些地方都先问这里：逆转期间不扣血，持续时间照走，也不计入储存伤害。
     * </p>
     *
     * @param entity 实体
     * @return 逆转中返回 true
     */
    public static boolean isReversing(@Nonnull EntityLivingBase entity) {
        Boolean state = EnchantmentDataManager.getData(REVERSAL_STATE_KEY, entity.getUniqueID());
        return state != null && state;
    }

    private static int getTotalLevel(EntityLivingBase entity) {
        Enchantment timeReversal = EnchantmentRegistry.getEnchantmentByClass(EnchantmentTimeReversal.class);
        if (timeReversal == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(timeReversal, armor);
            }
        }
        return totalLevel;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        if (shouldBlockMobTrigger(holder, true)) {
            return;
        }

        UUID uuid = holder.getUniqueID();

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        if (EnchantmentDataManager.isOnCooldown(REVERSAL_COOLDOWN_KEY, uuid)) {
            return;
        }

        if (holder.isDead) {
            return;
        }

        evt.setCanceled(true);
        holder.setHealth(1);
        holder.hurtResistantTime = REVIVE_INVULNERABLE_TICKS;

        EnchantmentDataManager.setCooldown(REVERSAL_COOLDOWN_KEY, uuid, REVERSAL_COOLDOWN);
        EnchantmentDataManager.setData(REVERSAL_STATE_KEY, uuid, true);
        EnchantmentDataManager.setData(REVERSAL_DAMAGE_KEY, uuid, 0f);

        new SynchronizationTask(ACCUMULATE_WINDOW) {
            @Override
            public void run() {
                if (holder.isEntityAlive()) {
                    Float accumulated = EnchantmentDataManager.getData(REVERSAL_DAMAGE_KEY, uuid);
                    if (accumulated != null) {
                        holder.heal(accumulated * HEAL_RATIO);
                    }
                }
                EnchantmentDataManager.removeData(REVERSAL_STATE_KEY, uuid);
                EnchantmentDataManager.removeData(REVERSAL_DAMAGE_KEY, uuid);
            }
        }.start();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();
        UUID uuid = holder.getUniqueID();

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        Boolean inReversal = EnchantmentDataManager.getData(REVERSAL_STATE_KEY, uuid);
        if (inReversal == null || !inReversal) {
            return;
        }

        if (evt.getEntity().equals(evt.getSource().getTrueSource())) {
            return;
        }

        evt.setCanceled(true);

        Float accumulated = EnchantmentDataManager.getData(REVERSAL_DAMAGE_KEY, uuid);
        if (accumulated == null) {
            accumulated = 0f;
        }
        EnchantmentDataManager.setData(REVERSAL_DAMAGE_KEY, uuid, accumulated + evt.getAmount());

        if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
            if (processingReflect) {
                return;
            }

            EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
            processingReflect = true;
            try {
                attacker.attackEntityFrom(evt.getSource(), evt.getAmount());
            } finally {
                processingReflect = false;
            }
        }
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        // 与死亡类附魔冲突
        if (isDeadEnchantment(ench)) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    private boolean isDeadEnchantment(Enchantment ench) {
        return ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class))
                || ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentLivingCorpse.class));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}