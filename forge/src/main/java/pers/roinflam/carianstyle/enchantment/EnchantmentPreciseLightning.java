package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.RandomUtils;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

/**
 * 精准落雷附魔
 * <p>v2.2：LivingDamage受击者视角入口接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "precise_lightning", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnchantmentCategory.ARMOR, slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}, conflictsWith = {EnchantmentCausalityPrinciple.class})
@Mod.EventBusSubscriber
public class EnchantmentPreciseLightning extends EnchantmentBase {

    public EnchantmentPreciseLightning() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) return;

        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getDirectEntity() instanceof Projectile)) return;
        if (!(damageSource.getEntity() instanceof LivingEntity)) return;

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角，远程反击落雷）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        LivingEntity attacker = (LivingEntity) damageSource.getEntity();

        Enchantment preciseLightning = EnchantmentRegistry.getEnchantmentByClass(EnchantmentPreciseLightning.class);
        if (preciseLightning == null) return;

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorSlots()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentHelper.getItemEnchantmentLevel(preciseLightning, armor);
        }
        if (ConfigLoader.levelLimit) totalLevel = Math.min(totalLevel, 10);
        if (totalLevel <= 0) return;

        final int effectiveLevel = totalLevel;
        final float originalDamage = evt.getAmount();

        new SynchronizationTask(5, 5) {
            private int time = 0;

            @Override
            public void run() {
                if (++time > effectiveLevel) {
                    this.cancel();
                    return;
                }

                Level world = attacker.level();
                if (world instanceof ServerLevel serverLevel) {
                    LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
                    if (lightning != null) {
                        lightning.moveTo(attacker.getX(), attacker.getY(), attacker.getZ());
                        lightning.setVisualOnly(true);
                        serverLevel.addFreshEntity(lightning);
                    }
                }

                attacker.invulnerableTime = attacker.invulnerableDuration / 2;

                int magnification = 1;
                if (attacker.level().isThundering()) {
                    magnification = 4;
                } else if (attacker.level().isRaining()) {
                    magnification = 2;
                }

                attacker.hurt(attacker.damageSources().lightningBolt(), originalDamage * 0.3f * magnification);

                if (attacker.onGround()) {
                    double x = RandomUtils.nextBoolean() ? victim.getX() - attacker.getX() : attacker.getX() - victim.getX();
                    double z = RandomUtils.nextBoolean() ? victim.getZ() - attacker.getZ() : attacker.getZ() - victim.getZ();
                    attacker.knockback(0.2f, x, z);
                }
            }
        }.start();
    }

    @Override
    public int getMinCost(int l) {
        return (int) ((30 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
