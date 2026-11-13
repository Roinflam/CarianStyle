package pers.roinflam.carianstyle.base.enchantment;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * 一个装备槽位的「附魔有没有可能变了」的轻量判据。
 *
 * <h3>问题</h3>
 * <p>
 * 玩家装备缓存以前用「物品 + 数量 + 耐久」的哈希判断装备变没变，每 tick 每个槽位都要调一次
 * {@code getDamageValue()}。有的模组工具把耐久算在自己的数据结构里，读一次耐久要重建工具数据，
 * 人多的服务器上这一项就占了附魔模组玩家 tick 开销的一半多。而耐久根本不影响附魔，换了也照样白扫一遍；
 * 反过来换成另一件同物品、同耐久、附魔不同的装备，哈希一样，要等强制重扫才看得出来。
 * </p>
 *
 * <h3>为什么这么判</h3>
 * <p>
 * 本模组读装备附魔只读物品的 {@code minecraft:enchantments} 组件（{@link EnchantmentLevels}，附魔书里存的不算，
 * 所以 {@code stored_enchantments} 不用记）。铁砧、砂轮、附魔台、{@code /enchant}、{@code ItemStack.enchant}、
 * {@code set(ENCHANTMENTS, ...)} 改附魔都是给物品换上一个新的 {@code ItemEnchantments} 对象，不在原对象上改。
 * 所以记下槽位里的物品对象和附魔组件对象，两样都是同一个对象就认为附魔没变：
 * </p>
 * <ul>
 *   <li>换手、换装备、物品被替换：槽位里是另一个对象；</li>
 *   <li>附魔增、删、改等级：组件换成了新对象；</li>
 *   <li>物品耗尽（数量归零、同一个对象变成空）：单独看 {@code isEmpty()}。</li>
 * </ul>
 * <p>
 * 看不出来的只有绕开正常写法的情况：拿到组件内部的条目原地改等级、{@code Mutable.toImmutable()} 之后接着改同一个
 * Mutable（两者共用底层的表），或别的模组改写了物品返回附魔的方法。原版、NeoForge 和本模组都没有这种写法，
 * 各处缓存另有定期强制重扫兜底，生效时机和 1.20.1 一样。
 * 1.20.1 的附魔在可变的 NBT 列表里，另外要比列表长度；这里不用。
 * 不读耐久、不算哈希，判一次只是一次组件查找。
 * </p>
 */
public final class EquipmentStamp {

    /** 记过、且当时是空槽位 */
    private static final Object EMPTY_MARK = new Object();

    /** 记下的物品对象；{@code null} = 还没记过 */
    private Object stack;
    private Object enchantments;

    /**
     * 和上次记下的比，变了就改记成现在这个。
     *
     * @param current 槽位里现在的物品
     * @return true = 和上次不一样（或者第一次记）
     */
    public boolean update(@Nonnull ItemStack current) {
        if (current.isEmpty()) {
            if (stack == EMPTY_MARK) {
                return false;
            }
            stack = EMPTY_MARK;
            enchantments = null;
            return true;
        }
        Object ench = current.get(DataComponents.ENCHANTMENTS);
        if (stack == current && enchantments == ench) {
            return false;
        }
        stack = current;
        enchantments = ench;
        return true;
    }

    /**
     * 只比不记。
     *
     * @param current 槽位里现在的物品
     * @return true = 和上次记下的一样
     */
    public boolean matches(@Nonnull ItemStack current) {
        if (current.isEmpty()) {
            return stack == EMPTY_MARK;
        }
        return stack == current && enchantments == current.get(DataComponents.ENCHANTMENTS);
    }
}
