package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentAncientDragonLightning;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 诺克斯之月附魔
 *
 * 护甲附魔，夜间迷惑敌人
 * 夜间每秒有 2.5% 概率让 32 格内正以穿戴者为目标的怪物一齐转向：
 * 各自在视线内、离自己不比穿戴者远的另一种生物里随机挑一个作为新目标。
 * <p>
 * 以前挂在怪物选目标的事件上，每只怪每次选中穿戴者各掷一次、不限距离，
 * 怪越多、重选目标越频繁，转移得越多。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "moon_of_noxtura",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentMoonOfNoxtura extends EnchantmentBase {

    /** 检查间隔（tick） */
    private static final int TICK_INTERVAL = 20;
    /** 每次检查的触发概率（%） */
    private static final double TRIGGER_CHANCE = 2.5D;
    /** 搜索以穿戴者为目标的怪物的半径（格） */
    private static final int SEARCH_RADIUS = 32;
    /** 参与计算的等级上限（开了等级上限时生效） */
    private static final int LEVEL_CAP = 10;

    public EnchantmentMoonOfNoxtura() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 每秒检查一次，只在穿戴者周围 32 格里找盯着他的怪
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        EntityPlayer player = evt.player;
        if (player.world.isRemote || evt.phase != TickEvent.Phase.START) {
            return;
        }
        if (player.ticksExisted % TICK_INTERVAL != 0) {
            return;
        }
        // 只在夜晚生效
        if (player.world.isDaytime()) {
            return;
        }
        if (!player.isEntityAlive()) {
            return;
        }

        Enchantment moonOfNoxtura = EnchantmentRegistry.getEnchantmentByClass(EnchantmentMoonOfNoxtura.class);
        if (moonOfNoxtura == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : player.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(moonOfNoxtura, armor);
            }
        }
        totalLevel = capLevel(totalLevel, LEVEL_CAP);
        if (totalLevel <= 0) {
            return;
        }

        if (!RandomUtil.percentageChance(TRIGGER_CHANCE)) {
            return;
        }

        List<EntityLiving> nearbyMobs = EntityUtil.getNearbyEntities(EntityLiving.class, player, SEARCH_RADIUS,
                mob -> mob != null && player.equals(mob.getAttackTarget()));

        for (EntityLiving mob : nearbyMobs) {
            // 在怪物视线内、离它不比穿戴者远的另一种生物里挑新目标
            double distance = mob.getDistance(player);
            List<EntityLivingBase> alternatives = EntityUtil.getNearbyEntities(
                    EntityLivingBase.class,
                    mob,
                    (int) distance,
                    entity -> entity != null
                            && entity.getClass() != mob.getClass()
                            && mob.canEntityBeSeen(entity)
                            && !entity.equals(mob)
                            && !entity.equals(player)
            );
            if (!alternatives.isEmpty()) {
                mob.setAttackTarget(alternatives.get(RandomUtil.getInt(0, alternatives.size() - 1)));
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        return !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentHealingByFire.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentShelterOfFire.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentPreciseLightning.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentAncientDragonLightning.class));
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}