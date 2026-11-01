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
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;

/**
 * 黑焰庇护附魔
 *
 * 受击：非魔法非无视防御伤害减少（等级×12.5%）
 * 代价：治疗量减少（等级×25%）
 */
@AutoRegisterEnchantment(
        id = "black_flame_shelter",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        conflictsWith = {
                EnchantmentShelterOfFire.class,
                EnchantmentHealingByFire.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentBlackFlameShelter extends EnchantmentBase {

    /** 参与计算的等级上限（levelLimit 开时） */
    private static final int LEVEL_CAP = 10;
    /** 每级物理伤害减免 */
    private static final float MELEE_REDUCTION_PER_LEVEL = 0.125F;
    /** 每级恢复削减 */
    private static final float HEAL_REDUCTION_PER_LEVEL = 0.25F;
    /**
     * 生效等级的硬上限，不受 levelLimit 开关影响
     * <p>
     * 8 级时减伤系数是 0（完全免疫物理伤害），9 级起系数为负，挨打反而加血。
     * levelLimit 是可关的平衡开关，这个是正确性约束，两者分开。
     * </p>
     */
    public static final int MAX_EFFECTIVE_LEVEL = 7;

    /**
     * 只能附在胸甲上；以前任意护甲件都能附
     */
    public EnchantmentBlackFlameShelter() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    private static int getTotalLevel(EntityLivingBase entity) {
        Enchantment blackFlameShelter = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameShelter.class);
        if (blackFlameShelter == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(blackFlameShelter, armor);
            }
        }
        totalLevel = capLevel(totalLevel, LEVEL_CAP);
        return Math.min(totalLevel, MAX_EFFECTIVE_LEVEL);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        if (damageSource.isMagicDamage() || damageSource.isUnblockable()) {
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

        evt.setAmount(evt.getAmount() * (1 - totalLevel * MELEE_REDUCTION_PER_LEVEL));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().world.isRemote) {
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

        evt.setAmount(evt.getAmount() * (1 - totalLevel * HEAL_REDUCTION_PER_LEVEL));
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