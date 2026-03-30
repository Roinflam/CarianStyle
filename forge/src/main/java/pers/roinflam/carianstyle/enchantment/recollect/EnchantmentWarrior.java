package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

/** 战士附魔 - 修复: getUsedItemHand -> InteractionHand.MAIN_HAND @version 2.1 */
@AutoRegisterEnchantment(id = "warrior", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber
public class EnchantmentWarrior extends EnchantmentBase {
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public EnchantmentWarrior() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage_attack(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity attacker)) return;
        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        Enchantment warrior = EnchantmentRegistry.getEnchantmentByClass(EnchantmentWarrior.class);
        if (warrior == null) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(warrior, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level > 0) evt.setAmount(evt.getAmount() * 1.25f);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage_hurter(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        LivingEntity victim = evt.getEntity();
        ItemStack heldItem = victim.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        Enchantment warrior = EnchantmentRegistry.getEnchantmentByClass(EnchantmentWarrior.class);
        if (warrior == null) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(warrior, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;
        evt.setAmount(evt.getAmount() * 0.5f);
        float damagePerTick = evt.getAmount() / 60;
        new SynchronizationTask(5, 1) {
            private int tick = 0;
            @Override public void run() {
                if (++tick > 60 || !victim.isAlive()) { this.cancel(); return; }
                if (victim.getHealth() - damagePerTick * 2 > 0) EntityLivingUtil.damageHealthDirectly(victim, damagePerTick);
                else { EntityLivingUtil.kill(victim, evt.getSource()); this.cancel(); }
            }
        }.start();
    }

    @SubscribeEvent
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity killer)) return;
        ItemStack heldItem = killer.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        Enchantment warrior = EnchantmentRegistry.getEnchantmentByClass(EnchantmentWarrior.class);
        if (warrior == null) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(warrior, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level > 0) killer.heal((killer.getMaxHealth() - killer.getHealth()) * 0.25f);
    }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
