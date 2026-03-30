package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentAncientDragonLightning;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import java.util.List;

/**
 * 诺克斯之月附魔
 * <p>
 * 性能优化：从ServerTickEvent遍历所有世界所有实体改为PlayerTickEvent
 * 原代码每秒遍历服务器所有维度的所有实体，性能开销极大
 * 优化后：只在有附魔的玩家周围搜索锁定自己的怪物
 * </p>
 * @version 2.1
 */
@AutoRegisterEnchantment(id = "moon_of_noxtura", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.ARMOR, slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}, conflictsWith = {EnchantmentHealingByFire.class, EnchantmentShelterOfFire.class, EnchantmentPreciseLightning.class, EnchantmentAncientDragonLightning.class}, forceTreasure = true)
@Mod.EventBusSubscriber
public class EnchantmentMoonOfNoxtura extends EnchantmentBase {
    public EnchantmentMoonOfNoxtura() { super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}); }

    /**
     * 优化：从ServerTickEvent改为PlayerTickEvent
     * 原代码遍历所有世界所有实体（getAllLevels -> getAllEntities），性能灾难
     * 优化后：每秒检查一次，只在有附魔的玩家周围32格搜索锁定自己的Mob
     */
    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.phase != TickEvent.Phase.START) return;
        // 每20tick（1秒）检查一次
        if (evt.player.tickCount % 20 != 0) return;
        // 只在夜晚生效
        if (evt.player.level().isDay()) return;

        Player player = evt.player;
        if (!player.isAlive()) return;

        Enchantment moonOfNoxtura = EnchantmentRegistry.getEnchantmentByClass(EnchantmentMoonOfNoxtura.class);
        if (moonOfNoxtura == null) return;

        // 检查护甲附魔
        int totalLevel = 0;
        for (ItemStack armor : player.getArmorSlots()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentHelper.getItemEnchantmentLevel(moonOfNoxtura, armor);
        }
        if (ConfigLoader.levelLimit) totalLevel = Math.min(totalLevel, 10);
        if (totalLevel <= 0) return;

        // 2.5%概率触发
        if (!RandomUtil.percentageChance(2.5)) return;

        // 搜索周围32格内锁定自己的Mob
        List<Mob> nearbyMobs = EntityUtil.getNearbyEntities(Mob.class, player, 32, mob -> {
            LivingEntity target = mob.getTarget();
            return target != null && target.equals(player);
        });

        for (Mob mob : nearbyMobs) {
            // 搜索mob视线内的其他可攻击实体
            double distance = mob.distanceTo(player);
            List<LivingEntity> alternatives = EntityUtil.getNearbyEntities(
                LivingEntity.class, mob, (int) distance,
                e -> e.getClass() != mob.getClass() && mob.hasLineOfSight(e) && !e.equals(mob) && !e.equals(player)
            );
            if (!alternatives.isEmpty()) {
                mob.setTarget(alternatives.get(RandomUtil.getInt(0, alternatives.size() - 1)));
            }
        }
    }

    @Override public int getMinCost(int l) { return (int)(35 * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
