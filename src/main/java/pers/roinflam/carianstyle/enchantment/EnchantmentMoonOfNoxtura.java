package pers.roinflam.carianstyle.enchantment;

import pers.roinflam.carianstyle.utils.util.DayNightUtil;
import pers.roinflam.carianstyle.utils.Reference;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentAncientDragonLightning;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
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
@AutoRegisterEnchantment(id = "moon_of_noxtura", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.VERY_RARE, type = EnumEnchantmentType.ARMOR, slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}, conflictsWith = {EnchantmentHealingByFire.class, EnchantmentShelterOfFire.class, EnchantmentPreciseLightning.class, EnchantmentAncientDragonLightning.class}, forceTreasure = true)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentMoonOfNoxtura extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.moon_of_noxtura.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "moon_of_noxtura";

    /**
     * 仇恨转移的结算间隔（tick）
     * <p>默认 20，允许范围 1 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle TICK_INTERVAL =
            EnchantmentValues.define(VALUE_ID, "tick_interval",
                    20, 1, 600);

    /**
     * 每次结算的触发概率（百分比）
     * <p>默认 2.5，允许范围 0.0 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_CHANCE =
            EnchantmentValues.define(VALUE_ID, "trigger_chance",
                    2.5D, 0.0D, 100.0D);

    /**
     * 仇恨转移的搜索半径（格）
     * <p>默认 32，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "search_radius",
                    32, 1, 64);

    public EnchantmentMoonOfNoxtura() { super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}); }

    /**
     * 优化：从ServerTickEvent改为PlayerTickEvent
     * 原代码遍历所有世界所有实体（getAllLevels -> getAllEntities），性能灾难
     * 优化后：每秒检查一次，只在有附魔的玩家周围32格搜索锁定自己的Mob
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote || evt.phase != TickEvent.Phase.START) return;
        // 每20tick（1秒）检查一次
        if (evt.player.ticksExisted % TICK_INTERVAL.getInt() != 0) return;
        // 只在夜晚生效
        if (DayNightUtil.isDay(evt.player.world)) return;

        EntityPlayer player = evt.player;
        if (!player.isEntityAlive()) return;

        Enchantment moonOfNoxtura = EnchantmentRegistry.getEnchantmentByClass(EnchantmentMoonOfNoxtura.class);
        if (moonOfNoxtura == null) return;

        // 检查护甲附魔（v-cache：走中央装备缓存）
        int totalLevel = EnchantmentEventHandler.armorTotal(player, moonOfNoxtura);
        totalLevel = applyLevelLimit(moonOfNoxtura, totalLevel, 10);
        if (totalLevel <= 0) return;

        // 2.5%概率触发
        if (!RandomUtil.percentageChance(TRIGGER_CHANCE.get())) return;

        // 搜索周围32格内锁定自己的Mob
        List<EntityLiving> nearbyMobs = EntityUtil.getNearbyEntities(EntityLiving.class, player, SEARCH_RADIUS.getInt(), mob -> {
            EntityLivingBase target = mob.getAttackTarget();
            return target != null && target.equals(player);
        });

        for (EntityLiving mob : nearbyMobs) {
            // 搜索mob视线内的其他可攻击实体
            double distance = mob.getDistance(player);
            List<EntityLivingBase> alternatives = EntityUtil.getNearbyEntities(
                EntityLivingBase.class, mob, (int) distance,
                e -> e.getClass() != mob.getClass() && mob.canEntityBeSeen(e) && !e.equals(mob) && !e.equals(player)
            );
            if (!alternatives.isEmpty()) {
                mob.setAttackTarget(alternatives.get(RandomUtil.getInt(0, alternatives.size() - 1)));
            }
        }
    }

    @Override public int getMinCost(int l) { return (int)(35 * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
