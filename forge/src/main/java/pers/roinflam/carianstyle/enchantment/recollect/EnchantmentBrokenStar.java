package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

/**
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
 *         这里选择保留攻势的 {@code >=}，若你更想要严格照描述，
 *         把攻击者分支的 {@code >=} 改成 {@code >} 即可。</li>
 * </ol>
 *
 * @version 2.3
 */
@AutoRegisterEnchantment(id = "broken_star", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber
public class EnchantmentBrokenStar extends EnchantmentBase {
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public EnchantmentBrokenStar() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @SubscribeEvent
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        Enchantment brokenStar = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBrokenStar.class);
        if (brokenStar == null) return;

        // 攻击者视角
        if (evt.getSource().getDirectEntity() instanceof LivingEntity attacker) {
            // ⭐ v2.2：怪物附魔触发开关（攻击者视角）
            if (!EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) {
                ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
                if (!heldItem.isEmpty()) {
                    int level = EnchantmentHelper.getItemEnchantmentLevel(brokenStar, heldItem);
                    if (ConfigLoader.levelLimit) level = Math.min(level, 10);
                    if (level > 0 && attacker.getHealth() >= attacker.getMaxHealth() / 2) {
                        evt.setAmount(evt.getAmount() * (!attacker.level().isDay() ? 2 : 1.5f));
                    }
                }
            }
        }

        // 受击者视角
        if (!evt.getSource().isCreativePlayer()) {
            LivingEntity victim = evt.getEntity();

            // ⭐ v2.2：怪物附魔触发开关（受击者视角）
            if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

            ItemStack heldItem = victim.getItemInHand(InteractionHand.MAIN_HAND);
            if (!heldItem.isEmpty()) {
                int level = EnchantmentHelper.getItemEnchantmentLevel(brokenStar, heldItem);
                // ⭐ v2.3 修复 B：补上等级上限，与攻击者分支保持一致。
                // 当前减伤系数不随等级变化，所以这一行暂时不影响结果，
                // 但缺了它意味着以后一旦把减伤做成随等级递增，就会绕过 levelLimit
                if (ConfigLoader.levelLimit) level = Math.min(level, 10);
                // ⭐ v2.3 修复 C：改用严格小于，与攻击者分支的 >= 互补。
                // 原来两边都含等号，生命值恰好半血时攻守两档会同时生效
                if (level > 0 && victim.getHealth() < victim.getMaxHealth() / 2) {
                    // ⭐ v2.3 修复 A：昼夜互换。
                    // 原为 (!isDay ? 0.75f : 0.5f) —— 夜晚只减伤 25%、白天减伤 50%，
                    // 与语言文件「夜晚翻倍」的描述正好相反，且与攻击者分支
                    // (!isDay ? 2 : 1.5f) 的昼夜方向自相矛盾。
                    // 现为白天 ×0.75（减伤 25%）、夜晚 ×0.5（减伤 50%），夜晚确实翻倍
                    evt.setAmount(evt.getAmount() * (!victim.level().isDay() ? 0.5f : 0.75f));
                }
            }
        }
    }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
