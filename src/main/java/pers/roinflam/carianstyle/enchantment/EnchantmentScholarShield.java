package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
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
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;

/**
 * 学者盾附魔
 *
 * 盾牌附魔，格挡时反伤和减伤
 * 格挡时：
 * - 对攻击者造成 10% × 等级 的魔法伤害（基于原伤害）
 * - 减少 7.5% × 等级 的伤害
 */
@AutoRegisterEnchantment(
        id = "scholar_shield",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentScholarShield extends EnchantmentBase {

    public EnchantmentScholarShield() {
        super(EnumEnchantmentType.BREAKABLE, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.MAINHAND,
                EntityEquipmentSlot.OFFHAND
        });
    }

    /**
     * 格挡时反伤
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        // 必须正在格挡
        if (!victim.isHandActive()) {
            return;
        }

        ItemStack activeItem = victim.getHeldItem(victim.getActiveHand());
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ItemShield)) {
            return;
        }

        Enchantment scholarShield = EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class);
        if (scholarShield == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(scholarShield, activeItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        // 对攻击者造成反伤
        attacker.attackEntityFrom(
                DamageSource.causeMobDamage(victim).setMagicDamage(),
                evt.getAmount() * level * 0.1f
        );
    }

    /**
     * 格挡时减伤
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getSource().getImmediateSource() == null) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        // 必须正在格挡
        if (!victim.isHandActive()) {
            return;
        }

        ItemStack activeItem = victim.getHeldItem(victim.getActiveHand());
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ItemShield)) {
            return;
        }

        Enchantment scholarShield = EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class);
        if (scholarShield == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(scholarShield, activeItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        // 减伤 7.5% × 等级
        evt.setAmount(evt.getAmount() - evt.getAmount() * level * 0.075f);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}