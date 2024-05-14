package pers.roinflam.carianstyle.enchantment;

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
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class EnchantmentPreciseLightning extends RaryBase {

    public EnchantmentPreciseLightning(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "precise_lightning");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.PRECISE_LIGHTNING;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getImmediateSource() instanceof IProjectile && damageSource.getTrueSource() instanceof EntityLivingBase) {
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
                EntityLivingBase hurter = evt.getEntityLiving();
                int bonusLevel = 0;
                for (@Nonnull ItemStack itemStack : hurter.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (ConfigLoader.levelLimit) {
                    bonusLevel = Math.min(bonusLevel, 10);
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
                            attacker.attackEntityFrom(DamageSource.LIGHTNING_BOLT, evt.getAmount() * 0.3f * magnification);
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
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(CarianStyleEnchantments.CAUSALITY_PRINCIPLE);
    }
}
