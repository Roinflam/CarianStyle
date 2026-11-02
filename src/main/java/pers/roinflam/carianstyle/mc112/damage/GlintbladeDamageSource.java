package pers.roinflam.carianstyle.mc112.damage;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 辉剑（{@code EntityGlintblades}）的投掷伤害源
 * <p>
 * 1.20.1 是 {@code damageSources().thrown(null, owner)}：直接实体为空、来源是释放者。
 * 1.12.2 这里直接实体仍然填剑本身——别的模组常在 {@code instanceof EntityDamageSource} 之后直接取实体，
 * 返回空会让它们空指针；原版投掷伤害的死亡信息也要两个实体都在。
 * </p>
 * <p>
 * 但直接实体不为空会带出和 1.20.1 不一样的结果，这里逐个对回去：
 * </p>
 * <ul>
 *   <li>伤害位置返回空：1.20.1 直接实体为空时 {@code getSourcePosition()} 为空，盾牌挡不住；
 *       1.12.2 的 {@code canBlockDamageSource} 在位置为空时同样判不挡。</li>
 *   <li>难度缩放看真正来源：1.20.1 的 {@code thrown} 是 {@code when_caused_by_living_non_player}，
 *       按来源实体判断；原版 {@code EntityDamageSource} 看的是直接实体（剑），永远不缩放。</li>
 *   <li>「直接实体是不是投射物 / 是不是为空」这类判断由调用处把直接实体是辉剑的当成没有直接实体（精准雷击、学者之盾）。</li>
 * </ul>
 * <p>
 * 弹射物标记、死亡信息键 {@code thrown}、饥饿消耗 0.1 和原版 {@code causeThrownDamage} 一样。
 * </p>
 */
public class GlintbladeDamageSource extends EntityDamageSourceIndirect {

    /**
     * @param blade 辉剑本身
     * @param owner 释放者；为空时取剑
     */
    public GlintbladeDamageSource(@Nonnull Entity blade, @Nullable Entity owner) {
        super("thrown", blade, owner != null ? owner : blade);
        setProjectile();
    }

    @Nullable
    @Override
    public Vec3d getDamageLocation() {
        return null;
    }

    @Override
    public boolean isDifficultyScaled() {
        Entity owner = getTrueSource();
        return owner instanceof EntityLivingBase && !(owner instanceof EntityPlayer);
    }
}
