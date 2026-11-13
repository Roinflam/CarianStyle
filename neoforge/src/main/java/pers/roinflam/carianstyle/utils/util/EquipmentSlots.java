package pers.roinflam.carianstyle.utils.util;

import net.minecraft.world.entity.EquipmentSlot;

/**
 * 附魔扫描用的装备槽位表。
 * <p>
 * <b>问题：</b>1.21.1 的 {@link EquipmentSlot} 枚举末尾多了 {@code BODY}（马铠、狼铠的槽位）。
 * 原来到处写 {@code EquipmentSlot.values()} 遍历 6 个槽位，直接照搬会多扫一个 BODY：
 * 马 / 狼身上的铠甲会被当成装备触发附魔，扫描开销也跟着多一份。
 * </p>
 * <p>
 * <b>做法：</b>统一用 {@link #SCAN}，内容就是 1.20.1 枚举的 6 个槽位、顺序也一样
 * （主手、副手、脚、腿、胸、头），扫描顺序依赖它的地方（先护甲后手持等）行为不变。
 * </p>
 */
public final class EquipmentSlots {

    /** 1.20.1 的 6 个槽位，顺序同 1.20.1 的 {@code EquipmentSlot.values()}。数组当只读用，不要改里面的元素。 */
    public static final EquipmentSlot[] SCAN = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    };

    private EquipmentSlots() {
    }
}
