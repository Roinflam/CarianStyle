package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleBurstParticles;

/**
 * 古龙雷附魔（维克的龙雷）
 * <p>v2.3：LivingDamage双向入口接入怪物附魔触发开关</p>
 * <p>
 * 修改记录 v2.4（龙雷红色闪电）：
 * - 视觉改红：还原艾尔登法环「龙雷」的红色雷击意象，攻击端召唤的原版蓝白 {@code LightningBolt}
 *   替换为 {@link CarianStyleBurstParticles#redLightning} 自绘的红色之字闪电柱（含分叉 + 落地红色
 *   冲击），与古龙雷击（ancient_dragon_lightning）保持一致。
 * - 原版闪电采用 {@code setVisualOnly(true)}，本就只有「视觉 + 音效」无副作用，去掉后需手动补雷声，
 *   故新增 {@code LIGHTNING_BOLT_THUNDER}（雷鸣）+ {@code LIGHTNING_BOLT_IMPACT}（落地）两条音效。
 * - 仅替换攻击端的闪电视觉与配套音效；防御端（雷电伤害减免）、触发概率、伤害倍率等机制完全未动。
 * </p>
 *
 * @author RoinFlam
 * @version 2.4
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
        // ⭐ v2.3：怪物附魔触发开关（受击者视角）
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

        // ⭐ v2.3：怪物附魔触发开关（攻击者视角）
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

            // v2.4：原版蓝白闪电替换为红色自绘闪电（龙雷红色雷击意象，与古龙雷击一致）
            CarianStyleBurstParticles.redLightning(serverLevel, lx, ly, lz);

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