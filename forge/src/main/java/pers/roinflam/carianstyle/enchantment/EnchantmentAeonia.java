package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

/**
 * 艾奥尼亚附魔
 * <p>
 * 被动：每秒给自己施加猩红腐烂
 * 攻击：目标有猩红腐烂时，治疗自身最大血量×10%
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "aeonia",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentAeonia extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentAeonia() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        if (victim.getEffect(CarianStylePotion.SCARLET_ROT.get()) == null) {
            return;
        }

        if (ctx.isHolderPlayer()) {
            if (ctx.getHolderAsPlayer().getAttackStrengthScale(0.5F) < 0.9F) {
                return;
            }
        }

        attacker.heal(attacker.getMaxHealth() * 0.1f);
    }

    @SubscribeEvent
    public static void onLivingTick(@NotNull LivingEvent.LivingTickEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getEntity().tickCount % 20 != 0) {
            return;
        }

        LivingEntity holder = evt.getEntity();

        // 修复：检查主手物品
        ItemStack heldItem = holder.getMainHandItem();
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment aeonia = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAeonia.class);
        if (aeonia == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(aeonia, heldItem);

        if (level > 0) {
            holder.addEffect(new MobEffectInstance(CarianStylePotion.SCARLET_ROT.get(), 21, 0));
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}