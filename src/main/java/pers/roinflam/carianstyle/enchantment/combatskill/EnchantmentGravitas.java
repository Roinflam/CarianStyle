package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * 重力附魔
 *
 * 攻击时给敌人施加重力效果；玩家持有时还会激活重力场，
 * 激活期间周围12格内其他生物持续受到轻微重力效果
 * <p>
 * 力场只有玩家能开（玩家 tick 里结算），怪物拿着命中只给被命中的目标挂效果；
 * 以前放在所有生物的更新事件里，怪物命中后也会开力场。玩家登出时清掉力场状态。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "gravitas",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentGravitas extends EnchantmentBase {

    private static final String GRAVITAS_ACTIVE_KEY = "gravitas_active";
    // 每级力场持续秒数
    private static final int ACTIVE_SECONDS_PER_LEVEL = 4;
    // 每级命中目标的重力效果秒数
    private static final int EFFECT_SECONDS_PER_LEVEL = 2;
    // 命中目标重力效果的基础等级（每级 -1% 攻速 / 移速 / 飞行速度）
    private static final int AMPLIFIER_BASE = 10;
    // 每级附魔额外增加的重力效果等级
    private static final int AMPLIFIER_PER_LEVEL = 4;
    // 力场内其它生物的重力效果等级（效果等级 9 即 -10%）
    private static final int AURA_AMPLIFIER = 9;
    // 力场半径（格）
    private static final int FIELD_RADIUS = 12;

    public EnchantmentGravitas() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时激活重力场并给敌人施加重力效果
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
        if (shouldBlockMobTrigger(attacker, false)) {
            return;
        }

        ItemStack heldItem = attacker.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment gravitas = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGravitas.class);
        if (gravitas == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(gravitas, heldItem);
        if (level <= 0) {
            return;
        }

        // 激活重力场状态（只有玩家的力场会在 onPlayerTick 里生效）
        int activeDuration = level * ACTIVE_SECONDS_PER_LEVEL * 20;
        EnchantmentDataManager.setCooldown(GRAVITAS_ACTIVE_KEY, attacker.getUniqueID(), activeDuration);

        // 给敌人施加重力效果
        int potionDuration = level * EFFECT_SECONDS_PER_LEVEL * 20;
        int potionLevel = AMPLIFIER_BASE + level * AMPLIFIER_PER_LEVEL - 1;
        victim.addPotionEffect(new PotionEffect(CarianStylePotion.GRAVITAS, potionDuration, potionLevel));
    }

    /**
     * 死亡时清理状态
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }
        EnchantmentDataManager.clearCooldown(GRAVITAS_ACTIVE_KEY, evt.getEntityLiving().getUniqueID());
    }

    /**
     * 登出时清理状态，下次登录是干净的起点
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(@Nonnull PlayerEvent.PlayerLoggedOutEvent evt) {
        EnchantmentDataManager.clearCooldown(GRAVITAS_ACTIVE_KEY, evt.player.getUniqueID());
    }

    /**
     * 力场激活期间，周围生物持续受到轻微重力效果（只处理玩家）
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote || evt.phase != TickEvent.Phase.START) {
            return;
        }

        EntityPlayer holder = evt.player;
        if (!holder.isEntityAlive()) {
            return;
        }

        UUID uuid = holder.getUniqueID();
        if (!EnchantmentDataManager.isOnCooldown(GRAVITAS_ACTIVE_KEY, uuid)) {
            return;
        }

        List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                holder,
                FIELD_RADIUS,
                entity -> !entity.equals(holder)
        );

        for (EntityLivingBase entity : nearbyEntities) {
            entity.addPotionEffect(new PotionEffect(CarianStylePotion.GRAVITAS, 2, AURA_AMPLIFIER));
        }
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        // 与击退冲突
        if (ench == Enchantments.KNOCKBACK) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }
}