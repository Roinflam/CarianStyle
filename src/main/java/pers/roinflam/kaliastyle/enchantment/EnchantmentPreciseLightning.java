package pers.roinflam.kaliastyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.RandomUtils;
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.kaliastyle.utils.util.EnchantmentUtil;

@Mod.EventBusSubscriber
public class EnchantmentPreciseLightning extends Enchantment {

    public EnchantmentPreciseLightning(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "precise_lightning");
        KaliaStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return KaliaStyleEnchantments.PRECISE_LIGHTNING;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getImmediateSource() instanceof IProjectile && damageSource.getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
                EntityLivingBase hurter = evt.getEntityLiving();
                int bonusLevel = 0;
                for (ItemStack itemStack : hurter.getArmorInventoryList()) {
                    if (itemStack != null) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (bonusLevel > 0) {
                    int finalBonusLevel = bonusLevel;
                    new SynchronizationTask(5, 5) {
                        private int time = 0;

                        @Override
                        public void run() {
                            if (++time > finalBonusLevel) {
                                this.cancel();
                                return;
                            }
                            World world = attacker.world;
                            world.addWeatherEffect(
                                    new EntityLightningBolt(
                                            world,
                                            attacker.posX,
                                            attacker.posY,
                                            attacker.posZ,
                                            true
                                    )
                            );
                            attacker.hurtResistantTime = attacker.maxHurtResistantTime / 2;
                            int magnification = 1;
                            if (attacker.world.isRaining()) {
                                magnification *= 2;
                            } else if (attacker.world.isThundering()) {
                                magnification *= 4;
                            }
                            attacker.attackEntityFrom(DamageSource.LIGHTNING_BOLT, (float) (evt.getAmount() * 0.3) * magnification);
                            if (attacker.onGround) {
                                double x = RandomUtils.nextBoolean() ? hurter.posX - attacker.posX : attacker.posX - hurter.posX;
                                double z = RandomUtils.nextBoolean() ? hurter.posZ - attacker.posZ : attacker.posZ - hurter.posZ;
                                attacker.attackedAtYaw = (float) (MathHelper.atan2(z, x) * (180D / Math.PI) - (double) attacker.rotationYaw);
                                attacker.knockBack(hurter, 0.2f, x, z);
                            }
                        }

                    }.start();
                }
            }
        }

    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 30 + (enchantmentLevel - 1) * 10;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(KaliaStyleEnchantments.CAUSALITY_PRINCIPLE);
    }
}
