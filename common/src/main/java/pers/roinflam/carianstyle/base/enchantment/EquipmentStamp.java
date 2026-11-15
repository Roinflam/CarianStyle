package pers.roinflam.carianstyle.base.enchantment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * 一个装备槽位的「附魔有没有可能变了」的轻量判据。
 *
 * <h3>问题</h3>
 * <p>
 * 玩家装备缓存以前用「物品 + 数量 + 耐久」的哈希判断装备变没变，每 tick 每个槽位都要调一次
 * {@code getDamageValue()}。匠魂这类工具把耐久存在自己的 NBT 结构里，{@code getDamage} 每次都要重建
 * 工具数据，人多的服务器上这一项就占了附魔模组玩家 tick 开销的一半多（约 0.13 ms/tick）。
 * 而耐久根本不影响附魔，换了也照样白扫一遍。
 * </p>
 *
 * <h3>为什么这么判</h3>
 * <p>
 * 附魔只来自物品 NBT 里的 {@code Enchantments}（附魔书是 {@code StoredEnchantments}）。
 * 记下槽位里的物品对象、这两个列表标签对象和它们的长度，四样都没变就认为附魔没变：
 * </p>
 * <ul>
 *   <li>换手、换装备、物品被替换：槽位里是另一个对象；</li>
 *   <li>铁砧、砂轮、附魔台、{@code setEnchantments}：产出新物品或换掉整个列表；</li>
 *   <li>{@code /enchant} 和 {@code ItemStack.enchant}：往列表里追加，长度变了；</li>
 *   <li>物品耗尽（数量归零、同一个对象变成空）：单独看 {@code isEmpty()}。</li>
 * </ul>
 * <p>
 * 只有「原地改已有某一条的等级 / id、长度不变」看不出来，各处缓存另有定期强制重扫兜底。
 * 不读耐久、不算 NBT 哈希，判一次只是两次标签查找。
 * </p>
 */
public final class EquipmentStamp {

    /** 记过、且当时是空槽位 */
    private static final Object EMPTY_MARK = new Object();

    /** 记下的物品对象；{@code null} = 还没记过 */
    private Object stack;
    private Tag enchantments;
    private int enchantmentsSize;
    private Tag stored;
    private int storedSize;

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
            stored = null;
            enchantmentsSize = 0;
            storedSize = 0;
            return true;
        }
        CompoundTag tag = current.getTag();
        Tag ench = tag == null ? null : tag.get("Enchantments");
        Tag book = tag == null ? null : tag.get("StoredEnchantments");
        int enchSize = sizeOf(ench);
        int bookSize = sizeOf(book);
        if (stack == current && enchantments == ench && enchantmentsSize == enchSize
                && stored == book && storedSize == bookSize) {
            return false;
        }
        stack = current;
        enchantments = ench;
        enchantmentsSize = enchSize;
        stored = book;
        storedSize = bookSize;
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
        if (stack != current) {
            return false;
        }
        CompoundTag tag = current.getTag();
        Tag ench = tag == null ? null : tag.get("Enchantments");
        Tag book = tag == null ? null : tag.get("StoredEnchantments");
        return enchantments == ench && enchantmentsSize == sizeOf(ench)
                && stored == book && storedSize == sizeOf(book);
    }

    private static int sizeOf(Tag tag) {
        return tag instanceof ListTag list ? list.size() : -1;
    }
}
