package pers.roinflam.carianstyle.mc112.util;

import net.minecraft.potion.PotionEffect;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;

/**
 * 原地改 {@code PotionEffect} 的时长和等级
 * <p>
 * 1.12.2 的 {@code PotionEffect} 只有 getter，时长、等级都是私有字段（{@code duration} / {@code field_76460_b}、
 * {@code amplifier} / {@code field_76461_c}）。效果修改钩子在 {@code PotionAddedEvent} 里要改的是
 * 马上要存进实体效果表（或合并进旧效果）的那个实例，只能反射改。
 * </p>
 */
public final class PotionEffectAccess {

    private static final Field DURATION = Reflect.field(PotionEffect.class, "duration", "field_76460_b");
    private static final Field AMPLIFIER = Reflect.field(PotionEffect.class, "amplifier", "field_76461_c");

    private PotionEffectAccess() {
    }

    /**
     * @param effect   药水效果
     * @param duration 新时长（tick）
     */
    public static void setDuration(@Nonnull PotionEffect effect, int duration) {
        try {
            DURATION.setInt(effect, duration);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("改不了药水时长", e);
        }
    }

    /**
     * @param effect    药水效果
     * @param amplifier 新等级（0 = I 级）
     */
    public static void setAmplifier(@Nonnull PotionEffect effect, int amplifier) {
        try {
            AMPLIFIER.setInt(effect, amplifier);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("改不了药水等级", e);
        }
    }
}
