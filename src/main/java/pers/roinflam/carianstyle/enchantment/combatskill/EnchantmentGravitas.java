package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;

/**
 * PORT-STUB(E1)
 *
 * 重力附魔
 * <p>v2.2：攻击者视角接入怪物附魔触发开关。PlayerTick天然玩家专属，无需检查。</p>
 * <p>v2.3：力场激活/结束时同步给客户端（{@link #GRAVITY_FIELD_SERIAL}），供
 * {@code GravitasDistortionRenderer} 绘制以施法者为中心、半径 {@link #FIELD_RADIUS} 格的
 * 地面范围圈。范围判定与视觉共用同一个 {@link #FIELD_RADIUS} 常量，避免两处各自写死数值导致
 * 以后改动漏改一处。</p>
 *
 * <h3>v2.4：补上登出时的集合清理</h3>
 * <p>
 * {@link #ACTIVE_FIELD_HOLDERS} 此前只在两处移除条目：力场自然结束、持有者死亡。
 * 少了第三条路径——<b>玩家在力场生效期间直接退出游戏</b>。
 * 这种情况下 UUID 会永久留在集合里。
 * </p>
 * <p>
 * 严格说这不是无界泄漏（上限是服务器历史玩家数，每条只有一个 UUID），
 * 但它会造成一个可观察的副作用：该玩家<b>下次登录时</b>集合里仍有他的 UUID，
 * 于是 {@code ACTIVE_FIELD_HOLDERS.add(uuid)} 返回 false，
 * {@link ClientSyncEffectManager#addEntity} 不会被调用，
 * <b>范围圈视觉不会出现</b>——直到力场结束再重新触发一次才恢复正常。
 * 也就是说这不只是内存问题，是一个会被玩家看见的显示 bug。
 * </p>
 * <p>
 * 现在在 {@code PlayerLoggedOutEvent} 里一并清掉 UUID 与冷却数据。
 * </p>
 *
 * @author RoinFlam
 * @version 2.4
 */
@AutoRegisterEnchantment(
        id = "gravitas",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        baseEnchantability = 35,
        levelMultiplier = 0
)
@Deprecated
public class EnchantmentGravitas extends EnchantmentBase {
    private static final String VALUE_ID = "gravitas";
    private static final EnchantmentValues.Handle ACTIVE_SECONDS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "active_seconds_per_level", 4, 1, 120);
    private static final EnchantmentValues.Handle EFFECT_SECONDS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "effect_seconds_per_level", 2, 1, 120);
    private static final EnchantmentValues.Handle AMPLIFIER_BASE = EnchantmentValues.define(VALUE_ID, "amplifier_base", 10, 1, 127);
    private static final EnchantmentValues.Handle AMPLIFIER_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "amplifier_per_level", 4, 0, 32);
    private static final EnchantmentValues.Handle AURA_AMPLIFIER = EnchantmentValues.define(VALUE_ID, "aura_amplifier", 9, 0, 127);
    private static final String GRAVITAS_ACTIVE_KEY = "gravitas_active";
    public static final EnchantmentValues.Handle FIELD_RADIUS = EnchantmentValues.defineShared(VALUE_ID, "field_radius", 12, 1, 64);
    public static final int GRAVITY_FIELD_SERIAL = 6;
    private static final Set<UUID> ACTIVE_FIELD_HOLDERS = ConcurrentHashMap.newKeySet();
    public EnchantmentGravitas() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        if (ench.is(Enchantments.KNOCKBACK)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }
}

