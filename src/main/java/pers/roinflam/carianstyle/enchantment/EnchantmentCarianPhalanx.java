package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E3)
 *
 * 卡利亚方阵附魔
 * <p>v2.2：LivingDamage攻击者视角入口接入怪物附魔触发开关</p>
 *
 * <h3>v3.0：剑阵改为围绕<b>施法者</b>成环并跟随（修正生成位置）</h3>
 * <p>
 * <b>原实现的问题：</b>辉剑是围着 {@code victim}（被打的人）以 3×3 网格生成的：
 * </p>
 * <pre>
 * showBlade.setPos(victim.getX() + x * 1.5, victim.getY() + 2, victim.getZ() + z * 1.5);
 * </pre>
 * <p>
 * 效果上等于「敌人头顶凭空冒出一圈剑再往下扎」——这既不是原作的表现，
 * 也让玩家完全感受不到「这阵是我召出来的」。原作卡利亚圆阵是
 * <b>辉剑在施法者身周成环 → 齐射向目标</b>。
 * </p>
 * <p>
 * <b>现改为：</b>以 {@code attacker}（射箭的人，即施法者）为中心、
 *  {@link #BLADE_COUNT} 把剑，
 * 并通过 {@link EntityGlintblades#setHoverAnchor(Vec3)} 挂上悬浮锚点——
 * 施法者走位时整个剑阵跟着平移，直到各自的延迟结束才依次射出。
 * </p>
 * <p>
 * <b>数量由 8 改为 9：</b>原来的 3×3 去掉中心恰好是 8 把，但语言文件里写的是
 * 「生成9道魔法辉剑」，两边对不上。改成环形之后数量不再受网格约束，
 * 索性取 9 把与文案一致，省掉一次文案改动。
 * </p>
 * <p>
 * <b>依次发射而非齐射：</b>延迟按下标递增（{@link #BASE_DELAY} + i × {@link #DELAY_STEP}），
 * 剑会绕着环一把接一把地飞出去。齐射在同一帧生成 9 个投射物，
 * 既看不清也容易撞上同一 tick 的多次 {@code hurt} 判定。
 * </p>
 *
 * @version 3.0
 */
@AutoRegisterEnchantment(id = "carian_phalanx", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnchantTarget.BOW, slots = {EquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentPyroxeneIce.class}, baseEnchantability = 20, levelMultiplier = 10)
@Deprecated
public class EnchantmentCarianPhalanx extends EnchantmentBase {
    private static final String VALUE_ID = "carian_phalanx";
    private static final EnchantmentValues.Handle BLADE_COUNT = EnchantmentValues.define(VALUE_ID, "blade_count",
                    9, 1, 64);
    private static final EnchantmentValues.Handle FAN_RADIUS = EnchantmentValues.define(VALUE_ID, "fan_radius",
                    2.1D, 0.5D, 32.0D);
    private static final EnchantmentValues.Handle BASE_DELAY = EnchantmentValues.define(VALUE_ID, "base_delay",
                    45, 0, 600);
    private static final EnchantmentValues.Handle DELAY_STEP = EnchantmentValues.define(VALUE_ID, "delay_step",
                    5, 0, 120);
    private static final EnchantmentValues.Handle TRIGGER_CHANCE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "trigger_chance_per_level",
                    2.0D, 0.0D, 100.0D);
    private static final double FAN_HALF_SPREAD = 125.0;
    private static final double FAN_HEIGHT_CENTER = 2.0;
    private static final double FAN_HEIGHT_DROP = 0.75;
    public EnchantmentCarianPhalanx() {
        super(EnchantTarget.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
}

