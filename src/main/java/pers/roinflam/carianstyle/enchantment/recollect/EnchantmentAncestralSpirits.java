package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

/**
 * 先祖之魂附魔
 *
 * 魔法伤害减半，受击后10秒内持续回血
 * 回血量 = (最大血量 - 当前血量) × 0.05 / 20
 */
@AutoRegisterEnchantment(
        id = "ancestral_spirits",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentAncestralSpirits extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentAncestralSpirits() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        Enchantment ancestralSpirits = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAncestralSpirits.class);
        if (ancestralSpirits == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(ancestralSpirits, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        // 魔法伤害减半
        if (evt.getSource().isMagicDamage()) {
            evt.setAmount(evt.getAmount() * 0.5f);
        }

        // 持续回血
        if (holder.isEntityAlive()) {
            float healPerTick = (holder.getMaxHealth() - holder.getHealth()) * 0.05f / 20;

            new SynchronizationTask(10, 10) {
                private int tick = 0;

                @Override
                public void run() {
                    tick += 10;
                    if (tick > 200 || !holder.isEntityAlive()) {
                        this.cancel();
                        return;
                    }
                    holder.heal(healPerTick);
                }
            }.start();
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}