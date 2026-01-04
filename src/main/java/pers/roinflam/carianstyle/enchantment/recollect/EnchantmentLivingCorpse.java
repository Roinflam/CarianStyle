package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
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
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 活尸附魔
 *
 * 死亡时满血复活，但开始持续失血直到再次死亡
 * 冷却4800tick
 */
@AutoRegisterEnchantment(
        id = "living_corpse",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentLivingCorpse extends EnchantmentBase {

    private static final String REVIVE_COOLDOWN_KEY = "living_corpse_cooldown";
    private static final String BLEEDING_STATE_KEY = "living_corpse_bleeding";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentLivingCorpse() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    private static int getTotalLevel(EntityLivingBase entity) {
        Enchantment livingCorpse = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLivingCorpse.class);
        if (livingCorpse == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(livingCorpse, armor);
            }
        }
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
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

        if (!EnchantmentDataManager.isOnCooldown(REVIVE_COOLDOWN_KEY, uuid)) {
            if (!holder.isDead) {
                evt.setCanceled(true);
                holder.setHealth(holder.getMaxHealth());

                EnchantmentDataManager.setCooldown(REVIVE_COOLDOWN_KEY, uuid, 4800);
                EnchantmentDataManager.setData(BLEEDING_STATE_KEY, uuid, true);

                DamageSource originalSource = evt.getSource();
                new SynchronizationTask(1, 1) {
                    private int tick = 0;

                    @Override
                    public void run() {
                        if (!holder.isEntityAlive()) {
                            this.cancel();
                            return;
                        }

                        float baseDamage = holder.getMaxHealth() * 0.01f / 20;
                        float damage = baseDamage * 5 + baseDamage * ++tick / 75;

                        if (holder.getHealth() - damage * 2 > 0) {
                            holder.setHealth(holder.getHealth() - damage);
                        } else {
                            EntityLivingUtil.kill(holder, originalSource);
                            EnchantmentDataManager.removeData(BLEEDING_STATE_KEY, uuid);
                            this.cancel();
                        }
                    }
                }.start();
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(@Nonnull EntityJoinWorldEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getEntity() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase holder = (EntityLivingBase) evt.getEntity();
        UUID uuid = holder.getUniqueID();

        Boolean bleeding = EnchantmentDataManager.getData(BLEEDING_STATE_KEY, uuid);
        if (bleeding == null || !bleeding) {
            return;
        }

        new SynchronizationTask(1, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (!holder.isEntityAlive()) {
                    this.cancel();
                    return;
                }

                float baseDamage = holder.getMaxHealth() * 0.01f / 20;
                float damage = baseDamage * 6 + baseDamage * ++tick / 30;

                if (holder.getHealth() - damage * 2 > 0) {
                    holder.setHealth(holder.getHealth() - damage);
                } else {
                    EntityLivingUtil.kill(holder, DamageSource.OUT_OF_WORLD);
                    EnchantmentDataManager.removeData(BLEEDING_STATE_KEY, uuid);
                    this.cancel();
                }
            }
        }.start();
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        // 与死亡类附魔冲突
        if (isDeadEnchantment(ench)) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    /**
     * 判断是否是死亡类附魔
     */
    private boolean isDeadEnchantment(Enchantment ench) {
        return ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class))
                || ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentTimeReversal.class));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}