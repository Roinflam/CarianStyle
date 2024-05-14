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
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentGreatbladePhalanx extends RaryBase {
    private static final Set<UUID> DEAD = new HashSet<>();

    public EnchantmentGreatbladePhalanx(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "greatblade_phalanx");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.GREATBLADE_PHALANX;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
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
                    if (!DEAD.contains(hurter.getUniqueID())) {
                        for (int i = 0; i < 3; i++) {
                            @Nonnull EntityGlintblades entityGlintblades_show = new EntityGlintblades(hurter, attacker).setDeadTick(75 + i * 25).setSize(7.5f);
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
                                    @Nonnull EntityGlintblades entityGlintblades = new EntityGlintblades(hurter, attacker);
                                    entityGlintblades.setSize(7.5f);
                                    entityGlintblades.posX = x;
                                    entityGlintblades.posY = y;
                                    entityGlintblades.posZ = z;

                                    entityGlintblades.setDamageSource(DamageSource.causeThrownDamage(entityGlintblades, hurter).setMagicDamage());
                                    entityGlintblades.setDamage((attacker.getMaxHealth() - attacker.getHealth()) * finalBonusLevel * 0.1f);
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
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.DEAD.contains(ench);
    }

}
