package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;
import java.util.UUID;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E1)
 *
 * 招架附魔
 * <p>v2.2：双向监听器入口接入怪物附魔触发开关</p>
 *
 * <h3>v2.3 修复：注解声明的附魔类型与构造函数不一致</h3>
 * <p>
 * <b>问题：</b>注解里写的是 {@code type = EnchantmentCategory.BREAKABLE}
 * （原版判定为「任何有耐久的物品」，镐、斧、锹全都算），
 * 而构造函数传的是自定义类型 {@code "SHIELD"}。
 * </p>
 * <p>
 * 目前 {@code EnchantmentRegistry.createEnchantmentInstance} 走的是无参构造函数分支，
 * 会把 {@code resolveEnchantmentCategory} 解析出来的注解类型<b>直接丢弃</b>，
 * 一切以构造函数为准，因此本附魔<b>当前运行时行为是正确的</b>（只能附在盾牌上）。
 * </p>
 * <p>
 * <b>但这是一颗雷。</b>一旦以后有人把注册器改成「以注解为准」——
 * 那其实才是注解本来的设计意图——本附魔会瞬间变成 BREAKABLE，
 * 也就是能被附到镐 / 斧 / 锹上，正好复现「盾牌附魔串到工具上」的故障现象。
 * </p>
 * <p>
 * <b>修复：</b>把注解改为 {@code customType = "SHIELD"}，与构造函数对齐。
 * 这样改<b>不改变任何当前运行时行为</b>，只是消除两边不一致的隐患。
 * </p>
 *
 * <h3>v2.4：成功架住时播放「格挡窗口」自绘特效</h3>
 * <p>
 * <b>为什么播在 {@link #onLivingAttack} 而不是 {@link #onLivingHurt}：</b>
 * 玩家需要的是「现在可以反击了」这个<b>预告</b>，而不是「刚才那一下有加成」这个事后通报。
 * 加成真正兑现是在 {@code onLivingHurt}，但那时玩家已经打出去了，提示没有意义。
 * 因此特效跟着 {@code setData(PARRY_LEVEL_KEY, ...)} 这一行走——
 * 数据写进去的那一刻，就是窗口开始的那一刻。
 * </p>
 * <p>
 * <b>时长与 10 tick 严格绑定。</b>客户端那边
 * {@code CombatArtEffectManager.PARRY_WINDOW_DURATION_MS} 取 500ms，
 * 正好等于这里 {@code setData(..., 10)} 的 10 tick。
 * 准星收缩到零的瞬间就是加成失效的瞬间——<b>改了这里的 10，必须同步改那个常量</b>，
 * 否则视觉会比机制早结束（玩家白白放过还能用的加成）或晚结束（玩家以为还有加成而挨打）。
 * </p>
 * <p>
 * 特效为定点，不跟随实体。举盾时原版会大幅削减移速，500ms 内位移不足半格，
 * 定点完全够用；为此改包格式、动到全部既有构造点，收益不成比例。
 * </p>
 *
 * @author RoinFlam
 * @version 2.4
 */
@Deprecated
public class EnchantmentParry extends EnchantmentBase {
    private static final String VALUE_ID = "parry";
    private static final EnchantmentValues.Handle WINDOW_TICKS = EnchantmentValues.define(VALUE_ID, "window_ticks",
                    10, 1, 200);
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.25D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle COOLDOWN_TICKS = EnchantmentValues.define(VALUE_ID, "cooldown_ticks",
                    40, 0, 2400);
    private static final String PARRY_LEVEL_KEY = "parry_level";
    private static final String PARRY_COOLDOWN_KEY = "parry_cooldown";
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

