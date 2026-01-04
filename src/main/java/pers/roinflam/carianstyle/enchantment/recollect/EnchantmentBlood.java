package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentBloodCollection;
import pers.roinflam.carianstyle.enchantment.EnchantmentBloodSlash;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 血附魔
 *
 * 每3次攻击触发一次：
 * - 造成目标当前血量×12%的直接伤害
 * - 治疗自身（最多自身最大血量×18%）
 * - 施加出血效果
 */
@AutoRegisterEnchantment(
        id = "blood",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
public class EnchantmentBlood extends EnchantmentBase {

    private static final String ATTACK_COUNT_KEY = "blood_attack_count";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentBlood() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLow(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        UUID uuid = attacker.getUniqueID();
        int attackCount = EnchantmentDataManager.getCounter(ATTACK_COUNT_KEY, uuid);

        if (attackCount >= 2) {
            EnchantmentDataManager.resetCounter(ATTACK_COUNT_KEY, uuid);

            float damage = victim.getHealth() * 0.12f;
            attacker.heal(Math.min(damage, attacker.getMaxHealth() * 0.18f));
            victim.setHealth(victim.getHealth() - damage);

            // 检查是否同时有血斩和血收附魔
            int hemorrhageLevel = 0;
            ItemStack heldItem = attacker.getHeldItem(attacker.getActiveHand());

            net.minecraft.enchantment.Enchantment bloodSlash = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBloodSlash.class);
            net.minecraft.enchantment.Enchantment bloodCollection = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBloodCollection.class);

            if (bloodSlash != null && bloodCollection != null) {
                if (EnchantmentHelper.getEnchantmentLevel(bloodSlash, heldItem) > 0 &&
                        EnchantmentHelper.getEnchantmentLevel(bloodCollection, heldItem) > 0) {
                    hemorrhageLevel = 7;
                }
            }

            victim.addPotionEffect(new PotionEffect(CarianStylePotion.HEMORRHAGE, 30, hemorrhageLevel));
        } else {
            EnchantmentDataManager.incrementCounter(ATTACK_COUNT_KEY, uuid, 6000);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}