package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentMoonOfNoxtura extends VeryRaryBase {

    public EnchantmentMoonOfNoxtura(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "moon_of_noxtura");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.MOON_OF_NOXTURA;
    }

    @SubscribeEvent
    public static void onLivingSetAttackTarget(LivingSetAttackTargetEvent evt) {
        if (!evt.getEntity().world.isRemote && !evt.getEntity().world.isDaytime()) {
            if (evt.getEntityLiving() instanceof EntityLiving && evt.getTarget() != null) {
                EntityLivingBase target = evt.getTarget();
                EntityLiving entityLiving = (EntityLiving) evt.getEntityLiving();
                int bonusLevel = 0;
                for (ItemStack itemStack : target.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (bonusLevel > 0) {
                    if (RandomUtil.percentageChance(2.5)) {
                        double distance = entityLiving.getDistance(target);
                        List<EntityLivingBase> entities = entityLiving.world.getEntitiesWithinAABB(
                                EntityLivingBase.class,
                                new AxisAlignedBB(entityLiving.getPosition()).expand(distance, distance, distance),
                                entityLivingBase -> entityLivingBase.getClass() != (entityLiving.getClass()) && entityLiving.canEntityBeSeen(entityLivingBase) && !entityLivingBase.equals(entityLiving) && !entityLivingBase.equals(target)
                        );
                        if (!entities.isEmpty()) {
                            entityLiving.setAttackTarget(entities.get(RandomUtil.getInt(0, entities.size() - 1)));
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 35;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !ench.equals(CarianStyleEnchantments.HEALING_BY_FIRE) &&
                !ench.equals(CarianStyleEnchantments.SHELTER_OF_FIRE) &&
                !ench.equals(CarianStyleEnchantments.PRECISE_LIGHTNING) &&
                !ench.equals(CarianStyleEnchantments.ANCIENT_DRAGON_LIGHTNING);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}
