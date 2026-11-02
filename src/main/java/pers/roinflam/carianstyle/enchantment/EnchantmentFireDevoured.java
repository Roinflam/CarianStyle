package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import java.util.List;

/**
 * PORT-STUB(E3)
 *
 * 火焰吞噬附魔
 * <p>
 * 武器附魔，火焰范围伤害
 * 攻击者着火时，对目标周围敌人造成火焰伤害并点燃
 * </p>
 *
 * <h3>性能安全上限（v2.1 新增）</h3>
 * <ul>
 *   <li>{@link #MAX_SEARCH_RADIUS}：AOE 搜索半径硬上限，防止高等级附魔（如 100 级）
 *       直接用等级当半径，导致一次搜索扫过数百个实体。</li>
 *   <li>{@link #MAX_TARGETS}：单次触发最大命中目标数上限，防止密集怪物场景下
 *       一次攻击触发数千次 LivingHurtEvent 事件链，导致看门狗超时崩服。</li>
 * </ul>
 *
 * <p>崩溃场景示例：若玩家手持 100 级 fire_devoured 武器，用范围攻击
 * 同时命中 50 个生物，原版逻辑会触发 50 × 300 ≈ 15,000 次 hurt 调用，
 * 每次 hurt 再走一整条 LivingHurtEvent 事件链（装了别的伤害处理模组还会更长），
 * 单个 tick 耗时轻松突破 60 秒，触发 Spigot Watchdog 崩服。</p>
 *
 * <h3>v2.2：要求满蓄力</h3>
 * <p>玩家必须满蓄力挥击才触发火焰 AOE 与点燃；连点时整段跳过。怪物持有者不受影响。</p>
 *
 * <h3>v2.3：不烧同类</h3>
 * <p>范围伤害只波及「别人」：持有者自己、同类（玩家不烧别的玩家，怪物不烧同种怪物）、队友和自己养的宠物都不烧。
 * 原来的筛选条件 {@code !e.equals(victim) || e.equals(attacker)} 等于除被打的目标外全部，连持有者自己也会烧到。</p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@AutoRegisterEnchantment(
        id = "fire_devoured",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        baseEnchantability = 10,
        levelMultiplier = 15
)
@Deprecated
public class EnchantmentFireDevoured extends EnchantmentBase {
    private static final String VALUE_ID = "fire_devoured";
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS = EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    8, 1, 64);
    private static final EnchantmentValues.Handle MAX_TARGETS = EnchantmentValues.define(VALUE_ID, "max_targets",
                    16, 1, 200);
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.15D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle IGNITE_SECONDS = EnchantmentValues.define(VALUE_ID, "ignite_seconds",
                    10, 1, 300);
    public EnchantmentFireDevoured() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
    }
}

