package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 卡利亚报复附魔
 * <p>
 * 盾牌格挡远程/魔法攻击时生成魔法剑反击
 * 每把剑伤害 = 原伤害×等级×20%
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "carian_retaliation",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.BREAKABLE,
        slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND},
        conflictsWith = {
                EnchantmentScholarShield.class,
                EnchantmentImmutableShield.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentCarianRetaliation extends EnchantmentBase {

    public EnchantmentCarianRetaliation() {
        super(EnchantmentCategory.BREAKABLE, new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        if (damageSource.getEntity() == null) {
            return;
        }

        boolean isRanged = !damageSource.getEntity().equals(damageSource.getDirectEntity());
        boolean isMagic = DamageSourceUtil.isMagicDamage(damageSource);
        if (!isRanged && !isMagic) {
            return;
        }

        LivingEntity holder = evt.getEntity();
        Entity attacker = damageSource.getEntity();

        if (!holder.isUsingItem()) {
            return;
        }

        ItemStack heldItem = holder.getItemInHand(holder.getUsedItemHand());

        if (heldItem.isEmpty() || !(heldItem.getItem() instanceof ShieldItem)) {
            return;
        }

        Enchantment carianRetaliation = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCarianRetaliation.class);
        if (carianRetaliation == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(carianRetaliation, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        final int effectiveLevel = level;
        final float baseDamage = evt.getAmount();

        for (int i = 0; i < 3; i++) {
            int delay = 40 + i * 5;

            EntityGlintblades showBlade = new EntityGlintblades(holder, attacker).setDeadTick(delay);
            double posX = holder.getX();
            double posY = holder.getY() + 0.5;
            double posZ = holder.getZ();

            if (i == 0) {
                posX -= 1;
                posZ += 1;
            } else if (i == 1) {
                posX -= 1;
                posZ -= 1;
            } else {
                posX += 1;
            }

            showBlade.setPos(posX, posY, posZ);
            holder.level().addFreshEntity(showBlade);

            final double finalPosX = posX;
            final double finalPosY = posY;
            final double finalPosZ = posZ;

            new SynchronizationTask(delay) {
                @Override
                public void run() {
                    EntityGlintblades attackBlade = new EntityGlintblades(holder, attacker);
                    attackBlade.setPos(finalPosX, finalPosY, finalPosZ);

                    DamageSource magicDamage = holder.damageSources().thrown(attackBlade, holder);
                    DamageSourceUtil.setMagicDamage(magicDamage);
                    attackBlade.setDamageSource(magicDamage);
                    attackBlade.setDamage(baseDamage * effectiveLevel * 0.2f);
                    attackBlade.shoot(1);
                    holder.level().addFreshEntity(attackBlade);
                }
            }.start();
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class)) &&
                !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentImmutableShield.class));
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}