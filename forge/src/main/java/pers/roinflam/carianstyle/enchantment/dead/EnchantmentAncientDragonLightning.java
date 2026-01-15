package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.apache.commons.lang3.RandomUtils;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

@AutoRegisterEnchantment(
        id = "ancient_dragon_lightning",
        category = EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentAncientDragonLightning extends EnchantmentBase {

    public EnchantmentAncientDragonLightning() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @Override
    protected void onDeath(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase hurter = ctx.getHolder();

        if (EnchantmentDataManager.isOnCooldown("ancient_dragon_lightning", hurter.getUniqueID())) {
            return;
        }

        if (hurter.isDead) {
            return;
        }

        EnchantmentDataManager.setCooldown("ancient_dragon_lightning", hurter.getUniqueID(), 1800);

        List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                hurter,
                60,
                15,
                entityLivingBase -> !entityLivingBase.equals(hurter)
        );

        List<Integer> list = RandomUtil.randomList(level * 100, entities.size());

        for (int i = 0; i < entities.size(); i++) {
            EntityLivingBase entityLivingBase = entities.get(i);
            int timeLightning = Math.min(list.get(i), level * 15);

            new SynchronizationTask(40, 5) {
                private int time = 0;

                @Override
                public void run() {
                    if (++time > timeLightning) {
                        this.cancel();
                        return;
                    }

                    World world = entityLivingBase.world;
                    world.addWeatherEffect(
                            new EntityLightningBolt(
                                    world,
                                    entityLivingBase.posX,
                                    entityLivingBase.posY,
                                    entityLivingBase.posZ,
                                    true
                            )
                    );

                    if (!entityLivingBase.isEntityAlive()) {
                        this.cancel();
                        return;
                    }

                    entityLivingBase.hurtResistantTime = entityLivingBase.maxHurtResistantTime / 2;

                    int magnification = 1;
                    if (entityLivingBase.world.isRaining()) {
                        magnification *= 2;
                    } else if (entityLivingBase.world.isThundering()) {
                        magnification *= 4;
                    }

                    entityLivingBase.attackEntityFrom(
                            DamageSource.LIGHTNING_BOLT,
                            entityLivingBase.getHealth() * 0.05f + entityLivingBase.getMaxHealth() * 0.005f * magnification
                    );

                    if (entityLivingBase.onGround) {
                        double x = RandomUtils.nextBoolean() ?
                                hurter.posX - entityLivingBase.posX :
                                entityLivingBase.posX - hurter.posX;
                        double z = RandomUtils.nextBoolean() ?
                                hurter.posZ - entityLivingBase.posZ :
                                entityLivingBase.posZ - hurter.posZ;
                        entityLivingBase.attackedAtYaw = (float) (MathHelper.atan2(z, x) * (180D / Math.PI) - (double) entityLivingBase.rotationYaw);
                        entityLivingBase.knockBack(hurter, 0.2f, x, z);
                    }
                }
            }.start();
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((36 + (enchantmentLevel - 1) * 20) * ConfigLoader.enchantingDifficulty);
    }

}