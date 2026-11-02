package pers.roinflam.carianstyle.enchantment;

import pers.roinflam.carianstyle.utils.Reference;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 洛蕾塔大弓附魔
 * <p>v2.1：EntityJoinLevel箭矢生成+ProjectileImpact命中两个入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "loretta_big_bow",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.BOW,
        slots = {EntityEquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentLorettaBigBow extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.loretta_big_bow.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "loretta_big_bow";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 箭矢基础伤害的倍率
     * <p>默认 1.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle ARROW_DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "arrow_damage_multiplier",
                    1.5D, 0.0D, 10.0D);

    /**
     * 爆炸强度
     * <p>默认 2.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle EXPLOSION_STRENGTH =
            EnchantmentValues.define(VALUE_ID, "explosion_strength",
                    2.0D, 0.0D, 20.0D);

    /**
     * 火箭矢的爆炸强度
     * <p>默认 3.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle EXPLOSION_STRENGTH_ON_FIRE =
            EnchantmentValues.define(VALUE_ID, "explosion_strength_on_fire",
                    3.0D, 0.0D, 20.0D);

    public EnchantmentLorettaBigBow() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 箭矢生成时增加伤害
     * <p>v2.1：射手视角接入怪物附魔触发开关</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onArrowJoinWorld(@Nonnull EntityJoinWorldEvent evt) {
        if (evt.getWorld().isRemote) {
            return;
        }

        if (!(evt.getEntity() instanceof EntityArrow)) {
            return;
        }
        EntityArrow arrow = (EntityArrow) evt.getEntity();

        // 1.12.2 版：箭的主人是 shootingEntity 字段。区块重新加载时这个字段不存盘、读回来是空的，
        // 所以加载进来的旧箭不会被再乘一次
        if (!(arrow.shootingEntity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase attacker = (EntityLivingBase) arrow.shootingEntity;

        // v2.1：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            heldItem = attacker.getHeldItemOffhand();
        }

        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment lorettaBigBow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLorettaBigBow.class);
        if (lorettaBigBow == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(lorettaBigBow, heldItem);

        level = applyLevelLimit(lorettaBigBow, level, LEVEL_CAP.getInt());

        if (level > 0) {
            arrow.setDamage(arrow.getDamage() * ARROW_DAMAGE_MULTIPLIER.get());
        }
    }

    /**
     * 箭矢命中时爆炸
     * <p>v2.1：射手视角接入怪物附魔触发开关</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        EntityArrow arrow = evt.getArrow();
        if (arrow.world.isRemote) {
            return;
        }

        if (!(arrow.shootingEntity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase attacker = (EntityLivingBase) arrow.shootingEntity;

        // v2.1：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            heldItem = attacker.getHeldItemOffhand();
        }

        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment lorettaBigBow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLorettaBigBow.class);
        if (lorettaBigBow == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(lorettaBigBow, heldItem);

        level = applyLevelLimit(lorettaBigBow, level, LEVEL_CAP.getInt());

        if (level <= 0) {
            return;
        }

        float explosionStrength = (float) (arrow.isBurning()
                ? EXPLOSION_STRENGTH_ON_FIRE.get() : EXPLOSION_STRENGTH.get());
        // 1.12.2 版：最后一个参数 false = 不破坏方块（对应 1.20.1 的 ExplosionInteraction.NONE）
        attacker.world.createExplosion(
                attacker,
                arrow.posX,
                arrow.posY,
                arrow.posZ,
                explosionStrength,
                false
        );
        spawnBlastCloud(attacker.world, arrow.posX, arrow.posY, arrow.posZ);
    }

    /**
     * 补一团大爆炸云
     * <p>问题：爆炸不破坏方块，原版客户端对这种爆炸只画一个小的 {@code EXPLOSION_LARGE} 粒子，
     * 强度 2~4 的爆炸看上去只冒一小团烟。</p>
     * <p>做法：服务端在爆炸点再发一个 {@code EXPLOSION_HUGE}（TNT 同款的大爆炸云），只是画面，
     * 伤害、击退、范围都不变。罗蕾塔的绝招也调这里。</p>
     */
    static void spawnBlastCloud(World level, double x, double y, double z) {
        if (level instanceof WorldServer) {
            WorldServer serverLevel = (WorldServer) level;
            serverLevel.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (25 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
