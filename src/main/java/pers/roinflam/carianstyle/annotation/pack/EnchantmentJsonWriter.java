package pers.roinflam.carianstyle.annotation.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 拼本模组附魔在 1.21.1 数据包里的定义 {@code data/carianstyle/enchantment/<id>.json}
 *
 * <h3>字段和 1.20.1 的对应</h3>
 * <ul>
 *   <li>{@code description}：翻译键 {@code enchantment.carianstyle.<id>}，和 1.20.1 一样，lang 不动。</li>
 *   <li>{@code supported_items}：{@code #carianstyle:enchantable/<类别>}，标签按 1.20.1 的 instanceof 现场算
 *       （见 {@link EnchantmentTagWriter#itemTags}）。不写 {@code primary_items}：不写就等于 {@code supported_items}，
 *       1.20.1 附魔台用的 {@code canApplyAtEnchantingTable} 本来也就是 {@code canEnchant}。</li>
 *   <li>{@code max_level}：稀有度定的最高级（UNCOMMON 5、RARE 3、VERY_RARE 1）。</li>
 *   <li>{@code weight} / {@code anvil_cost}：{@code useVanillaRarityWeight} 开时按稀有度映射回原版（权重 5 / 2 / 1，铁砧 2 / 4 / 8），
 *       关时退回 1.20.1 构造器里那个占位的 COMMON（权重 10、铁砧 1）。1.21.1 铁砧每级加 {@code anvil_cost}、附魔书减半至少 1，
 *       和 1.20.1 按稀有度倍率算是同一个式子。</li>
 *   <li>{@code min_cost}：{@code base = floor(B × 难度)}，{@code per_level_above_first = round(M × 难度)}（固定花费的 M = 0）。
 *       1.20.1 是整体 {@code (int) ((B + (等级-1) × M) × 难度)}，1.21.1 只能写线性的；难度 1.0（默认）和 2.0 时逐级相等，
 *       其它难度个别等级差 1～2。</li>
 *   <li>{@code max_cost}：base 比 {@code min_cost} 多 50、per 相同（1.20.1 的覆写全是「最低 + 50」，拆成线性后每级仍然精确相等）。</li>
 *   <li>{@code slots}：构造器给的槽位（1.20.1 真正生效的那份）换成槽位组：主手 {@code mainhand}、双手 {@code hand}、
 *       四件护甲 {@code armor}、单件护甲 {@code chest} / {@code legs} / {@code feet} / {@code head}。</li>
 *   <li>{@code exclusive_set}：有互斥对象的才写，指向 {@code #carianstyle:exclusive_set/<id>}。</li>
 *   <li>{@code effects}：不写，行为全在代码里。</li>
 * </ul>
 *
 * @author RoinFlam
 */
final class EnchantmentJsonWriter {

    private static final Set<EquipmentSlot> ARMOR_SLOTS =
            EnumSet.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final Set<EquipmentSlot> HAND_SLOTS = EnumSet.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);

    private EnchantmentJsonWriter() {
    }

    /**
     * 附魔 JSON 在数据包里的路径 {@code carianstyle:enchantment/<id>.json}
     */
    @Nonnull
    static ResourceLocation file(@Nonnull EnchantmentBase base) {
        return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "enchantment/" + base.getId() + ".json");
    }

    /**
     * 按配置拼附魔定义。
     *
     * @param base         行为对象
     * @param settings     这次生成用的配置
     * @param hasExclusive 有没有互斥对象（有才写 {@code exclusive_set}）
     */
    @Nonnull
    static JsonObject write(@Nonnull EnchantmentBase base, @Nonnull GeneratedDataPack.Settings settings, boolean hasExclusive) {
        double d = settings.difficulty();
        int costBase = base.getCostBase(d);
        int costPer = base.getCostPerLevel(d);

        JsonObject json = new JsonObject();
        json.add("description", description(base));
        json.addProperty("supported_items", "#" + base.getTarget().itemTag().location());
        json.addProperty("weight", weight(base, settings));
        json.addProperty("max_level", base.getMaxLevel());
        json.add("min_cost", cost(costBase, costPer));
        json.add("max_cost", cost(costBase + 50, costPer));
        json.addProperty("anvil_cost", anvilCost(base, settings));
        json.add("slots", slots(base.getSlots()));
        if (hasExclusive) {
            json.addProperty("exclusive_set", "#" + EnchantmentTagWriter.exclusiveSetTag(base));
        }
        return json;
    }

    /**
     * 兜底用的最简定义：只为了让注册表里有这一条（存档里带它的物品不丢附魔），数值取最保守的。
     */
    @Nonnull
    static JsonObject minimal(@Nonnull EnchantmentBase base) {
        JsonObject json = new JsonObject();
        json.add("description", description(base));
        json.addProperty("supported_items", "#" + base.getTarget().itemTag().location());
        json.addProperty("weight", 1);
        json.addProperty("max_level", Math.max(1, Math.min(255, base.getMaxLevel())));
        json.add("min_cost", cost(1, 0));
        json.add("max_cost", cost(51, 0));
        json.addProperty("anvil_cost", 1);
        JsonArray slots = new JsonArray();
        slots.add("any");
        json.add("slots", slots);
        return json;
    }

    /** 同 {@code EnchantmentBase#getWeight}，配置取自 {@code settings} */
    static int weight(@Nonnull EnchantmentBase base, @Nonnull GeneratedDataPack.Settings settings) {
        if (!settings.vanillaWeight()) {
            return 10;
        }
        return switch (base.getEnchantmentRarity()) {
            case VERY_RARE -> 1;
            case RARE -> 2;
            case UNCOMMON -> 5;
        };
    }

    /** 同 {@code EnchantmentBase#getAnvilCost}，配置取自 {@code settings} */
    static int anvilCost(@Nonnull EnchantmentBase base, @Nonnull GeneratedDataPack.Settings settings) {
        if (!settings.vanillaWeight()) {
            return 1;
        }
        return switch (base.getEnchantmentRarity()) {
            case VERY_RARE -> 8;
            case RARE -> 4;
            case UNCOMMON -> 2;
        };
    }

    private static JsonObject description(EnchantmentBase base) {
        JsonObject description = new JsonObject();
        description.addProperty("translate", base.getDescriptionId());
        return description;
    }

    private static JsonObject cost(int base, int perLevelAboveFirst) {
        JsonObject cost = new JsonObject();
        cost.addProperty("base", base);
        cost.addProperty("per_level_above_first", perLevelAboveFirst);
        return cost;
    }

    /**
     * 构造器给的槽位 → 槽位组。能整组表示的用组名（双手 {@code hand}、四件护甲 {@code armor}），
     * 其余逐个写单槽的组名。
     */
    static JsonArray slots(EquipmentSlot[] slots) {
        Set<EquipmentSlot> set = slots.length == 0 ? EnumSet.noneOf(EquipmentSlot.class) : EnumSet.of(slots[0], slots);
        JsonArray out = new JsonArray();
        List<String> names = new ArrayList<>();
        if (set.containsAll(HAND_SLOTS)) {
            names.add("hand");
            set.removeAll(HAND_SLOTS);
        }
        if (set.containsAll(ARMOR_SLOTS)) {
            names.add("armor");
            set.removeAll(ARMOR_SLOTS);
        }
        for (EquipmentSlot slot : set) {
            names.add(switch (slot) {
                case MAINHAND -> "mainhand";
                case OFFHAND -> "offhand";
                case HEAD -> "head";
                case CHEST -> "chest";
                case LEGS -> "legs";
                case FEET -> "feet";
                case BODY -> "body";
            });
        }
        if (names.isEmpty()) {
            names.add("any");
        }
        names.forEach(out::add);
        return out;
    }
}
