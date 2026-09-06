package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

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
 * <p>崩溃场景示例：若玩家手持 100 级 fire_devoured 武器 + SlashBlade 残影 areaAttack
 * 同时命中 50 个 NPC，原版逻辑会触发 50 × 300 ≈ 15,000 次 hurt 调用，
 * 每次 hurt 再走一整条 LivingHurtEvent → l2library → l2damagetracker → l2hostility 事件链，
 * 单个 tick 耗时轻松突破 60 秒，触发 Spigot Watchdog 崩服。</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "fire_devoured",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentFireDevoured extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.fire_devoured.desc，
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
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 作为攻击者造成伤害时触发（最低优先级）
     * <p>攻击者着火时，对受害者周围的敌人造成火焰 AOE 伤害并点燃。</p>
     *
     * @param ctx   附魔触发上下文
     * @param level 附魔等级
     */
    @Override
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, LEVEL_CAP.getInt());
        }

        if (attacker.getRemainingFireTicks() <= 0) {
            return;
        }

        // ⭐ v2.1：搜索半径硬上限，防止等级直接当半径导致扫描范围爆炸
        // 例如 100 级原本 = 半径 100 方块（200³ = 800 万方块体积），硬封顶 8 方块
        int searchRadius = Math.min(effectiveLevel, MAX_SEARCH_RADIUS.getInt());

        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                victim,
                searchRadius,
                entity -> !entity.equals(victim) || entity.equals(attacker)
        );

        // 伤害倍率保留原有线性等级加成（游戏设计不变）
        float damage = ctx.getDamage() * effectiveLevel * (float) DAMAGE_PER_LEVEL.get();

        // ⭐ v2.1：单次触发最大命中目标数硬上限，防止事件链风暴
        int hitCount = 0;
        for (LivingEntity entity : nearbyEntities) {
            if (hitCount >= MAX_TARGETS.getInt()) {
                break;
            }
            entity.hurt(entity.damageSources().inFire(), damage);

            if (entity.getRemainingFireTicks() < 200) {
                entity.setSecondsOnFire(IGNITE_SECONDS.getInt());
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
