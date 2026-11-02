package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;

/**
 * PORT-STUB(E3)
 *
 * 卡利亚报复附魔
 * <p>v2.2：LivingAttack格挡反击入口接入怪物附魔触发开关</p>
 *
 * <h3>v3.0：剑阵跟随持盾者</h3>
 * <p>
 * <b>生成位置本来就是对的</b>——三把剑以 {@code holder} 为中心呈 120° 三角布置，
 * 符合原作「格挡瞬间在身前弹出辉剑」的表现，本次没有改动几何。
 * </p>
 * <p>
 * 改的是两点：
 * </p>
 * <ol>
 *     <li>挂上 {@link EntityGlintblades#setHoverAnchor(Vec3)}，悬浮的 40~50 tick 里
 *         剑跟着持盾者走。举盾格挡本就是个会边挡边挪的动作，剑钉在原地会立刻穿帮；</li>
 *     <li>攻击剑的发射点改用持盾者的<b>实时</b>位置推算。原实现捕获的是生成时的
 *         {@code posX/posY/posZ}，持盾者移动后剑会从身后凭空飞出。</li>
 * </ol>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "carian_retaliation",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.SHIELD,
        slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND},
        conflictsWith = {
                EnchantmentScholarShield.class,
                EnchantmentImmutableShield.class
        },
        baseEnchantability = 20,
        levelMultiplier = 15
)
@Deprecated
public class EnchantmentCarianRetaliation extends EnchantmentBase {
    private static final String VALUE_ID = "carian_retaliation";
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle BLADE_COUNT = EnchantmentValues.define(VALUE_ID, "blade_count",
                    3, 1, 32);
    private static final EnchantmentValues.Handle BASE_DELAY = EnchantmentValues.define(VALUE_ID, "base_delay",
                    40, 0, 600);
    private static final EnchantmentValues.Handle DELAY_STEP = EnchantmentValues.define(VALUE_ID, "delay_step",
                    5, 0, 120);
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.2D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle TRACKING_STRENGTH = EnchantmentValues.define(VALUE_ID, "tracking_strength",
                    0.15D, 0.0D, 1.0D);
    private static final double RING_RADIUS = 1.5;
    private static final double RING_HEIGHT = 0.5;
    public EnchantmentCarianRetaliation() {
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"),
                new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND});
    }
    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        return !ench.isOf(EnchantmentScholarShield.class) &&
                !ench.isOf(EnchantmentImmutableShield.class);
    }
}

