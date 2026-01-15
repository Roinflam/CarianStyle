package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
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
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

/**
 * 卡利亚报复附魔
 *
 * 盾牌格挡远程/魔法攻击时生成魔法剑反击
 * 每把剑伤害 = 原伤害×等级×20%
 */
@AutoRegisterEnchantment(
        id = "carian_retaliation",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.BREAKABLE,
        conflictsWith = {
                EnchantmentScholarShield.class,
                EnchantmentImmutableShield.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentCarianRetaliation extends EnchantmentBase {

    public EnchantmentCarianRetaliation() {
        super(EnumEnchantmentType.BREAKABLE, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        if (damageSource.getTrueSource() == null) {
            return;
        }

        boolean isRanged = !damageSource.getTrueSource().equals(damageSource.getImmediateSource());
        boolean isMagic = damageSource.isMagicDamage();
        if (!isRanged && !isMagic) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();
        Entity attacker = damageSource.getTrueSource();

        if (!holder.isHandActive()) {
            return;
        }

        ItemStack heldItem = holder.getHeldItem(holder.getActiveHand());

        if (heldItem.isEmpty() || !(heldItem.getItem() instanceof ItemShield)) {
            return;
        }

        Enchantment carianRetaliation = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCarianRetaliation.class);
        if (carianRetaliation == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(carianRetaliation, heldItem);

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
            showBlade.posY += 0.5;

            if (i == 0) {
                showBlade.posX -= 1;
                showBlade.posZ += 1;
            } else if (i == 1) {
                showBlade.posX -= 1;
                showBlade.posZ -= 1;
            } else {
                showBlade.posX += 1;
            }

            holder.world.spawnEntity(showBlade);

            new SynchronizationTask(delay) {
                @Override
                public void run() {
                    double posX = showBlade.posX;
                    double posY = showBlade.posY;
                    double posZ = showBlade.posZ;

                    EntityGlintblades attackBlade = new EntityGlintblades(holder, attacker);
                    attackBlade.posX = posX;
                    attackBlade.posY = posY;
                    attackBlade.posZ = posZ;

                    attackBlade.setDamageSource(DamageSource.causeThrownDamage(attackBlade, holder).setMagicDamage());
                    attackBlade.setDamage(baseDamage * effectiveLevel * 0.2f);
                    attackBlade.shoot(1);
                    holder.world.spawnEntity(attackBlade);
                }
            }.start();
        }
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        return !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class)) &&
                !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentImmutableShield.class));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}