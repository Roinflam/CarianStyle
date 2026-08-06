package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 重力附魔
 * <p>v2.2：攻击者视角接入怪物附魔触发开关。PlayerTick天然玩家专属，无需检查。</p>
 * <p>v2.3：力场激活/结束时同步给客户端（{@link #GRAVITY_FIELD_SERIAL}），供
 * {@code GravitasDistortionRenderer} 绘制以施法者为中心、半径 {@link #FIELD_RADIUS} 格的
 * 地面范围圈。范围判定与视觉共用同一个 {@link #FIELD_RADIUS} 常量，避免两处各自写死数值导致
 * 以后改动漏改一处。</p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@AutoRegisterEnchantment(
        id = "gravitas",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentGravitas extends EnchantmentBase {

    private static final String GRAVITAS_ACTIVE_KEY = "gravitas_active";

    /**
     * 重力力场的作用半径（格）。周围生物范围判定（{@link #onPlayerTick}）与客户端范围圈视觉
     * （{@code GravitasDistortionRenderer}）必须保持一致，故抽成公开常量供渲染器引用，
     * 避免两处各自写死 12 导致以后改动漏改一处。
     */
    public static final int FIELD_RADIUS = 12;

    /**
     * 重力力场的客户端同步序列号，供 {@code GravitasDistortionRenderer} 判断「谁正在施放力场」，
     * 从而绘制以施法者为中心、半径 {@link #FIELD_RADIUS} 格的地面范围圈。序列号 1~3 为自定义火焰、
     * 4 为隐身、5 为猩红腐败（见 {@code ScarletRotSyncHandler}），本附魔取 6，避免冲突。
     */
    public static final int GRAVITY_FIELD_SERIAL = 6;

    /**
     * 力场持有者集合：记录当前「已同步给客户端」的玩家 UUID。
     * <p>{@link EnchantmentDataManager} 只提供「是否在冷却中」的瞬时查询、没有到期回调，
     * 故力场的开启/关闭边沿由本集合自行检测：力场刚激活时（此前不在集合中）才调用一次
     * {@link ClientSyncEffectManager#addEntity} 广播；力场结束时（不再在冷却中但仍在集合中）
     * 才调用一次 {@link ClientSyncEffectManager#removeEntity}，避免每 tick 重复处理。
     * 仅服务端 tick 线程访问。</p>
     */
    private static final Set<UUID> ACTIVE_FIELD_HOLDERS = ConcurrentHashMap.newKeySet();

    public EnchantmentGravitas() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时激活重力场并给敌人施加重力效果
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
        LivingEntity attacker = (LivingEntity) evt.getSource().getEntity();

        // ⭐ v2.2：怪物附魔触发开关（攻击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment gravitas = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGravitas.class);
        if (gravitas == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(gravitas, heldItem);

        if (level <= 0) {
            return;
        }

        int activeDuration = level * 4 * 20;
        EnchantmentDataManager.setCooldown(GRAVITAS_ACTIVE_KEY, attacker.getUUID(), activeDuration);

        int potionDuration = level * 2 * 20;
        int potionLevel = 10 + level * 4 - 1;
        victim.addEffect(new MobEffectInstance(
                CarianStylePotion.GRAVITAS.get(),
                potionDuration,
                potionLevel
        ));
    }

    /**
     * 死亡时清理状态（清理类，不需要拦截）
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity dead = evt.getEntity();
        EnchantmentDataManager.clearCooldown(GRAVITAS_ACTIVE_KEY, dead.getUUID());
        // 力场持有者死亡：同步移除范围圈视觉，无需等待客户端每 5 秒一次的定期重同步
        if (ACTIVE_FIELD_HOLDERS.remove(dead.getUUID())) {
            ClientSyncEffectManager.removeEntity(dead, GRAVITY_FIELD_SERIAL);
        }
    }

    /**
     * 激活状态时，周围生物持续受到轻微重力效果；同时维护力场的客户端同步状态，
     * 驱动 {@code GravitasDistortionRenderer} 绘制以自身为中心的地面范围圈。
     * <p>PlayerTickEvent 仅玩家触发，无需开关检查</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.phase != TickEvent.Phase.START) {
            return;
        }

        Player holder = evt.player;
        if (!holder.isAlive()) {
            return;
        }

        UUID uuid = holder.getUUID();
        boolean active = EnchantmentDataManager.isOnCooldown(GRAVITAS_ACTIVE_KEY, uuid);

        if (!active) {
            // 力场已结束：若此前同步过范围圈，通知客户端移除
            if (ACTIVE_FIELD_HOLDERS.remove(uuid)) {
                ClientSyncEffectManager.removeEntity(holder, GRAVITY_FIELD_SERIAL);
            }
            return;
        }

        // 力场刚激活（此前不在集合中）：同步给客户端，绘制范围圈
        if (ACTIVE_FIELD_HOLDERS.add(uuid)) {
            ClientSyncEffectManager.addEntity(holder, GRAVITY_FIELD_SERIAL);
        }

        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                holder,
                FIELD_RADIUS,
                entity -> !entity.equals(holder)
        );

        for (LivingEntity entity : nearbyEntities) {
            entity.addEffect(new MobEffectInstance(CarianStylePotion.GRAVITAS.get(), 2, 9));
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        if (ench == Enchantments.KNOCKBACK) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
