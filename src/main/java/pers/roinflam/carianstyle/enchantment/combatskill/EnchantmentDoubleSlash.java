package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class EnchantmentDoubleSlash extends RaryBase {

    public EnchantmentDoubleSlash(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "double_slash");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.DOUBLE_SLASH;
    }

    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().damageType.equals("waterfowlDance") && evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (attacker instanceof EntityPlayer) {
                    if (EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker) != 1) {
                        return;
                    } else {
                        ((EntityPlayer) attacker).resetCooldown();
                    }
                    if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));

                if (bonusLevel > 0) {
                            if (RandomUtil.percentageChance(bonusLevel * 5 + 20)) {
                                hurter.hurtResistantTime = hurter.maxHurtResistantTime / 2;
                                evt.getSource().damageType = "waterfowlDance";
                                hurter.attackEntityFrom(DamageSource.MAGIC, (float) (evt.getAmount() * bonusLevel * 0.2));
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 8) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        return !CarianStyleEnchantments.COMBAT_SKILL.contains(ench);
    }

}
