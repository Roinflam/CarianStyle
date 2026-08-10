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
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;

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
 * <h3>天气倍率必须互斥判断</h3>
 * <p>
 * MC 中雷暴时 {@code isRaining()} <b>也返回 true</b>。
 * 早期版本先乘 isRaining 再乘 isThundering，倍率变成 {@code 1*2*4=8} 而非预期的 4。
 * 现改为互斥：雷暴 4x、下雨 2x、晴天 1x（与 PreciseLightning 一致）。
 * </p>
 *
 * <h3>红色龙雷：去掉原版闪电后必须自己补雷声</h3>
 * <p>
 * 为还原艾尔登法环「龙雷」的红色雷击意象，原版蓝白 {@code LightningBolt}
 * 已替换为 {@link CarianStyleEffects#redLightning}（自绘红色之字电柱 + 分叉 + 落地红色冲击）。
 * </p>
 * <p>
 * 原版闪电用的是 {@code setVisualOnly(true)}，本就只有「视觉 + 音效」无副作用，
 * 但<b>音效随它一起没了</b>。因此下方两条 {@code playSound}
 * （{@code LIGHTNING_BOLT_THUNDER} 雷鸣 + {@code LIGHTNING_BOLT_IMPACT} 落地）
 * <b>必须保留</b>，否则只有画面没有声音。
 * </p>
 *
 * <h3>为什么高频落雷不会「鬼畜」</h3>
 * <p>
 * 本附魔对同一目标每 5 tick 重复降雷。若每次都新建特效实例，
 * 同一处会同时叠着多道形态各异的闪电并不断新生，视觉上呈高频跳变。
 * </p>
 * <p>
 * 客户端 {@code AoeEffectManager} 对 {@code TYPE_RED_LIGHTNING} 做了 2.5 格内的
 * <b>同位置合并</b>——重复落雷只续命已存在那道、不新建，且其外形种子保持不变，
 * 因此表现为一道<b>持续劈着、缓慢明灭</b>的雷。这是红闪独有的处理，
 * 其余演出类型不参与合并（详见 {@code refreshNearbyRedLightning}）。
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
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

    /**
     * 死亡触发：对范围内敌人持续降下红色龙雷。
     *
     * @param ctx   附魔上下文
     * @param level 附魔等级
     */
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

                        // ⭐ 原版蓝白闪电替换为红色自绘闪电（古龙龙雷意象）。
                        // 客户端会对 2.5 格内的重复落雷做同位置合并，
                        // 因此高频重复调用不会叠成一团「鬼畜」，而是一道持续劈着的雷。
                        CarianStyleEffects.redLightning(serverLevel, lx, ly, lz);

                        // 原版闪电去掉后需手动补雷声：雷鸣 + 落地，
                        // 音高带轻微随机，避免高频重复时听感机械
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

                    // 互斥判断，先判雷暴再判下雨。
                    // MC 中 isThundering() 为 true 时 isRaining() 也为 true，
                    // 累乘会导致雷暴变成 8x；互斥后雷暴 4x、下雨 2x、晴天 1x
                    int magnification = 1;
                    if (entityLivingBase.level().isThundering()) {
                        magnification = 4;
                    } else if (entityLivingBase.level().isRaining()) {
                        magnification = 2;
                    }

                    entityLivingBase.hurt(
                            entityLivingBase.damageSources().lightningBolt(),
                            entityLivingBase.getHealth() * 0.05f
                                    + entityLivingBase.getMaxHealth() * 0.005f * magnification
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
