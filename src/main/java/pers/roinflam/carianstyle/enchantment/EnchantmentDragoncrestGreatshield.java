package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
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
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 龙徽大盾附魔
 *
 * 护甲附魔，物理伤害护盾叠层系统
 * 受到物理伤害时叠加护盾层数（最多20层）
 * 每层持续30秒，满20层时减伤25%
 */
@AutoRegisterEnchantment(
        id = "dragoncrest_greatshield",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentDragoncrestGreatshield extends EnchantmentBase {

    private static final int MAX_SHIELD_LEVEL = 19;  // 最大层数（药水等级0-19，共20层）
    private static final int SHIELD_DURATION = 600;  // 持续时间30秒

    public EnchantmentDragoncrestGreatshield() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 受到物理伤害时叠加护盾层数
     * 由于需要检查受击者的护甲，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        // 只对非魔法、可格挡的物理伤害生效
        if (damageSource.isMagicDamage() || damageSource.isUnblockable()) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        Enchantment dragoncrest = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDragoncrestGreatshield.class);

        if (dragoncrest == null) {
            return;
        }

        // 从护甲获取附魔等级
        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(dragoncrest, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        // 获取当前护盾效果
        @Nullable PotionEffect currentShield = victim.getActivePotionEffect(CarianStylePotion.DRAGONCREST_GREATSHIELD);

        if (currentShield == null) {
            // 没有护盾，添加初始层（等级0）
            victim.addPotionEffect(new PotionEffect(
                    CarianStylePotion.DRAGONCREST_GREATSHIELD,
                    SHIELD_DURATION,
                    0
            ));
        } else if (currentShield.getAmplifier() < MAX_SHIELD_LEVEL) {
            // 未满层，叠加层数
            victim.addPotionEffect(new PotionEffect(
                    CarianStylePotion.DRAGONCREST_GREATSHIELD,
                    SHIELD_DURATION,
                    currentShield.getAmplifier() + 1
            ));
        } else {
            // 已满层（20层），刷新持续时间并减伤25%
            victim.addPotionEffect(new PotionEffect(
                    CarianStylePotion.DRAGONCREST_GREATSHIELD,
                    SHIELD_DURATION,
                    MAX_SHIELD_LEVEL
            ));
            evt.setAmount(evt.getAmount() * 0.75f);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.PROTECTION);
    }
}