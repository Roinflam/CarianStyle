package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
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
import pers.roinflam.carianstyle.network.AoeEffectPacket;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;

import java.util.List;

/**
 * 排斥附魔
 * <p>
 * 护腿附魔，受击时范围击退
 * 受到攻击时击退周围所有敌人
 * 范围 = 5 + (等级 - 1) × 0.75 格
 * </p>
 *
 * <h3>特效：贴地的冲击环</h3>
 * <p>
 * 触发点广播 {@link AoeEffectPacket#TYPE_REPULSION}——
 * 两道发光环从中心猛烈外推扩张并快速淡出的短促冲击波（约 520ms）。
 * </p>
 * <p>
 * <b>中心 Y 必须取脚底</b>（由 {@link CarianStyleEffects#repulsion} 的实体重载自动处理）。
 * 早期版本传的是半身高 {@code getY() + bbHeight * 0.5}——那个取法对「贴身球状爆发」
 * 是合理的，但本演出实际是<b>两道水平地面光环</b>
 * （见 {@code AoeEffectRenderer.drawRepulsion}，由 {@code glowRing} 绘制、y 固定），
 * 于是环被画在腰部、悬在半空，既不符合「以自身为中心把周围推开」的地面冲击语义，
 * 也和模组内其余地面法阵（因果律、冻结地震、光环等一律贴地）不一致。
 * </p>
 * <p>
 * 渲染器侧另有 {@code Y_OFFSET = 0.02f} 的离地微抬用于避免与地形 z-fighting，
 * 调用方无需额外补偿。
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "exclude",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR_LEGS,
        slots = {EquipmentSlot.LEGS}
)
@Mod.EventBusSubscriber
public class EnchantmentExclude extends EnchantmentBase {

    /** AOE 搜索半径硬上限（方块） */
    private static final double MAX_SEARCH_RADIUS = 10.0;

    /** 单次触发最大命中目标数 */
    private static final int MAX_TARGETS = 20;

    public EnchantmentExclude() {
        super(EnchantmentCategory.ARMOR_LEGS, new EquipmentSlot[]{EquipmentSlot.LEGS});
    }

    /**
     * 受击事件监听：范围击退周围所有生物。
     *
     * @param evt 生物受到攻击事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // 怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        Enchantment exclude = EnchantmentRegistry.getEnchantmentByClass(EnchantmentExclude.class);

        if (exclude == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(exclude, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        double range = 5 + (totalLevel - 1) * 0.75;
        double searchRadius = Math.min(range, MAX_SEARCH_RADIUS);

        // ⭐ 受击触发范围击退时播放一发排斥冲击波（双环猛烈外推、约 520ms）。
        // 传实体重载 → 内部取脚底坐标，冲击环贴地（详见类注释）。
        // 纯服务端发包，不生成实体、不触发任何事件，不影响下方击退逻辑。
        if (victim.level() instanceof ServerLevel serverLevel) {
            CarianStyleEffects.repulsion(serverLevel, victim);
        }

        List<LivingEntity> targets = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                victim,
                searchRadius,
                entity -> !entity.equals(victim)
        );

        int hitCount = 0;
        for (LivingEntity target : targets) {
            if (hitCount >= MAX_TARGETS) {
                break;
            }
            double x = victim.getX() - target.getX();
            double z = victim.getZ() - target.getZ();
            target.knockback(0.5f, x, z);
            hitCount++;
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
