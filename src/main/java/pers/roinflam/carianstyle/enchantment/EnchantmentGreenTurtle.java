package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 绿龟附魔
 * <p>
 * 受到治疗时额外回复：治疗量 × 7.5% × 全身等级，再加 全身等级 × 0.15 × 已损失生命比例。
 * </p>
 * <p>v2.1：LivingHeal受治疗者视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "green_turtle",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentGreenTurtle extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.green_turtle.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "green_turtle";

    /**
     * 每级按治疗量提供的额外治疗比例
     * <p>默认 0.075，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.075D, 0.0D, 2.0D);

    /**
     * 每级按已损失生命比例提供的额外治疗系数
     * <p>默认 0.15，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle MISSING_HEALTH_BONUS =
            EnchantmentValues.define(VALUE_ID, "missing_health_bonus",
                    0.15D, 0.0D, 5.0D);

    public EnchantmentGreenTurtle() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntityLiving().world.isRemote) {
            return;
        }

        EntityLivingBase entity = evt.getEntityLiving();

        // v2.1：怪物附魔触发开关（受治疗者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(entity, false)) return;

        Enchantment greenTurtle = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGreenTurtle.class);
        if (greenTurtle == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(greenTurtle, armor);
            }
        }

        totalLevel = decayLevel(greenTurtle, totalLevel);

        if (totalLevel <= 0) {
            return;
        }

        float missingHealthPercent = 1 - entity.getHealth() / entity.getMaxHealth();
        float bonusHeal = evt.getAmount() * totalLevel * (float) HEAL_PER_LEVEL.get()
                + totalLevel * (float) MISSING_HEALTH_BONUS.get() * missingHealthPercent;

        evt.setAmount(evt.getAmount() + bonusHeal);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        return super.checkCompatibility(ench)
                && !ench.equals(Enchantments.PROTECTION)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameShelter.class));
    }
}
