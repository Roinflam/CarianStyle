package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 先祖之魂附魔
 * <p>
 * 魔法伤害减半，受击后10秒内持续回血
 * 回血量 = (最大血量 - 当前血量) × 0.05 / 20
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "ancestral_spirits",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST}
)
@Mod.EventBusSubscriber
public class EnchantmentAncestralSpirits extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentAncestralSpirits() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getSource().isCreativePlayer()) {
            return;
        }

        LivingEntity holder = evt.getEntity();

        Enchantment ancestralSpirits = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAncestralSpirits.class);
        if (ancestralSpirits == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(ancestralSpirits, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        // 魔法伤害减半
        if (DamageSourceUtil.isMagicDamage(evt.getSource())) {
            evt.setAmount(evt.getAmount() * 0.5f);
        }

        // 持续回血
        if (holder.isAlive()) {
            float healPerTick = (holder.getMaxHealth() - holder.getHealth()) * 0.05f / 20;

            new SynchronizationTask(10, 10) {
                private int tick = 0;

                @Override
                public void run() {
                    tick += 10;
                    if (tick > 200 || !holder.isAlive()) {
                        this.cancel();
                        return;
                    }
                    holder.heal(healPerTick);
                }
            }.start();
        }
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