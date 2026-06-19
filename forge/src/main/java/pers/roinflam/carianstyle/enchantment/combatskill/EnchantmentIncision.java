package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

/**
 * 切割附魔
 * <p>v2.2：onLivingDeath 击杀者视角接入怪物附魔触发开关。
 * onDamageAsAttacker 走中央事件分发器，已经在 scanEntity 入口被通用开关拦截。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "incision",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentIncision extends EnchantmentBase {

    public EnchantmentIncision() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        if (!attacker.hasEffect(CarianStylePotion.INCISION.get())) {
            if (attacker.getHealth() >= attacker.getMaxHealth() * 0.75f) {
                attacker.setHealth(attacker.getHealth() - attacker.getMaxHealth() * 0.5f);
                attacker.addEffect(new MobEffectInstance(CarianStylePotion.INCISION.get(), 200, 0));
            }
        } else {
            float healAmount = Math.min(ctx.getDamage() * 0.25f, attacker.getMaxHealth() * 0.25f);
            attacker.heal(healAmount);
            victim.addEffect(new MobEffectInstance(CarianStylePotion.HEMORRHAGE.get(), 30, 0));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity killer = (LivingEntity) evt.getSource().getDirectEntity();

        if (!killer.isAlive() || killer.equals(evt.getEntity())) {
            return;
        }

        // ⭐ v2.2：怪物附魔触发开关（击杀者视角，击杀续命非濒死触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(killer, false)) return;

        ItemStack heldItem = killer.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment incision = EnchantmentRegistry.getEnchantmentByClass(EnchantmentIncision.class);
        if (incision == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(incision, heldItem);

        if (level <= 0) {
            return;
        }

        MobEffectInstance incisionEffect = killer.getEffect(CarianStylePotion.INCISION.get());
        if (incisionEffect == null) {
            return;
        }

        killer.heal((killer.getMaxHealth() - killer.getHealth()) * 0.1f);

        int newDuration = Math.min(incisionEffect.getDuration() + 100, 200);
        killer.addEffect(new MobEffectInstance(CarianStylePotion.INCISION.get(), newDuration, 0));
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
