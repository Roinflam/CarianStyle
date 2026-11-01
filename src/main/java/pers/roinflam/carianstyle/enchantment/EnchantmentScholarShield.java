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
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import javax.annotation.Nonnull;

/**
 * 学者盾附魔
 *
 * 盾牌附魔，格挡时反伤和减伤
 * 格挡时：
 * - 对攻击者造成 10% × 等级 的伤害（基于原伤害，普通生物近战伤害）
 * - 减少 7.5% × 等级 的伤害
 * <p>
 * 改为最高 5 级、只能附在盾牌上。反伤改成普通的生物攻击伤害（以前带魔法标记），
 * 并加了重入保护：以前双方都举盾时反伤会来回弹，等级够高时会无限递归。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "scholar_shield",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentScholarShield extends EnchantmentBase {

    // 每级反弹给攻击者的伤害比例
    private static final float REFLECT_PER_LEVEL = 0.1F;
    // 每级的减伤比例
    private static final float REDUCTION_PER_LEVEL = 0.075F;
    // 等级上限（开了等级限制时）
    private static final int LEVEL_CAP = 10;

    /**
     * 正在结算反伤时置位：反伤本身是带攻击者的生物伤害，会再次进入 onLivingAttack，
     * 被反伤的一方也举着本附魔的盾时就会再反回来。只拦反伤，不拦减伤，被反伤者举盾照样减免。
     */
    private static final ThreadLocal<Boolean> PROCESSING_RETALIATION = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public EnchantmentScholarShield() {
        super(CarianStyleEnchantments.SHIELD, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.MAINHAND,
                EntityEquipmentSlot.OFFHAND
        });
    }

    /**
     * 格挡时反伤
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote || PROCESSING_RETALIATION.get()) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
        if (shouldBlockMobTrigger(victim, false)) {
            return;
        }

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

        level = capLevel(level, LEVEL_CAP);

        if (level <= 0) {
            return;
        }

        // 对攻击者造成反伤；try-finally 保证标记复位，不然反伤会永久失效
        PROCESSING_RETALIATION.set(Boolean.TRUE);
        try {
            attacker.attackEntityFrom(DamageSource.causeMobDamage(victim), evt.getAmount() * level * REFLECT_PER_LEVEL);
        } finally {
            PROCESSING_RETALIATION.set(Boolean.FALSE);
        }
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
        if (shouldBlockMobTrigger(victim, false)) {
            return;
        }

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

        level = capLevel(level, LEVEL_CAP);

        if (level <= 0) {
            return;
        }

        // 减伤 7.5% × 等级
        evt.setAmount(evt.getAmount() - evt.getAmount() * level * REDUCTION_PER_LEVEL);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}