package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import java.util.UUID;

/**
 * 时间逆转附魔
 * <p>
 * 死亡时触发：取消死亡，进入逆转状态100tick
 * 逆转状态：免疫伤害并反弹，累积伤害值
 * 逆转结束：治疗累积伤害×25%
 * 冷却6000tick
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "time_reversal",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST}
)
@Mod.EventBusSubscriber
public class EnchantmentTimeReversal extends EnchantmentBase {

    private static final String REVERSAL_COOLDOWN_KEY = "time_reversal_cooldown";
    private static final String REVERSAL_STATE_KEY = "time_reversal_state";
    private static final String REVERSAL_DAMAGE_KEY = "time_reversal_damage";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentTimeReversal() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    /**
     * 获取实体装备的时间逆转附魔总等级
     *
     * @param entity 实体
     * @return 附魔总等级
     */
    private static int getTotalLevel(LivingEntity entity) {
        Enchantment timeReversal = EnchantmentRegistry.getEnchantmentByClass(EnchantmentTimeReversal.class);
        if (timeReversal == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(timeReversal, armor);
            }
        }
        return totalLevel;
    }

    /**
     * 监听生物死亡事件 - 触发时间逆转
     *
     * @param evt 死亡事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity holder = evt.getEntity();
        UUID uuid = holder.getUUID();

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        if (EnchantmentDataManager.isOnCooldown(REVERSAL_COOLDOWN_KEY, uuid)) {
            return;
        }

        // 取消死亡事件
        evt.setCanceled(true);

        // 保留1点生命值
        holder.setHealth(1);
        holder.invulnerableTime = 20;

        // 设置冷却和逆转状态
        EnchantmentDataManager.setCooldown(REVERSAL_COOLDOWN_KEY, uuid, 6000);
        EnchantmentDataManager.setData(REVERSAL_STATE_KEY, uuid, true);
        EnchantmentDataManager.setData(REVERSAL_DAMAGE_KEY, uuid, 0f);

        // 100tick后结束逆转状态
        new SynchronizationTask(100) {
            @Override
            public void run() {
                if (holder.isAlive()) {
                    Float accumulated = EnchantmentDataManager.getData(REVERSAL_DAMAGE_KEY, uuid);
                    if (accumulated != null) {
                        // 治疗累积伤害的25%
                        holder.heal(accumulated * 0.25f);
                    }
                }
                EnchantmentDataManager.removeData(REVERSAL_STATE_KEY, uuid);
                EnchantmentDataManager.removeData(REVERSAL_DAMAGE_KEY, uuid);
            }
        }.start();
    }

    /**
     * 监听生物受击事件 - 逆转状态下反弹伤害
     *
     * @param evt 受击事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity holder = evt.getEntity();
        UUID uuid = holder.getUUID();

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        Boolean inReversal = EnchantmentDataManager.getData(REVERSAL_STATE_KEY, uuid);
        if (inReversal == null || !inReversal) {
            return;
        }

        // 防止自伤循环
        if (evt.getEntity().equals(evt.getSource().getEntity())) {
            return;
        }

        // 取消伤害
        evt.setCanceled(true);

        // 累积伤害值
        Float accumulated = EnchantmentDataManager.getData(REVERSAL_DAMAGE_KEY, uuid);
        if (accumulated == null) {
            accumulated = 0f;
        }
        EnchantmentDataManager.setData(REVERSAL_DAMAGE_KEY, uuid, accumulated + evt.getAmount());

        // 反弹伤害
        if (evt.getSource().getEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) evt.getSource().getEntity();
            attacker.hurt(evt.getSource(), evt.getAmount());
        }
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
        return ench instanceof EnchantmentFullMoon || ench instanceof EnchantmentLivingCorpse;
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