package pers.roinflam.carianstyle.codex;

import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * 命名空间到模组名的解析与排序。
 *
 * <h3>为什么单独抽一个类</h3>
 * <p>
 * 「原版排最前、其余按模组名排」这条规则，冲突列表、外部附魔列表、
 * 以后可能还有别的地方都要用。写在各自的比较器里迟早会出现
 * 「这边原版在前、那边在后」的不一致——排序规则一旦分叉，
 * 玩家会觉得界面是随机的。
 * </p>
 *
 * <h3>缓存</h3>
 * <p>
 * {@link ModList#getModContainerById} 每次都要遍历模组列表，而排序会对同一个
 * 命名空间反复求名。缓存一次就够——模组列表在运行期不会变。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
public final class ModNames {

    /** 原版命名空间 */
    private static final String VANILLA = "minecraft";

    /** 命名空间 -> 显示名，避免排序时反复查模组列表 */
    private static final Map<String, String> CACHE = new HashMap<>();

    private ModNames() {
    }

    /**
     * 取某个命名空间对应的模组显示名。
     *
     * @param namespace 命名空间
     * @return 显示名；查不到时回退到命名空间本身
     */
    @Nonnull
    public static String displayName(@Nonnull String namespace) {
        return CACHE.computeIfAbsent(namespace, ns -> ModList.get()
                .getModContainerById(ns)
                .map(c -> c.getModInfo().getDisplayName())
                .orElse(ns));
    }

    /**
     * 取显示用的组件形式；原版走本模组的语言键，其余用模组自己的名字。
     *
     * @param namespace 命名空间
     * @return 显示名组件
     */
    @Nonnull
    public static Component displayComponent(@Nonnull String namespace) {
        return VANILLA.equals(namespace)
                ? Component.translatable("carianstyle.codex.foreign.vanilla")
                : Component.literal(displayName(namespace));
    }

    /**
     * 排序用的一级权重：原版永远排最前。
     * <p>
     * 原版附魔是所有人都认识的参照物，把它放在最前面，玩家扫一眼就能
     * 用熟悉的东西定位；混在模组名的字母序里反而要找。
     * </p>
     *
     * @param namespace 命名空间
     * @return 权重，越小越靠前
     */
    public static int order(@Nonnull String namespace) {
        return VANILLA.equals(namespace) ? 0 : 1;
    }

    /**
     * 比较两个命名空间的显示顺序。
     *
     * @param a 命名空间 A
     * @param b 命名空间 B
     * @return 比较结果
     */
    public static int compare(@Nonnull String a, @Nonnull String b) {
        int byOrder = Integer.compare(order(a), order(b));
        if (byOrder != 0) {
            return byOrder;
        }
        // 按显示名而不是命名空间排：玩家看到的是「Apotheosis」，
        // 按 apotheosis 排和按 Apotheosis 排在有中文名的模组上会得出不同结果
        return displayName(a).compareToIgnoreCase(displayName(b));
    }
}
