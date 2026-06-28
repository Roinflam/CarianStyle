package pers.roinflam.carianstyle.enchantment;

import net.minecraft.core.particles.ParticleTypes;
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
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.AoeEffectPacket;
import pers.roinflam.carianstyle.network.VisualNetwork;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleBurstParticles;

import java.util.List;

/**
 * 冻结地震附魔
 * <p>v2.2：LivingDamage受击者重击触发入口接入怪物附魔触发开关</p>
 * <p>v2.3：新增重击落地时的冰霜冲击波粒子视觉（纯服务端 sendParticles 广播，
 * 不新增网络包，不触碰任何范围冻结/上限逻辑）。</p>
 * <p>v2.4：在重击触发点接入已有的自绘 AOE 演出 {@link AoeEffectPacket#TYPE_FROST_QUAKE}
 * （放射地裂 + 霜环 + 中心冰花），即玩家所求的「脚下自绘冰爆」。该特效类型与渲染早已存在于
 * {@code AoeEffectPacket / AoeEffectManager / AoeEffectRenderer} 中，本次<b>仅在触发点广播一发</b>，
 * 不改任何机制（依旧是单次受击 ≥ 25% 最大生命瞬发）、不改范围冻结/上限逻辑，也保留 v2.3 的原版雪花粒子环
 * （二者叠加：粒子 + 自绘几何）。若只想要自绘演出，可单独删除下方 {@code shockwaveRing} 调用。</p>
 *
 * @author RoinFlam
 * @version 2.4
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

    private static final int MAX_SEARCH_RADIUS = 10;
    private static final int MAX_TARGETS = 20;

    /**
     * 自绘 FROST_QUAKE 特效的广播半径外扩量（格）。
     * <p>{@link VisualNetwork#sendToNearby} 的 range 决定「哪些客户端会收到这发特效包」，
     * 应略大于特效本身的视觉半径，保证站在效果边缘的玩家也能完整看到演出。</p>
     */
    private static final double EFFECT_BROADCAST_MARGIN = 24.0D;

    public EnchantmentFreezingEarthquake() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角，重击范围冻结）
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

        // 视觉：重击触发时在受击者脚下发射一圈冰霜冲击波（落地震荡）。
        // 纯服务端 sendParticles 广播，粒子不触发任何事件，不影响下方范围冻结逻辑
        if (victim.level() instanceof ServerLevel serverLevel) {
            // —— v2.3：原版雪花粒子冲击波环（保留） ——
            CarianStyleBurstParticles.shockwaveRing(
                    serverLevel,
                    victim.getX(), victim.getY() + 0.1, victim.getZ(),
                    searchRadius, 24, ParticleTypes.SNOWFLAKE
            );

            // —— v2.4：在受击者脚下广播一发自绘 FROST_QUAKE 立体冰爆（放射地裂 + 霜环 + 中心冰花） ——
            // 该效果类型与其渲染早已存在（AoeEffectPacket.TYPE_FROST_QUAKE → AoeEffectManager/AoeEffectRenderer），
            // 此处仅触发，不新增任何渲染代码、不改任何机制。
            // 定点不跟随（NO_ENTITY），特效尺寸取实际作用半径 searchRadius；
            // 广播半径在视觉半径基础上外扩 EFFECT_BROADCAST_MARGIN，保证边缘玩家也能看到完整演出。
            VisualNetwork.sendToNearby(
                    serverLevel,
                    victim.getX(), victim.getY(), victim.getZ(),
                    searchRadius + EFFECT_BROADCAST_MARGIN,
                    new AoeEffectPacket(
                            AoeEffectPacket.TYPE_FROST_QUAKE,
                            victim.getX(), victim.getY(), victim.getZ(),
                            (float) searchRadius
                    )
            );
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
