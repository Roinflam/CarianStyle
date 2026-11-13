package pers.roinflam.carianstyle.base.entity;

import net.minecraft.nbt.CompoundTag;
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
 * 本模组在这个属性上挂一个永久修饰符 {@link #ID}（加法），记的是「本模组给的那份盾」，只在吸收值低于它时才缩：
 * </p>
 * <ul>
 *   <li>{@link #grant}：把吸收值设成调用方照 1.20.1 原式算好的新值（例如 {@code getAbsorptionAmount() + x}），
 *       先把修饰符设成 {@code max(新值 − 其它来源的上限, min(旧修饰符, 新值))}，再 {@code setAbsorptionAmount}；</li>
 *   <li>{@link #afterAbsorb}：伤害阶段扣完吸收之后、以及带这个修饰符的实体每 tick 一次，把修饰符缩成
 *       {@code min(旧修饰符, 当前吸收)}，为 0 就移除；</li>
 *   <li>「其它来源的上限」= 去掉本模组修饰符后的属性值，在同一个调用里摘下、取值、按新数值加回
 *       （原版只在 {@code refreshDirtyAttributes} 时按上限夹吸收值，中间不会被夹）。</li>
 * </ul>
 *
 * <h3>为什么不按「吸收值 − 其它来源的上限」每 tick 重算</h3>
 * <p>
 * 1.21.1 的吸收药水开始时是 {@code max(当前吸收, 4×(等级+1))}，不再往上叠，但上限照样 +4。
 * 如果每 tick 把修饰符重算成「吸收 − 其它上限」，已经有 20 点盾的实体喝了吸收 I，修饰符会被算成 16；
 * 药水到期原版扣掉那 4 点上限，盾就从 20 变成 16，喝药反而亏 4 点，看起来像 bug（1.20.1 是先 +4 再 −4，还是 20）。
 * 所以修饰符只跟着吸收值往下缩，别的来源的上限变化不动它。
 * </p>
 * <p>
 * 已知差别：先吃金苹果、再叠本模组的盾、盾被打掉一部分、然后金苹果到期——1.20.1 是「当前 − 4×(等级+1)」，
 * 这里吸收值只被夹到「本模组那份」，最多比 1.20.1 多留 4×(等级+1) 点。偏向玩家，实际玩不出来，接受。
 * 金苹果先吃、盾没挨打就到期的常见情况和 1.20.1 一样（剩本模组给的那份）。
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

    /** 实体持久数据里的记号：进世界时的旧档吸收值已经核过，见 {@link #onEntityJoinLevel} */
    static final String LEGACY_CHECKED = Reference.MOD_ID + ":absorption_legacy_checked";

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
            // 已有的那份盾不因这次设值变少（除非新值本身更低），超出其它上限的部分照加
            double kept = Math.min(currentExtra(attribute), newAbsorption);
            setExtra(attribute, Math.max(0.0D, Math.max((double) newAbsorption - capOther(attribute), kept)));
        }
        entity.setAbsorptionAmount(newAbsorption);
    }

    /**
     * 吸收值变少之后把本模组那份上限缩成「min(旧值, 当前吸收)」，为 0 就移除。没有这个修饰符的实体什么都不做。
     * 只往下缩，其它来源的上限变了也不动（见类注释）。
     *
     * @param entity 实体
     */
    public static void afterAbsorb(@Nonnull LivingEntity entity) {
        if (entity.level().isClientSide) {
            return;
        }
        // 先只查不建：getAttribute 在实体还没有这份属性实例时会当场建一个，每只怪都会多一条存进存档、发给客户端
        if (!entity.getAttributes().hasModifier(Attributes.MAX_ABSORPTION, ID)) {
            return;
        }
        AttributeInstance attribute = entity.getAttribute(Attributes.MAX_ABSORPTION);
        if (attribute == null) {
            return;
        }
        AttributeModifier modifier = attribute.getModifier(ID);
        if (modifier == null) {
            return;
        }
        double absorption = Math.max(0.0D, entity.getAbsorptionAmount());
        if (absorption < modifier.amount()) {
            setExtra(attribute, absorption);
        }
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

    /** 本模组修饰符现在的数值，没有为 0 */
    private static double currentExtra(AttributeInstance attribute) {
        AttributeModifier modifier = attribute.getModifier(ID);
        return modifier == null ? 0.0D : modifier.amount();
    }

    private static void setExtra(AttributeInstance attribute, double extra) {
        attribute.removeModifier(ID);
        if (extra > 0.0D) {
            attribute.addPermanentModifier(new AttributeModifier(ID, extra, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /**
     * 带本模组修饰符的实体每 tick 对一次账（金苹果到期、别的途径扣了吸收之后把多出来的上限收回）。
     * 没有修饰符的实体只查一次属性表就返回，不会给它建吸收上限的属性实例。
     */
    @SubscribeEvent
    public static void onLivingTick(LivingTickEvent event) {
        afterAbsorb(event.getEntity());
    }

    /**
     * 旧存档：1.20.1 存下的盾超过 1.21.1 的上限时补一个修饰符，保住吸收值。每个实体一辈子只核一次。
     * <p>
     * <b>问题</b>：进世界那一刻装备、饰品给的吸收上限还没套上（原版装备属性要第一个 tick 才加），
     * 别的来源合法给到的吸收值这时也显得「超上限」。每次进世界都补的话，配合别的模组「把吸收回满到上限」，
     * 重进一次上限就涨一截，可以无限刷。
     * </p>
     * <p>
     * <b>为什么这么改</b>：只有 1.20.1 存下的实体才需要补，它们第一次在 1.21.1 进世界时身上没有核过的记号；
     * 核过一次（不管补没补）就在实体的持久数据里记下，之后进世界不再补。新生成的实体第一次进世界就记上。
     * </p>
     * <p>
     * <b>行为影响</b>：旧档的盾照样保住；之后重进不会再把吸收值转成上限。
     * </p>
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        if (data.getBoolean(LEGACY_CHECKED)) {
            return;
        }
        data.putBoolean(LEGACY_CHECKED, true);
        float absorption = entity.getAbsorptionAmount();
        if (absorption > entity.getMaxAbsorption() + EPSILON) {
            grant(entity, absorption);
        }
    }
}
