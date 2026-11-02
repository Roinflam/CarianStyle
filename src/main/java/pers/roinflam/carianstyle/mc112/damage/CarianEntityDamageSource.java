package pers.roinflam.carianstyle.mc112.damage;

import net.minecraft.entity.Entity;
import net.minecraft.util.EntityDamageSourceIndirect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 本模组新造的、带实体的伤害源
 * <p>
 * 继承 {@code EntityDamageSourceIndirect}，直接实体和真正来源分开放（1.20.1 的 {@code DamageSource(type, direct, causing)}）。
 * 只有一个实体时两个都是它。死亡信息照原版间接伤害的写法：{@code death.attack.<msgId>}，参数是死者和来源，
 * 来源手上的物品改过名时用 {@code .item}，和 1.20.1 带实体时的键一致。
 * </p>
 * <p>
 * 难度缩放：原版 {@code EntityDamageSource} 的规则是「来源是非玩家生物就缩放」，这里再和类型自己的标志取与，
 * 所以只有本来就缩放的类型（冻伤）被怪物打出来时才缩放，和 1.20.1 的 {@code scaling} 一致。
 * </p>
 */
public class CarianEntityDamageSource extends EntityDamageSourceIndirect implements ICarianDamageSource {

    private final String typeId;
    private final Set<String> builtinTags;
    private final float exhaustion;
    private final boolean difficultyScaled;
    private boolean bypassesArmorAtHurt;

    /**
     * @param typeId           伤害类型 id
     * @param msgId            死亡信息键
     * @param direct           直接造成伤害的实体，不能为空
     * @param owner            真正的来源；为空时取 {@code direct}
     * @param builtinTags      这个类型在 1.20.1 带的标签
     * @param exhaustion       饥饿消耗
     * @param difficultyScaled 类型本身是否按难度缩放
     */
    public CarianEntityDamageSource(@Nonnull String typeId, @Nonnull String msgId, @Nonnull Entity direct,
                                    @Nullable Entity owner, @Nonnull Set<String> builtinTags,
                                    float exhaustion, boolean difficultyScaled) {
        super(msgId, direct, owner != null ? owner : direct);
        this.typeId = typeId;
        this.builtinTags = Collections.unmodifiableSet(new HashSet<>(builtinTags));
        this.exhaustion = exhaustion;
        this.difficultyScaled = difficultyScaled;
    }

    @Override
    public float getHungerDamage() {
        return exhaustion;
    }

    @Override
    public boolean isDifficultyScaled() {
        return difficultyScaled && super.isDifficultyScaled();
    }

    @Override
    public String typeId() {
        return typeId;
    }

    @Override
    public Set<String> builtinTags() {
        return builtinTags;
    }

    @Override
    public boolean bypassesArmorAtHurt() {
        return bypassesArmorAtHurt;
    }

    @Override
    public void setBypassesArmorAtHurt(boolean value) {
        this.bypassesArmorAtHurt = value;
    }
}
