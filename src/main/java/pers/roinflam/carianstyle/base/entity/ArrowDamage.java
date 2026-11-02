package pers.roinflam.carianstyle.base.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.DoubleUnaryOperator;

/**
 * 改箭伤时把弓上的力量加成算进去
 *
 * <h3>问题</h3>
 * <p>
 * 1.20.1 的力量在拉弓放箭时直接 {@code setBaseDamage(base + 0.5 × 等级 + 0.5)}，箭进世界时 {@code baseDamage} 已经含力量，
 * 本模组在进世界 / 命中事件里改 {@code baseDamage} 时乘的是含力量的值。1.21.1 的力量是附魔 JSON 里的
 * {@code minecraft:damage} 效果，到 {@code AbstractArrow.onHitEntity} 里才由 {@code EnchantmentHelper.modifyDamage}
 * 加上，晚于本模组的改动，照搬就变成「(基础 × 倍率) + 力量」。例：硬箭 1 级 + 力量 V，1.20.1 是 (2 + 3) × 1.8 = 9，
 * 照搬变成 2 × 1.8 + 2.5 = 6.1（力量从 3 变 2.5 是 1.21 原版自己改的，不归本模组管）。
 * </p>
 *
 * <h3>做法</h3>
 * <p>
 * 先用和命中时原版完全同一套的求值（{@code modifyDamage(世界, 弓, 目标, 箭伤害源, 0)}）求出力量要加多少 p：
 * 力量的条件只看直接攻击者是不是箭，目标只是占位，进世界时还没有目标就用射手。
 * {@link #scale} 把 baseDamage 改成 {@code f(base + p) − p}：命中时原版再加回 p，结果就是「对含力量的值乘」。
 * 几处按顺序叠加时每一步都「加回 p 再算、算完减掉 p」，最终和 1.20.1 一样是对含力量的值依次乘。
 * </p>
 */
public final class ArrowDamage {

    private ArrowDamage() {
    }

    /**
     * 命中时原版要给这支箭加的附魔伤害（力量等），不在服务端或箭没有记下发射它的武器时为 0。
     *
     * @param arrow  箭
     * @param target 命中目标；还没有目标（刚进世界）时传 null
     */
    public static float bonus(@Nonnull AbstractArrow arrow, @Nullable Entity target) {
        if (!(arrow.level() instanceof ServerLevel serverLevel)) {
            return 0.0F;
        }
        ItemStack weapon = arrow.getWeaponItem();
        if (weapon == null || weapon.isEmpty()) {
            return 0.0F;
        }
        Entity owner = arrow.getOwner();
        // 伤害源照 onHitEntity 的写法：没有射手时攻击者记成箭自己
        DamageSource source = arrow.damageSources().arrow(arrow, owner != null ? owner : arrow);
        Entity victim = target != null ? target : (owner != null ? owner : arrow);
        return EnchantmentHelper.modifyDamage(serverLevel, weapon, victim, source, 0.0F);
    }

    /**
     * 含力量加成的箭伤（1.20.1 的 {@code getBaseDamage()}），给只读不改的地方用（唤星的雷伤、硬箭的演出估值）。
     */
    public static double effectiveBase(@Nonnull AbstractArrow arrow, @Nullable Entity target) {
        return arrow.getBaseDamage() + bonus(arrow, target);
    }

    /**
     * 按 1.20.1 的写法改箭伤：{@code f} 拿到的是含力量的值，返回含力量的新值。
     *
     * @param arrow  箭
     * @param target 命中目标；进世界时传 null
     * @param f      1.20.1 里对 {@code getBaseDamage()} 的那条式子
     */
    public static void scale(@Nonnull AbstractArrow arrow, @Nullable Entity target, @Nonnull DoubleUnaryOperator f) {
        double p = bonus(arrow, target);
        arrow.setBaseDamage(f.applyAsDouble(arrow.getBaseDamage() + p) - p);
    }
}
