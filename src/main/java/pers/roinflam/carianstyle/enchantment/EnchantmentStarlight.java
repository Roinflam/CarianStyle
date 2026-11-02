package pers.roinflam.carianstyle.enchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.block.light.HideLight;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.tileentity.MoveLight;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * PORT-STUB(E4)
 *
 * 星光附魔 - 优化: LivingTickEvent -&gt; PlayerTickEvent
 * <p>
 * 亮度 = 全身星光等级之和 × 3，最高 15；光源方块跟着亮度走（{@link HideLight#LEVEL}），
 * 换装后已经放下的光源也会变亮变暗。
 * </p>
 *
 * <h3>v2.2 性能优化：装备扫描缓存 + 方块更新标志</h3>
 *
 * <p><b>优化前的开销。</b>本附魔挂在 {@code PlayerTickEvent} 上，
 * 服务器里<b>每个玩家每 tick</b>都会执行：</p>
 * <ol>
 *   <li>遍历 4 个护甲槽，对每一件调用
 *       {@code EnchantmentHelper.getItemEnchantmentLevel}——
 *       该方法会<b>反序列化物品的附魔 ListTag</b>，是实打实的 NBT 解析；</li>
 *   <li>然后才判断玩家有没有这个附魔。</li>
 * </ol>
 * <p>
 * 顺序反了：<b>绝大多数玩家根本没有星光附魔</b>，却每人每 tick 白付 4 次 NBT 解析。
 * 50 人服务器就是 200 次/tick、4000 次/秒，而其中可能一次都没命中。
 * </p>
 *
 * <p><b>做法。</b>为每个玩家缓存「是否装备了星光」这个布尔值，
 * 每 {@link #RESCAN_INTERVAL} tick（1 秒）才重新扫描一次护甲槽。
 * 没有该附魔的玩家每 tick 的开销降为「一次 Map 查询 + 一次计数器自增」，
 * NBT 解析减少 95%。</p>
 * <p>
 * <b>为什么 1 秒的延迟可以接受：</b>缓存过期后最坏情况是玩家换上带星光的护甲后
 * 最多 1 秒才开始发光、脱下后最多 1 秒才停止。这个效果本身是「走到哪亮到哪」的
 * 环境光，晚一秒亮起完全无法察觉——不像伤害类附魔那样需要即时生效。
 * </p>
 * <p>
 * <b>为什么不用 {@code EnchantmentEventHandler} 里那套装备哈希缓存：</b>
 * 那套缓存服务的是「走模板方法分发」的附魔，本附魔是独立 {@code @SubscribeEvent}，
 * 接不进去。把它们统一收口是后续批次的工作，本次只做局部优化，不动那边的结构。
 * </p>
 *
 * <p><b>另一处改动：{@code setBlock} 的更新标志由 3 改为 {@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE}。</b></p>
 * <p>
 * 标志 3 = {@code UPDATE_NEIGHBORS(1) | UPDATE_CLIENTS(2)}，其中
 * {@code UPDATE_NEIGHBORS} 会通知六个相邻方块「你旁边变了」。
 * 而本方块是个 {@code AirBlock} 子类的纯光源，<b>没有任何方块需要对它作出反应</b>。
 * </p>
 * <p>
 * 更要紧的是：玩家移动时本附魔<b>每 tick 放一个新光源方块</b>，
 * 也就是每秒 20 次向周围广播方块更新。如果玩家从一片红石电路旁边跑过去，
 * 这 20 次/秒的邻居通知会真实地触发红石重算。去掉这个标志后行为不变、副作用消失。
 * </p>
 * <p>
 * 保留 {@code UPDATE_CLIENTS}（客户端必须收到才能看见光），
 * 追加 {@code UPDATE_KNOWN_SHAPE} 跳过形状更新（空气方块没有形状可传播）。
 * </p>
 *
 * <p><b>不能做的优化：整体降频。</b>
 * {@code MoveLight} 的 BlockEntity 在存在超过 1 tick 后会自我移除，
 * 靠的是每 tick 的 {@code retime()} 续命。若把整个方法降频到 N tick 一次，
 * 光源会变成「亮 2 tick、灭 N-2 tick」的闪烁。因此方块侧的逻辑必须保持每 tick 执行，
 * 只能优化它<b>前面</b>那段判定。</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "starlight", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnchantTarget.ARMOR, slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}, baseEnchantability = 23, levelMultiplier = 9)
@Deprecated
public class EnchantmentStarlight extends EnchantmentBase {
    private static final String VALUE_ID = "starlight";
    private static final EnchantmentValues.Handle RESCAN_INTERVAL = EnchantmentValues.define(VALUE_ID, "rescan_interval",
                    20, 1, 200);
    private static final EnchantmentValues.Handle BRIGHTNESS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "brightness_per_level",
                    3, 1, 15);
    private static final EnchantmentValues.Handle MAX_BRIGHTNESS = EnchantmentValues.define(VALUE_ID, "max_brightness",
                    15, 1, 15);
    private static final int LIGHT_BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Map<UUID, ArmorScanCache> SCAN_CACHE = new ConcurrentHashMap<>();
    public EnchantmentStarlight() {
        super(EnchantTarget.ARMOR, new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET});
    }

    private static final class ArmorScanCache {
    }
}

