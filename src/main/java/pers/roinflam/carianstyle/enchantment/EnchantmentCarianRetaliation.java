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
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;

@Mod.EventBusSubscriber
public class EnchantmentCarianRetaliation extends RaryBase {

    public EnchantmentCarianRetaliation(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "carian_retaliation");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.CARIAN_RETALIATION;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getTrueSource() != null && (!damageSource.getImmediateSource().equals(damageSource.getTrueSource()) || damageSource.isMagicDamage())) {
                EntityLivingBase hurter = evt.getEntityLiving();
                Entity attacker = damageSource.getTrueSource();
                if (hurter.isHandActive()) {
                    ItemStack itemStack = hurter.getHeldItem(hurter.getActiveHand());
                    if (!itemStack.isEmpty() && itemStack.getItem() instanceof ItemShield) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                        if (bonusLevel > 0) {
                            for (int i = 0; i < 3; i++) {
                                EntityGlintblades entityGlintblades_show = new EntityGlintblades(hurter, attacker).setDeadTick(40 + i * 5);
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

                                new SynchronizationTask(40 + i * 5) {

                                    @Override
                                    public void run() {
                                        double x = entityGlintblades_show.posX;
                                        double y = entityGlintblades_show.posY;
                                        double z = entityGlintblades_show.posZ;
                                        EntityGlintblades entityGlintblades = new EntityGlintblades(hurter, attacker);
                                        entityGlintblades.posX = x;
                                        entityGlintblades.posY = y;
                                        entityGlintblades.posZ = z;

                                        entityGlintblades.setDamageSource(DamageSource.causeThrownDamage(entityGlintblades, hurter).setMagicDamage());
                                        entityGlintblades.setDamage((float) (evt.getAmount() * bonusLevel * 0.2));
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
        return 20 + (enchantmentLevel - 1) * 15;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !ench.equals(CarianStyleEnchantments.SCHOLAR_SHIELD) &&
                !ench.equals(CarianStyleEnchantments.IMMUTABLE_SHIELD);
    }

}
