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
import net.minecraftforge.event.entity.living.LivingAttackEvent;
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

/**
 * 黄金誓约附魔
 *
 * 护甲附魔，战斗时双方都能获得增益
 * 受到攻击时：
 * - 受击者获得黄金誓约效果（持续 = 2.5 × 等级 秒，效果等级 = 附魔等级 - 1）
 * - 攻击者也获得黄金誓约效果（等级上限额外限制为5）
 */
@AutoRegisterEnchantment(
        id = "golden_vow",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentGoldenVow extends EnchantmentBase {

    public EnchantmentGoldenVow() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 战斗时双方都获得黄金誓约效果
     * 由于需要累加护甲等级且同时检查双方，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();

        Enchantment goldenVow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGoldenVow.class);
        if (goldenVow == null) {
            return;
        }

        // 处理受击者
        int victimLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                victimLevel += EnchantmentHelper.getEnchantmentLevel(goldenVow, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            victimLevel = Math.min(victimLevel, 10);
        }

        if (victimLevel > 0) {
            // 持续时间 = 2.5 × 等级 × 20tick
            int duration = (int) (2.5 * victimLevel * 20);
            victim.addPotionEffect(new PotionEffect(
                    CarianStylePotion.GOLDEN_VOW,
                    duration,
                    victimLevel - 1
            ));
        }

        // 处理攻击者
        int attackerLevel = 0;
        for (ItemStack armor : attacker.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                attackerLevel += EnchantmentHelper.getEnchantmentLevel(goldenVow, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            attackerLevel = Math.min(attackerLevel, 10);
        }

        if (attackerLevel > 0) {
            // 攻击者等级额外限制为5
            attackerLevel = Math.min(attackerLevel, 5);

            int duration = (int) (2.5 * attackerLevel * 20);
            attacker.addPotionEffect(new PotionEffect(
                    CarianStylePotion.GOLDEN_VOW,
                    duration,
                    attackerLevel - 1
            ));
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((35 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
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