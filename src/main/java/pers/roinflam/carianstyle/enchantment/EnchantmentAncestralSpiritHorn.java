package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
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
 * 先祖之角附魔
 *
 * 受到魔法伤害时减伤25%
 * 受到魔法伤害后持续回血（伤害×等级×0.05/20 每10tick，持续200tick）
 */
@AutoRegisterEnchantment(
        id = "ancestral_spirit_horn",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        conflictsWith = {
                EnchantmentShelterOfFire.class,
                EnchantmentHealingByFire.class,
                EnchantmentBlackFlameShelter.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentAncestralSpiritHorn extends EnchantmentBase {

    /** 魔法伤害倍率（受到 75%） */
    private static final float DAMAGE_MULTIPLIER = 0.75F;
    /** 每级回复本次伤害的比例 */
    private static final float HEAL_PER_LEVEL = 0.05F;

    /**
     * 只能附在胸甲上；以前任意护甲件都能附，全身叠加起来比单件强
     */
    public EnchantmentAncestralSpiritHorn() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    private static int getTotalLevel(EntityLivingBase entity) {
        Enchantment ancestralSpiritHorn = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAncestralSpiritHorn.class);
        if (ancestralSpiritHorn == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(ancestralSpiritHorn, armor);
            }
        }
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }
        return totalLevel;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        if (damageSource.canHarmInCreative() || !damageSource.isMagicDamage()) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        if (shouldBlockMobTrigger(holder, false)) {
            return;
        }

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        evt.setAmount(evt.getAmount() * DAMAGE_MULTIPLIER);

        float healPerTick = evt.getAmount() * totalLevel * HEAL_PER_LEVEL / 20;

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

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        if (ench == Enchantments.PROTECTION) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}