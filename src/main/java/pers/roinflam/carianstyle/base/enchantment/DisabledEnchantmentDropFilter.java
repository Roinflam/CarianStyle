package pers.roinflam.carianstyle.base.enchantment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 被禁用附魔的掉落物兜底清洗
 *
 * <h3>问题</h3>
 * <p>
 * {@link EnchantmentBase} 覆写了 isDiscoverable / isAllowedOnBooks / canApplyAtEnchantingTable / isTradeable，
 * 原版附魔台、不带附魔列表的 {@code enchant_randomly}、{@code enchant_with_levels}、村民交易都会跳过被禁用的附魔。
 * 但还有这几条来路不经过这些判断：
 * <ul>
 *   <li>战利品表里写死了附魔列表的 {@code enchant_randomly}：列表非空时原版直接从列表里抽，不看 isDiscoverable；</li>
 *   <li>其它模组自己遍历附魔注册表挑附魔，或者在配置加载之前就把「可发现的附魔」缓存成列表、之后一直用它；</li>
 *   <li>其它模组直接构造附魔书，再塞进 LivingDropsEvent 或丢进世界。</li>
 * </ul>
 * 这些东西最后大多以掉落物实体的形式出现在世界里（怪物死亡掉落、LivingDropsEvent 里追加的掉落、
 * 钓鱼、以物易物、玩家丢出……），都要经过 {@code EntityJoinLevelEvent}。
 * </p>
 *
 * <h3>为什么这么改</h3>
 * <p>
 * 掉落物实体加入服务端世界时，从物品的 Enchantments 与 StoredEnchantments 里剔除被禁用的附魔，
 * 不针对任何一个具体模组。附魔书剔完一条不剩就换成同数量的普通书：
 * 原版 {@code enchant_randomly} 找不到可用附魔时给的也是原样的书，玩家看到的结果和「这次没抽到附魔」一样；
 * 而且掉落物照常生成，不取消实体，依赖 addFreshEntity 返回值的模组不会出岔子。
 * </p>
 * <p>
 * 优先级取 HIGHEST：有的模组会在这个事件里把掉落物直接收进玩家背包并取消实体，要赶在它们之前处理。
 * </p>
 *
 * <h3>开销</h3>
 * <p>
 * 这个事件每个实体加入世界都会触发。禁用列表为空时第一行就返回；
 * 只看新生成的掉落物（从存档读回来的不管），没有 NBT 的物品直接跳过；
 * 只有本模组命名空间的附魔条目才会去查注册表；没有需要剔除的条目时不复制物品。
 * </p>
 *
 * <h3>行为影响</h3>
 * <ul>
 *   <li>玩家自己丢出、死亡掉落的物品上若带着被禁用的附魔，同样会被剔除（被禁用的附魔本来就不产生任何效果）。
 *       之后把它从禁用列表里删掉，已经剔除的不会回来。</li>
 *   <li>直接放进容器或背包、不经过掉落物实体的来路（战利品箱、进度奖励、指令给予等）这里管不到。</li>
 * </ul>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DisabledEnchantmentDropFilter {

    private static final String TAG_ENCHANTMENTS = "Enchantments";
    private static final String ENCHANTMENT_PREFIX = Reference.MOD_ID + ":";

    private DisabledEnchantmentDropFilter() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemEntityJoinLevel(@Nonnull EntityJoinLevelEvent evt) {
        String[] blacklist = ConfigLoader.uninstallEnchantment;
        if (blacklist == null || blacklist.length == 0) {
            return;
        }
        if (!(evt.getEntity() instanceof ItemEntity itemEntity) || evt.loadedFromDisk() || evt.getLevel().isClientSide()) {
            return;
        }

        ItemStack stack = itemEntity.getItem();
        if (!stack.hasTag()) {
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
        CompoundTag tag = stack.getTag();
        if (tag == null
                || (!containsDisabled(tag, TAG_ENCHANTMENTS) && !containsDisabled(tag, EnchantedBookItem.TAG_STORED_ENCHANTMENTS))) {
            return stack;
        }

        ItemStack copy = stack.copy();
        CompoundTag copyTag = copy.getOrCreateTag();
        removeDisabled(copyTag, TAG_ENCHANTMENTS);
        removeDisabled(copyTag, EnchantedBookItem.TAG_STORED_ENCHANTMENTS);

        if (copy.is(Items.ENCHANTED_BOOK) && !copyTag.contains(EnchantedBookItem.TAG_STORED_ENCHANTMENTS)) {
            return new ItemStack(Items.BOOK, copy.getCount());
        }
        if (copyTag.isEmpty()) {
            copy.setTag(null);
        }
        return copy;
    }

    private static boolean containsDisabled(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return false;
        }
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            if (isDisabledEntry(list.getCompound(i))) {
                return true;
            }
        }
        return false;
    }

    private static void removeDisabled(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        list.removeIf(entry -> entry instanceof CompoundTag compound && isDisabledEntry(compound));
        // 不留空列表：空的 Enchantments 会让物品和同种无附魔物品叠不到一起
        if (list.isEmpty()) {
            tag.remove(key);
        }
    }

    /**
     * 与原版 {@code EnchantmentHelper.getEnchantmentId(CompoundTag)} 用同样的方式解析 id，
     * 原版认不出的条目（大小写不对、缺命名空间）本来也不会生效，不用管。
     */
    private static boolean isDisabledEntry(CompoundTag entry) {
        String id = entry.getString("id");
        if (!id.startsWith(ENCHANTMENT_PREFIX)) {
            return false;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return false;
        }
        Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(key);
        return enchantment instanceof EnchantmentBase base && base.isDisabled();
    }
}
