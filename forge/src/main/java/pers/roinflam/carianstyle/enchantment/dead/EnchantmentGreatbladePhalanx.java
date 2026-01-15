package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

@AutoRegisterEnchantment(
        id = "greatblade_phalanx",
        category = EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentGreatbladePhalanx extends EnchantmentBase {

    public EnchantmentGreatbladePhalanx() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    @Override
    protected void onDeath(@Nonnull EnchantmentContext ctx, int level) {
        if (!(ctx.getDamageSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase hurter = ctx.getHolder();
        EntityLivingBase attacker = (EntityLivingBase) ctx.getDamageSource().getTrueSource();

        if (EnchantmentDataManager.isOnCooldown("greatblade_phalanx", hurter.getUniqueID())) {
            return;
        }

        EnchantmentDataManager.setCooldown("greatblade_phalanx", hurter.getUniqueID(), 6000);

        for (int i = 0; i < 3; i++) {
            EntityGlintblades entityGlintbladesShow = new EntityGlintblades(hurter, attacker)
                    .setDeadTick(75 + i * 25)
                    .setSize(7.5f);

            entityGlintbladesShow.posY += 5;

            if (i == 0) {
                entityGlintbladesShow.posX -= 10;
                entityGlintbladesShow.posZ += 10;
            } else if (i == 1) {
                entityGlintbladesShow.posX -= 10;
                entityGlintbladesShow.posZ -= 10;
            } else {
                entityGlintbladesShow.posX += 10;
            }

            hurter.world.spawnEntity(entityGlintbladesShow);

            int finalLevel = level;
            new SynchronizationTask(75 + i * 25) {
                @Override
                public void run() {
                    double x = entityGlintbladesShow.posX;
                    double y = entityGlintbladesShow.posY;
                    double z = entityGlintbladesShow.posZ;

                    EntityGlintblades entityGlintblades = new EntityGlintblades(hurter, attacker);
                    entityGlintblades.setSize(7.5f);
                    entityGlintblades.posX = x;
                    entityGlintblades.posY = y;
                    entityGlintblades.posZ = z;

                    entityGlintblades.setDamageSource(
                            DamageSource.causeThrownDamage(entityGlintblades, hurter).setMagicDamage()
                    );
                    entityGlintblades.setDamage(
                            (attacker.getMaxHealth() - attacker.getHealth()) * finalLevel * 0.1f
                    );
                    entityGlintblades.shoot(1);

                    hurter.world.spawnEntity(entityGlintblades);
                }
            }.start();
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

}