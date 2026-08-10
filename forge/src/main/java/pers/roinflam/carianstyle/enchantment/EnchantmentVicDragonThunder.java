package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
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
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;

/**
 * 古龙雷附魔（维克的龙雷）
 * <p>
 * 武器附魔，双向生效：<b>防御端</b>按等级减免雷电伤害（15%/级，满 100% 直接免疫）；
 * <b>攻击端</b>按天气概率召唤红色龙雷追加伤害。
 * </p>
 *
 * <h3>红色龙雷：去掉原版闪电后必须自己补雷声</h3>
 * <p>
 * 攻击端召唤的原版蓝白 {@code LightningBolt} 已替换为
 * {@link CarianStyleEffects#redLightning}，与古龙雷击保持一致的红色雷击意象。
 * </p>
 * <p>
 * 原版闪电用的是 {@code setVisualOnly(true)}，本就只有「视觉 + 音效」无副作用，
 * 但<b>音效随它一起没了</b>。因此下方两条 {@code playSound}
 * （雷鸣 + 落地）<b>必须保留</b>，否则只有画面没有声音。
 * </p>
 *
 * <h3>天气倍率互斥判断</h3>
 * <p>
 * MC 中雷暴时 {@code isRaining()} 也返回 true，故触发概率与伤害倍率均为互斥判断：
 * 雷暴 100% 概率 / 4x 伤害，下雨 {@code level*10}% / 2x，晴天 {@code level*5}% / 1x。
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "vic_dragon_thunder",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentVicDragonThunder extends EnchantmentBase {

    public EnchantmentVicDragonThunder() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 伤害事件监听：防御端做雷电减伤，攻击端概率召唤红色龙雷。
     *
     * @param evt 生物受到伤害事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        Enchantment vicDragonThunder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentVicDragonThunder.class);
        if (vicDragonThunder == null) {
            return;
        }

        // 防御：雷电伤害减免（受击者视角）
        // 怪物附魔触发开关（受击者视角）
        if (!EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) {
            if (!evt.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                    && "lightningBolt".equals(evt.getSource().getMsgId())) {
                ItemStack victimHeld = victim.getItemInHand(InteractionHand.MAIN_HAND);
                if (!victimHeld.isEmpty()) {
                    int level = EnchantmentHelper.getItemEnchantmentLevel(vicDragonThunder, victimHeld);
                    if (ConfigLoader.levelLimit) {
                        level = Math.min(level, 10);
                    }
                    if (level > 0) {
                        if (level * 0.15 >= 1) {
                            evt.setCanceled(true);
                            return;
                        }
                        evt.setAmount(evt.getAmount() - evt.getAmount() * level * 0.15f);
                    }
                }
            }
        }

        // 攻击：召唤落雷（攻击者视角）
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity attacker)) {
            return;
        }

        // 怪物附魔触发开关（攻击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack attackerHeld = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (attackerHeld.isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(vicDragonThunder, attackerHeld);
        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }
        if (level <= 0) {
            return;
        }

        int triggerChance;
        if (attacker.level().isThundering()) {
            triggerChance = 100;
        } else if (attacker.level().isRaining()) {
            triggerChance = level * 5 * 2;
        } else {
            triggerChance = level * 5;
        }

        if (!RandomUtil.percentageChance(triggerChance)) {
            return;
        }

        Level world = victim.level();
        if (world instanceof ServerLevel serverLevel) {
            double lx = victim.getX();
            double ly = victim.getY();
            double lz = victim.getZ();

            // ⭐ 原版蓝白闪电替换为红色自绘闪电（龙雷红色雷击意象，与古龙雷击一致）
            CarianStyleEffects.redLightning(serverLevel, lx, ly, lz);

            // 原版闪电去掉后需手动补雷声：雷鸣 + 落地，音高带轻微随机，避免机械重复
            serverLevel.playSound(null, lx, ly, lz,
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                    0.8f, 0.9f + serverLevel.random.nextFloat() * 0.2f);
            serverLevel.playSound(null, lx, ly, lz,
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER,
                    0.5f, 1.0f + serverLevel.random.nextFloat() * 0.2f);
        }

        victim.invulnerableTime = 10;

        int magnification = 1;
        if (attacker.level().isThundering()) {
            magnification = 4;
        } else if (attacker.level().isRaining()) {
            magnification = 2;
        }

        victim.hurt(victim.damageSources().lightningBolt(), evt.getAmount() * level * 0.5f * magnification);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
