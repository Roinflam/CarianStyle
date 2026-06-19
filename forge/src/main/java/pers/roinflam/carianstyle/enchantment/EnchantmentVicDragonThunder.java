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
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

/**
 * 古龙雷附魔
 * <p>v2.3：LivingDamage双向入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.3
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
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (lightning != null) {
                lightning.moveTo(victim.getX(), victim.getY(), victim.getZ());
                lightning.setVisualOnly(true);
                serverLevel.addFreshEntity(lightning);
            }
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
