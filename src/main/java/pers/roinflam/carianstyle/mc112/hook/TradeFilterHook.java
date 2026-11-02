package pers.roinflam.carianstyle.mc112.hook;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Items;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.VillagerRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.mc112.util.Reflect;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 村民附魔书交易只卖「可交易」的附魔
 * <p>
 * 1.12.2 的图书管理员附魔书交易（{@code EntityVillager.ListEnchantedBookForEmeralds}）从整个附魔注册表里<b>等概率</b>抽一个，
 * 不看任何开关：一百多个模组附魔会把原版附魔挤掉，被禁用的附魔也会被抽到。
 * 1.20.1 的同类交易只从 {@code isTradeable()} 为 true 的附魔里抽，本模组附魔默认不可交易（{@code allowVillagerBookTrade = false}）。
 * </p>
 * <p>
 * 做法：postInit 时遍历所有职业、职业分支、每一级的交易列表，把原版那个附魔书交易换成 {@link FilteredBookTrade}。
 * 价格公式照原版重写，只是候选池换成「不是本模组附魔、或者 {@code isTradeable()} 为 true」。
 * {@code VillagerCareer.getTrades(int)} 返回的是不能改的视图，所以反射改它的私有字段 {@code trades}
 * （Forge 自己的类，不混淆）；职业的分支列表 {@code careers} 同理。
 * </p>
 * <p>
 * 本类调用了 {@code EnchantmentBase#isTradeable()}，这是平台层引用玩法基类的一个例外。
 * 别的模组自己写的附魔书交易（不是原版那个类的实例）不动。
 * </p>
 */
public final class TradeFilterHook {

    private TradeFilterHook() {
    }

    /**
     * 换掉所有职业里原版的附魔书交易（postInit 调一次）
     */
    @SuppressWarnings("unchecked")
    public static void install() {
        Field careersField = Reflect.fieldOrNull(VillagerRegistry.VillagerProfession.class, "careers", "careers");
        Field tradesField = Reflect.fieldOrNull(VillagerRegistry.VillagerCareer.class, "trades", "trades");
        if (careersField == null || tradesField == null) {
            LogUtil.warn("卡利亚式附魔 - 找不到村民交易表字段，附魔书交易不过滤");
            return;
        }
        int replaced = 0;
        for (VillagerRegistry.VillagerProfession profession : ForgeRegistries.VILLAGER_PROFESSIONS.getValuesCollection()) {
            try {
                List<VillagerRegistry.VillagerCareer> careers = (List<VillagerRegistry.VillagerCareer>) careersField.get(profession);
                if (careers == null) {
                    continue;
                }
                for (VillagerRegistry.VillagerCareer career : careers) {
                    List<List<EntityVillager.ITradeList>> levels = (List<List<EntityVillager.ITradeList>>) tradesField.get(career);
                    if (levels == null) {
                        continue;
                    }
                    for (List<EntityVillager.ITradeList> trades : levels) {
                        for (int i = 0; i < trades.size(); i++) {
                            EntityVillager.ITradeList trade = trades.get(i);
                            if (trade != null && trade.getClass() == EntityVillager.ListEnchantedBookForEmeralds.class) {
                                trades.set(i, new FilteredBookTrade());
                                replaced++;
                            }
                        }
                    }
                }
            } catch (IllegalAccessException | RuntimeException e) {
                LogUtil.warn("卡利亚式附魔 - 处理职业 %s 的交易时出错：%s", profession.getRegistryName(), e);
            }
        }
        LogUtil.info("卡利亚式附魔 - 已替换 %d 个村民附魔书交易", replaced);
    }

    /**
     * 只卖可交易附魔的附魔书交易（价格公式照原版 {@code ListEnchantedBookForEmeralds}）
     */
    public static final class FilteredBookTrade implements EntityVillager.ITradeList {

        @Override
        public void addMerchantRecipe(@Nonnull IMerchant merchant, @Nonnull MerchantRecipeList recipeList, @Nonnull Random random) {
            List<Enchantment> pool = new ArrayList<>();
            for (Enchantment enchantment : Enchantment.REGISTRY) {
                if (isTradeable(enchantment)) {
                    pool.add(enchantment);
                }
            }
            if (pool.isEmpty()) {
                return;
            }
            Enchantment enchantment = pool.get(random.nextInt(pool.size()));
            int level = MathHelper.getInt(random, enchantment.getMinLevel(), enchantment.getMaxLevel());
            ItemStack book = ItemEnchantedBook.getEnchantedItemStack(new EnchantmentData(enchantment, level));
            int price = 2 + random.nextInt(5 + level * 10) + 3 * level;
            if (enchantment.isTreasureEnchantment()) {
                price *= 2;
            }
            if (price > 64) {
                price = 64;
            }
            recipeList.add(new MerchantRecipe(new ItemStack(Items.BOOK), new ItemStack(Items.EMERALD, price), book));
        }

        /**
         * 能不能进交易池：不是本模组附魔、或者本模组附魔的 {@code isTradeable()} 为 true
         *
         * @param enchantment 附魔
         * @return 能卖为 true
         */
        public static boolean isTradeable(@Nonnull Enchantment enchantment) {
            return !(enchantment instanceof EnchantmentBase) || ((EnchantmentBase) enchantment).isTradeable();
        }
    }
}
