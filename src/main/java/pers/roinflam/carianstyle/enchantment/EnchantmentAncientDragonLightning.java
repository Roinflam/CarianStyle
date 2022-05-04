package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
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
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentAncientDragonLightning extends Enchantment {
    private static final Set<UUID> THUNDER = new HashSet<>();

    public EnchantmentAncientDragonLightning(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "ancient_dragon_lightning");
        CarianStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.ANCIENT_DRAGON_LIGHTNING;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            int bonusLevel = 0;
            for (ItemStack itemStack : hurter.getArmorInventoryList()) {
                if (itemStack != null) {
                    bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                }
            }
            if (bonusLevel > 0) {
                if (!THUNDER.contains(hurter.getUniqueID())) {
                    if (!hurter.isDead) {
                        THUNDER.add(hurter.getUniqueID());
                        List<Entity> entities = EntityUtil.getNearbyEntities(
                                hurter,
                                60,
                                15,
                                entity -> entity instanceof EntityLivingBase && !entity.equals(hurter)
                        );
                        List<Integer> list = RandomUtil.randomList(bonusLevel * 100, entities.size());
                        for (int i = 0; i < list.size(); i++) {
                            EntityLivingBase entityLivingBase = (EntityLivingBase) entities.get(i);
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
                                    entityLivingBase.attackEntityFrom(DamageSource.LIGHTNING_BOLT, (float) (entityLivingBase.getHealth() * 0.05 + entityLivingBase.getMaxHealth() * 0.005 * magnification));
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
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 36 + (enchantmentLevel - 1) * 20;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !ench.equals(CarianStyleEnchantments.FULL_MOON) &&
                !ench.equals(CarianStyleEnchantments.LIVING_CORPSE) &&
                !ench.equals(CarianStyleEnchantments.TIME_REVERSAL);
    }
}
