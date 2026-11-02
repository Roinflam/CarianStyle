package pers.roinflam.carianstyle.codex;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.enchantment.Enchantment;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 读取附魔的生效槽位。
 *
 * <h3>从哪里读</h3>
 * <p>
 * 1.21.1 的附魔是注册表里的数据，生效槽位直接写在定义里（{@code definition().slots()}），
 * 原版和别家模组的附魔都一样能读到。1.20.1 那时 {@code Enchantment.slots} 是私有字段、没有 getter，
 * 只能按类型反射去找；这一套在 1.21.1 已经用不着了。
 * </p>
 *
 * <h3>槽位组展开成槽位</h3>
 * <p>
 * 定义里存的是槽位组（{@code EquipmentSlotGroup}：主手、护甲、任意……），界面按单个槽位逐个列名字，
 * 所以这里把组展开。展开顺序照 1.20.1 原版附魔的写法：护甲组按「头、胸、腿、脚」
 * （1.20.1 {@code Enchantments.ARMOR_SLOTS} 的顺序），其余按 {@link EquipmentSlot#values()} 的声明顺序；
 * 几个组展开后有重复的只留第一次出现的。
 * </p>
 * <p>
 * 1.21.1 多了一个 {@code BODY} 槽（马铠、狼铠），「护甲」「任意」两个组在 1.21.1 里都包含它。
 * 原版能放进这个槽的马铠、狼铠都上不了附魔，这一格对这两个组的附魔实际上永远是空的，
 * 所以展开这两个组时不列它，界面和 1.20.1 看到的一样；只有定义里单独写了 {@code body} 组的才列出来。
 * </p>
 *
 * <h3>失败时的行为</h3>
 * <p>
 * 定义里没写槽位就返回空列表，界面据此整块略过槽位一行。
 * 这是纯展示信息，缺了不影响任何玩法。
 * </p>
 *
 * @author RoinFlam
 * @version 1.1
 */
public final class EnchantmentSlots {

    /** 护甲组的展开顺序，与 1.20.1 原版 {@code Enchantments.ARMOR_SLOTS} 一致 */
    private static final EquipmentSlot[] ARMOR_ORDER = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private EnchantmentSlots() {
    }

    /**
     * 取某个附魔的生效槽位。
     *
     * @param enchantment 附魔（世界注册表里的 Holder）
     * @return 槽位列表；定义里没写槽位时为空列表
     */
    @Nonnull
    public static List<EquipmentSlot> of(@Nonnull Holder<Enchantment> enchantment) {
        List<EquipmentSlotGroup> groups = enchantment.value().definition().slots();
        if (groups.isEmpty()) {
            return Collections.emptyList();
        }
        Set<EquipmentSlot> slots = new LinkedHashSet<>();
        for (EquipmentSlotGroup group : groups) {
            if (group == EquipmentSlotGroup.ARMOR) {
                Collections.addAll(slots, ARMOR_ORDER);
                continue;
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot == EquipmentSlot.BODY && group != EquipmentSlotGroup.BODY) {
                    continue;
                }
                if (group.test(slot)) {
                    slots.add(slot);
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(slots));
    }

    /**
     * @return 槽位是否可读（1.21.1 直接读定义，恒为 true；保留给界面原有的判断）
     */
    public static boolean isAvailable() {
        return true;
    }
}
