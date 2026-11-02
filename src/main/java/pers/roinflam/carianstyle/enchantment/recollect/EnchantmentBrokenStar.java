package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E2)
 *
 * 碎星附魔
 * <p>v2.2：双向监听器入口接入怪物附魔触发开关</p>
 *
 * <h3>视觉反馈由 HUD 承担，不做世界特效</h3>
 * <p>
 * 本附魔是<b>以半血为界的两档持续状态</b>，还要再叠一层昼夜。这种「我现在在哪一档、
 * 倍率是多少」的信息只有常驻显示才有用，攻击瞬间闪一下解决不了任何问题；
 * 而且减伤档是防御性的，画在世界里等于向对手广播自己已经残血。
 * </p>
 * <p>
 * 因此改由 {@code CarianStyleCombatStateDisplay} 拆成「碎星·攻势」「碎星·守势」两行显示，
 * 两者互斥，数字直接就是当前的增伤 / 减伤百分比。
 * </p>
 *
 * <h3>v2.3 同时修复三处与语言文件描述不符的地方</h3>
 * <ol>
 *     <li><b>守势昼夜倒置</b>：原为 {@code !isDay() ? 0.75f : 0.5f}，
 *         即夜晚只减伤 25%、白天减伤 50%——与「夜晚翻倍」的描述完全相反，
 *         也与攻击者分支 {@code !isDay() ? 2 : 1.5f} 的昼夜方向自相矛盾。
 *         现改为白天 ×0.75、夜晚 ×0.5。</li>
 *     <li><b>守势缺少等级上限</b>：攻击者分支有 {@code ConfigLoader.levelLimit} 判断，
 *         受击者分支没有。当前减伤系数不随等级变化所以暂时不影响结果，
 *         但这个不一致迟早会出问题。</li>
 *     <li><b>半血时两档同时生效</b>：原来攻击者用 {@code >=}、受击者也用 {@code <=}，
 *         生命值恰好 50% 时攻守两档一起触发。现受击者改为严格 {@code <}，
 *         两档严格互补、无重叠也无空档。
 *         <b>注意这与语言文件的「＞50% / ＜50%」措辞略有出入</b>——
 *         按字面写会让恰好半血时两档都不生效，留出一个死区；
 *         这里选择保留攻势的 {@code >=}，要严格照描述的话，
 *         把攻击者分支的 {@code >=} 改成 {@code >} 即可。</li>
 * </ol>
 *
 * @version 2.3
 */
@Deprecated
public class EnchantmentBrokenStar extends EnchantmentBase {
    private static final String VALUE_ID = "broken_star";
    private static final EnchantmentValues.Handle HEALTH_THRESHOLD = EnchantmentValues.define(VALUE_ID, "health_threshold",
                    0.5D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle ATTACK_MULTIPLIER_NIGHT = EnchantmentValues.define(VALUE_ID, "attack_multiplier_night",
                    2.0D, 0.0D, 10.0D);
    private static final EnchantmentValues.Handle ATTACK_MULTIPLIER_DAY = EnchantmentValues.define(VALUE_ID, "attack_multiplier_day",
                    1.5D, 0.0D, 10.0D);
    private static final EnchantmentValues.Handle DEFENSE_MULTIPLIER_NIGHT = EnchantmentValues.define(VALUE_ID, "defense_multiplier_night",
                    0.5D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle DEFENSE_MULTIPLIER_DAY = EnchantmentValues.define(VALUE_ID, "defense_multiplier_day",
                    0.75D, 0.0D, 2.0D);
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public int getMinCost(int l) {
        return 0;
    }
    public int getMaxCost(int l) {
        return 0;
    }
}

