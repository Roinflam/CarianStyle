package pers.roinflam.carianstyle.codex;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * PORT-STUB(G)
 *
 * 原版及其它模组附魔的百科索引。
 *
 * <h3>与 {@link EnchantmentCodex} 的关系</h3>
 * <p>
 * 两者是<b>并列</b>的两份索引，各自服务一个标签页；冲突关系则是<b>互通</b>的：
 * 本模组附魔的详情里能点开原版的「击退」，原版「击退」的详情里也能点回「重力」。
 * </p>
 * <p>
 * 互通靠的是同一套判定——{@code isCompatibleWith}。这一点很重要：
 * 如果两边各自维护一份冲突表，迟早会出现「A 说与 B 冲突、B 说不冲突」的矛盾，
 * 而玩家会以为是百科出错。共用同一个判定源就不存在这个问题。
 * </p>
 *
 * <h3>构建开销</h3>
 * <p>
 * 设外部附魔 N 个（原版 40 左右，装了附魔类模组可能到几百）：
 * 两两互测是 N²，加上与本模组 113 个的互测。N=200 时约 6.2 万次
 * {@code isCompatibleWith}，在客户端首次打开界面时跑一次，几十毫秒量级。
 * </p>
 * <p>
 * 之所以敢这么算，是因为它<b>只在客户端、只跑一次</b>，
 * 而换来的是完全准确的冲突关系——手写规则表在装了未知模组时必然出错。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
@Deprecated
public final class ForeignCodex {
    private static final Object BUILD_LOCK = new Object();
    private ForeignCodex() {
    }
    @Nonnull public static List<ForeignMeta> getAll() {
        return java.util.List.of();
    }
    @Nullable public static ForeignMeta get(@Nullable String key) {
        return null;
    }
    @Nonnull public static List<ForeignMeta> search(@Nullable String keyword) {
        return java.util.List.of();
    }
    public static void invalidate() {
    }
}

