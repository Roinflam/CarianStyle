package pers.roinflam.carianstyle.annotation.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * 拼内置数据包里的标签：获取途径、禁用、互斥、适用物品
 *
 * <h3>获取途径标签（{@code data/minecraft/tags/enchantment/*.json}，{@code replace: false}，只追加本模组的条目）</h3>
 * <table>
 *   <tr><th>标签</th><th>1.20.1 对应</th><th>谁进</th></tr>
 *   <tr><td>{@code in_enchanting_table}</td><td>非宝藏 + {@code isDiscoverable} + {@code canApplyAtEnchantingTable}</td><td>非宝藏、未禁用</td></tr>
 *   <tr><td>{@code on_random_loot}</td><td>{@code isDiscoverable}</td><td>未禁用</td></tr>
 *   <tr><td>{@code tradeable}</td><td>{@code isTradeable}</td><td>开了 {@code allowVillagerBookTrade}、未禁用</td></tr>
 *   <tr><td>{@code on_traded_equipment} / {@code on_mob_spawn_equipment}</td><td>{@code enchantItem(…, treasure=false)}</td><td>非宝藏、未禁用</td></tr>
 *   <tr><td>{@code treasure}</td><td>{@code isTreasureOnly}（交易价格翻倍经 {@code #double_trade_price}）</td><td>宝藏</td></tr>
 *   <tr><td>{@code curse}</td><td>{@code isCurse}</td><td>注解 {@code isCurse}</td></tr>
 * </table>
 * <p>
 * 本模组的附魔<b>不进 {@code minecraft:non_treasure}</b>：原版的交易、附魔台、两个装备标签都整个引用它，进了它就绕过了上面的配置开关。
 * 另有 {@code carianstyle:disabled}（被禁用的），给创造物品栏过滤和图鉴用。禁用的附魔照样注册，只是不进任何获取途径标签。
 * 每个标签都生成文件（空的也生成），{@code /reload} 后成员会跟着配置变。
 * </p>
 *
 * <h3>互斥（{@code data/carianstyle/tags/enchantment/exclusive_set/<id>.json}）</h3>
 * <p>
 * 对「本模组全部附魔 × (本模组全部附魔 + 原版 {@link Enchantments} 里的全部键)」逐对求单边的
 * {@code checkCompatibility}，为 false 的写进前者的互斥集合（不写自己：1.21.1 判兼容本来就先判不是同一个）。
 * 1.21.1 只要一边写了就互斥，和 1.20.1 两边都调 {@code checkCompatibility} 的结果一样。
 * 原版附魔写成 {@code {"id": ..., "required": false}}：服主用数据包删掉某个原版附魔时，标签不会加载失败。
 * </p>
 *
 * <h3>适用物品（{@code data/carianstyle/tags/item/enchantable/<类别>.json}）</h3>
 * <p>
 * 1.21.1 原版的物品标签只收原版物品，别的模组的剑、盔甲要靠作者自己加，直接用原版标签很多模组武器就上不了本模组的附魔。
 * 所以按 1.20.1 的 instanceof 判定（{@link EnchantTarget#matches}）遍历物品注册表现场算；数据包加载时物品注册表早已冻结，
 * 算出来的集合和 1.20.1 一样，数据包作者还能往里追加。马铠、狼铠不算护甲（见 {@link EnchantTarget}）。
 * </p>
 *
 * @author RoinFlam
 */
final class EnchantmentTagWriter {

    /** 原版 {@link Enchantments} 里的全部键，按 id 排好 */
    private static volatile List<ResourceKey<Enchantment>> vanillaKeys;

    private EnchantmentTagWriter() {
    }

    // ==================== 获取途径 ====================

    /**
     * 获取途径标签和 {@code carianstyle:disabled}：路径 → 标签 JSON
     */
    @Nonnull
    static Map<ResourceLocation, JsonObject> acquisitionTags(@Nonnull List<EnchantmentBase> enchantments,
                                                             @Nonnull GeneratedDataPack.Settings s) {
        Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
        Predicate<EnchantmentBase> obtainable = base -> !s.isTreasure(base) && !s.isDisabled(base);
        out.put(vanilla("in_enchanting_table"), members(enchantments, obtainable));
        out.put(vanilla("on_random_loot"), members(enchantments, base -> !s.isDisabled(base)));
        out.put(vanilla("tradeable"), members(enchantments, s::isTradeable));
        out.put(vanilla("on_traded_equipment"), members(enchantments, obtainable));
        out.put(vanilla("on_mob_spawn_equipment"), members(enchantments, obtainable));
        out.put(vanilla("treasure"), members(enchantments, s::isTreasure));
        out.put(vanilla("curse"), members(enchantments, EnchantmentBase::isCurse));
        out.put(enchantmentTagFile("disabled"), members(enchantments, s::isDisabled));
        return out;
    }

    private static JsonObject members(List<EnchantmentBase> enchantments, Predicate<EnchantmentBase> filter) {
        List<JsonElement> values = new ArrayList<>();
        for (EnchantmentBase base : enchantments) {
            if (filter.test(base)) {
                values.add(new JsonPrimitive(base.getKey().location().toString()));
            }
        }
        return tag(values);
    }

    private static ResourceLocation vanilla(String name) {
        return ResourceLocation.withDefaultNamespace("tags/enchantment/" + name + ".json");
    }

    private static ResourceLocation enchantmentTagFile(String name) {
        return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "tags/enchantment/" + name + ".json");
    }

    // ==================== 互斥 ====================

    /**
     * 每个附魔的互斥对象（只含有互斥对象的附魔，按 id 排序）。
     *
     * @param strict false 时某个附魔的判断抛异常就当它没有互斥对象、打 ERROR 继续（默认配置兜底那一遍）
     */
    @Nonnull
    static Map<EnchantmentBase, List<JsonElement>> exclusiveSets(@Nonnull List<EnchantmentBase> enchantments, boolean strict) {
        List<EnchantKey> ours = new ArrayList<>(enchantments.size());
        for (EnchantmentBase base : enchantments) {
            ours.add(EnchantKey.of(base));
        }
        List<EnchantKey> vanilla = new ArrayList<>();
        for (ResourceKey<Enchantment> key : vanillaKeys()) {
            vanilla.add(EnchantKey.of(key));
        }

        Map<EnchantmentBase, List<JsonElement>> out = new LinkedHashMap<>();
        int oneSided = 0;
        for (EnchantmentBase a : enchantments) {
            TreeSet<String> ourIds = new TreeSet<>();
            TreeSet<String> vanillaIds = new TreeSet<>();
            try {
                for (EnchantKey b : ours) {
                    if (b.base() == a || a.compatibleWith(b)) {
                        continue;
                    }
                    ourIds.add(b.toString());
                    if (b.base().compatibleWith(EnchantKey.of(a))) {
                        oneSided++;
                    }
                }
                for (EnchantKey b : vanilla) {
                    if (!a.compatibleWith(b)) {
                        vanillaIds.add(b.toString());
                    }
                }
            } catch (RuntimeException e) {
                if (strict) {
                    throw e;
                }
                LogUtil.error("卡利亚式附魔 - 附魔 %s 的互斥判断出错，这一次不写它的互斥集合：%s", a.getId(), e);
                continue;
            }
            if (ourIds.isEmpty() && vanillaIds.isEmpty()) {
                continue;
            }
            List<JsonElement> values = new ArrayList<>();
            for (String id : ourIds) {
                values.add(new JsonPrimitive(id));
            }
            for (String id : vanillaIds) {
                JsonObject optional = new JsonObject();
                optional.addProperty("id", id);
                optional.addProperty("required", false);
                values.add(optional);
            }
            out.put(a, values);
        }
        // a 排斥 b、b 却不排斥 a 的（1.21.1 只要一边写了就互斥，和 1.20.1 一样，只记个数）
        LogUtil.debug("卡利亚式附魔 - 互斥集合：%d 个附魔有互斥对象，单边互斥 %d 对", out.size(), oneSided);
        return out;
    }

    /**
     * 附魔 JSON 里 {@code exclusive_set} 指向的标签 {@code carianstyle:exclusive_set/<id>}
     */
    @Nonnull
    static ResourceLocation exclusiveSetTag(@Nonnull EnchantmentBase base) {
        return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "exclusive_set/" + base.getId());
    }

    @Nonnull
    static ResourceLocation exclusiveSetFile(@Nonnull EnchantmentBase base) {
        return enchantmentTagFile("exclusive_set/" + base.getId());
    }

    /**
     * 原版 {@link Enchantments} 的全部键（反射读它的公开静态常量，1.21.1 里它们就是 {@code ResourceKey}）。
     * 互斥规则只引用原版附魔，别的模组的附魔不用枚举。
     */
    @SuppressWarnings("unchecked")
    private static List<ResourceKey<Enchantment>> vanillaKeys() {
        List<ResourceKey<Enchantment>> keys = vanillaKeys;
        if (keys != null) {
            return keys;
        }
        List<ResourceKey<Enchantment>> found = new ArrayList<>();
        for (Field field : Enchantments.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != ResourceKey.class) {
                continue;
            }
            try {
                found.add((ResourceKey<Enchantment>) field.get(null));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        found.sort(Comparator.comparing(k -> k.location().toString()));
        keys = List.copyOf(found);
        vanillaKeys = keys;
        return keys;
    }

    // ==================== 适用物品 ====================

    /**
     * 十个类别的物品标签：路径 → 标签 JSON。
     *
     * @param strict false 时某个物品判定抛异常就跳过它
     */
    @Nonnull
    static Map<ResourceLocation, JsonObject> itemTags(boolean strict) {
        Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
        for (EnchantTarget target : EnchantTarget.values()) {
            TreeSet<String> ids = new TreeSet<>();
            for (Map.Entry<ResourceKey<Item>, Item> en : BuiltInRegistries.ITEM.entrySet()) {
                try {
                    if (target.matches(en.getValue())) {
                        ids.add(en.getKey().location().toString());
                    }
                } catch (RuntimeException e) {
                    if (strict) {
                        throw e;
                    }
                    LogUtil.error("卡利亚式附魔 - 物品 %s 的类别判定出错，跳过：%s", en.getKey().location(), e);
                }
            }
            List<JsonElement> values = new ArrayList<>();
            for (String id : ids) {
                values.add(new JsonPrimitive(id));
            }
            ResourceLocation tag = target.itemTag().location();
            out.put(ResourceLocation.fromNamespaceAndPath(tag.getNamespace(), "tags/item/" + tag.getPath() + ".json"), tag(values));
        }
        return out;
    }

    // ==================== 公用 ====================

    /**
     * 标签文件：{@code {"replace": false, "values": [...]}}
     */
    @Nonnull
    static JsonObject tag(@Nonnull List<JsonElement> values) {
        JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        json.add("values", array);
        return json;
    }
}
