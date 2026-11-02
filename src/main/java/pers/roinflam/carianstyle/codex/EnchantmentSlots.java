package pers.roinflam.carianstyle.codex;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 读取附魔的生效槽位（{@code Enchantment.applicableEquipmentTypes}）。
 *
 * <h3>为什么要反射</h3>
 * <p>
 * 本模组自己的附魔能从注解里读到槽位，但原版和别家模组的读不到——
 * {@code Enchantment.applicableEquipmentTypes} 是 {@code private final}，没有对应的 getter。
 * </p>
 * <p>
 * 唯一的公开近路 {@code getEntityEquipment(EntityLivingBase)} 走不通：它只返回
 * <b>该槽位上有物品</b>的条目，玩家空着手就什么都查不到，答案会随玩家当前装备而变。
 * </p>
 *
 * <h3>为什么按类型找而不是按名字</h3>
 * <p>
 * 常规做法是按 MCP 名 + SRG 名查字段，名字必须写死，
 * 而<b>写错了不会编译报错，只会在正式环境运行时抛异常</b>——恰恰是开发时测不出来的那一类错。
 * </p>
 * <p>
 * 而 {@code Enchantment} 声明的实例字段里，{@code EntityEquipmentSlot[]} 类型的<b>只有一个</b>
 * （其余是 {@code Rarity}、{@code EnumEnchantmentType}、{@code String}）。
 * 按类型匹配因此是无歧义的，且开发环境（MCP 名）与正式环境（SRG 名）
 * 走的是同一段代码——没有映射名要维护。
 * </p>
 * <p>
 * 代价是万一有别的模组往 {@code Enchantment} 里加了第二个 {@code EntityEquipmentSlot[]} 字段，
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
public final class EnchantmentSlots {

    /**
     * 解析出的字段；{@code null} 表示解析失败或存在歧义。
     * <p>只在类初始化时解析一次，之后是一次字段读取。</p>
     */
    private static final Field SLOTS_FIELD = resolve();

    private EnchantmentSlots() {
    }

    /**
     * 在 {@code Enchantment} 声明的字段里找出唯一的 {@code EntityEquipmentSlot[]}。
     *
     * @return 该字段；找不到或不唯一时返回 null
     */
    private static Field resolve() {
        Field found = null;
        for (Field field : Enchantment.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.getType() != EntityEquipmentSlot[].class) {
                continue;
            }
            if (found != null) {
                LogUtil.warn("卡利亚式附魔 - Enchantment 中存在多个 EntityEquipmentSlot[] 字段"
                                + "（%s 与 %s），无法确定哪个是生效槽位，已放弃读取",
                        found.getName(), field.getName());
                return null;
            }
            found = field;
        }

        if (found == null) {
            LogUtil.warn("卡利亚式附魔 - 未能在 Enchantment 中找到 EntityEquipmentSlot[] 字段，"
                    + "百科将不显示外部附魔的生效槽位");
            return null;
        }

        try {
            found.setAccessible(true);
        } catch (Exception e) {
            LogUtil.warn("卡利亚式附魔 - 无法访问 Enchantment 的槽位字段 %s：%s",
                    found.getName(), e.getMessage());
            return null;
        }
        LogUtil.debug("卡利亚式附魔 - 已解析附魔槽位字段：%s", found.getName());
        return found;
    }

    /**
     * 取某个附魔的生效槽位。
     *
     * @param enchantment 附魔
     * @return 槽位列表；解析失败时为空列表
     */
    @Nonnull
    public static List<EntityEquipmentSlot> of(@Nonnull Enchantment enchantment) {
        if (SLOTS_FIELD == null) {
            return Collections.emptyList();
        }
        try {
            Object value = SLOTS_FIELD.get(enchantment);
            if (value instanceof EntityEquipmentSlot[]) {
                return Collections.unmodifiableList(Arrays.asList(((EntityEquipmentSlot[]) value).clone()));
            }
        } catch (Exception e) {
            LogUtil.debug("卡利亚式附魔 - 读取 %s 的槽位失败：%s",
                    enchantment.getName(), e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * @return 槽位字段是否可用
     */
    public static boolean isAvailable() {
        return SLOTS_FIELD != null;
    }
}
