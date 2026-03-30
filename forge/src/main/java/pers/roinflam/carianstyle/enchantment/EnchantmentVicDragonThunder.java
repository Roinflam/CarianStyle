package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
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
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

/**
 * 古龙雷附魔
 * <p>
 * 攻击时概率召唤落雷（概率受天气影响）
 * 持有时减免雷电伤害
 * </p>
 * <p>
 * 修复记录 v2.2：
 * - 全部2处 getUsedItemHand() → InteractionHand.MAIN_HAND
 * - 天气倍率bug修复：原代码先乘isRaining再乘isThundering，
 *   MC中雷暴时isRaining()也返回true，导致倍率变成1*2*4=8而不是4。
 *   改为互斥判断：雷暴4x，下雨2x，晴天1x
 *   触发概率也改为互斥判断，与PreciseLightning和AncientDragonLightning一致
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
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

        // ==================== 防御：雷电伤害减免 ====================
        // v2.2修复：使用主手
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

        // ==================== 攻击：召唤落雷 ====================
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity attacker)) {
            return;
        }

        // v2.2修复：使用主手
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

        // v2.2修复：触发概率改为互斥判断
        // 原代码：晴天=level*5, 下雨=level*5*2, 雷暴=100（但雷暴时isRaining也true会先命中下雨分支）
        // 修复后：先判断雷暴再判断下雨，确保分支互斥
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

        // 召唤视觉闪电
        Level world = victim.level();
        if (world instanceof ServerLevel serverLevel) {
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (lightning != null) {
                lightning.moveTo(victim.getX(), victim.getY(), victim.getZ());
                lightning.setVisualOnly(true);
                serverLevel.addFreshEntity(lightning);
            }
        }

        // 重置无敌帧
        victim.invulnerableTime = 10;

        // v2.2修复：伤害倍率改为互斥判断
        // 原代码：if(isRaining) *=2; if(isThundering) *=4; → 雷暴时实际为1*2*4=8倍
        // 修复后：互斥判断，雷暴=4x，下雨=2x，晴天=1x
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
