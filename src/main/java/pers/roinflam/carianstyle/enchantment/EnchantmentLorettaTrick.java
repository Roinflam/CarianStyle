package pers.roinflam.carianstyle.enchantment;

import pers.roinflam.carianstyle.utils.Reference;
import net.minecraft.util.EnumHand;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.enchantment.EnchantmentHelper;
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
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

/**
 * 洛蕾塔戏法附魔
 * <p>v2.2：ProjectileImpact射手视角入口接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "loretta_trick",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.BOW,
        slots = {EntityEquipmentSlot.MAINHAND},
        conflictsWith = {EnchantmentLorettaBigBow.class},
        forceTreasure = true
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentLorettaTrick extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.loretta_trick.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "loretta_trick";

    /**
     * 箭矢基础伤害的削减比例
     * <p>默认 0.25，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle ARROW_DAMAGE_PENALTY =
            EnchantmentValues.define(VALUE_ID, "arrow_damage_penalty",
                    0.25D, 0.0D, 1.0D);

    /**
     * 爆炸强度
     * <p>默认 3.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle EXPLOSION_STRENGTH =
            EnchantmentValues.define(VALUE_ID, "explosion_strength",
                    3.0D, 0.0D, 20.0D);

    /**
     * 火箭矢的爆炸强度
     * <p>默认 4.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle EXPLOSION_STRENGTH_ON_FIRE =
            EnchantmentValues.define(VALUE_ID, "explosion_strength_on_fire",
                    4.0D, 0.0D, 20.0D);

    public EnchantmentLorettaTrick() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        EntityArrow arrow = evt.getArrow();
        if (arrow.world.isRemote) return;
        // 1.12.2 版：箭的主人是 shootingEntity 字段
        if (!(arrow.shootingEntity instanceof EntityLivingBase)) return;
        EntityLivingBase attacker = (EntityLivingBase) arrow.shootingEntity;

        // v2.2：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getHeldItem(EnumHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        Enchantment lorettaTrick = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLorettaTrick.class);
        if (lorettaTrick == null) return;
        int level = EnchantmentHelper.getEnchantmentLevel(lorettaTrick, heldItem);
        level = applyLevelLimit(lorettaTrick, level, 10);
        if (level <= 0) return;

        arrow.setDamage(arrow.getDamage()
                - arrow.getDamage() * ARROW_DAMAGE_PENALTY.get());
        float explosionStrength = (float) (arrow.isBurning()
                ? EXPLOSION_STRENGTH_ON_FIRE.get() : EXPLOSION_STRENGTH.get());
        new SynchronizationTask(1, 5) {
            private int time = 0;

            @Override
            public void run() {
                if (++time > 4) {
                    this.cancel();
                    return;
                }
                double offsetX = -2.5 + RandomUtil.getInt(0, 5);
                double offsetZ = -2.5 + RandomUtil.getInt(0, 5);
                // 1.12.2 版：最后一个参数 false = 不破坏方块（对应 1.20.1 的 ExplosionInteraction.NONE）
                attacker.world.createExplosion(attacker, arrow.posX + offsetX, arrow.posY, arrow.posZ + offsetZ,
                        explosionStrength, false);
                // 不破坏方块的爆炸原版只画一小团烟，补一团大爆炸云（见罗蕾塔的大弓）
                EnchantmentLorettaBigBow.spawnBlastCloud(attacker.world, arrow.posX + offsetX, arrow.posY, arrow.posZ + offsetZ);
            }
        }.start();
    }

    @Override
    public int getMinCost(int l) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
