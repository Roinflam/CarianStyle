package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
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
 * <p>
 * 只能附在盾牌上（以前所有可损坏物品都能附，附在别的东西上不起作用）。
 * 治疗 / 护盾光环只由玩家施放（玩家 tick 里结算），以前任何生物举着都会结算。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "holy_ground",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentHolyGround extends EnchantmentBase {

    // 每级减伤比例（每个举盾者各算一次，依次乘算）
    private static final float DAMAGE_REDUCTION_PER_LEVEL = 0.05F;
    // 光环脉冲间隔（tick）
    private static final int PULSE_INTERVAL_TICKS = 60;
    // 每级每次回复的最大生命值比例
    private static final float HEAL_PER_LEVEL = 0.015F;
    // 每级每次获得的吸收盾（最大生命值比例）
    private static final float ABSORPTION_PER_LEVEL = 0.03F;
    // 光环半径（格）
    private static final int RADIUS = 16;

    public EnchantmentHolyGround() {
        super(CarianStyleEnchantments.SHIELD, new EntityEquipmentSlot[]{
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
        if (shouldBlockMobTrigger(victim, false)) {
            return;
        }

        Enchantment holyGround = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHolyGround.class);
        if (holyGround == null) {
            return;
        }

        // 查找附近16格内的同类生物
        List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                victim,
                RADIUS,
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
                evt.setAmount(evt.getAmount() - evt.getAmount() * level * DAMAGE_REDUCTION_PER_LEVEL);
            }
        }
    }

    /**
     * 治疗/护盾光环：玩家举盾时每60tick为附近同类生物提供治疗和护盾
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote || evt.phase != TickEvent.Phase.START) {
            return;
        }

        if (evt.player.ticksExisted % PULSE_INTERVAL_TICKS != 0) {
            return;
        }

        EntityPlayer holder = evt.player;
        if (!holder.isEntityAlive()) {
            return;
        }

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
                RADIUS,
                entity -> entity.getClass() == holder.getClass()
        );

        for (EntityLivingBase entity : nearbyEntities) {
            boolean effectApplied = false;

            // 治疗：最大血量 × 等级 × 1.5%
            if (entity.getHealth() < entity.getMaxHealth()) {
                entity.heal(entity.getMaxHealth() * level * HEAL_PER_LEVEL);
                effectApplied = true;
            }

            // 护盾：吸收量 +3% × 等级，上限 = 最大血量/3 × 等级
            float maxAbsorption = entity.getMaxHealth() / 3 * level;
            if (entity.getAbsorptionAmount() < maxAbsorption) {
                float newAbsorption = Math.min(
                        entity.getAbsorptionAmount() + entity.getMaxHealth() * level * ABSORPTION_PER_LEVEL,
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