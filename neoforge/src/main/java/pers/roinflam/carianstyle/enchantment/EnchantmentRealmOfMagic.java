package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;

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
        type = EnchantTarget.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        conflictsWith = {EnchantmentToppsStand.class},
        forceTreasure = true,
        baseEnchantability = 30,
        levelMultiplier = 0
)
@EventBusSubscriber
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
        super(EnchantTarget.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }
        if (!DamageSourceUtil.isMagicDamage(evt.getSource())) {
            return;
        }

        LivingEntity attacker = (LivingEntity) evt.getSource().getEntity();

        // v2.1：怪物附魔触发开关（攻击者视角，团队魔法增伤）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        EnchantmentBase realmOfMagic = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRealmOfMagic.class);
        if (realmOfMagic == null) {
            return;
        }

        List<LivingEntity> allies = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                attacker,
                ALLY_SEARCH_RADIUS.getInt(),
                entity -> entity.getClass() == attacker.getClass()
        );

        for (LivingEntity ally : allies) {
            for (ItemStack armor : ally.getArmorSlots()) {
                if (!armor.isEmpty()) {
                    if (EnchantmentLevels.get(realmOfMagic, armor) > 0) {
                        evt.setAmount(evt.getAmount() + evt.getAmount() * (float) DAMAGE_BONUS.get());
                        return;
                    }
                }
            }
        }
    }

}
