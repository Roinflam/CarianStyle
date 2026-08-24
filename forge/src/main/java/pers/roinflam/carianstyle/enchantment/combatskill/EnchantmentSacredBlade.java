package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentBlackFlameBlade;
import pers.roinflam.carianstyle.enchantment.EnchantmentDeathBlade;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;

import java.util.UUID;

/**
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
 * <b>⚠ 升级提示：</b>旧版本在你的存档里可能已经给玩家累积了很深的攻击力削弱
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
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "sacred_blade",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentDeathBlade.class,
                EnchantmentBlackFlameBlade.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentSacredBlade extends EnchantmentBase {

    /**
     * 攻击力削弱修正器的 UUID。
     * <p>
     * <b>v3.0 起施加在「被砍的亡灵」身上</b>（此前错误地施加在持有者身上，详见类注释）。
     * UUID 本身保持不变——这样 {@link #onPlayerRespawn} / {@link #onEntityJoinLevel}
     * 的清除逻辑能同时清掉旧版本残留在玩家身上的那份，玩家重登一次即可恢复。
     * </p>
     */
    private static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("0dada439-4e61-fd5e-44d7-c620fd5a11fb");
    private static final String ATTACK_DAMAGE_MODIFIER_NAME = "enchantment.sacred_blade";

    /** 单次削弱步长系数（× 等级）：每命中一次亡灵，其攻击力再降 5%×等级 */
    private static final double PENALTY_STEP_PER_LEVEL = -0.05;

    /** 削弱下限：最多把目标的攻击力削到只剩 1% */
    private static final double PENALTY_FLOOR = -0.99;

    public EnchantmentSacredBlade() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 判断是否为亡灵生物
        if (victim.getMobType() == MobType.UNDEAD) {
            // 对亡灵：额外伤害 = 当前伤害 × 等级 × 0.25 × 目标血量比例
            float healthRatio = victim.getHealth() / victim.getMaxHealth();
            float bonusDamage = ctx.getDamage() * level * 0.25f * healthRatio;
            ctx.addDamage(bonusDamage);

            // 治疗攻击者（上限为最大血量的10%）
            float healAmount = Math.min(bonusDamage * 0.2f, attacker.getMaxHealth() * 0.1f);
            attacker.heal(healAmount);

            // ⭐ v3.0 修复：削弱的是【被砍的亡灵】，不是持有者。
            // 旧代码传的是 attacker，导致玩家每砍一刀亡灵自己就弱 5%×等级、
            // 累积到 -99% 后砍普通骷髅都要砍半天（详见类注释「v3.0 修复」小节）。
            applyAttackPenalty(victim, level);

            // 神圣净化特效（定点，锁在命中坐标）。
            // 严格放在亡灵分支内——对非亡灵是 -80% 伤害的负收益，
            // 那时放净化特效会误导玩家以为打出了强力一击。纯视觉，不产生任何机制影响。
            if (victim.level() instanceof ServerLevel serverLevel) {
                CarianStyleEffects.sacredPurge(serverLevel, victim);
            }
        } else {
            // 对非亡灵：伤害降为20%
            ctx.multiplyDamage(0.2f);
        }
    }

    /**
     * 对目标施加累积的攻击力削弱（每次命中再降一档，最多 -99%）。
     * <p>
     * <b>v3.0：施加对象由「持有者」改为「被命中的亡灵」</b>——
     * 这才是语言文件里「永久削弱目标」的本意（详见类注释）。
     * </p>
     * <p>
     * 累积方式：读出目标身上已有的同 UUID 修正器，在其基础上再减一个步长后重新写入，
     * 而非覆盖为固定值。因此连续攻击同一只亡灵，它会越来越虚。
     * </p>
     *
     * @param target 被削弱的目标（v3.0 起为被命中的亡灵）
     * @param level  附魔等级
     */
    private void applyAttackPenalty(@NotNull LivingEntity target, int level) {
        AttributeInstance attributeInstance = target.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attributeInstance == null) {
            return;
        }

        double penaltyStep = level * PENALTY_STEP_PER_LEVEL;
        AttributeModifier existing = attributeInstance.getModifier(ATTACK_DAMAGE_MODIFIER_ID);

        if (existing == null) {
            // 首次命中该目标：添加削弱
            attributeInstance.addPermanentModifier(new AttributeModifier(
                    ATTACK_DAMAGE_MODIFIER_ID,
                    ATTACK_DAMAGE_MODIFIER_NAME,
                    penaltyStep,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        } else {
            // 再次命中：在现有基础上累积一个步长，下限 -99%
            double newReduction = Math.max(existing.getAmount() + penaltyStep, PENALTY_FLOOR);
            attributeInstance.removeModifier(ATTACK_DAMAGE_MODIFIER_ID);
            attributeInstance.addPermanentModifier(new AttributeModifier(
                    ATTACK_DAMAGE_MODIFIER_ID,
                    ATTACK_DAMAGE_MODIFIER_NAME,
                    newReduction,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
    }

    /**
     * 玩家重生时清除攻击力削弱。
     * <p>
     * <b>v3.0 说明：</b>修复后削弱只会施加在亡灵身上，玩家理论上不会再有这个修正器。
     * 但本方法<b>必须保留</b>——它负责清理旧版本残留在玩家身上的那份
     * （最深 -99%，详见类注释的「升级提示」）。
     * </p>
     *
     * @param evt 重生事件
     */
    @SubscribeEvent
    public static void onPlayerRespawn(@NotNull PlayerEvent.PlayerRespawnEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.isEndConquered()) {
            return;
        }
        removeAttackPenalty(evt.getEntity());
    }

    /**
     * 实体加入世界时清除残留的攻击力削弱。
     * <p>
     * 对亡灵而言这意味着「削弱在区块重载 / 重进世界后失效」——
     * 与其说是设计，不如说是永久修正器无法随实体持久化的现实限制；
     * 但对一场战斗内的连续输出而言完全够用（削弱在同一只怪身上会持续累积）。
     * </p>
     * <p>
     * <b>更重要的是：本方法是旧版本残留削弱的主要清理入口。</b>
     * 受影响的玩家<b>重新登录一次即可恢复</b>满伤害（登录会触发本事件）。
     * </p>
     *
     * @param evt 实体加入世界事件
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(@NotNull EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide || !(evt.getEntity() instanceof LivingEntity)) {
            return;
        }
        removeAttackPenalty((LivingEntity) evt.getEntity());
    }

    /**
     * 移除攻击力削弱修正器。
     *
     * @param entity 实体
     */
    private static void removeAttackPenalty(@NotNull LivingEntity entity) {
        AttributeInstance attributeInstance = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attributeInstance != null) {
            attributeInstance.removeModifier(ATTACK_DAMAGE_MODIFIER_ID);
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        if (ench == Enchantments.SHARPNESS) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
