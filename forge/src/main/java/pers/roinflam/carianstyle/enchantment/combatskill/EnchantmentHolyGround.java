// 文件：EnchantmentHolyGround.java
// 路径：forge/src/main/java/pers/roinflam/carianstyle/enchantment/combatskill/EnchantmentHolyGround.java
package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 圣地附魔
 * <p>
 * 光环效果：举盾时为16格内同类生物提供减伤、治疗和护盾
 * 减伤：-5%×等级（叠加）
 * 治疗：最大血量×1.5%×等级（每60tick）
 * 护盾：吸收量+3%×等级，上限=最大血量/3×等级
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "holy_ground",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.BREAKABLE,
        slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentHolyGround extends EnchantmentBase {

    public EnchantmentHolyGround() {
        super(EnchantmentCategory.BREAKABLE, new EquipmentSlot[]{
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND
        });
    }

    /**
     * 减伤光环：附近有人举着此附魔盾牌时，受击者获得减伤
     */
    @SubscribeEvent
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        Enchantment holyGround = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHolyGround.class);
        if (holyGround == null) {
            return;
        }

        // 查找附近16格内的同类生物
        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                victim,
                16,
                entity -> entity.getClass() == victim.getClass()
        );

        for (LivingEntity entity : nearbyEntities) {
            // 检查是否正在举盾
            if (!entity.isUsingItem()) {
                continue;
            }

            ItemStack activeItem = entity.getItemInHand(entity.getUsedItemHand());
            if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
                continue;
            }

            int level = EnchantmentHelper.getItemEnchantmentLevel(holyGround, activeItem);

            if (level > 0) {
                // 减伤 -5% × 等级
                evt.setAmount(evt.getAmount() - evt.getAmount() * level * 0.05f);
            }
        }
    }

    /**
     * 治疗/护盾光环：举盾时每60tick为附近同类生物提供治疗和护盾
     */
    @SubscribeEvent
    public static void onLivingUpdate(@NotNull LivingEvent.LivingTickEvent evt) {
        if (evt.getEntity().tickCount % 60 != 0) {
            return;
        }

        LivingEntity holder = evt.getEntity();

        // 检查是否正在举盾
        if (!holder.isUsingItem()) {
            return;
        }

        ItemStack activeItem = holder.getItemInHand(holder.getUsedItemHand());
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
            return;
        }

        Enchantment holyGround = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHolyGround.class);
        if (holyGround == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(holyGround, activeItem);

        if (level <= 0) {
            return;
        }

        // 查找附近16格内的同类生物
        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                holder,
                16,
                entity -> entity.getClass() == holder.getClass()
        );

        for (LivingEntity entity : nearbyEntities) {
            boolean effectApplied = false;

            // 治疗：最大血量 × 等级 × 1.5%
            if (entity.getHealth() < entity.getMaxHealth()) {
                entity.heal(entity.getMaxHealth() * level * 0.015f);
                effectApplied = true;
            }

            // 护盾：吸收量 +3% × 等级，上限 = 最大血量/3 × 等级
            float maxAbsorption = entity.getMaxHealth() / 3 * level;
            if (entity.getAbsorptionAmount() < maxAbsorption) {
                float newAbsorption = Math.min(
                        entity.getAbsorptionAmount() + entity.getMaxHealth() * level * 0.03f,
                        maxAbsorption
                );
                entity.setAbsorptionAmount(newAbsorption);
                effectApplied = true;
            }

            // 有效果时播放音效
            if (effectApplied) {
                entity.playSound(SoundEvents.PLAYER_LEVELUP, 1, 3);
            }
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}