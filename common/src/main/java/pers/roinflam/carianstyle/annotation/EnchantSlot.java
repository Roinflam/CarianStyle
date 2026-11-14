package pers.roinflam.carianstyle.annotation;

import net.minecraft.world.entity.EquipmentSlot;

// 注解里不能直接写 EquipmentSlot：Fabric 出包时注解里的枚举常量名会被映射成 intermediary 字段名（field_6173），
// 运行期按名字找不到常量，一读注解就抛 AnnotationFormatError。开发环境用的是明文名，看不出来。
public enum EnchantSlot {
    MAINHAND(EquipmentSlot.MAINHAND),
    OFFHAND(EquipmentSlot.OFFHAND),
    FEET(EquipmentSlot.FEET),
    LEGS(EquipmentSlot.LEGS),
    CHEST(EquipmentSlot.CHEST),
    HEAD(EquipmentSlot.HEAD);

    private final EquipmentSlot vanilla;

    EnchantSlot(EquipmentSlot vanilla) {
        this.vanilla = vanilla;
    }

    public EquipmentSlot vanilla() {
        return vanilla;
    }
}
