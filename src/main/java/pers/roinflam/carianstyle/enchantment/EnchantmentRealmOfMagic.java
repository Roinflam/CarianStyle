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
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 魔法领域附魔
 * <p>v2.1：LivingHurt攻击者视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "realm_of_magic",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET},
        conflictsWith = {EnchantmentToppsStand.class},
        forceTreasure = true
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentRealmOfMagic extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.realm_of_magic.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "realm_of_magic";

    /**
     * 友方搜索半径（格）
     * <p>默认 6，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle ALLY_SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "ally_search_radius",
                    6, 1, 64);

    /**
     * 附近有同类携带者时的额外伤害比例
     * <p>默认 0.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_BONUS =
            EnchantmentValues.define(VALUE_ID, "damage_bonus",
                    0.5D, 0.0D, 10.0D);

    public EnchantmentRealmOfMagic() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntityLiving().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }
        if (!DamageSourceUtil.isMagicDamage(evt.getSource())) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();

        // v2.1：怪物附魔触发开关（攻击者视角，团队魔法增伤）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        Enchantment realmOfMagic = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRealmOfMagic.class);
        if (realmOfMagic == null) {
            return;
        }

        List<EntityLivingBase> allies = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                attacker,
                ALLY_SEARCH_RADIUS.getInt(),
                entity -> entity.getClass() == attacker.getClass()
        );

        for (EntityLivingBase ally : allies) {
            for (ItemStack armor : ally.getArmorInventoryList()) {
                if (!armor.isEmpty()) {
                    if (EnchantmentHelper.getEnchantmentLevel(realmOfMagic, armor) > 0) {
                        evt.setAmount(evt.getAmount() + evt.getAmount() * (float) DAMAGE_BONUS.get());
                        return;
                    }
                }
            }
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
