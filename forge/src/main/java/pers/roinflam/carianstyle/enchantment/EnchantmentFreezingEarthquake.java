package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.AoeEffectPacket;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;

import java.util.List;

/**
 * 冻结地震附魔
 * <p>
 * 护甲附魔。单次受击达到 25% 最大生命值时，将周围站在地面的敌人击飞并施加冻伤。
 * </p>
 *
 * <h3>特效：一次触发只发一发包</h3>
 * <p>
 * 触发点广播 {@link AoeEffectPacket#TYPE_FROST_QUAKE}
 * （放射地裂 + 霜环外滚 + 中心冰花），半径与实际作用半径一致。
 * </p>
 * <p>
 * <b>这里踩过一个坑，值得留档。</b>早期版本写成了两行：先调
 * {@code shockwaveRing(..., ParticleTypes.SNOWFLAKE)}，
 * 再手动 {@code VisualNetwork.sendToNearby(new AoeEffectPacket(TYPE_FROST_QUAKE, ...))}，
 * 注释解释为「原版雪花粒子环 + 自绘几何，二者叠加」。
 * 但那个前提当时就已不成立——特效系统早已改为纯自绘发包，
 * {@code ParticleTypes.*} 只是「选哪套演出」的标识符，不会生成任何原版粒子。
 * 于是两行的真实效果是<b>把同一个 FROST_QUAKE 包发了两遍</b>。
 * </p>
 * <p>
 * 客户端 {@code AoeEffectManager} 只对 {@code TYPE_RED_LIGHTNING} 做同位置合并，
 * FROST_QUAKE 不在合并之列，两发包各自 {@code spawn} 出独立实例，
 * 在同一坐标、同一时刻、同一半径完全重叠：顶点量翻倍、
 * 叠加混合后亮度明显偏浓（每层 alpha 被叠加两次），
 * 还额外占用 {@code MAX_ACTIVE}(64) 的存活特效名额。
 * </p>
 * <p>
 * 现调用 {@link CarianStyleEffects#frostQuake}，方法名即语义，
 * 不再依赖任何需要背诵的对应关系。
 * <b>以后加特效时请守住这条：一次触发只发一发包。</b>
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "freezing_earthquake",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
@Mod.EventBusSubscriber
public class EnchantmentFreezingEarthquake extends EnchantmentBase {

    /** AOE 搜索半径硬上限（方块） */
    private static final int MAX_SEARCH_RADIUS = 10;

    /** 单次触发最大命中目标数 */
    private static final int MAX_TARGETS = 20;

    public EnchantmentFreezingEarthquake() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    /**
     * 受击事件监听：单次受击达到 25% 最大生命值时，触发范围击飞 + 冻伤。
     *
     * @param evt 生物受到伤害事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // 怪物附魔触发开关（受击者视角，重击范围冻结）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        if (evt.getAmount() < victim.getMaxHealth() * 0.25f) {
            return;
        }

        Enchantment freezingEarthquake = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFreezingEarthquake.class);
        if (freezingEarthquake == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(freezingEarthquake, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        int range = 3 + (totalLevel - 1) * 2;
        int searchRadius = Math.min(range, MAX_SEARCH_RADIUS);

        // ⭐ 唯一的一发特效广播（早期曾误写成两发，导致冰爆重叠播放，详见类注释）。
        // 特效半径取实际作用半径 searchRadius，保证「看到多大就打多大」；
        // 广播范围由 CarianStyleEffects.BROADCAST_RANGE(64 格) 统一决定。
        // 纯服务端发包，不生成实体、不触发任何事件，不影响下方范围冻结逻辑。
        if (victim.level() instanceof ServerLevel serverLevel) {
            CarianStyleEffects.frostQuake(serverLevel, victim, searchRadius);
        }

        List<LivingEntity> targets = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                victim,
                searchRadius,
                entity -> entity.onGround() && !entity.equals(victim)
        );

        final int effectiveLevel = totalLevel;

        int hitCount = 0;
        for (LivingEntity target : targets) {
            if (hitCount >= MAX_TARGETS) {
                break;
            }

            if (target.onGround()) {
                target.setDeltaMovement(
                        target.getDeltaMovement().x,
                        effectiveLevel * 0.35f,
                        target.getDeltaMovement().z
                );

                target.addEffect(new MobEffectInstance(
                        CarianStylePotion.FROSTBITE.get(),
                        effectiveLevel * 5 * 20,
                        effectiveLevel - 1
                ));
                hitCount++;
            }
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
