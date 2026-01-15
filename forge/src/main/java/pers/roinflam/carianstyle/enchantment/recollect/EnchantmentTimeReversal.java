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
 * 时间逆转附魔
 *
 * 死亡时触发：取消死亡，进入逆转状态100tick
 * 逆转状态：免疫伤害并反弹，累积伤害值
 * 逆转结束：治疗累积伤害×25%
 * 冷却6000tick
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

    public EnchantmentTimeReversal() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
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

        EnchantmentDataManager.setCooldown(REVERSAL_COOLDOWN_KEY, uuid, 6000);
        EnchantmentDataManager.setData(REVERSAL_STATE_KEY, uuid, true);
        EnchantmentDataManager.setData(REVERSAL_DAMAGE_KEY, uuid, 0f);

        new SynchronizationTask(100) {
            @Override
            public void run() {
                if (holder.isEntityAlive()) {
                    Float accumulated = EnchantmentDataManager.getData(REVERSAL_DAMAGE_KEY, uuid);
                    if (accumulated != null) {
                        holder.heal(accumulated * 0.25f);
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
            EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
            attacker.attackEntityFrom(evt.getSource(), evt.getAmount());
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