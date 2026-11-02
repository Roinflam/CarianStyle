package pers.roinflam.carianstyle.enchantment;

import pers.roinflam.carianstyle.utils.Reference;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 戴狄卡之祸附魔（诅咒）
 * <p>v2.1：LivingHurt受击者视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "daedicar_woe",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET},
        forceTreasure = true,
        isCurse = true
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentDaedicarWoe extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.daedicar_woe.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "daedicar_woe";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 命中后无敌帧相对默认时长的比例
     * <p>默认 0.75，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle INVULNERABLE_RATIO =
            EnchantmentValues.define(VALUE_ID, "invulnerable_ratio",
                    0.75D, 0.0D, 2.0D);

    /**
     * 触发时的伤害倍率
     * <p>默认 3.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    3.0D, 0.0D, 20.0D);

    public EnchantmentDaedicarWoe() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntityLiving().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        // v2.1：怪物附魔触发开关（受击者视角，诅咒效果对怪物也属于触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        Enchantment daedicarWoe = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDaedicarWoe.class);
        if (daedicarWoe == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(daedicarWoe, armor);
            }
        }

        totalLevel = applyLevelLimit(daedicarWoe, totalLevel, LEVEL_CAP.getInt());

        if (totalLevel <= 0) {
            return;
        }

        victim.hurtResistantTime = (int) (victim.maxHurtResistantTime * INVULNERABLE_RATIO.get());
        evt.setAmount(evt.getAmount() * (float) DAMAGE_MULTIPLIER.get());
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
