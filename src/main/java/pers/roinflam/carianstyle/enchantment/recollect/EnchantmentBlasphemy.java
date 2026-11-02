package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.FoodStats;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 亵渎附魔
 * <p>击杀目标时按目标最大生命回复自身，玩家另外恢复饱食度。</p>
 * <p>v2.2：onLivingDeath 入口接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "blasphemy", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnumEnchantmentType.WEAPON, slots = {EntityEquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentBlasphemy extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.blasphemy.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "blasphemy";

    /**
     * 击杀时按目标最大生命回复自身的比例
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_RATIO =
            EnchantmentValues.define(VALUE_ID, "heal_ratio",
                    0.1D, 0.0D, 2.0D);

    /**
     * 击杀时恢复的饱食度点数
     * <p>默认 2，允许范围 0 ~ 20。</p>
     */
    private static final EnchantmentValues.Handle FOOD_RESTORE =
            EnchantmentValues.define(VALUE_ID, "food_restore",
                    2, 0, 20);

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentBlasphemy() { super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) return;
        EntityLivingBase killer = (EntityLivingBase) evt.getSource().getImmediateSource();
        EntityLivingBase dead = evt.getEntityLiving();
        if (!killer.isEntityAlive() || dead.equals(killer)) return;

        // v2.2：怪物附魔触发开关（击杀者视角，击杀奖励非濒死触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(killer, false)) return;

        ItemStack heldItem = killer.getHeldItemMainhand();
        if (heldItem.isEmpty()) return;
        Enchantment blasphemy = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlasphemy.class);
        if (blasphemy == null) return;
        int level = EnchantmentHelper.getEnchantmentLevel(blasphemy, heldItem);
        level = applyLevelLimit(blasphemy, level, 10);
        if (level <= 0) return;
        killer.heal(dead.getMaxHealth() * (float) HEAL_RATIO.get());
        if (killer instanceof EntityPlayer) {
            FoodStats foodData = ((EntityPlayer) killer).getFoodStats();
            foodData.setFoodLevel(Math.min(foodData.getFoodLevel() + FOOD_RESTORE.getInt(), 20));
        }
    }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
