package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
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
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 巨人火焰附魔
 *
 * 着火时受击反弹50%伤害（火焰）
 * 减伤：伤害 × 血量比例 × 0.25
 * 免疫火焰伤害并转化为治疗（10tick冷却）
 */
@AutoRegisterEnchantment(
        id = "giant_flame",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentGiantFlame extends EnchantmentBase {

    private static final String FLAME_HEAL_COOLDOWN_KEY = "giant_flame_heal_cooldown";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentGiantFlame() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    private static int getTotalLevel(EntityLivingBase entity) {
        Enchantment giantFlame = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGiantFlame.class);
        if (giantFlame == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(giantFlame, armor);
            }
        }
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }
        return totalLevel;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        if (EntityUtil.getFire(holder) <= 0) {
            return;
        }

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        attacker.attackEntityFrom(DamageSource.IN_FIRE, evt.getAmount() * 0.5f);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        float healthRatio = holder.getHealth() / holder.getMaxHealth();
        evt.setAmount(evt.getAmount() - evt.getAmount() * healthRatio * 0.25f);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!evt.getEntityLiving().isEntityAlive()) {
            return;
        }

        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();
        UUID uuid = holder.getUniqueID();

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        if (!evt.getSource().isFireDamage()) {
            return;
        }

        evt.setCanceled(true);

        if (!EnchantmentDataManager.isOnCooldown(FLAME_HEAL_COOLDOWN_KEY, uuid)) {
            holder.heal(evt.getAmount());
            EnchantmentDataManager.setCooldown(FLAME_HEAL_COOLDOWN_KEY, uuid, 10);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}