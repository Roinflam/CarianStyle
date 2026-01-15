package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;

/**
 * 艾奥尼亚附魔
 *
 * 被动：每秒给自己施加猩红腐烂
 * 攻击：目标有猩红腐烂时，治疗自身最大血量×10%
 */
@AutoRegisterEnchantment(
        id = "aeonia",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        conflictsWith = {
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentAeonia extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentAeonia() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        if (victim.getActivePotionEffect(CarianStylePotion.SCARLET_ROT) == null) {
            return;
        }

        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        attacker.heal(attacker.getMaxHealth() * 0.1f);
    }

    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getEntity().world.getTotalWorldTime() % 20 != 0) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        if (holder.getHeldItem(holder.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment aeonia = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAeonia.class);
        if (aeonia == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                aeonia,
                holder.getHeldItem(holder.getActiveHand()));

        if (level > 0) {
            holder.addPotionEffect(new PotionEffect(CarianStylePotion.SCARLET_ROT, 21, 0));
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}