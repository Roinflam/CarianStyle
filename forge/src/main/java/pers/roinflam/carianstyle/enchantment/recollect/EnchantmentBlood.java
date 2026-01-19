package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentBloodCollection;
import pers.roinflam.carianstyle.enchantment.EnchantmentBloodSlash;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import java.util.UUID;

/**
 * 血附魔
 * <p>
 * 每3次攻击触发一次：
 * - 造成目标当前血量×12%的直接伤害
 * - 治疗自身（最多自身最大血量×18%）
 * - 施加出血效果
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "blood",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentBlood extends EnchantmentBase {

    private static final String ATTACK_COUNT_KEY = "blood_attack_count";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentBlood() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLow(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        UUID uuid = attacker.getUUID();
        int attackCount = EnchantmentDataManager.getCounter(ATTACK_COUNT_KEY, uuid);

        if (attackCount >= 2) {
            EnchantmentDataManager.resetCounter(ATTACK_COUNT_KEY, uuid);

            float damage = victim.getHealth() * 0.12f;
            attacker.heal(Math.min(damage, attacker.getMaxHealth() * 0.18f));
            victim.setHealth(victim.getHealth() - damage);

            // 检查是否同时有血斩和血收附魔
            int hemorrhageLevel = 0;
            ItemStack heldItem = attacker.getItemInHand(attacker.getUsedItemHand());

            Enchantment bloodSlash = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBloodSlash.class);
            Enchantment bloodCollection = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBloodCollection.class);

            if (bloodSlash != null && bloodCollection != null) {
                if (EnchantmentHelper.getItemEnchantmentLevel(bloodSlash, heldItem) > 0 &&
                        EnchantmentHelper.getItemEnchantmentLevel(bloodCollection, heldItem) > 0) {
                    hemorrhageLevel = 7;
                }
            }

            victim.addEffect(new MobEffectInstance(CarianStylePotion.HEMORRHAGE.get(), 30, hemorrhageLevel));
        } else {
            EnchantmentDataManager.incrementCounter(ATTACK_COUNT_KEY, uuid, 6000);
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