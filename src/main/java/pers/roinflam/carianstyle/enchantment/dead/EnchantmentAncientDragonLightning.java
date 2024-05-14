package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.RandomUtils;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentAncientDragonLightning extends RaryBase {
    private static final Set<UUID> THUNDER = new HashSet<>();

    public EnchantmentAncientDragonLightning(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "ancient_dragon_lightning");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.ANCIENT_DRAGON_LIGHTNING;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
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
                if (!THUNDER.contains(hurter.getUniqueID())) {
                    if (!hurter.isDead) {
                        THUNDER.add(hurter.getUniqueID());
                        @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                                EntityLivingBase.class,
                                hurter,
                                60,
                                15,
                                entityLivingBase -> !entityLivingBase.equals(hurter));
                        @Nonnull List<Integer> list = RandomUtil.randomList(bonusLevel * 100, entities.size());
                        for (int i = 0; i < entities.size(); i++) {
                            EntityLivingBase entityLivingBase = entities.get(i);
                            int timeLightning = Math.min(list.get(i), bonusLevel * 15);
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
                                    entityLivingBase.attackEntityFrom(DamageSource.LIGHTNING_BOLT, entityLivingBase.getHealth() * 0.05f + entityLivingBase.getMaxHealth() * 0.005f * magnification);
                                    if (entityLivingBase.onGround) {
                                        double x = RandomUtils.nextBoolean() ? hurter.posX - entityLivingBase.posX : entityLivingBase.posX - hurter.posX;
                                        double z = RandomUtils.nextBoolean() ? hurter.posZ - entityLivingBase.posZ : entityLivingBase.posZ - hurter.posZ;
                                        entityLivingBase.attackedAtYaw = (float) (MathHelper.atan2(z, x) * (180D / Math.PI) - (double) entityLivingBase.rotationYaw);
                                        entityLivingBase.knockBack(hurter, 0.2f, x, z);
                                    }
                                }

                            }.start();
                        }
                    }
                }
                new SynchronizationTask(1800) {

                    @Override
                    public void run() {
                        THUNDER.remove(hurter.getUniqueID());
                    }

                }.start();
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((36 + (enchantmentLevel - 1) * 20) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.DEAD.contains(ench);
    }
}
