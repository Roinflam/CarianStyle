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
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;

@Mod.EventBusSubscriber
public class EnchantmentCarianPhalanx extends Enchantment {

    public EnchantmentCarianPhalanx(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "carian_phalanx");
        CarianStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.CARIAN_PHALANX;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getImmediateSource() instanceof EntityArrow && damageSource.getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
                if (attacker.getHeldItemMainhand() != null) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItemMainhand());
                    if (bonusLevel > 0) {
                        if (RandomUtil.percentageChance(bonusLevel * 2)) {
                            for (int x = -1; x < 2; x++) {
                                for (int z = -1; z < 2; z++) {
                                    EntityGlintblades entityGlintblades_show = new EntityGlintblades(attacker, hurter).setDeadTick((int) (55 + x * 7.5 + z * 7.5));
                                    entityGlintblades_show.posX += x;
                                    entityGlintblades_show.posY += 1;
                                    entityGlintblades_show.posZ += z;
                                    hurter.world.spawnEntity(entityGlintblades_show);

                                    new SynchronizationTask((int) (55 + x * 7.5 + z * 7.5)) {

                                        @Override
                                        public void run() {
                                            double posX = entityGlintblades_show.posX;
                                            double posY = entityGlintblades_show.posY;
                                            double posZ = entityGlintblades_show.posZ;
                                            EntityGlintblades entityGlintblades = new EntityGlintblades(attacker, hurter);
                                            entityGlintblades.posX = posX;
                                            entityGlintblades.posY = posY;
                                            entityGlintblades.posZ = posZ;

                                            entityGlintblades.setDamageSource(DamageSource.causeThrownDamage(entityGlintblades, attacker).setMagicDamage());
                                            entityGlintblades.setDamage((float) (evt.getAmount() * bonusLevel * 0.05));
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

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(CarianStyleEnchantments.PYROXENE_ICE);
    }
}
