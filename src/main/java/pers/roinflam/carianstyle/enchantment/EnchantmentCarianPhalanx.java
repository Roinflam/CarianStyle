package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class EnchantmentCarianPhalanx extends RaryBase {

    public EnchantmentCarianPhalanx(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "carian_phalanx");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.CARIAN_PHALANX;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getImmediateSource() instanceof EntityArrow && damageSource.getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (ConfigLoader.levelLimit) {
                        bonusLevel = Math.min(bonusLevel, 10);
                    }
                    if (bonusLevel > 0) {
                        if (RandomUtil.percentageChance(bonusLevel * 2)) {
                            for (int x = -1; x < 2; x++) {
                                for (int z = -1; z < 2; z++) {
                                    @Nonnull EntityGlintblades entityGlintblades_show = new EntityGlintblades(attacker, hurter).setDeadTick((int) (55 + x * 7.5 + z * 7.5));
                                    entityGlintblades_show.posX += x;
                                    entityGlintblades_show.posY += 1;
                                    entityGlintblades_show.posZ += z;
                                    hurter.world.spawnEntity(entityGlintblades_show);

                                    int finalBonusLevel = bonusLevel;
                                    new SynchronizationTask((int) (55 + x * 7.5 + z * 7.5)) {

                                        @Override
                                        public void run() {
                                            double posX = entityGlintblades_show.posX;
                                            double posY = entityGlintblades_show.posY;
                                            double posZ = entityGlintblades_show.posZ;
                                            @Nonnull EntityGlintblades entityGlintblades = new EntityGlintblades(attacker, hurter);
                                            entityGlintblades.posX = posX;
                                            entityGlintblades.posY = posY;
                                            entityGlintblades.posZ = posZ;

                                            entityGlintblades.setDamageSource(DamageSource.causeThrownDamage(entityGlintblades, attacker).setMagicDamage());
                                            entityGlintblades.setDamage(evt.getAmount() * finalBonusLevel * 0.05f);
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
    }

    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(CarianStyleEnchantments.PYROXENE_ICE);
    }
}
