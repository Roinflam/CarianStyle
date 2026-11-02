package pers.roinflam.carianstyle.annotation;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.Locale;

/**
 * 附魔能上哪类物品
 * <p>
 * 1.20.1 用的是原版的 {@code EnchantmentCategory} 枚举（WEAPON、BOW、ARMOR……）加上本模组自己建的
 * SHIELD / ARMS / PICKAXE 三个自定义类别；1.21.1 原版删掉了这个枚举，物品能不能上附魔改成看附魔 JSON 里
 * {@code supported_items} 指向的物品标签。这里把 1.20.1 用到的十个类别收成本模组自己的枚举，
 * 每个值对应内置数据包里生成的一个物品标签 {@code carianstyle:enchantable/<小写名>}。
 * </p>
 *
 * <h3>为什么标签按 instanceof 现场算，不直接用原版标签</h3>
 * <p>
 * 1.21.1 原版的 {@code #enchantable/sword} 之类只收原版物品，模组的剑、盔甲要靠作者自己往里加，
 * 直接用原版标签会让很多模组武器上不了本模组的附魔。所以内置数据包加载时遍历物品注册表，
 * 用 {@link #matches(Item)}（和 1.20.1 的判定一样是 instanceof）算出标签成员；数据包作者还能往标签里追加。
 * </p>
 *
 * <h3>和 1.20.1 判定的一处不同</h3>
 * <p>
 * 1.21.1 的马铠、狼铠（{@link AnimalArmorItem}）继承 {@link ArmorItem}，1.20.1 的马铠继承的是 {@code Item}。
 * 不排除的话护甲类附魔能被铁砧打到马铠上，还会因为 BODY 槽在马、狼身上触发，所以护甲类一律排除它。
 * 行为上和 1.20.1 一致（1.20.1 本来就上不去）。
 * </p>
 */
public enum EnchantTarget {
    /** 剑（1.20.1 原版 WEAPON 只认 {@code SwordItem}，本模组照旧不认斧子、重锤） */
    WEAPON,
    /** 弓 */
    BOW,
    /** 任意护甲 */
    ARMOR,
    /** 头盔（1.20.1 没有附魔用，留着备用） */
    ARMOR_HEAD,
    /** 胸甲 */
    ARMOR_CHEST,
    /** 护腿 */
    ARMOR_LEGS,
    /** 靴子 */
    ARMOR_FEET,
    /** 盾牌（1.20.1 的自定义类别 SHIELD） */
    SHIELD,
    /** 剑、弓、弩（1.20.1 的自定义类别 ARMS） */
    ARMS,
    /** 只认镐子（1.20.1 的自定义类别 PICKAXE，原版 DIGGER 会连斧、锹、锄一起认） */
    PICKAXE;

    /**
     * 物品是不是这一类，和 1.20.1 的 {@code EnchantmentCategory#canEnchant} 同一套 instanceof 判定。
     *
     * @param item 物品
     * @return 是否属于这一类
     */
    public boolean matches(@Nonnull Item item) {
        return switch (this) {
            case WEAPON -> item instanceof SwordItem;
            case BOW -> item instanceof BowItem;
            case ARMOR -> isPlayerArmor(item);
            case ARMOR_HEAD -> isPlayerArmor(item) && ((ArmorItem) item).getEquipmentSlot() == EquipmentSlot.HEAD;
            case ARMOR_CHEST -> isPlayerArmor(item) && ((ArmorItem) item).getEquipmentSlot() == EquipmentSlot.CHEST;
            case ARMOR_LEGS -> isPlayerArmor(item) && ((ArmorItem) item).getEquipmentSlot() == EquipmentSlot.LEGS;
            case ARMOR_FEET -> isPlayerArmor(item) && ((ArmorItem) item).getEquipmentSlot() == EquipmentSlot.FEET;
            case SHIELD -> item instanceof ShieldItem;
            case ARMS -> item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem;
            case PICKAXE -> item instanceof PickaxeItem;
        };
    }

    private static boolean isPlayerArmor(Item item) {
        return item instanceof ArmorItem && !(item instanceof AnimalArmorItem);
    }

    /**
     * 物品标签路径里的名字，例如 {@code armor_chest}。
     */
    @Nonnull
    public String tagName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * 内置数据包为这一类生成的物品标签 {@code carianstyle:enchantable/<名字>}，附魔 JSON 的 {@code supported_items} 指向它。
     */
    @Nonnull
    public TagKey<Item> itemTag() {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "enchantable/" + tagName()));
    }
}
