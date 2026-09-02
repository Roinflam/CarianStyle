package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

import java.util.UUID;

/**
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
@AutoRegisterEnchantment(id = "golden_law", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber
public class EnchantmentGoldenLaw extends EnchantmentBase {
    private static final String IMMUNITY_COOLDOWN_KEY = "golden_law_immunity";
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public EnchantmentGoldenLaw() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getSource().isCreativePlayer()) return;
        Enchantment goldenLaw = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGoldenLaw.class);
        if (goldenLaw == null) return;

        // 攻击者视角
        if (evt.getSource().getDirectEntity() instanceof LivingEntity attacker) {
            // ⭐ v2.2：怪物附魔触发开关（攻击者视角）
            if (!EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) {
                ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
                if (!heldItem.isEmpty()) {
                    int level = EnchantmentHelper.getItemEnchantmentLevel(goldenLaw, heldItem);
                    if (level > 0) {
                        float healthRatio = attacker.getHealth() / attacker.getMaxHealth();
                        evt.setAmount(evt.getAmount() + evt.getAmount() * 0.15f + evt.getAmount() * 0.45f * (1 - healthRatio));
                    }
                }
            }
        }

        // 受击者视角
        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        ItemStack heldItem = victim.getItemInHand(InteractionHand.MAIN_HAND);
        if (!heldItem.isEmpty()) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(goldenLaw, heldItem);
            if (level > 0) evt.setAmount(evt.getAmount() * 0.85f);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getSource().isCreativePlayer()) return;
        LivingEntity holder = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角，"小伤免疫"非濒死触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        UUID uuid = holder.getUUID();
        ItemStack mainHand = holder.getMainHandItem();
        if (mainHand.isEmpty()) return;
        Enchantment goldenLaw = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGoldenLaw.class);
        if (goldenLaw == null) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(goldenLaw, mainHand);
        if (level <= 0) return;
        if (evt.getAmount() <= holder.getHealth() * 0.15) {
            evt.setCanceled(true);
            // ⭐ v2.3：小伤免疫生效
            emitLawTablet(holder, evt.getSource().getDirectEntity());
            return;
        }
        if (!EnchantmentDataManager.isOnCooldown(IMMUNITY_COOLDOWN_KEY, uuid)) {
            evt.setCanceled(true);
            EnchantmentDataManager.setCooldown(IMMUNITY_COOLDOWN_KEY, uuid, 100);
            // ⭐ v2.3：每 5 秒一次的完全免疫生效
            emitLawTablet(holder, evt.getSource().getDirectEntity());
        }
    }

    /**
     * 播放黄金律法碑特效。
     * <p>
     * 抽成一个方法是因为免疫有两条互斥的触发路径（小伤免疫 / 冷却免疫），
     * 两处的表现应当完全一致——玩家不需要区分是哪一种免疫救了自己，
     * 只需要知道「这一下被律法挡住了」。
     * </p>
     * <p>
     * 有伤害来源时碑面正对来源方向，读作「挡住了这一下」；
     * 来源为空时（如摔落、窒息这类无实体伤害）退回持有者自身朝向。
     * </p>
     *
     * @param holder 免疫了这次伤害的持有者
     * @param source 伤害的直接来源实体，可为 null
     */
    private static void emitLawTablet(@NotNull LivingEntity holder, @Nullable Entity source) {
        if (!(holder.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (source != null) {
            CarianStyleCombatArtEffects.goldenLaw(serverLevel, holder, source);
        } else {
            CarianStyleCombatArtEffects.goldenLaw(serverLevel, holder);
        }
    }

    @Override protected boolean checkCompatibility(Enchantment ench) {
        if (ench.getClass().getPackage().getName().contains("law") && !ench.equals(this)) return false;
        return super.checkCompatibility(ench);
    }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
