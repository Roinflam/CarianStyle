package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
 * 熔炉之羽附魔
 *
 * 护甲附魔，以受到更多伤害换取机动性增益
 * 受击时：
 * - 受到的伤害增加25%
 * - 大幅增加无敌帧（hurtResistantTime = max + max/2 × 等级 × 1.5）
 * - 获得速度效果（持续 = 等级 × 40tick，效果等级 = 附魔等级 - 1）
 * - 获得跳跃提升效果（持续 = 等级 × 40tick，效果等级 = 附魔等级 - 1）
 */
@AutoRegisterEnchantment(
        id = "furnace_feather",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentFurnaceFeather extends EnchantmentBase {

    public EnchantmentFurnaceFeather() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 受击时增加25%伤害（代价）
     * 由于需要累加所有护甲的附魔等级，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        Enchantment furnaceFeather = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFurnaceFeather.class);

        if (furnaceFeather == null) {
            return;
        }

        // 从所有护甲累加附魔等级
        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(furnaceFeather, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        // 受到伤害增加25%
        evt.setAmount(evt.getAmount() + evt.getAmount() * 0.25f);
    }

    /**
     * 受击后获得无敌帧和机动性增益
     * 由于需要累加所有护甲的附魔等级，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        Enchantment furnaceFeather = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFurnaceFeather.class);

        if (furnaceFeather == null) {
            return;
        }

        // 从所有护甲累加附魔等级
        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(furnaceFeather, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        // 增加无敌帧
        victim.hurtResistantTime = (int) (victim.maxHurtResistantTime + victim.maxHurtResistantTime / 2 * totalLevel * 1.5);

        // 施加速度效果
        victim.addPotionEffect(new PotionEffect(
                MobEffects.SPEED,
                totalLevel * 40,
                totalLevel - 1
        ));

        // 施加跳跃提升效果
        victim.addPotionEffect(new PotionEffect(
                MobEffects.JUMP_BOOST,
                totalLevel * 40,
                totalLevel - 1
        ));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.PROTECTION);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}