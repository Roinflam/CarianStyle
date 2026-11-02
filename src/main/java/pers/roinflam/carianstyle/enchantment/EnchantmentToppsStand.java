package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 托普斯之架附魔
 * <p>
 * 受击者或攻击者周围 6 格内只要有人穿着本附魔，这一下魔法伤害就整个作废。
 * </p>
 * <p>v2.1：LivingDamage受击者视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "topps_stand",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET},
        forceTreasure = true
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentToppsStand extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.topps_stand.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "topps_stand";

    /**
     * 友方搜索半径（格，受击方与攻击方各搜一次）
     * <p>默认 6，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "search_radius",
                    6, 1, 64);

    public EnchantmentToppsStand() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntityLiving().world.isRemote) {
            return;
        }

        if (!DamageSourceUtil.isMagicDamage(evt.getSource())) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        // v2.1：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        Enchantment toppsStand = EnchantmentRegistry.getEnchantmentByClass(EnchantmentToppsStand.class);
        if (toppsStand == null) {
            return;
        }

        List<EntityLivingBase> entities = new ArrayList<>(
                EntityUtil.getNearbyEntities(EntityLivingBase.class, victim, SEARCH_RADIUS.getInt()));
        if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
            entities.addAll(EntityUtil.getNearbyEntities(EntityLivingBase.class, attacker, SEARCH_RADIUS.getInt()));
        }

        for (EntityLivingBase entity : entities) {
            for (ItemStack armor : entity.getArmorInventoryList()) {
                if (!armor.isEmpty()) {
                    if (EnchantmentHelper.getEnchantmentLevel(toppsStand, armor) > 0) {
                        evt.setCanceled(true);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
