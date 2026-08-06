package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.RandomUtils;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleBurstParticles;

import java.util.List;

/**
 * 古龙雷电附魔
 * <p>
 * 死亡时触发：
 * - 对60格范围内的敌人降下闪电
 * - 根据天气加成伤害倍率
 * - 持续攻击直到敌人死亡
 * </p>
 * <p>
 * 修复记录 v2.1：
 * - 天气倍率修正：原代码先乘isRaining再乘isThundering，
 *   MC中雷暴时isRaining()也返回true，导致倍率变成1*2*4=8而不是4。
 *   改为互斥判断：雷暴4x，下雨2x，晴天1x（与PreciseLightning一致）
 * </p>
 * <p>
 * 修改记录 v2.2（龙雷红色闪电）：
 * - 视觉改红：还原艾尔登法环「龙雷」的红色雷击意象，原版蓝白 {@code LightningBolt} 替换为
 *   {@link CarianStyleBurstParticles#redLightning} 自绘的红色之字闪电柱（含分叉 + 落地红色冲击）。
 * - 原版闪电采用 {@code setVisualOnly(true)}，本就只有「视觉 + 音效」无副作用，去掉后需手动补雷声，
 *   故新增 {@code LIGHTNING_BOLT_THUNDER}（雷鸣）+ {@code LIGHTNING_BOLT_IMPACT}（落地）两条音效。
 * - 伤害 / 击退 / 天气倍率等机制逻辑完全未动，仅替换闪电的视觉与配套音效。
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "ancient_dragon_lightning",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST}
)
public class EnchantmentAncientDragonLightning extends EnchantmentBase {

    public EnchantmentAncientDragonLightning() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @Override
    protected void onDeath(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity hurter = ctx.getHolder();

        if (EnchantmentDataManager.isOnCooldown("ancient_dragon_lightning", hurter.getUUID())) {
            return;
        }

        EnchantmentDataManager.setCooldown("ancient_dragon_lightning", hurter.getUUID(), 1800);

        List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                hurter,
                60,
                15,
                entityLivingBase -> !entityLivingBase.equals(hurter)
        );

        List<Integer> list = RandomUtil.randomList(level * 100, entities.size());

        for (int i = 0; i < entities.size(); i++) {
            LivingEntity entityLivingBase = entities.get(i);
            int timeLightning = Math.min(list.get(i), level * 15);

            new SynchronizationTask(40, 5) {
                private int time = 0;

                @Override
                public void run() {
                    if (++time > timeLightning) {
                        this.cancel();
                        return;
                    }

                    Level world = entityLivingBase.level();
                    if (world instanceof ServerLevel serverLevel) {
                        double lx = entityLivingBase.getX();
                        double ly = entityLivingBase.getY();
                        double lz = entityLivingBase.getZ();

                        // v2.2：原版蓝白闪电替换为红色自绘闪电（古龙龙雷意象）
                        CarianStyleBurstParticles.redLightning(serverLevel, lx, ly, lz);

                        // 原版闪电去掉后需手动补雷声：雷鸣 + 落地，音高带轻微随机，避免机械重复
                        serverLevel.playSound(null, lx, ly, lz,
                                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                                0.8f, 0.9f + serverLevel.random.nextFloat() * 0.2f);
                        serverLevel.playSound(null, lx, ly, lz,
                                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER,
                                0.5f, 1.0f + serverLevel.random.nextFloat() * 0.2f);
                    }

                    if (!entityLivingBase.isAlive()) {
                        this.cancel();
                        return;
                    }

                    entityLivingBase.invulnerableTime = 10;

                    // v2.1修复：改为互斥判断，先判断雷暴再判断下雨
                    // MC中isThundering()为true时isRaining()也为true
                    // 原代码累乘导致雷暴=8x，修正后雷暴=4x、下雨=2x、晴天=1x
                    int magnification = 1;
                    if (entityLivingBase.level().isThundering()) {
                        magnification = 4;
                    } else if (entityLivingBase.level().isRaining()) {
                        magnification = 2;
                    }

                    entityLivingBase.hurt(
                            entityLivingBase.damageSources().lightningBolt(),
                            entityLivingBase.getHealth() * 0.05f + entityLivingBase.getMaxHealth() * 0.005f * magnification
                    );

                    if (entityLivingBase.onGround()) {
                        double x = RandomUtils.nextBoolean() ?
                                hurter.getX() - entityLivingBase.getX() :
                                entityLivingBase.getX() - hurter.getX();
                        double z = RandomUtils.nextBoolean() ?
                                hurter.getZ() - entityLivingBase.getZ() :
                                entityLivingBase.getZ() - hurter.getZ();
                        entityLivingBase.knockback(0.2f, x, z);
                    }
                }
            }.start();
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((36 + (enchantmentLevel - 1) * 20) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
