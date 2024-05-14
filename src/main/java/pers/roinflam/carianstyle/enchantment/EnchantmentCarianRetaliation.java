package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class EnchantmentCarianRetaliation extends RaryBase {

    public EnchantmentCarianRetaliation(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "carian_retaliation");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.CARIAN_RETALIATION;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getTrueSource() != null && (!damageSource.getTrueSource().equals(damageSource.getImmediateSource()) || damageSource.isMagicDamage())) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable Entity attacker = damageSource.getTrueSource();
                if (hurter.isHandActive()) {
                    @Nonnull ItemStack itemStack = hurter.getHeldItem(hurter.getActiveHand());
                    if (!itemStack.isEmpty() && itemStack.getItem() instanceof ItemShield) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                        if (ConfigLoader.levelLimit) {
                            bonusLevel = Math.min(bonusLevel, 10);
                        }
                        if (bonusLevel > 0) {
                            for (int i = 0; i < 3; i++) {
                                @Nonnull EntityGlintblades entityGlintblades_show = new EntityGlintblades(hurter, attacker).setDeadTick(40 + i * 5);
                                entityGlintblades_show.posY += 0.5;
                                if (i == 0) {
                                    entityGlintblades_show.posX -= 1;
                                    entityGlintblades_show.posZ += 1;
                                } else if (i == 1) {
                                    entityGlintblades_show.posX -= 1;
                                    entityGlintblades_show.posZ -= 1;
                                } else {
                                    entityGlintblades_show.posX += 1;
                                }
                                hurter.world.spawnEntity(entityGlintblades_show);

                                int finalBonusLevel = bonusLevel;
                                new SynchronizationTask(40 + i * 5) {

                                    @Override
                                    public void run() {
                                        double x = entityGlintblades_show.posX;
                                        double y = entityGlintblades_show.posY;
                                        double z = entityGlintblades_show.posZ;
                                        @Nonnull EntityGlintblades entityGlintblades = new EntityGlintblades(hurter, attacker);
                                        entityGlintblades.posX = x;
                                        entityGlintblades.posY = y;
                                        entityGlintblades.posZ = z;

                                        entityGlintblades.setDamageSource(DamageSource.causeThrownDamage(entityGlintblades, hurter).setMagicDamage());
                                        entityGlintblades.setDamage(evt.getAmount() * finalBonusLevel * 0.2f);
                                        entityGlintblades.shoot(1);
                                        hurter.world.spawnEntity(entityGlintblades);
                                    }

                                }.start();
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        return !ench.equals(CarianStyleEnchantments.SCHOLAR_SHIELD) &&
                !ench.equals(CarianStyleEnchantments.IMMUTABLE_SHIELD);
    }

}
