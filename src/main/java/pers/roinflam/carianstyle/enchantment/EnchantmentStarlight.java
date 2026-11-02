package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E4)
 * <p>星光附魔 - 优化: LivingTickEvent -&gt; PlayerTickEvent：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "starlight", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnumEnchantmentType.ARMOR, slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET})
public class EnchantmentStarlight extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "starlight";

    /**
     * 护甲扫描缓存的刷新间隔（tick）；调大更省性能，代价是换装后生效更慢
     * <p>默认 20，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle RESCAN_INTERVAL =
            EnchantmentValues.define(VALUE_ID, "rescan_interval",
                    20, 1, 200);

    /**
     * 每级星光提供的亮度
     * <p>默认 3，允许范围 1 ~ 15。</p>
     */
    private static final EnchantmentValues.Handle BRIGHTNESS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "brightness_per_level",
                    3, 1, 15);

    /**
     * 亮度上限
     * <p>默认 15，允许范围 1 ~ 15。</p>
     */
    private static final EnchantmentValues.Handle MAX_BRIGHTNESS =
            EnchantmentValues.define(VALUE_ID, "max_brightness",
                    15, 1, 15);

    /**
     * 放置光源方块时使用的更新标志。
     * <p>{@code UPDATE_CLIENTS} 让客户端看见光；{@code UPDATE_KNOWN_SHAPE} 跳过形状传播。
     * 刻意<b>不含</b> {@code UPDATE_NEIGHBORS}，理由见类注释。</p>
     */
    private static final int LIGHT_BLOCK_FLAGS = 2 | 16 /* 1.20.1：Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE */;

    /**
     * 玩家装备扫描缓存。
     * <p>
     * 用 {@link ConcurrentHashMap} 而非普通 HashMap：本 Map 在服务端 tick 线程写入、
     * 在玩家登出事件里移除，两者在专用服务器上同属主线程，但 Mohist 这类混合端
     * 存在插件从其它线程触发登出事件的可能，用并发容器成本极低而更稳妥。
     * </p>
     */
    private static final Map<UUID, Object> SCAN_CACHE = new ConcurrentHashMap<>();

    public EnchantmentStarlight() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET});
    }

    @Override public int getMinCost(int l) { return (int)((23 + (l - 1) * 9) * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
