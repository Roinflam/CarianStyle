package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E4)
 *
 * 学者盾附魔
 * <p>v2.2：受击反伤+受击减伤双向入口接入怪物附魔触发开关</p>
 * <p>v2.3：onLivingAttack 反伤入口新增 ThreadLocal 重入保护，
 * 阻断双方同时举学者盾对攻时反伤伤害互相触发对方反伤、形成来回反弹的事件级联。
 * 单次反伤与减伤逻辑（含被反伤者举盾减免反伤）完全保留，仅阻断连锁二次反伤。</p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@AutoRegisterEnchantment(
        id = "scholar_shield",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantTarget.SHIELD,
        slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND},
        baseEnchantability = 5,
        levelMultiplier = 10
)
@Deprecated
public class EnchantmentScholarShield extends EnchantmentBase {
    private static final String VALUE_ID = "scholar_shield";
    private static final EnchantmentValues.Handle REFLECT_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "reflect_per_level",
                    0.1D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle REDUCTION_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "reduction_per_level",
                    0.075D, 0.0D, 1.0D);
    private static final ThreadLocal<Boolean> PROCESSING_RETALIATION = ThreadLocal.withInitial(() -> Boolean.FALSE);
    public EnchantmentScholarShield() {
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EquipmentSlot[]{
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        });
    }
}

