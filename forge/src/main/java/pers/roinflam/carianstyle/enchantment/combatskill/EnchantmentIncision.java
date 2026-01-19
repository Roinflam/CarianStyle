// 文件：EnchantmentIncision.java
// 路径：forge/src/main/java/pers/roinflam/carianstyle/enchantment/combatskill/EnchantmentIncision.java
package pers.roinflam.carianstyle.enchantment.combatskill;

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
 * <p>
 * 激活：血量>=75%时攻击，消耗50%血量获得INCISION状态（200tick）
 * 激活后：攻击治疗自身（min(伤害×0.25, 最大血量×0.25)），给敌人施加出血
 * 击杀：治疗（损失血量×0.1），延长INCISION时间
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
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

        // 玩家需要刚挥剑
        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        if (!attacker.hasEffect(CarianStylePotion.INCISION.get())) {
            // 激活阶段：血量>=75%时消耗50%血量获得INCISION
            if (attacker.getHealth() >= attacker.getMaxHealth() * 0.75f) {
                attacker.setHealth(attacker.getHealth() - attacker.getMaxHealth() * 0.5f);
                attacker.addEffect(new MobEffectInstance(CarianStylePotion.INCISION.get(), 200, 0));
            }
        } else {
            // 激活后：治疗自身，给敌人施加出血
            float healAmount = Math.min(ctx.getDamage() * 0.25f, attacker.getMaxHealth() * 0.25f);
            attacker.heal(healAmount);
            victim.addEffect(new MobEffectInstance(CarianStylePotion.HEMORRHAGE.get(), 30, 0));
        }
    }

    /**
     * 击杀敌人时：治疗并延长INCISION时间
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity killer = (LivingEntity) evt.getSource().getDirectEntity();

        // 击杀者必须存活且不是自杀
        if (!killer.isAlive() || killer.equals(evt.getEntity())) {
            return;
        }

        ItemStack heldItem = killer.getItemInHand(killer.getUsedItemHand());
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

        // 治疗损失血量的10%
        killer.heal((killer.getMaxHealth() - killer.getHealth()) * 0.1f);

        // 延长INCISION时间（+100tick，上限200tick）
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