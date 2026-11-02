package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import java.util.List;

/**
 * PORT-STUB(E1)
 *
 * 誓复仇附魔
 * <p>
 * 周围敌人越多，伤害越高（每个敌人增加 2.5% × 等级）
 * 如果攻击的是复仇目标，额外增加 5% × 等级 的伤害
 * </p>
 *
 * <h3>性能安全上限（v2.1 新增）</h3>
 * <ul>
 *   <li>{@link #MAX_SEARCH_RADIUS}：AOE 搜索半径硬上限，防止等级×2 直接当半径导致高等级时扫描范围爆炸。</li>
 *   <li>{@link #MAX_COUNTED_TARGETS}：计数目标数硬上限，防止密集怪物场景下 entities.size() 作乘数导致伤害倍率失控。</li>
 * </ul>
 *
 * <p>本附魔每次攻击都触发，触发频率极高，且 entities.size() 直接乘入伤害公式，
 * 原版等级 100 + 周围 200 个实体时伤害倍率 = 1 + 0.025 × 100 × 200 = 501 倍，
 * 既是性能风险也是数值风险，必须双重封顶。</p>
 *
 * <h3>视觉反馈由 HUD 承担，不做世界特效</h3>
 * <p>
 * 本附魔的关键信息是「周围有几个生物在给我加伤」，这是一个<b>持续变化的数值</b>，
 * 靠一次半秒的打击演出根本表达不了——玩家看到一道光，仍然不知道现在是 3 个还是 12 个。
 * 因此改由 {@code CarianStyleCombatStateDisplay} 在 HUD 上常驻显示实时目标数
 * （带进度条，填满即达到 {@link #MAX_COUNTED_TARGETS} 的加成封顶）。
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "vowed_revenge",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        baseEnchantability = 10,
        levelMultiplier = 10
)
@Deprecated
public class EnchantmentVowedRevenge extends EnchantmentBase {
    private static final String VALUE_ID = "vowed_revenge";
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS = EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    8, 1, 64);
    private static final EnchantmentValues.Handle DAMAGE_PER_TARGET_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_target_per_level",
                    0.025D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle REVENGE_DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "revenge_damage_per_level",
                    0.05D, 0.0D, 2.0D);
    private static final int MAX_COUNTED_TARGETS = 20;
    public EnchantmentVowedRevenge() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
    }
}

