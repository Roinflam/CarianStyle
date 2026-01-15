package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.FoodStats;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;

/**
 * 亵渎附魔
 *
 * 击杀敌人时治疗自身（目标最大血量×10%）
 * 玩家额外恢复2点饥饿值
 */
@AutoRegisterEnchantment(
        id = "blasphemy",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentBlasphemy extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentBlasphemy() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase killer = (EntityLivingBase) evt.getSource().getImmediateSource();
        EntityLivingBase dead = evt.getEntityLiving();

        if (!killer.isEntityAlive() || dead.equals(killer)) {
            return;
        }

        if (killer.getHeldItem(killer.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment blasphemy = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlasphemy.class);
        if (blasphemy == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                blasphemy,
                killer.getHeldItem(killer.getActiveHand()));

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        killer.heal(dead.getMaxHealth() * 0.1f);

        if (killer instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) killer;
            FoodStats foodStats = player.getFoodStats();
            foodStats.setFoodLevel(Math.min(foodStats.getFoodLevel() + 2, 20));
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}