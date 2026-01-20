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
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import java.util.UUID;

/**
 * 活尸附魔
 * <p>
 * 死亡时满血复活，但开始持续失血直到再次死亡
 * 冷却4800tick
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
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

    private static final String REVIVE_COOLDOWN_KEY = "living_corpse_cooldown";
    private static final String BLEEDING_STATE_KEY = "living_corpse_bleeding";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentLivingCorpse() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    /**
     * 获取实体装备的活尸附魔总等级
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
            totalLevel = Math.min(totalLevel, 10);
        }
        return totalLevel;
    }

    /**
     * 监听生物死亡事件 - 触发复活机制
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

        if (EnchantmentDataManager.isOnCooldown(REVIVE_COOLDOWN_KEY, uuid)) {
            return;
        }

        // 取消死亡事件
        evt.setCanceled(true);

        // 满血复活
        holder.setHealth(holder.getMaxHealth());
        holder.invulnerableTime = 20;

        // 设置冷却和流血状态
        EnchantmentDataManager.setCooldown(REVIVE_COOLDOWN_KEY, uuid, 4800);
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

                float baseDamage = holder.getMaxHealth() * 0.01f / 20;
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

                float baseDamage = holder.getMaxHealth() * 0.01f / 20;
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