package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

/**
 * PORT-STUB(E2)
 *
 * 巨剑方阵附魔
 * <p>
 * 死亡时触发：
 * - 在空中生成3把巨大的辉石剑
 * - 延迟后向攻击者发射
 * - 伤害基于攻击者损失的生命值
 * </p>
 *
 * <h3>v3.0：把生成距离从 ±10 格收到贴身</h3>
 * <p>
 * <b>原实现的问题：</b>三把剑的偏移分别是 {@code (-10,+10)}、{@code (-10,-10)}、{@code (+10,0)}，
 * 也就是散布在死者周围<b>十格开外</b>、跨度达 20 格。加上 {@code size=7.5} 的体积，
 * 玩家死的那一刻只会看到三把巨剑从视野边缘外冒出来，完全读不出「这是我的护甲在反击」。
 * 原作巨剑方阵是在施法者<b>身后上方</b>浮起数把巨剑再压下去。
 * </p>
 * <p>
 * 现改为半径 {@link #RING_RADIUS} 的环形布置、高度 {@link #RING_HEIGHT}——
 * 巨剑本身就有 7.5 的体积，4 格半径已足够让三把剑彼此不穿模，同时全部落在死者视野内。
 * </p>
 * <p>
 * <b>刻意不挂悬浮锚点：</b>本附魔在持有者<b>死亡瞬间</b>触发，尸体随即移除，
 * 没有可跟随的对象。{@code EntityGlintblades} 在释放者失效时会自动停止跟随、原地悬停，
 * 因此这里直接不设锚点即可，行为一致且少一次同步写入。
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "greatblade_phalanx",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        baseEnchantability = 30,
        levelMultiplier = 15
)
@Deprecated
public class EnchantmentGreatbladePhalanx extends EnchantmentBase {
    private static final String VALUE_ID = "greatblade_phalanx";
    private static final EnchantmentValues.Handle BLADE_COUNT = EnchantmentValues.define(VALUE_ID, "blade_count",
                    3, 1, 32);
    private static final EnchantmentValues.Handle RING_RADIUS = EnchantmentValues.define(VALUE_ID, "ring_radius",
                    4.0D, 0.5D, 32.0D);
    private static final EnchantmentValues.Handle RING_HEIGHT = EnchantmentValues.define(VALUE_ID, "ring_height",
                    4.5D, 0.0D, 32.0D);
    private static final EnchantmentValues.Handle COOLDOWN = EnchantmentValues.define(VALUE_ID, "cooldown",
                    6000, 20, 144000);
    private static final EnchantmentValues.Handle BASE_DELAY = EnchantmentValues.define(VALUE_ID, "base_delay",
                    75, 0, 600);
    private static final EnchantmentValues.Handle DELAY_STEP = EnchantmentValues.define(VALUE_ID, "delay_step",
                    25, 0, 300);
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.1D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle TRACKING_STRENGTH = EnchantmentValues.define(VALUE_ID, "tracking_strength",
                    0.08D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle MAX_LIFETIME = EnchantmentValues.define(VALUE_ID, "max_lifetime",
                    120, 20, 1200);
    public EnchantmentGreatbladePhalanx() {
        super(EnchantTarget.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }
    protected void onDeath(@NotNull EnchantmentContext ctx, int pieceLevel) {
    }
}

