package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 灾厄附魔（诅咒）
 *
 * 受击伤害×1.5
 * 每tick 2%概率吸引32格内怪物
 */
@AutoRegisterEnchantment(
        id = "calamity",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        isCurse = true
)
@Mod.EventBusSubscriber
public class EnchantmentCalamity extends EnchantmentBase {

    /** 参与计算的等级上限（只用来判断有没有） */
    private static final int LEVEL_CAP = 10;
    /** 受到的伤害倍率 */
    private static final float DAMAGE_MULTIPLIER = 1.5F;
    /** 每 tick 嘲讽一次的概率（百分比） */
    private static final double TRIGGER_CHANCE = 2.0D;

    /**
     * 只能附在胸甲上；以前任意护甲件都能附
     */
    public EnchantmentCalamity() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    private static int getArmorLevel(EntityLivingBase entity) {
        Enchantment calamity = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCalamity.class);
        if (calamity == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(calamity, armor);
            }
        }
        return capLevel(totalLevel, LEVEL_CAP);
    }

    private static int getTotalLevel(EntityLivingBase entity) {
        Enchantment calamity = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCalamity.class);
        if (calamity == null) {
            return 0;
        }

        int totalLevel = 0;

        // 主手 + 四件护甲；以前看的是「正在用的那只手」
        totalLevel += EnchantmentHelper.getEnchantmentLevel(calamity, entity.getHeldItemMainhand());

        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(calamity, armor);
            }
        }

        return capLevel(totalLevel, LEVEL_CAP);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        if (shouldBlockMobTrigger(victim, false)) {
            return;
        }

        // 以前是 ×2，和说明写的 150% 不符
        int totalLevel = getArmorLevel(victim);
        if (totalLevel > 0) {
            evt.setAmount(evt.getAmount() * DAMAGE_MULTIPLIER);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote) {
            return;
        }

        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        if (!RandomUtil.percentageChance(TRIGGER_CHANCE)) {
            return;
        }

        EntityPlayer player = evt.player;
        if (!player.isEntityAlive()) {
            return;
        }

        int totalLevel = getTotalLevel(player);
        if (totalLevel <= 0) {
            return;
        }

        List<EntityMob> nearbyMobs = EntityUtil.getNearbyEntities(
                EntityMob.class,
                player,
                32
        );

        for (EntityMob mob : nearbyMobs) {
            EntityLivingBase currentTarget = mob.getAttackTarget();

            if (currentTarget == null || !currentTarget.isEntityAlive()) {
                if (RandomUtil.percentageChance(25)) {
                    mob.setAttackTarget(player);
                }
            } else if (!currentTarget.equals(player)) {
                if (RandomUtil.percentageChance(50)) {
                    mob.setAttackTarget(player);
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }
}