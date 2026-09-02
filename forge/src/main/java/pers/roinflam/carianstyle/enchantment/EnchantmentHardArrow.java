package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

import java.util.List;

/**
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
@AutoRegisterEnchantment(id = "hard_arrow", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnchantmentCategory.BOW, slots = {EquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber
public class EnchantmentHardArrow extends EnchantmentBase {
    public EnchantmentHardArrow() { super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact_Arrow(@NotNull ProjectileImpactEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        if (!(evt.getProjectile() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof LivingEntity attacker)) return;

        // ⭐ v2.2：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        Enchantment hardArrow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHardArrow.class);
        if (hardArrow == null) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(hardArrow, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;
        arrow.setBaseDamage(arrow.getBaseDamage() + arrow.getBaseDamage() * level * 0.8);

        // ⭐ v2.3：冲击特效。
        // 本方法对「射中方块」与「射中实体」一视同仁地加伤，但特效需要一个命中实体
        // 作为锚点（冲击环以它为原点沿箭道后退），故只在实体命中时播。
        // 射中方块时不播是刻意的：那种情况下加伤没有意义，画出来只会是噪音
        if (evt.getRayTraceResult() instanceof EntityHitResult entityHit
                && attacker.level() instanceof ServerLevel serverLevel) {
            // ⭐ v2.4：演出尺寸随伤害缩放，故把伤害估值一并传过去。
            //
            // 这里必须自己估算，不能直接用 getBaseDamage() —— 原版箭矢的实际伤害是
            //     Mth.ceil(clamp(速度模长 × baseDamage))
            // （见 AbstractArrow.onHitEntity）。一支强弓满蓄力射出的箭速度模长约 3，
            // 直接拿 baseDamage 当伤害会低估到三分之一，演出永远缩在最小档。
            //
            // 注意这仍然只是估值：真实伤害还会经过暴击、护甲、抗性、以及本模组
            // 其它附魔的加成。但演出尺寸只需要一个「这一下重不重」的量级判断，
            // 为了精确而把特效推迟到伤害结算之后，反而会让视觉比音效和击退慢半拍
            float estimatedDamage = (float) (arrow.getBaseDamage()
                    * arrow.getDeltaMovement().length());
            CarianStyleCombatArtEffects.hardArrow(serverLevel, attacker,
                    entityHit.getEntity(), estimatedDamage);
        }
    }

    @Override
    protected void onHurtAsVictimHighest(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.getAttacker() == null) return;
        LivingEntity victim = ctx.getHolder();
        List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, victim, 12, entity -> !entity.equals(victim));
        if (entities.isEmpty()) return;
        ctx.addDamage(ctx.getDamage() * level * 0.8f);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingKnockBack(@NotNull LivingKnockBackEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角，被击退强化）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        ItemStack heldItem = victim.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        Enchantment hardArrow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHardArrow.class);
        if (hardArrow == null) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(hardArrow, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;
        List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, victim, 12, entity -> !entity.equals(victim));
        if (entities.isEmpty()) return;
        evt.setStrength(evt.getStrength() + evt.getStrength() * level * 0.75f);
    }

    @Override public int getMinCost(int l) { return (int)((5 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
    @Override protected boolean checkCompatibility(@NotNull Enchantment ench) { return super.checkCompatibility(ench) && !ench.equals(Enchantments.POWER_ARROWS); }
}
