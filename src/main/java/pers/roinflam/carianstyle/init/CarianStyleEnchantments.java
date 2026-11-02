package pers.roinflam.carianstyle.init;

import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.registries.DeferredRegister;
import pers.roinflam.carianstyle.utils.Reference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.*;

/**
 * PORT-STUB(B1a)
 *
 * 模组附魔注册类
 * <p>
 * 统一管理所有自定义附魔的实例化和分类
 * </p>
 *
 * <h3>v1.1 修复：补上缺失的 PICKAXE 自定义类型</h3>
 * <p>
 * {@link #getCustomEnchantmentCategory(String)} 的文档注释、以及
 * {@code AutoRegisterEnchantment#customType()} 的文档注释里都写明了支持
 * {@code "PICKAXE"}，但 switch 分支里<b>从来没有实现</b>，
 * 传入 "PICKAXE" 会静默返回 null。
 * </p>
 * <p>
 * 后果分两种，都很隐蔽：
 * </p>
 * <ul>
 *   <li>若从注解走（{@code customType = "PICKAXE"}）——
 *       {@code EnchantmentRegistry.resolveEnchantmentCategory} 会抛
 *       {@code IllegalArgumentException}，被 {@code registerSingle} 的 try/catch 吞掉，
 *       只在日志里留一行 error，<b>该附魔直接不注册</b>，游戏里查无此附魔；</li>
 *   <li>若从构造函数走（{@code super(getCustomEnchantmentCategory("PICKAXE"), ...)}）——
 *       会把 null 传给 {@code Enchantment} 的 category 字段，
 *       之后任何一次 {@code canEnchant} / {@code canApplyAtEnchantingTable} 调用都会 NPE。</li>
 * </ul>
 * <p>
 * 本次按文档补上 {@link #PICKAXE}，判定为 {@code item instanceof PickaxeItem}，
 * 供 {@code EnchantmentRockBlaster} 使用（该附魔的实际逻辑本来就只认镐子）。
 * </p>
 *
 * @version 1.1
 */
@Deprecated
public class CarianStyleEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS_REGISTER = null; // PORT-STUB
    public static final int RECOLLECT_ENCHANTABILITY = 38;
    public static final List<Enchantment> ENCHANTMENTS = new ArrayList<>();
    public static final Set<Enchantment> RECOLLECT = new HashSet<>();
    public static final Set<Enchantment> COMBAT_SKILL = new HashSet<>();
    public static final Set<Enchantment> LAW = new HashSet<>();
    public static final Set<Enchantment> DEAD = new HashSet<>();
    public static int getRegisteredCount() {
        return 0;
    }
}

