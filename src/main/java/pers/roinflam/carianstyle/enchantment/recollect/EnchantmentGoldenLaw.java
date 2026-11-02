package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;
import java.util.UUID;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E2)
 *
 * 黄金律法附魔
 * <p>v2.2：双向监听器入口接入怪物附魔触发开关</p>
 *
 * <h3>v2.3：接入律法碑特效</h3>
 * <p>
 * 胸前立起一面矩形黄金律法碑 + 碑面刻纹 + 脚下外扩金环。
 * <b>只挂在 {@link #onLivingAttack} 的两个 {@code setCanceled(true)} 分支上</b>，
 * 即「免疫真正生效」的那一刻。
 * </p>
 * <p>
 * <b>刻意不给 {@link #onLivingHurt} 里的增伤 / 减伤加视觉</b>——那两个是常驻数值、
 * 每次交火都在生效，加了会变成一个持续闪烁的噪音源。而免疫是离散事件，
 * 且此前完全不可见（伤害被取消，看起来和「没打中」一模一样），
 * 这正是最需要反馈的地方。
 * </p>
 * <p>
 * 「矩形碑面」是全模组唯一的矩形图形，与同为金色的祈祷一击（竖直光柱 + 十字）、
 * 神圣净化（三维十字）、黄金树祝福（根须 + 落叶）在形状上彻底分开。
 * </p>
 *
 * @version 2.3
 */
@Deprecated
public class EnchantmentGoldenLaw extends EnchantmentBase {
    private static final String VALUE_ID = "golden_law";
    private static final EnchantmentValues.Handle BASE_BONUS = EnchantmentValues.define(VALUE_ID, "base_bonus",
                    0.15D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle MISSING_HEALTH_BONUS = EnchantmentValues.define(VALUE_ID, "missing_health_bonus",
                    0.45D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    0.85D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle IMMUNITY_THRESHOLD = EnchantmentValues.define(VALUE_ID, "immunity_threshold",
                    0.15D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle IMMUNITY_COOLDOWN = EnchantmentValues.define(VALUE_ID, "immunity_cooldown",
                    100, 1, 12000);
    private static final String IMMUNITY_COOLDOWN_KEY = "golden_law_immunity";
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    protected boolean checkCompatibility(Enchantment ench) {
        return false;
    }
    public int getMinCost(int l) {
        return 0;
    }
    public int getMaxCost(int l) {
        return 0;
    }
}

