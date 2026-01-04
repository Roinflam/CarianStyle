package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
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
 * 绿龟附魔
 *
 * 护甲附魔，增强治疗效果
 * 治疗时：
 * - 基础增益：治疗量 × 等级 × 7.5%
 * - 低血量加成：等级 × 15% × (1 - 当前血量百分比)
 * - 血量越低，加成越高
 */
@AutoRegisterEnchantment(
        id = "green_turtle",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentGreenTurtle extends EnchantmentBase {

    public EnchantmentGreenTurtle() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 治疗时增加治疗量
     * 由于需要累加所有护甲的附魔等级，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase entity = evt.getEntityLiving();

        Enchantment greenTurtle = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGreenTurtle.class);
        if (greenTurtle == null) {
            return;
        }

        // 从所有护甲累加附魔等级
        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(greenTurtle, armor);
            }
        }

        // 注意：原代码没有等级上限检查，保持原逻辑
        if (totalLevel <= 0) {
            return;
        }

        // 计算血量缺失百分比
        float missingHealthPercent = 1 - entity.getHealth() / entity.getMaxHealth();

        // 基础增益：治疗量 × 等级 × 7.5%
        // 低血量加成：等级 × 15% × 血量缺失百分比
        float bonusHeal = evt.getAmount() * totalLevel * 0.075f
                + totalLevel * 0.15f * missingHealthPercent;

        evt.setAmount(evt.getAmount() + bonusHeal);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        return super.canApplyTogether(ench)
                && !ench.equals(Enchantments.PROTECTION)
                && !ench .equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameShelter.class));
    }
}