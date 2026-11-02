package pers.roinflam.carianstyle.mc112.damage;

import net.minecraft.util.DamageSource;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 本模组新造的、不带实体的伤害源
 * <p>
 * 不能继承 {@code EntityDamageSource}：它的 {@code getDamageLocation} 无条件读直接实体，打到举盾的目标就空指针；
 * {@code EntityDamageSourceIndirect.getDeathMessage} 在两个实体都为空时也空指针；
 * 别的模组还常见 {@code instanceof EntityDamageSource} 之后直接取实体。所以没有实体时用这个普通伤害源。
 * </p>
 * <p>
 * 饥饿消耗和难度缩放照 1.20.1 的伤害类型给：1.12.2 的 {@code hungerDamage} 是私有字段，
 * 打开「无视护甲」还会把它清成 0，所以直接覆写 {@link #getHungerDamage()} 返回构造时的值。
 * </p>
 */
public class CarianDamageSource extends DamageSource implements ICarianDamageSource {

    private final String typeId;
    private final Set<String> builtinTags;
    private final float exhaustion;
    private final boolean difficultyScaled;
    private boolean bypassesArmorAtHurt;

    /**
     * @param typeId           伤害类型 id
     * @param msgId            死亡信息键（{@code death.attack.<msgId>}）
     * @param builtinTags      这个类型在 1.20.1 带的标签
     * @param exhaustion       造成伤害时的饥饿消耗
     * @param difficultyScaled 是否按难度缩放（照原样返回；本模组的类型只在来源是非玩家生物时才缩放，没有实体时传 false）
     */
    public CarianDamageSource(@Nonnull String typeId, @Nonnull String msgId, @Nonnull Set<String> builtinTags,
                              float exhaustion, boolean difficultyScaled) {
        super(msgId);
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
        return difficultyScaled;
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
