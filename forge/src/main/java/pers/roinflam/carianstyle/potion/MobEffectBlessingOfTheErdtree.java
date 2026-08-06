package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 黄金树祝福药水效果
 * <p>
 * 效果：
 * - 最大生命值+15%×等级
 * - 每3秒回复最大生命值×4%×(等级+1)
 * </p>
 *
 * <h3>v1.1 修复：客户端重复治疗导致的血量不同步</h3>
 * <p>
 * <b>问题：</b>{@link #applyEffectTick} 此前缺少客户端守卫。原版
 * {@code LivingEntity.tickEffects()} 在<b>逻辑服务端与逻辑客户端两侧都会执行</b>
 * （单人游戏亦然——单机是「一个进程内同时跑客户端与内置服务端」），
 * 因此 {@code heal()} 每 3 秒在客户端也会被调用一次。
 * </p>
 * <p>
 * <b>更麻烦的是两侧治疗量并不相等。</b>{@code LivingEntity.heal()} 会触发
 * {@code LivingHealEvent}，而本模组几乎所有治疗量修正的监听器
 * （{@code MobEffectIncision} 的 +60%、{@code MobEffectBadOmen} 的 -50%、
 * {@code MobEffectScarletRot} 的 -25% 等）开头都有 {@code isClientSide} 守卫、
 * 在客户端会直接返回。于是：
 * <ul>
 *     <li>服务端治疗量 = 基础值 × 各类修正系数；</li>
 *     <li>客户端治疗量 = 基础值（<b>未经任何修正</b>）。</li>
 * </ul>
 * 两侧算出的血量不同，客户端血条会先跳到一个错误数值、再被服务端的同步包拉回，
 * 表现为血条闪跳。同时挂着切腹（+60% 治疗）或噩兆（-50% 治疗）时偏差最明显。
 * </p>
 * <p>
 * <b>不会崩溃</b>，与 {@link MobEffectIncision} / {@link MobEffectHemorrhage} 的
 * NPE 崩溃不同——{@code heal()} 不会走到 {@code Player.die()} 那条路径。
 * </p>
 * <p>
 * <b>修复方式：</b>在方法开头加客户端守卫。治疗自此只在服务端结算一次，
 * 血量经原版同步机制下发给客户端，血条不再闪跳。
 * </p>
 *
 * @author RoinFlam
 * @version 1.1
 */
public class MobEffectBlessingOfTheErdtree extends IconBase {

    public MobEffectBlessingOfTheErdtree(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        this.addAttributeModifier(
                Attributes.MAX_HEALTH,
                "c407bffa-97df-adf8-51db-5681fdef4b8c",
                0.15,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 60 == 0;
    }

    /**
     * 每 3 秒回复最大生命值×4%×(等级+1)。
     * <p>
     * <b>v1.1：仅在逻辑服务端执行。</b>原版会在双端各调用一次本方法，
     * 而客户端因各类治疗修正监听器被守卫拦下、算出的治疗量与服务端不一致，
     * 会造成血条闪跳（详见类注释的「v1.1 修复」小节）。
     * </p>
     *
     * @param entityLivingBaseIn 受影响的实体
     * @param amplifier          效果等级
     */
    @Override
    public void applyEffectTick(@Nonnull LivingEntity entityLivingBaseIn, int amplifier) {
        // ⭐ v1.1 客户端守卫：治疗只在逻辑服务端结算一次，
        // 血量经原版同步机制下发给客户端，避免两侧治疗量不一致导致血条闪跳。
        if (entityLivingBaseIn.level().isClientSide) {
            return;
        }

        entityLivingBaseIn.heal(entityLivingBaseIn.getMaxHealth() * (amplifier + 1) * 0.04f);
    }

    @Nonnull
    @Override
    @OnlyIn(Dist.CLIENT)
    protected ResourceLocation getIconTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/mob_effect/blessing_of_the_erdtree.png");
    }
}
