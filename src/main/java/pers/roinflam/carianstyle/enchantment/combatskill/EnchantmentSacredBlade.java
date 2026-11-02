package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.enchantment.EnchantmentBlackFlameBlade;
import pers.roinflam.carianstyle.enchantment.EnchantmentDeathBlade;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;
import java.util.UUID;
import net.neoforged.fml.common.EventBusSubscriber;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;

/**
 * PORT-STUB(E1)
 *
 * 神圣之刃附魔
 * <p>
 * 对亡灵生物：额外伤害 = 伤害 × 等级 × 0.25 × 目标血量比例，并治疗自身；
 * 同时<b>永久削弱该亡灵</b>的攻击力（等级 × -5%，累积，最多 -99%）。
 * 对非亡灵生物：伤害降为 20%。
 * </p>
 *
 * <h3>v3.0 修复：攻击力削弱施加对象搞反（严重机制 bug）</h3>
 * <p>
 * <b>现象：</b>装备本附魔后连续攻击亡灵，会发现<b>自己的伤害越打越低</b>，
 * 砍一只普通骷髅要砍半天；换下武器也不恢复，必须死亡重生或重进世界才复原。
 * </p>
 * <p>
 * <b>根因：</b>{@link #applyAttackPenalty} 此前传入的是 {@code attacker}——
 * 也就是把削弱加在了<b>持有者自己</b>身上：
 * </p>
 * <pre>
 * applyAttackPenalty(attacker, level);   // ← 削弱的是自己
 * </pre>
 * <p>
 * 而附魔的设计意图（见语言文件 {@code sacred_blade.desc}）写得很清楚：
 * </p>
 * <blockquote>
 * 对亡灵：基于目标当前血量额外造成至多[附魔等级]×25%伤害，
 * 回复20%额外伤害的生命值（至多10%最大生命值），<b>永久削弱目标[附魔等级]×5%伤害</b>
 * </blockquote>
 * <p>
 * <b>削弱的对象是「目标」，不是持有者。</b>本附魔的定位是「对亡灵特攻的圣剑」，
 * 三项收益（额外伤害 / 吸血 / 削弱目标）本就全是正收益，
 * 唯一的代价是描述第一句的「对非亡灵造成伤害 -80%」——
 * 也就是说它是一把<b>专精武器</b>：打亡灵极强、打别的极弱。
 * 而旧代码额外给持有者叠了一个隐藏的、不可逆的、会累积到 -99% 的自我削弱，
 * 这既不在描述里、也让附魔在实战中越用越废。
 * </p>
 * <p>
 * <b>修复：</b>{@code applyAttackPenalty(attacker, level)} →
 * {@code applyAttackPenalty(victim, level)}。修复后的实际表现：
 * </p>
 * <ul>
 *     <li>持有者的攻击力<b>不再受任何影响</b>，砍多少刀都是满伤害；</li>
 *     <li>被砍的那只亡灵每挨一刀，自身攻击力累积降低 {@code 等级 × 5%}（上限 -99%），
 *         也就是「越砍越虚」——与「神圣之力净化亡灵」的语义一致。</li>
 * </ul>
 * <p>
 * <b>升级提示：</b>旧版本在你的存档里可能已经给玩家累积了很深的攻击力削弱
 * （{@code MULTIPLY_TOTAL} 的永久修正器，最深 -99%）。本次修复<b>不会自动清除历史残留</b>——
 * {@link #onPlayerRespawn} 与 {@link #onEntityJoinLevel} 会在玩家重生 / 实体加入世界时清掉它，
 * 因此<b>受影响的玩家重新登录一次即可恢复</b>（登录会触发 {@code EntityJoinLevelEvent}）。
 * 若发现有人伤害异常低且不肯重登，让他死一次也行。
 * </p>
 *
 * <h3>历史修复记录</h3>
 * <ul>
 *     <li>添加攻击力惩罚清除机制：玩家重生时清除、实体加入世界时清除；</li>
 *     <li>修复累积逻辑：每次攻击亡灵时惩罚递增，而非固定值。</li>
 * </ul>
 *
 * <h3>v2.2：神圣净化特效</h3>
 * <p>
 * 命中亡灵时，调用
 * {@link CarianStyleEffects#sacredPurge(ServerLevel, LivingEntity)} 广播一个
 * <b>定点</b>的短促自绘演出（700ms）：目标处金色三维十字光刃爆开 → 净化环向外扩散 →
 * 金色光尘升天 → 地面圣徽余辉。
 * </p>
 * <p>
 * <b>为什么用定点而非跟随：</b>这是「打中那一下」的瞬时反馈，锁在命中坐标才有打击感；
 * 若跟随目标，爆闪会跟着被击退的怪一起飘走，反而把「一刀砍实了」的手感冲淡。
 * </p>
 * <p>
 * <b>只对亡灵触发。</b>对非亡灵是 -80% 伤害的巨大负收益，
 * 那种情况下放净化特效会误导玩家以为打出了强力一击。
 * </p>
 *
 * <h3>v3.1：削弱与回血要求满蓄力</h3>
 * <p>
 * <b>问题：</b>永久削弱是每命中一次亡灵就固定叠一档（{@code 等级 × 5%}），和这一下打了多少无关。
 * 5 级时连点 4 下就能把一只亡灵削到 -99%，打一群时每次点击削一只。
 * </p>
 * <p>
 * <b>修改：</b>亡灵分支里只把「回血 + 永久削弱」包进 {@link EnchantmentBase#isFullyCharged} 判断，
 * 玩家未满蓄力时跳过这两项；怪物持有者不受影响。
 * </p>
 * <ul>
 *     <li>对亡灵的增伤照常结算——它按本次伤害成比例，连点本来就不占便宜；</li>
 *     <li>对非亡灵的 ×0.2 惩罚<b>不受蓄力影响</b>。如果在方法开头按蓄力直接 return，
 *         连点反而能躲掉惩罚，满蓄力却吃满，那就弄反了。</li>
 * </ul>
 *
 * @author RoinFlam
 * @version 3.1
 */
@AutoRegisterEnchantment(
        id = "sacred_blade",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentDeathBlade.class,
                EnchantmentBlackFlameBlade.class
        },
        baseEnchantability = 10,
        levelMultiplier = 15
)
@Deprecated
public class EnchantmentSacredBlade extends EnchantmentBase {
    private static final String VALUE_ID = "sacred_blade";
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.25D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle LIFESTEAL_RATIO = EnchantmentValues.define(VALUE_ID, "lifesteal_ratio",
                    0.2D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle LIFESTEAL_CAP_RATIO = EnchantmentValues.define(VALUE_ID, "lifesteal_cap_ratio",
                    0.1D, 0.0D, 1.0D);
    private static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("0dada439-4e61-fd5e-44d7-c620fd5a11fb");
    private static final String ATTACK_DAMAGE_MODIFIER_NAME = "enchantment.sacred_blade";
    private static final double PENALTY_STEP_PER_LEVEL = -0.05;
    private static final double PENALTY_FLOOR = -0.99;
    public EnchantmentSacredBlade() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
    }
    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        if (ench.is(Enchantments.SHARPNESS)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }
}

