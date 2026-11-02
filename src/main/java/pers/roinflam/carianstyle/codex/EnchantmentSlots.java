package pers.roinflam.carianstyle.codex;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.utils.util.LogUtil;
import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;

/**
 * PORT-STUB(G)
 *
 * 读取附魔的生效槽位（{@code Enchantment.slots}）。
 *
 * <h3>为什么要反射</h3>
 * <p>
 * 本模组自己的附魔能从注解里读到槽位，但原版和别家模组的读不到——
 * {@code Enchantment.slots} 是 {@code private final}，没有对应的 getter。
 * </p>
 * <p>
 * 唯一的公开近路 {@code getSlotItems(LivingEntity)} 走不通：它只返回
 * <b>该槽位上有物品</b>的条目，玩家空着手就什么都查不到，答案会随玩家当前装备而变。
 * </p>
 *
 * <h3>为什么按类型找而不是按名字</h3>
 * <p>
 * 常规做法是 {@code ObfuscationReflectionHelper.findField(Enchantment.class, "f_XXXXX_")}，
 * 传 SRG 名，Forge 在开发环境自动映射回 {@code slots}。问题是这个 SRG 名必须写死，
 * 而<b>写错了不会编译报错，只会在生产环境运行时抛异常</b>——恰恰是开发时测不出来的那一类错。
 * </p>
 * <p>
 * 而 {@code Enchantment} 声明的四个字段里，{@code EquipmentSlot[]} 类型的<b>只有一个</b>
 * （其余是 {@code Rarity}、{@code EnchantmentCategory}、{@code String}）。
 * 按类型匹配因此是无歧义的，且开发环境（{@code slots}）与生产环境（{@code f_XXXXX_}）
 * 走的是同一段代码——没有映射名要维护，版本升级也不会因为改名而失效。
 * </p>
 * <p>
 * 代价是万一以后官方给 {@code Enchantment} 加了第二个 {@code EquipmentSlot[]} 字段，
 * 匹配就不再唯一。所以这里<b>发现多个就直接放弃</b>并记一条日志，
 * 而不是取第一个——猜错的后果是界面长期显示错误的槽位，没人会发现。
 * </p>
 *
 * <h3>失败时的行为</h3>
 * <p>
 * 反射拿不到就返回空列表，界面据此整块略过槽位一行。
 * 这是纯展示信息，缺了不影响任何玩法，没有理由为它冒崩溃的风险。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
@Deprecated
public final class EnchantmentSlots {
    private static final Field SLOTS_FIELD = null; // PORT-STUB
    private EnchantmentSlots() {
    }
    @Nonnull public static List<EquipmentSlot> of(@Nonnull Enchantment enchantment) {
        return java.util.List.of();
    }
    public static boolean isAvailable() {
        return false;
    }
}

