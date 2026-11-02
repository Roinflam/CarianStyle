package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E1)
 *
 * 对空射击附魔
 * <p>v2.2：射手视角接入怪物附魔触发开关</p>
 * <p>
 * v2.3：接入对空射击特效（自更高处竖直贯下的箭光 + <b>目标高度处</b>的空爆环）。
 * 特效<b>只在高度差判定通过之后</b>才播——普通命中不该有这个演出。
 * </p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@AutoRegisterEnchantment(
        id = "sky_shot",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.BOW,
        slots = {EquipmentSlot.MAINHAND},
        baseEnchantability = 10,
        levelMultiplier = 15
)
@Deprecated
public class EnchantmentSkyShot extends EnchantmentBase {
    private static final String VALUE_ID = "sky_shot";
    private static final EnchantmentValues.Handle HEIGHT_THRESHOLD = EnchantmentValues.define(VALUE_ID, "height_threshold_blocks",
                    5.0D, 0.0D, 128.0D);
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle TARGET_HEALTH_DAMAGE = EnchantmentValues.define(VALUE_ID, "target_health_damage",
                    0.1D, 0.0D, 1.0D);
    public EnchantmentSkyShot() {
        super(EnchantTarget.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
}

