package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;

/**
 * 古龙雷附魔（维克的龙雷）
 * <p>
 * 武器附魔，双向生效：<b>防御端</b>按等级减免雷电伤害（15%/级，满 100% 直接免疫）；
 * <b>攻击端</b>按天气概率召唤红色龙雷追加伤害。
 * </p>
 *
 * <h3>红色龙雷：视觉与雷声都交给 {@link CarianStyleEffects#redLightningStrike}</h3>
 * <p>
 * 攻击端召唤的原版蓝白 {@code LightningBolt} 已替换为自绘红闪，
 * 与古龙雷击保持一致的红色雷击意象。
 * </p>
 * <p>
 * 原版闪电用的是 {@code setVisualOnly(true)}，本就只有「视觉 + 音效」无副作用，
 * 但<b>音效随它一起没了</b>，所以雷声必须自己补。
 * </p>
 * <p>
 * <b>v3.1：雷声不再由本类手写 {@code playSound}</b>，改为与视觉一并交给门面方法。
 * 原先本类与 {@code EnchantmentAncientDragonLightning} 各自维护一份音量 / 音高
 * 完全相同的 {@code playSound}，属于复制粘贴，改一处忘一处就会导致两个龙雷附魔听感不一致；
 * 现在共用 {@code CarianStyleEffects} 顶部的一组常量。
 * </p>
 *
 * <h3>本附魔是「按实体节流」真正生效的那一处</h3>
 * <p>
 * {@link CarianStyleEffects#redLightningStrike} 内部有两级节流，二者针对的场景完全不同：
 * </p>
 * <ul>
 *     <li><b>视觉按实体节流</b>（3 tick）——正是为本附魔设计的。本附魔在<b>每次造成伤害</b>时
 *         按概率触发，而雷暴天概率是 100%；玩家攻速一高（或用了多重攻击类附魔），
 *         同一个目标一两 tick 内就能连发好几次，画面上是同一处疯狂闪。
 *         节流后表现为一道持续劈着的雷，机制伤害<b>完全不受影响</b>
 *         （下方 {@code victim.hurt} 在节流之外，每次触发都照常结算）；</li>
 *     <li><b>雷声按网格节流</b>（3 tick / 64 格）——对本附魔收益有限（本来就只有一个目标），
 *         它主要是为古龙雷击的百目标场景准备的。</li>
 * </ul>
 * <p>
 * <b>务必注意：节流只作用于视听，不作用于伤害。</b>被节流掉的那次仍然会走完
 * {@code victim.hurt(...)}，玩家该吃的伤害一点不少——省掉的只是重复的画面与声音。
 * </p>
 *
 * <h3>天气倍率互斥判断</h3>
 * <p>
 * MC 中雷暴时 {@code isRaining()} 也返回 true，故触发概率与伤害倍率均为互斥判断：
 * 雷暴 100% 概率 / 4x 伤害，下雨 {@code level*10}% / 2x，晴天 {@code level*5}% / 1x。
 * </p>
 *
 * @author RoinFlam
 * @version 3.1
 */
@AutoRegisterEnchantment(
        id = "vic_dragon_thunder",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentVicDragonThunder extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.vic_dragon_thunder.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "vic_dragon_thunder";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级对雷电伤害的减免比例
     * <p>默认 0.15，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "reduction_per_level",
                    0.15D, 0.0D, 1.0D);

    /**
     * 每级的基础触发概率（百分比）
     * <p>默认 5.0，允许范围 0.0 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle CHANCE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "chance_per_level",
                    5.0D, 0.0D, 100.0D);

    /**
     * 每级的雷击伤害倍率
     * <p>默认 0.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.5D, 0.0D, 10.0D);

    public EnchantmentVicDragonThunder() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 伤害事件监听：防御端做雷电减伤，攻击端概率召唤红色龙雷。
     *
     * @param evt 生物受到伤害事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        Enchantment vicDragonThunder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentVicDragonThunder.class);
        if (vicDragonThunder == null) {
            return;
        }

        // 防御：雷电伤害减免（受击者视角）
        // 怪物附魔触发开关（受击者视角）
        if (!EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) {
            if (!evt.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                    && "lightningBolt".equals(evt.getSource().getMsgId())) {
                ItemStack victimHeld = victim.getItemInHand(InteractionHand.MAIN_HAND);
                if (!victimHeld.isEmpty()) {
                    int level = EnchantmentHelper.getItemEnchantmentLevel(vicDragonThunder, victimHeld);
                    if (ConfigLoader.levelLimit) {
                        level = Math.min(level, LEVEL_CAP.getInt());
                    }
                    if (level > 0) {
                        if (level * REDUCTION_PER_LEVEL.get() >= 1) {
                            evt.setCanceled(true);
                            return;
                        }
                        evt.setAmount(evt.getAmount() - evt.getAmount() * level * (float) REDUCTION_PER_LEVEL.get());
                    }
                }
            }
        }

        // 攻击：召唤落雷（攻击者视角）
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity attacker)) {
            return;
        }

        // 怪物附魔触发开关（攻击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack attackerHeld = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (attackerHeld.isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(vicDragonThunder, attackerHeld);
        if (ConfigLoader.levelLimit) {
            level = Math.min(level, LEVEL_CAP.getInt());
        }
        if (level <= 0) {
            return;
        }

        // v-cfg：改为 double —— 触发概率一旦可配置就未必是整数
        // （比如想设 2.5%），用 int 会把小数部分截掉。
        // RandomUtil.percentageChance 本来就收 double，无需其它改动。
        double triggerChance;
        if (attacker.level().isThundering()) {
            triggerChance = 100;
        } else if (attacker.level().isRaining()) {
            triggerChance = level * CHANCE_PER_LEVEL.get() * 2;
        } else {
            triggerChance = level * CHANCE_PER_LEVEL.get();
        }

        if (!RandomUtil.percentageChance(triggerChance)) {
            return;
        }

        Level world = victim.level();
        if (world instanceof ServerLevel serverLevel) {
            // ⭐ v3.1：视觉 + 雷声一次调用，两级节流在门面类内部各自生效。
            // 本附魔是「按实体节流」真正生效的那一处——雷暴天 100% 概率 + 高攻速时，
            // 同一目标一两 tick 内会连发好几次，节流后表现为一道持续劈着的雷。
            //
            // ⚠ 节流只作用于视听：下方 victim.hurt 在节流之外，
            // 被拦掉的那次仍然会照常结算伤害（详见类注释）。
            CarianStyleEffects.redLightningStrike(serverLevel, victim);
        }

        victim.invulnerableTime = 10;

        int magnification = 1;
        if (attacker.level().isThundering()) {
            magnification = 4;
        } else if (attacker.level().isRaining()) {
            magnification = 2;
        }

        victim.hurt(victim.damageSources().lightningBolt(), evt.getAmount() * level * (float) DAMAGE_PER_LEVEL.get() * magnification);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
