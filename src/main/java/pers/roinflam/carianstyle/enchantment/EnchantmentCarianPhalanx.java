package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
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
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;

/**
 * 卡利亚方阵附魔
 *
 * 弓箭伤害时概率生成魔法剑阵列攻击目标
 * 每把剑伤害 = 原伤害×等级×5%
 */
@AutoRegisterEnchantment(
        id = "carian_phalanx",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        conflictsWith = {
                EnchantmentPyroxeneIce.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentCarianPhalanx extends EnchantmentBase {

    public EnchantmentCarianPhalanx() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        if (!(damageSource.getImmediateSource() instanceof EntityArrow)) {
            return;
        }
        if (!(damageSource.getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();

        if (attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment carianPhalanx = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCarianPhalanx.class);
        if (carianPhalanx == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                carianPhalanx,
                attacker.getHeldItem(attacker.getActiveHand()));

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        if (!RandomUtil.percentageChance(level * 2)) {
            return;
        }

        final int effectiveLevel = level;
        final float baseDamage = evt.getAmount();

        for (int x = -1; x < 2; x++) {
            for (int z = -1; z < 2; z++) {
                int delay = (int) (55 + x * 7.5 + z * 7.5);

                EntityGlintblades showBlade = new EntityGlintblades(attacker, victim).setDeadTick(delay);
                showBlade.posX += x;
                showBlade.posY += 1;
                showBlade.posZ += z;
                victim.world.spawnEntity(showBlade);

                new SynchronizationTask(delay) {
                    @Override
                    public void run() {
                        double posX = showBlade.posX;
                        double posY = showBlade.posY;
                        double posZ = showBlade.posZ;

                        EntityGlintblades attackBlade = new EntityGlintblades(attacker, victim);
                        attackBlade.posX = posX;
                        attackBlade.posY = posY;
                        attackBlade.posZ = posZ;

                        attackBlade.setDamageSource(DamageSource.causeThrownDamage(attackBlade, attacker).setMagicDamage());
                        attackBlade.setDamage(baseDamage * effectiveLevel * 0.05f);
                        attackBlade.shoot(1);
                        victim.world.spawnEntity(attackBlade);
                    }
                }.start();
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}