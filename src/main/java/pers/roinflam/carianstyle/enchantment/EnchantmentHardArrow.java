package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;
import java.util.List;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E4)
 *
 * 硬箭附魔
 * <p>v2.2：ProjectileImpact射手+LivingKnockBack受击者入口接入怪物附魔触发开关。
 * onHurtAsVictimHighest 走中央事件分发器，已被 scanEntity 拦截。</p>
 *
 * <h3>v2.3：接入视觉反馈（两半各一个）</h3>
 * <ul>
 *     <li><b>加伤那半边</b>：命中实体时播钉入式冲击
 *         （{@code CombatArtBurstRenderer}）。见下方
 *         {@link #onProjectileImpact_Arrow}。
 *         <b>v2.4 起演出尺寸随这一箭的伤害线性缩放</b>——
 *         打掉目标一半最大生命时用满，轻擦一下只有三分之一大，
 *         使演出大小本身成为一条无需读数字的伤害反馈。</li>
 *     <li><b>代价那半边</b>：由客户端的 {@code HardArrowRangeRenderer} 画一个
 *         <b>只有持有者自己看得见</b>的 12 格范围光环，范围内有生物时由绿转赤。
 *         <b>那部分完全不需要服务端配合</b>——它只读本地玩家的主手物品，
 *         零网络包，因此本文件里没有任何与之相关的代码。</li>
 * </ul>
 * <p>
 * 之所以要给代价那半边单独做提示：12 格远超肉眼可靠估距的范围，
 * 而它带来的是<b>受伤 +80%×等级</b>的巨额惩罚，玩家却只能靠目测。
 * </p>
 *
 * @version 2.4
 */
@Deprecated
public class EnchantmentHardArrow extends EnchantmentBase {
    private static final String VALUE_ID = "hard_arrow";
    private static final EnchantmentValues.Handle ARROW_DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "arrow_damage_per_level",
                    0.8D, 0.0D, 10.0D);
    private static final EnchantmentValues.Handle MELEE_DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "melee_damage_per_level",
                    0.8D, 0.0D, 10.0D);
    private static final EnchantmentValues.Handle KNOCKBACK_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "knockback_per_level",
                    0.75D, 0.0D, 10.0D);
    private static final EnchantmentValues.Handle AOE_RADIUS = EnchantmentValues.define(VALUE_ID, "aoe_radius",
                    12, 1, 64);
    protected void onHurtAsVictimHighest(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int l) {
        return 0;
    }
    public int getMaxCost(int l) {
        return 0;
    }
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return false;
    }
}

