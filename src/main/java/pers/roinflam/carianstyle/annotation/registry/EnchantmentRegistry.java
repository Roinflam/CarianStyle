package pers.roinflam.carianstyle.annotation.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * 附魔注册管理器
 * 使用 ModFileScanData 进行注解扫描
 *
 * <h3>v3.0：1.21.1 起只建行为对象，不注册</h3>
 * <p>
 * 1.21.1 的附魔注册表是数据包注册表，条目由内置数据包按注解生成（代码注册的口子没有了）。
 * 这里照旧扫 {@link AutoRegisterEnchantment}、实例化附魔类，但只维护三张表：
 * id → 行为对象、注册表键 → 行为对象、类 → 行为对象。物品上的附魔（注册表 Holder）反查行为对象走
 * {@link #byHolder} / {@link #byKey}。
 * </p>
 * <p>
 * 键表用 {@link IdentityHashMap}：{@code ResourceKey.create} 会驻留，同一个 id 永远是同一个对象。
 * 表在模组构造阶段建好以后只读，不需要并发保护。
 * </p>
 *
 * <h3>uninstallEnchantment 的 id 校验</h3>
 * <p>
 * 原来在主类的通用设置阶段拿 Forge 的附魔注册表逐条核对。1.21.1 的附魔注册表要等世界加载才有，改成：
 * 写的是本模组附魔的，在通用设置阶段（配置已经读完）按这里的 id 表核对；写的是别的命名空间的，
 * 等服务器启动后按服务器的注册表核对，各打一条警告（这一项只对本模组的附魔生效）。
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@EventBusSubscriber(modid = Reference.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class EnchantmentRegistry {

    private static final Map<String, EnchantmentBase> BY_ID = new HashMap<>();
    private static final Map<ResourceKey<Enchantment>, EnchantmentBase> BY_KEY = new IdentityHashMap<>();
    private static final Map<Class<?>, EnchantmentBase> CLASS_TO_INSTANCE = new HashMap<>();
    private static final List<EnchantmentBase> ALL = new ArrayList<>();

    /**
     * 使用 ModFileScanData 扫描并建立所有附魔的行为对象
     *
     * @param packageName 要扫描的包名（实际上这里会扫描整个mod）
     */
    public static void scanAndRegister(@NotNull String packageName) {
        LogUtil.info("卡利亚式附魔 - 开始扫描包：%s", packageName);
        long startTime = System.currentTimeMillis();

        try {
            // 获取ModFileScanData
            ModFileScanData scanData = ModList.get()
                    .getModFileById(Reference.MOD_ID)
                    .getFile()
                    .getScanResult();

            // 获取所有带有AutoRegisterEnchantment注解的类
            Type annotationType = Type.getType(AutoRegisterEnchantment.class);
            List<ModFileScanData.AnnotationData> annotations = scanData.getAnnotations().stream()
                    .filter(a -> annotationType.equals(a.annotationType()))
                    .toList();

            int scannedCount = annotations.size();
            int registeredCount = 0;

            LogUtil.debug("卡利亚式附魔 - 找到 %d 个带注解的类", scannedCount);

            for (ModFileScanData.AnnotationData annotationData : annotations) {
                try {
                    // 获取类名
                    String className = annotationData.memberName();
                    LogUtil.debug("卡利亚式附魔 - 处理类：%s", className);

                    // 加载类
                    Class<?> clazz = Class.forName(className);

                    // 检查是否是EnchantmentBase的子类
                    if (!EnchantmentBase.class.isAssignableFrom(clazz)) {
                        LogUtil.warn("卡利亚式附魔 - 类 %s 不是EnchantmentBase的子类", className);
                        continue;
                    }

                    // 检查是否是抽象类或接口
                    if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                        LogUtil.warn("卡利亚式附魔 - 类 %s 是抽象类或接口", className);
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    Class<? extends EnchantmentBase> enchantmentClass = (Class<? extends EnchantmentBase>) clazz;

                    if (registerSingle(enchantmentClass)) {
                        registeredCount++;
                    }

                } catch (ClassNotFoundException e) {
                    LogUtil.error("卡利亚式附魔 - 无法加载类：%s", e, annotationData.memberName());
                } catch (Exception e) {
                    LogUtil.error("卡利亚式附魔 - 处理类时出错：%s", e, annotationData.memberName());
                }
            }

            long endTime = System.currentTimeMillis();
            LogUtil.info("卡利亚式附魔 - 扫描完成，扫描了 %d 个类，注册了 %d 个附魔，耗时 %d 毫秒",
                    scannedCount, registeredCount, endTime - startTime);

        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 扫描过程发生错误", e);
            throw new RuntimeException("附魔自动注册失败", e);
        }
    }

    /**
     * 建立单个附魔类的行为对象
     *
     * @param enchantmentClass 附魔类
     * @return 是否成功
     */
    private static boolean registerSingle(@NotNull Class<? extends EnchantmentBase> enchantmentClass) {
        try {
            AutoRegisterEnchantment annotation = enchantmentClass.getAnnotation(AutoRegisterEnchantment.class);
            if (annotation == null) {
                LogUtil.warn("卡利亚式附魔 - 类 %s 没有AutoRegisterEnchantment注解", enchantmentClass.getSimpleName());
                return false;
            }

            LogUtil.debug("卡利亚式附魔 - 正在注册：%s", annotation.id());

            if (BY_ID.containsKey(annotation.id())) {
                LogUtil.error("卡利亚式附魔 - 附魔 id 重复：%s（%s 和 %s）", annotation.id(),
                        BY_ID.get(annotation.id()).getClass().getSimpleName(), enchantmentClass.getSimpleName());
                return false;
            }

            EnchantmentBase enchantment = createEnchantmentInstance(enchantmentClass);

            if (enchantment.getTarget() != annotation.type()) {
                LogUtil.warn("卡利亚式附魔 - %s 注解的 type（%s）和构造器给的类别（%s）不一致，以构造器为准",
                        annotation.id(), annotation.type(), enchantment.getTarget());
            }

            BY_ID.put(annotation.id(), enchantment);
            BY_KEY.put(enchantment.getKey(), enchantment);
            CLASS_TO_INSTANCE.put(enchantmentClass, enchantment);
            ALL.add(enchantment);

            // 添加到分类列表
            addToCategory(annotation, enchantment);

            LogUtil.debug("卡利亚式附魔 - %s 注册成功", annotation.id());

            return true;

        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 注册失败：%s", e, enchantmentClass.getSimpleName());
            return false;
        }
    }

    /**
     * 创建附魔实例（无参构造器；类别和槽位由附魔类自己的构造器传给 super）
     */
    private static EnchantmentBase createEnchantmentInstance(
            @NotNull Class<? extends EnchantmentBase> enchantmentClass) throws Exception {
        try {
            Constructor<? extends EnchantmentBase> constructor = enchantmentClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            EnchantmentBase enchantment = constructor.newInstance();
            LogUtil.debug("卡利亚式附魔 - 使用无参构造函数创建：%s", enchantmentClass.getSimpleName());
            return enchantment;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    String.format("附魔类 %s 必须提供无参构造函数", enchantmentClass.getSimpleName()));
        }
    }

    /**
     * 将附魔添加到对应分类列表
     */
    private static void addToCategory(@NotNull AutoRegisterEnchantment annotation, @NotNull EnchantmentBase enchantment) {
        switch (annotation.category()) {
            case COMBAT_SKILL:
                CarianStyleEnchantments.COMBAT_SKILL.add(enchantment);
                break;
            case RECOLLECT:
                CarianStyleEnchantments.RECOLLECT.add(enchantment);
                break;
            case LAW:
                CarianStyleEnchantments.LAW.add(enchantment);
                break;
            case DEAD:
                CarianStyleEnchantments.DEAD.add(enchantment);
                break;
            case GENERAL:
                break;
        }
    }

    // ==================== 查询 ====================

    /**
     * 通过附魔 id 找行为对象
     * <p>
     * 写不写命名空间都行（{@code prayerful_strike} 或 {@code carianstyle:prayerful_strike}）；
     * 别的命名空间、不认识的 id 返回 null。
     * </p>
     *
     * @param id 附魔 id
     * @return 行为对象，不存在则返回 null
     */
    @Nullable
    public static EnchantmentBase byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        String prefix = Reference.MOD_ID + ":";
        if (id.startsWith(prefix)) {
            id = id.substring(prefix.length());
        } else if (id.indexOf(':') >= 0) {
            return null;
        }
        return BY_ID.get(id);
    }

    /**
     * 通过注册表键找行为对象（物品上的附魔 → 行为对象）
     */
    @Nullable
    public static EnchantmentBase byKey(@Nullable ResourceKey<Enchantment> key) {
        if (key == null) {
            return null;
        }
        EnchantmentBase base = BY_KEY.get(key);
        if (base == null && Reference.MOD_ID.equals(key.location().getNamespace())) {
            // 驻留的键按身份就能找到；万一来的是别处构造的同名键，退回按 id 找
            base = BY_ID.get(key.location().getPath());
        }
        return base;
    }

    /**
     * 通过注册表 Holder 找行为对象；不是本模组的附魔、或 Holder 没有键时返回 null
     */
    @Nullable
    public static EnchantmentBase byHolder(@Nullable Holder<Enchantment> holder) {
        if (holder == null) {
            return null;
        }
        return holder.unwrapKey().map(EnchantmentRegistry::byKey).orElse(null);
    }

    /**
     * 通过附魔 id 获取附魔实例（旧名字，同 {@link #byId}）
     */
    @Nullable
    public static EnchantmentBase getEnchantment(@NotNull String id) {
        return byId(id);
    }

    /**
     * 通过附魔类获取附魔实例
     *
     * @param clazz 附魔类
     * @return 附魔实例，不存在则返回null
     */
    @Nullable
    public static EnchantmentBase getEnchantmentByClass(@NotNull Class<? extends EnchantmentBase> clazz) {
        return CLASS_TO_INSTANCE.get(clazz);
    }

    /**
     * 获取所有已注册的附魔ID
     *
     * @return 不可修改的附魔ID集合
     */
    @NotNull
    public static Set<String> getAllEnchantmentIds() {
        return Collections.unmodifiableSet(BY_ID.keySet());
    }

    /**
     * 全部附魔行为对象，按扫描顺序（内置数据包生成、图鉴用）
     */
    @NotNull
    public static List<EnchantmentBase> all() {
        return Collections.unmodifiableList(ALL);
    }

    /**
     * 附魔行为对象个数
     */
    public static int size() {
        return ALL.size();
    }

    // ==================== uninstallEnchantment 的 id 校验 ====================

    /**
     * 对不上任何本模组附魔的条目不会报错，只会悄悄不生效（例如填了显示名、拼错了 id），逐条点名。
     *
     * @param checkForeign true：只核对写了别的命名空间的条目（服务器启动后调）；false：只核对其余的
     */
    private static void checkUninstallList(@Nullable Registry<Enchantment> registry, boolean checkForeign) {
        String[] list = ConfigLoader.uninstallEnchantment;
        if (list == null || list.length == 0) {
            return;
        }
        for (String entry : list) {
            if (entry == null) {
                continue;
            }
            boolean known = false;
            for (String id : BY_ID.keySet()) {
                if (ConfigLoader.entryNamesEnchantment(entry, id)) {
                    known = true;
                    break;
                }
            }
            if (known) {
                continue;
            }
            ResourceLocation foreign = foreignId(entry);
            if (foreign == null) {
                if (!checkForeign) {
                    LogUtil.warn("卡利亚式附魔 - uninstallEnchantment 中的「%s」没有对应的附魔，这一项不会生效（应填附魔 id，例如 prayerful_strike）", entry);
                }
                continue;
            }
            if (!checkForeign) {
                continue;
            }
            if (registry != null && registry.containsKey(foreign)) {
                LogUtil.warn("卡利亚式附魔 - uninstallEnchantment 中的「%s」是别的模组的附魔，这个选项只管本模组的附魔，这一项不会生效", entry);
            } else {
                LogUtil.warn("卡利亚式附魔 - uninstallEnchantment 中的「%s」没有对应的附魔，这一项不会生效（应填附魔 id，例如 prayerful_strike）", entry);
            }
        }
    }

    /**
     * 写了别的命名空间（含冒号、且不是本模组）的条目解析成 id；其余返回 null
     */
    @Nullable
    private static ResourceLocation foreignId(String entry) {
        String s = entry.trim();
        int colon = s.indexOf(':');
        if (colon <= 0 || s.indexOf(',') >= 0 || s.indexOf('，') >= 0) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(s.toLowerCase(Locale.ROOT));
        if (rl == null || Reference.MOD_ID.equals(rl.getNamespace())) {
            return null;
        }
        return rl;
    }

    /**
     * 服务器启动后核对写了别的命名空间的条目（附魔注册表这时才有）
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            checkUninstallList(event.getServer().registryAccess().registryOrThrow(Registries.ENCHANTMENT), true);
        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 核对 uninstallEnchantment 时出错", e);
        }
    }

    /**
     * 通用设置阶段（配置已读完）核对写本模组附魔的条目
     */
    @EventBusSubscriber(modid = Reference.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class SetupCheck {
        private SetupCheck() {
        }

        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent event) {
            checkUninstallList(null, false);
        }
    }
}
