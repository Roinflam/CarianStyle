package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.mc112.util.EntityFireAccess;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import javax.annotation.Nonnull;
import java.util.List;

/**
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
 * 单个 tick 耗时轻松突破 60 秒，触发服务端看门狗崩服。</p>
 *
 * <h3>v2.2：要求满蓄力</h3>
 * <p>玩家必须满蓄力挥击才触发火焰 AOE 与点燃；连点时整段跳过。怪物持有者不受影响。</p>
 *
 * <h3>v2.3：不烧同类</h3>
 * <p>范围伤害只波及「别人」：持有者自己、同类（玩家不烧别的玩家，怪物不烧同种怪物）、队友和自己养的宠物都不烧。
 * 原来的筛选条件 {@code !e.equals(victim) || e.equals(attacker)} 等于除被打的目标外全部，连持有者自己也会烧到。</p>
 * <p>
 * 1.12.2 版：剩余燃烧时间读 {@code Entity} 的私有字段 {@code fire}（1.20.1 是 {@code getRemainingFireTicks()}）；
 * 火焰伤害用原版共享的 {@code DamageSource.IN_FIRE}，只读不改。
 * </p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@AutoRegisterEnchantment(
        id = "fire_devoured",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentFireDevoured extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.fire_devoured.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "fire_devoured";

    /**
     * AOE 搜索半径上限（格）
     * <p>默认 8，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    8, 1, 64);

    /**
     * 单次触发最大命中目标数
     * <p>默认 16，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    16, 1, 200);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级的溅射伤害倍率
     * <p>默认 0.15，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.15D, 0.0D, 5.0D);

    /**
     * 点燃目标的持续秒数
     * <p>默认 10，允许范围 1 ~ 300。</p>
     */
    private static final EnchantmentValues.Handle IGNITE_SECONDS =
            EnchantmentValues.define(VALUE_ID, "ignite_seconds",
                    10, 1, 300);

    public EnchantmentFireDevoured() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 作为攻击者造成伤害时触发（最低优先级）
     * <p>攻击者着火时，对受害者周围的敌人造成火焰 AOE 伤害并点燃。</p>
     *
     * @param ctx   附魔触发上下文
     * @param level 附魔等级
     */
    @Override
    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // v2.2：每次命中最多 16 次 hurt 加点燃，连点等于成倍放大事件风暴，要求满蓄力
        if (!isFullyCharged(attacker)) {
            return;
        }

        int effectiveLevel = level;
        effectiveLevel = capLevel(effectiveLevel, LEVEL_CAP.getInt());

        if (EntityFireAccess.getRemainingFireTicks(attacker) <= 0) {
            return;
        }

        // v2.1：搜索半径硬上限，防止等级直接当半径导致扫描范围爆炸
        // 例如 100 级原本 = 半径 100 方块（200³ = 800 万方块体积），硬封顶 8 方块
        int searchRadius = Math.min(effectiveLevel, MAX_SEARCH_RADIUS.getInt());

        List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                victim,
                searchRadius,
                // 除了被打的目标，只波及「别人」：持有者自己、同类、队友和自己养的宠物都不烧
                // （原来的写法 !e.equals(victim) || e.equals(attacker) 等于除目标外全部，连持有者自己也会烧到）
                entity -> !entity.equals(victim) && !isSameKindOrAlly(attacker, entity)
        );

        // 伤害倍率保留原有线性等级加成（游戏设计不变）
        float damage = ctx.getDamage() * effectiveLevel * (float) DAMAGE_PER_LEVEL.get();

        // v2.1：单次触发最大命中目标数硬上限，防止事件链风暴
        int hitCount = 0;
        for (EntityLivingBase entity : nearbyEntities) {
            if (hitCount >= MAX_TARGETS.getInt()) {
                break;
            }
            // 免火生物在 1.20.1 对 is_fire 伤害无敌；1.12.2 的 attackEntityFrom 不看 isImmuneToFire，要自己判。
            // 计数照旧（1.20.1 里 hurt 返回 false 也算一个目标）；点燃对免火生物没效果，照旧
            if (!entity.isImmuneToFire()) {
                entity.attackEntityFrom(DamageSource.IN_FIRE, damage);
            }
            if (EntityFireAccess.getRemainingFireTicks(entity) < 200) {
                entity.setFire(IGNITE_SECONDS.getInt());
            }
            hitCount++;
        }
    }

    /**
     * 获取附魔最小消耗
     *
     * @param enchantmentLevel 附魔等级
     * @return 最小经验消耗
     */
    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    /**
     * 获取附魔最大消耗
     *
     * @param enchantmentLevel 附魔等级
     * @return 最大经验消耗
     */
    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
