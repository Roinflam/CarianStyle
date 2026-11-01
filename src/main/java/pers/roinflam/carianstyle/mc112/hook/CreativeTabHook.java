package pers.roinflam.carianstyle.mc112.hook;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.EnumEnchantmentType;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 把本模组的三个自定义附魔类型挂到创造模式标签页上
 * <p>
 * 1.12.2 的创造模式标签页按 {@code getRelevantEnchantmentTypes()} 决定往里放哪些附魔书：
 * 用自定义类型（盾牌、剑和弓、镐）的附魔不在任何标签页的列表里，书就不出现在「战斗」「工具」页，只能搜到。
 * 这里在 init 阶段把盾牌、剑和弓两种类型加进「战斗」，镐加进「工具」。
 * 1.20.1 由创造模式标签页的内容事件处理，不需要这一步。
 * </p>
 */
public final class CreativeTabHook {

    private static boolean installed = false;

    private CreativeTabHook() {
    }

    /**
     * 挂上去（init，重复调用安全）
     */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        add(CreativeTabs.COMBAT, CarianStyleEnchantments.SHIELD, CarianStyleEnchantments.ARMS);
        add(CreativeTabs.TOOLS, CarianStyleEnchantments.PICKAXE);
        LogUtil.debug("卡利亚式附魔 - 自定义附魔类型已加入创造模式标签页");
    }

    private static void add(@Nonnull CreativeTabs tab, EnumEnchantmentType... types) {
        List<EnumEnchantmentType> list = new ArrayList<>(Arrays.asList(tab.getRelevantEnchantmentTypes()));
        for (EnumEnchantmentType type : types) {
            if (type != null && !list.contains(type)) {
                list.add(type);
            }
        }
        tab.setRelevantEnchantmentTypes(list.toArray(new EnumEnchantmentType[0]));
    }
}
