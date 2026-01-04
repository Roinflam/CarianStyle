package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 黄金律法附魔
 *
 * 攻击：增伤15%~60%（血量越低越高）
 * 受击：减伤15%
 * 免疫：伤害<=血量15%直接免疫，否则100tick冷却内免疫一次
 */
@AutoRegisterEnchantment(
        id = "golden_law",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentGoldenLaw extends EnchantmentBase {

    private static final String IMMUNITY_COOLDOWN_KEY = "golden_law_immunity";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentGoldenLaw() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        Enchantment goldenLaw = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGoldenLaw.class);
        if (goldenLaw == null) {
            return;
        }

        // 攻击者视角：增伤
        if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

            if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                int level = EnchantmentHelper.getEnchantmentLevel(
                        goldenLaw,
                        attacker.getHeldItem(attacker.getActiveHand()));

                if (level > 0) {
                    float healthRatio = attacker.getHealth() / attacker.getMaxHealth();
                    float bonusDamage = evt.getAmount() * 0.15f + evt.getAmount() * 0.45f * (1 - healthRatio);
                    evt.setAmount(evt.getAmount() + bonusDamage);
                }
            }
        }

        // 受击者视角：减伤15%
        EntityLivingBase victim = evt.getEntityLiving();

        if (!victim.getHeldItem(victim.getActiveHand()).isEmpty()) {
            int level = EnchantmentHelper.getEnchantmentLevel(
                    goldenLaw,
                    victim.getHeldItem(victim.getActiveHand()));

            if (level > 0) {
                evt.setAmount(evt.getAmount() * 0.85f);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();
        UUID uuid = holder.getUniqueID();

        if (holder.getHeldItemMainhand().isEmpty()) {
            return;
        }

        Enchantment goldenLaw = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGoldenLaw.class);
        if (goldenLaw == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(goldenLaw, holder.getHeldItemMainhand());

        if (level <= 0) {
            return;
        }

        if (evt.getAmount() <= holder.getHealth() * 0.15) {
            evt.setCanceled(true);
            return;
        }

        if (!EnchantmentDataManager.isOnCooldown(IMMUNITY_COOLDOWN_KEY, uuid)) {
            evt.setCanceled(true);
            EnchantmentDataManager.setCooldown(IMMUNITY_COOLDOWN_KEY, uuid, 100);
        }
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        // 与律法类附魔冲突（通过包名判断）
        if (isLawEnchantment(ench) && !ench.equals(this)) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    private boolean isLawEnchantment(Enchantment ench) {
        return ench.getClass().getPackage().getName().contains("law");
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}