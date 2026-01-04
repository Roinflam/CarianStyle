package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 圣地附魔
 *
 * 光环效果：举盾时为16格内同类生物提供减伤、治疗和护盾
 * 减伤：-5%×等级（叠加）
 * 治疗：最大血量×1.5%×等级（每60tick）
 * 护盾：吸收量+3%×等级，上限=最大血量/3×等级
 */
@AutoRegisterEnchantment(
        id = "holy_ground",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentHolyGround extends EnchantmentBase {

    public EnchantmentHolyGround() {
        super(EnumEnchantmentType.BREAKABLE, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.MAINHAND,
                EntityEquipmentSlot.OFFHAND
        });
    }

    /**
     * 减伤光环：附近有人举着此附魔盾牌时，受击者获得减伤
     */
    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        Enchantment holyGround = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHolyGround.class);
        if (holyGround == null) {
            return;
        }

        // 查找附近16格内的同类生物
        List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                victim,
                16,
                entity -> entity.getClass() == victim.getClass()
        );

        for (EntityLivingBase entity : nearbyEntities) {
            // 检查是否正在举盾
            if (!entity.isHandActive()) {
                continue;
            }

            ItemStack activeItem = entity.getHeldItem(entity.getActiveHand());
            if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ItemShield)) {
                continue;
            }

            int level = EnchantmentHelper.getEnchantmentLevel(holyGround, activeItem);

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
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        if (evt.getEntity().ticksExisted % 60 != 0) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        // 检查是否正在举盾
        if (!holder.isHandActive()) {
            return;
        }

        ItemStack activeItem = holder.getHeldItem(holder.getActiveHand());
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ItemShield)) {
            return;
        }

        Enchantment holyGround = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHolyGround.class);
        if (holyGround == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(holyGround, activeItem);

        if (level <= 0) {
            return;
        }

        // 查找附近16格内的同类生物
        List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                holder,
                16,
                entity -> entity.getClass() == holder.getClass()
        );

        for (EntityLivingBase entity : nearbyEntities) {
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
                entity.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1, 3);
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}