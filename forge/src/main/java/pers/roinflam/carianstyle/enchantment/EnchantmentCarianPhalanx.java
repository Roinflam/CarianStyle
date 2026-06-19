package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 卡利亚方阵附魔
 * <p>v2.2：LivingDamage攻击者视角入口接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "carian_phalanx", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnchantmentCategory.BOW, slots = {EquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentPyroxeneIce.class})
@Mod.EventBusSubscriber
public class EnchantmentCarianPhalanx extends EnchantmentBase {
    public EnchantmentCarianPhalanx() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getDirectEntity() instanceof AbstractArrow)) return;
        if (!(damageSource.getEntity() instanceof LivingEntity attacker)) return;

        // ⭐ v2.2：怪物附魔触发开关（攻击者视角，箭矢命中追加魔法剑阵）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        LivingEntity victim = evt.getEntity();
        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        Enchantment carianPhalanx = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCarianPhalanx.class);
        if (carianPhalanx == null) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(carianPhalanx, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0 || !RandomUtil.percentageChance(level * 2)) return;

        final int effectiveLevel = level;
        final float baseDamage = evt.getAmount();
        for (int x = -1; x < 2; x++) {
            for (int z = -1; z < 2; z++) {
                if (x == 0 && z == 0) continue;
                int delay = (int) (55 + (Math.abs(x) + Math.abs(z)) * 7.5);
                EntityGlintblades showBlade = new EntityGlintblades(attacker, victim).setDeadTick(delay).setSize(1.0f);
                showBlade.setPos(victim.getX() + x * 1.5, victim.getY() + 2, victim.getZ() + z * 1.5);
                victim.level().addFreshEntity(showBlade);
                final double fX = showBlade.getX(), fY = showBlade.getY(), fZ = showBlade.getZ();
                new SynchronizationTask(delay) {
                    @Override
                    public void run() {
                        if (!victim.isAlive() || victim.isRemoved()) return;
                        EntityGlintblades attackBlade = new EntityGlintblades(attacker, victim).setSize(1.0f)
                                .setDamage(baseDamage * effectiveLevel * 0.05f)
                                .setDamageSource(attacker.damageSources().thrown(null, attacker))
                                .setTrackingStrength(0.2f).setMaxLifetime(80);
                        DamageSourceUtil.setMagicDamage(attackBlade.getDamageSource());
                        attackBlade.setPos(fX, fY, fZ);
                        attackBlade.shoot(1.5f);
                        victim.level().addFreshEntity(attackBlade);
                    }
                }.start();
            }
        }
    }

    @Override
    public int getMinCost(int l) {
        return (int) ((20 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
