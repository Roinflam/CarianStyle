package pers.roinflam.carianstyle.annotation;

import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.EventPriority;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PORT-STUB(B1a)
 *
 * 附魔自动注册注解
 * <p>
 * 在附魔类上添加此注解后，系统会自动完成注册等操作
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@Deprecated
public @interface AutoRegisterEnchantment {
    String id();
    pers.roinflam.carianstyle.annotation.EnchantmentCategory category();
    EnchantmentRarity rarity();
    String customType() default "";
    EquipmentSlot[] slots() default {EquipmentSlot.MAINHAND};
    Class<?>[] conflictsWith() default {};
    Class<?>[] allowWith() default {};
    int baseEnchantability() default -1;
    int levelMultiplier() default 10;
    boolean forceTreasure() default false;
    boolean isCurse() default false;
}

