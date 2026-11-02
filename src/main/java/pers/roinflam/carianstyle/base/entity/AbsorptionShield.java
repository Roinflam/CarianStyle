package pers.roinflam.carianstyle.base.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import pers.roinflam.carianstyle.event.LivingTickEvent;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 本模组给的吸收护盾（圣律、圣域、火焰的疗愈啊……）一律从这里给
 *
 * <h3>问题</h3>
 * <p>
 * 1.21.1 的 {@code LivingEntity.setAbsorptionAmount} 是 final，会把值夹到 {@code [0, getMaxAbsorption()]}，
 * 上限读属性 {@code generic.max_absorption}，默认 0：原版的伤害吸收药水就是靠给这个属性加修饰符才有盾的。
 * 1.20.1 只判 {@code < 0}。照搬 1.20.1 的 {@code setAbsorptionAmount(x)}，盾全是 0，而且不报错。
 * 另外属性变小时原版会在 {@code refreshDirtyAttributes} 里把吸收值夹到新上限。
 * </p>
 *
 * <h3>做法</h3>
 * <p>
 * 本模组在这个属性上挂一个永久修饰符 {@link #ID}（加法），数值始终等于「吸收值超出其它来源上限的部分」：
 * </p>
 * <ul>
 *   <li>{@link #grant}：把吸收值设成调用方照 1.20.1 原式算好的新值（例如 {@code getAbsorptionAmount() + x}），
 *       先把修饰符设成 {@code max(0, 新值 − 其它来源的上限)}，再 {@code setAbsorptionAmount}；</li>
 *   <li>{@link #afterAbsorb}：伤害阶段扣完吸收之后、以及带这个修饰符的实体每 tick 一次，把修饰符缩回
 *       {@code max(0, 当前吸收 − 其它来源的上限)}，为 0 就移除；</li>
 *   <li>「其它来源的上限」= 去掉本模组修饰符后的属性值，在同一个调用里摘下、取值、按新数值加回
 *       （原版只在 {@code refreshDirtyAttributes} 时按上限夹吸收值，中间不会被夹）。</li>
 * </ul>
 * <p>
 * 这样金苹果到期时原版移除它那份上限，吸收值被夹到「当前 − 金苹果那份」，和 1.20.1「减掉 4×(等级+1)」一样；
 * 只有本模组那份已经被打光、吸收值低于原版上限时，按 1.21.1 原版的夹法（夹到新上限）。
 * </p>
 * <p>
 * 修饰符是永久的，随实体存档；吸收值本身也存档，读档时直接写字段不夹。1.20.1 存下的盾（那时没有这个属性），
 * 实体进世界时按同样的式子补一个修饰符，不会第一下就被夹没。
 * </p>
 * <p>
 * 只在服务端调。读吸收值的地方不用改。
 * </p>
 */
@EventBusSubscriber(modid = Reference.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class AbsorptionShield {

    /** 本模组的吸收上限修饰符 */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "absorption_shield");

    /** 吸收值和上限比较时容忍的误差（属性按 double 算、吸收值是 float） */
    private static final float EPSILON = 1.0E-4F;

    private AbsorptionShield() {
    }

    /**
     * 把实体的吸收值设成 {@code newAbsorption}，需要时抬高本模组那份上限。
     *
     * @param entity        实体（服务端）
     * @param newAbsorption 新的吸收值（照 1.20.1 原式算，不用管上限）
     */
    public static void grant(@Nonnull LivingEntity entity, float newAbsorption) {
        AttributeInstance attribute = entity.getAttribute(Attributes.MAX_ABSORPTION);
        if (attribute != null) {
            setExtra(attribute, Math.max(0.0D, (double) newAbsorption - capOther(attribute)));
        }
        entity.setAbsorptionAmount(newAbsorption);
    }

    /**
     * 吸收值变少之后把本模组那份上限缩回「当前吸收 − 其它来源的上限」，为 0 就移除。没有这个修饰符的实体什么都不做。
     *
     * @param entity 实体
     */
    public static void afterAbsorb(@Nonnull LivingEntity entity) {
        if (entity.level().isClientSide) {
            return;
        }
        AttributeInstance attribute = entity.getAttribute(Attributes.MAX_ABSORPTION);
        if (attribute == null || attribute.getModifier(ID) == null) {
            return;
        }
        double extra = Math.max(0.0D, (double) entity.getAbsorptionAmount() - capOther(attribute));
        setExtra(attribute, extra);
    }

    /**
     * 同 {@link #afterAbsorb}，给别处（吸收值被别的途径改小时）手动调。
     */
    public static void refresh(@Nonnull LivingEntity entity) {
        afterAbsorb(entity);
    }

    /**
     * 去掉本模组修饰符后的上限。会把本模组修饰符摘下来：调用方紧接着用 {@link #setExtra} 按新数值加回。
     */
    private static double capOther(AttributeInstance attribute) {
        attribute.removeModifier(ID);
        return attribute.getValue();
    }

    private static void setExtra(AttributeInstance attribute, double extra) {
        attribute.removeModifier(ID);
        if (extra > 0.0D) {
            attribute.addPermanentModifier(new AttributeModifier(ID, extra, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /**
     * 带本模组修饰符的实体每 tick 对一次账（金苹果到期、别的途径扣了吸收之后把多出来的上限收回）。
     * 没有修饰符的实体两次哈希查找就返回。
     */
    @SubscribeEvent
    public static void onLivingTick(LivingTickEvent event) {
        afterAbsorb(event.getEntity());
    }

    /**
     * 旧存档：1.20.1 存下的盾超过 1.21.1 的上限时补一个修饰符，保住吸收值。
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        float absorption = entity.getAbsorptionAmount();
        if (absorption > entity.getMaxAbsorption() + EPSILON) {
            grant(entity, absorption);
        }
    }
}
