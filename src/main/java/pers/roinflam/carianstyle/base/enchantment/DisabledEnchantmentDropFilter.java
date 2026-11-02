package pers.roinflam.carianstyle.base.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 被禁用附魔的掉落物兜底清洗
 *
 * <h3>问题</h3>
 * <p>
 * {@link EnchantmentBase} 覆写了 isAllowedOnBooks / canApplyAtEnchantingTable，
 * 原版附魔台、附魔书都会跳过被禁用的附魔，村民交易由 {@code TradeFilterHook} 过滤。
 * 但还有这几条来路不经过这些判断：
 * <ul>
 *   <li>战利品表的 {@code enchant_randomly}：1.12.2 版不看任何开关，列表为空时从全部附魔里抽；</li>
 *   <li>其它模组自己遍历附魔注册表挑附魔，或者在配置加载之前就把「可发现的附魔」缓存成列表、之后一直用它；</li>
 *   <li>其它模组直接构造附魔书，再塞进 LivingDropsEvent 或丢进世界。</li>
 * </ul>
 * 这些东西最后大多以掉落物实体的形式出现在世界里（怪物死亡掉落、LivingDropsEvent 里追加的掉落、
 * 钓鱼、玩家丢出……），都要经过 {@code EntityJoinWorldEvent}。
 * </p>
 *
 * <h3>为什么这么改</h3>
 * <p>
 * 掉落物实体加入服务端世界时，从物品的 {@code ench} 与 {@code StoredEnchantments} 里剔除被禁用的附魔，
 * 不针对任何一个具体模组。附魔书剔完一条不剩就换成同数量的普通书：
 * 原版 {@code enchant_randomly} 找不到可用附魔时给的也是原样的书，玩家看到的结果和「这次没抽到附魔」一样；
 * 而且掉落物照常生成，不取消实体，依赖 spawnEntity 返回值的模组不会出岔子。
 * </p>
 * <p>
 * 优先级取 HIGHEST：有的模组会在这个事件里把掉落物直接收进玩家背包并取消实体，要赶在它们之前处理。
 * </p>
 *
 * <h3>开销</h3>
 * <p>
 * 这个事件每个实体加入世界都会触发。禁用列表为空时第一行就返回；
 * 没有 NBT 的物品直接跳过；没有需要剔除的条目时不复制物品。
 * </p>
 *
 * <h3>行为影响</h3>
 * <ul>
 *   <li>玩家自己丢出、死亡掉落的物品上若带着被禁用的附魔，同样会被剔除（被禁用的附魔本来就不产生任何效果）。
 *       之后把它从禁用列表里删掉，已经剔除的不会回来。</li>
 *   <li>1.12.2 版：这个事件分不出「新生成」和「区块加载时从存档读回来」，地上原有的掉落物在区块重新加载时也会被清一次。
 *       禁用的附魔本来就无效，清掉不影响玩法，只是以后重新启用时这些掉落物上的附魔回不来了。</li>
 *   <li>直接放进容器或背包、不经过掉落物实体的来路（战利品箱、进度奖励、指令给予等）这里管不到；
 *       刚生成的战利品箱由 {@code DisabledLootCleaner} 另外清。</li>
 * </ul>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class DisabledEnchantmentDropFilter {

    /** 1.12.2 版：物品上的附魔列表键（1.20.1 是 Enchantments） */
    private static final String TAG_ENCHANTMENTS = "ench";
    private static final String TAG_STORED_ENCHANTMENTS = "StoredEnchantments";

    private DisabledEnchantmentDropFilter() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemEntityJoinLevel(@Nonnull EntityJoinWorldEvent evt) {
        String[] blacklist = ConfigLoader.uninstallEnchantment;
        if (blacklist == null || blacklist.length == 0) {
            return;
        }
        if (!(evt.getEntity() instanceof EntityItem) || evt.getWorld().isRemote) {
            return;
        }
        EntityItem itemEntity = (EntityItem) evt.getEntity();

        ItemStack stack = itemEntity.getItem();
        if (!stack.hasTagCompound()) {
            return;
        }

        ItemStack cleaned = withoutDisabledEnchantments(stack);
        if (cleaned != stack) {
            itemEntity.setItem(cleaned);
        }
    }

    /**
     * 返回剔除了被禁用附魔的物品。没有需要剔除的条目时原样返回同一个对象，否则返回新的物品，不改动传入的物品。
     * 附魔书剔完一条不剩时返回同数量的普通书。
     */
    @Nonnull
    public static ItemStack withoutDisabledEnchantments(@Nonnull ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null
                || (!containsDisabled(tag, TAG_ENCHANTMENTS) && !containsDisabled(tag, TAG_STORED_ENCHANTMENTS))) {
            return stack;
        }

        ItemStack copy = stack.copy();
        NBTTagCompound copyTag = copy.getTagCompound();
        if (copyTag == null) {
            return stack;
        }
        removeDisabled(copyTag, TAG_ENCHANTMENTS);
        removeDisabled(copyTag, TAG_STORED_ENCHANTMENTS);

        if (copy.getItem() == Items.ENCHANTED_BOOK && !copyTag.hasKey(TAG_STORED_ENCHANTMENTS)) {
            return new ItemStack(Items.BOOK, copy.getCount());
        }
        if (copyTag.hasNoTags()) {
            copy.setTagCompound(null);
        }
        return copy;
    }

    private static boolean containsDisabled(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_LIST)) {
            return false;
        }
        NBTTagList list = tag.getTagList(key, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            if (isDisabledEntry(list.getCompoundTagAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static void removeDisabled(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_LIST)) {
            return;
        }
        NBTTagList list = tag.getTagList(key, Constants.NBT.TAG_COMPOUND);
        for (int i = list.tagCount() - 1; i >= 0; i--) {
            if (isDisabledEntry(list.getCompoundTagAt(i))) {
                list.removeTag(i);
            }
        }
        // 不留空列表：空的 ench 会让物品和同种无附魔物品叠不到一起
        if (list.hasNoTags()) {
            tag.removeTag(key);
        }
    }

    /**
     * 1.12.2 版：附魔条目里存的是数字 id（{@code id} 短整型），和原版读法一样用 {@code Enchantment.getEnchantmentByID} 查
     */
    private static boolean isDisabledEntry(NBTTagCompound entry) {
        Enchantment enchantment = Enchantment.getEnchantmentByID(entry.getShort("id"));
        return enchantment instanceof EnchantmentBase && ((EnchantmentBase) enchantment).isDisabled();
    }
}
