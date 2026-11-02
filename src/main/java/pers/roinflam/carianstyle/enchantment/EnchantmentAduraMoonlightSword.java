package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * PORT-STUB(E3)
 *
 * 阿杜拉月光剑附魔
 * <p>
 * 攻击变为魔法伤害，对目标周围敌人施加冻伤
 * 白天：叠加+1等级，夜晚：叠加+2等级
 * </p>
 *
 * <h3>性能安全上限（v2.1 新增）</h3>
 * <ul>
 *   <li>{@link #MAX_SEARCH_RADIUS}：AOE 搜索半径硬上限，防止高等级附魔（如 100 级）
 *       直接把等级当半径，导致 100 格搜索扫过大量实体。</li>
 *   <li>{@link #MAX_TARGETS}：单次触发最大命中目标数上限，防止密集怪物场景下
 *       对大量实体施加效果导致事件风暴。</li>
 * </ul>
 *
 * <p>本附魔每次攻击触发，触发频率极高，必须严格封顶。</p>
 *
 * <h3>v2.2：不冻同类</h3>
 * <p>被打的目标照常吃冻伤；周围只波及「别人」：同类（玩家不冻别的玩家，怪物不冻同种怪物）、队友和自己养的宠物不冻。
 * 原来只排除持有者自己，会把队友和宠物一起冻上。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "adura_moonlight_sword",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentEpilepsyFire.class,
                EnchantmentEatShit.class,
                EnchantmentHypnoticSmoke.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class
        },
        baseEnchantability = 30,
        levelMultiplier = 10
)
@Deprecated
public class EnchantmentAduraMoonlightSword extends EnchantmentBase {
    private static final String VALUE_ID = "adura_moonlight_sword";
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS = EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    6, 1, 64);
    private static final EnchantmentValues.Handle MAX_TARGETS = EnchantmentValues.define(VALUE_ID, "max_targets",
                    16, 1, 200);
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle FROSTBITE_DURATION = EnchantmentValues.define(VALUE_ID, "frostbite_duration",
                    200, 20, 6000);
    private static final EnchantmentValues.Handle FROSTBITE_MAX_AMPLIFIER = EnchantmentValues.define(VALUE_ID, "frostbite_max_amplifier",
                    9, 0, 127);
    public EnchantmentAduraMoonlightSword() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
    }
}

