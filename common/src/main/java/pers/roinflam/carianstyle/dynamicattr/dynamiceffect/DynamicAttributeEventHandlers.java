package pers.roinflam.carianstyle.dynamicattr.dynamiceffect;

import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.bus.EventBusSubscriber;
import pers.roinflam.carianstyle.bus.EventPriority;
import pers.roinflam.carianstyle.bus.SubscribeEvent;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.event.LivingChangeTargetEvent;
import pers.roinflam.carianstyle.event.LivingHealEvent;

/**
 * 嚎叫、癫火、隐身三个动态属性的事件监听。
 *
 * <h3>为什么是静态监听</h3>
 * <p>
 * 以前每个实体挂上效果时，给它单独建一个处理器对象登记到总线上，效果没了再摘掉；处理器里先比
 * 「是不是绑定的那个实体」，再查这个实体身上有没有效果。动态属性按实体 UUID 存，一个实体同一种效果只有一份，
 * 处理器和效果同生同灭，所以「有处理器」和「查得到效果」总是一起成立。
 * 现在改成常驻的静态监听，直接查事件里那个实体身上有没有效果：没有就马上返回，不改事件；有就照原样处理。
 * 结果和以前一样，还省掉了运行期频繁登记 / 摘除监听的开销。
 * 唯一的区别：在一次治疗 / 换目标事件分发的途中才给实体上效果，以前要到下一次事件才生效，现在这一次就生效；
 * 施加这三种效果的地方都不在这两种事件里，实际走不到。
 * </p>
 *
 * <h3>同档先后</h3>
 * <p>
 * 运行期挂上的监听总排在同档静态监听之后。三个监听都设最大的 {@code order}，保持排在本模组同档监听的最后：
 * 治疗的两个乘法要连同档里加上去的部分（翠绿乌龟）一起打折；隐身的取消要在同档别的监听（睡眠）看过之后再做，
 * 不然它们因为事件已被取消而不再执行。
 * 嚎叫和癫火同时在一个实体上时，两个乘法以前按上效果的先后，现在固定癫火在前、嚎叫在后（同类按方法名排），
 * 浮点结果最多差最后一位。
 * </p>
 */
@EventBusSubscriber
public final class DynamicAttributeEventHandlers {

    private DynamicAttributeEventHandlers() {
    }

    /**
     * 癫火：受到的治疗减少 90%。
     */
    @SubscribeEvent(priority = EventPriority.LOW, order = Integer.MAX_VALUE)
    public static void onEpilepsyFireHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        if (DynamicAttributeManager.has(entity, DynamicAttributes.EPILEPSY_FIRE_BURNING)) {
            event.setAmount(event.getAmount() * 0.1f);
        }
    }

    /**
     * 沙布里里的嚎叫：受到的治疗减少 10% ×（等级 + 1）。
     */
    @SubscribeEvent(priority = EventPriority.LOW, order = Integer.MAX_VALUE)
    public static void onHowlShabririHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        int level = DynamicAttributeManager.getAmplifier(entity, DynamicAttributes.HOWL_SHABRIRI);
        if (level < 0) return;

        float reduction = (level + 1) * 0.1f;
        event.setAmount(event.getAmount() * (1 - reduction));
    }

    /**
     * 隐身：生物不能把隐身的实体设为目标。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, order = Integer.MAX_VALUE)
    public static void onStealthChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null) return;

        if (DynamicAttributeManager.has(target, DynamicAttributes.STEALTH)) {
            event.setCanceled(true);
        }
    }
}
