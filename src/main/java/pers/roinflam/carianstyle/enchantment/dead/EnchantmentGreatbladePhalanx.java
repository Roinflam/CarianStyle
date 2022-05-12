package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentGreatbladePhalanx extends Enchantment {
    private static final Set<UUID> DEAD = new HashSet<>();

    public EnchantmentGreatbladePhalanx(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "greatblade_phalanx");
        CarianStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.GREATBLADE_PHALANX;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
                int bonusLevel = 0;
                for (ItemStack itemStack : hurter.getArmorInventoryList()) {
                    if (itemStack != null) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (bonusLevel > 0) {
                    if (!DEAD.contains(hurter.getUniqueID())) {
                        for (int i = 0; i < 3; i++) {
                            EntityGlintblades entityGlintblades_show = new EntityGlintblades(hurter, attacker).setDeadTick(75 + i * 25).setSize((float) 7.5);
                            entityGlintblades_show.posY += 5;
                            if (i == 0) {
                                entityGlintblades_show.posX -= 10;
                                entityGlintblades_show.posZ += 10;
                            } else if (i == 1) {
                                entityGlintblades_show.posX -= 10;
                                entityGlintblades_show.posZ -= 10;
                            } else {
                                entityGlintblades_show.posX += 10;
                            }
                            hurter.world.spawnEntity(entityGlintblades_show);

                            int finalBonusLevel = bonusLevel;
                            new SynchronizationTask(75 + i * 25) {

                                @Override
                                public void run() {
                                    double x = entityGlintblades_show.posX;
                                    double y = entityGlintblades_show.posY;
                                    double z = entityGlintblades_show.posZ;
                                    EntityGlintblades entityGlintblades = new EntityGlintblades(hurter, attacker);
                                    entityGlintblades.setSize((float) 7.5);
                                    entityGlintblades.posX = x;
                                    entityGlintblades.posY = y;
                                    entityGlintblades.posZ = z;

                                    entityGlintblades.setDamageSource(DamageSource.causeThrownDamage(entityGlintblades, hurter).setMagicDamage());
                                    entityGlintblades.setDamage((float) ((attacker.getMaxHealth() - attacker.getHealth()) * finalBonusLevel * 0.1));
                                    entityGlintblades.shoot(1);

                                    hurter.world.spawnEntity(entityGlintblades);
                                }

                            }.start();
                        }
                        DEAD.add(hurter.getUniqueID());
                        new SynchronizationTask(6000) {

                            @Override
                            public void run() {
                                DEAD.remove(hurter.getUniqueID());
                            }

                        }.start();
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
        return 3;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 30 + (enchantmentLevel - 1) * 15;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.DEAD.contains(ench);
    }

}
