package pers.roinflam.carianstyle.annotation.registry;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * 附魔注册管理器
 * 使用Forge的ModFileScanData进行注解扫描
 *
 * @author RoinFlam
 * @version 2.0
 */
public class EnchantmentRegistry {

    private static final Map<String, Enchantment> REGISTERED_ENCHANTMENTS = new HashMap<>();
    private static final Map<Class<?>, Enchantment> CLASS_TO_INSTANCE = new HashMap<>();
    private static final List<EnchantmentRegistration> ALL_REGISTRATIONS = new ArrayList<>();

    /**
     * 使用Forge的ModFileScanData扫描并注册所有附魔
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
     * 注册单个附魔类
     *
     * @param enchantmentClass 附魔类
     * @return 是否注册成功
     */
    private static boolean registerSingle(@NotNull Class<? extends EnchantmentBase> enchantmentClass) {
        try {
            AutoRegisterEnchantment annotation = enchantmentClass.getAnnotation(AutoRegisterEnchantment.class);
            if (annotation == null) {
                LogUtil.warn("卡利亚式附魔 - 类 %s 没有AutoRegisterEnchantment注解", enchantmentClass.getSimpleName());
                return false;
            }

            LogUtil.debug("卡利亚式附魔 - 正在注册：%s", annotation.id());

            // ⭐ 解析 EnchantmentCategory
            EnchantmentCategory category = resolveEnchantmentCategory(annotation);

            // ⭐ 使用正确的 EnchantmentCategory 创建附魔实例
            Enchantment enchantment = createEnchantmentInstance(enchantmentClass, annotation, category);

            // 通过 DeferredRegister 注册到游戏
            CarianStyleEnchantments.registerEnchantment(annotation.id(), enchantment);

            // 存储到本地缓存（用于查询）
            REGISTERED_ENCHANTMENTS.put(annotation.id(), enchantment);
            CLASS_TO_INSTANCE.put(enchantmentClass, enchantment);

            // 创建注册信息
            EnchantmentRegistration registration = new EnchantmentRegistration(enchantmentClass, enchantment, annotation);
            ALL_REGISTRATIONS.add(registration);

            // 添加到分类列表
            addToCategory(registration);

            LogUtil.debug("卡利亚式附魔 - %s 注册成功", annotation.id());

            return true;

        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 注册失败：%s", e, enchantmentClass.getSimpleName());
            return false;
        }
    }

    /**
     * 解析注解中的 EnchantmentCategory
     * <p>
     * 优先使用 customType，如果为空则使用 type
     * </p>
     *
     * @param annotation 附魔注解
     * @return 解析后的 EnchantmentCategory
     * @throws IllegalArgumentException 如果 customType 无效
     */
    private static EnchantmentCategory resolveEnchantmentCategory(@NotNull AutoRegisterEnchantment annotation) {
        String customType = annotation.customType();

        // 情况1：使用了自定义类型（customType 不为空）
        if (customType != null && !customType.isEmpty()) {
            EnchantmentCategory category = CarianStyleEnchantments.getCustomEnchantmentCategory(customType);

            if (category == null) {
                throw new IllegalArgumentException(
                        String.format("未知的自定义附魔类型: %s (附魔ID: %s)。" +
                                        "可用类型: SHIELD, ARMS, PICKAXE",
                                customType, annotation.id())
                );
            }

            LogUtil.debug("卡利亚式附魔 - 使用自定义类型：%s -> %s", customType, category);
            return category;
        }

        // 情况2：使用原版类型（customType 为空）
        EnchantmentCategory category = annotation.type();
        LogUtil.debug("卡利亚式附魔 - 使用原版类型：%s", category);
        return category;
    }

    /**
     * 创建附魔实例
     * <p>
     * 尝试多种构造函数签名以兼容不同的附魔类实现
     * </p>
     *
     * @param enchantmentClass 附魔类
     * @param annotation 附魔注解
     * @param category 附魔类型
     * @return 附魔实例
     * @throws Exception 如果创建失败
     */
    private static Enchantment createEnchantmentInstance(
            @NotNull Class<? extends EnchantmentBase> enchantmentClass,
            @NotNull AutoRegisterEnchantment annotation,
            @NotNull EnchantmentCategory category
    ) throws Exception {

        // 尝试1：无参构造函数（最常见）
        try {
            Constructor<? extends EnchantmentBase> constructor = enchantmentClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Enchantment enchantment = constructor.newInstance();

            // 如果附魔类支持后期设置 category，这里可以调用
            // 目前使用构造函数中硬编码的方式，所以这里不做处理
            LogUtil.debug("卡利亚式附魔 - 使用无参构造函数创建：%s", enchantmentClass.getSimpleName());

            return enchantment;

        } catch (NoSuchMethodException e) {
            // 没有无参构造函数，尝试其他方式
            LogUtil.debug("卡利亚式附魔 - 类 %s 没有无参构造函数，尝试其他构造函数", enchantmentClass.getSimpleName());
        }

        // 尝试2：(EnchantmentCategory, EquipmentSlot[]) 构造函数
        try {
            Constructor<? extends EnchantmentBase> constructor = enchantmentClass.getDeclaredConstructor(
                    EnchantmentCategory.class,
                    net.minecraft.world.entity.EquipmentSlot[].class
            );
            constructor.setAccessible(true);

            Enchantment enchantment = constructor.newInstance(category, annotation.slots());
            LogUtil.debug("卡利亚式附魔 - 使用 (EnchantmentCategory, EquipmentSlot[]) 构造函数创建：%s",
                    enchantmentClass.getSimpleName());

            return enchantment;

        } catch (NoSuchMethodException e) {
            // 没有找到合适的构造函数
            throw new IllegalStateException(
                    String.format("附魔类 %s 必须提供以下构造函数之一：\n" +
                                    "1. 无参构造函数\n" +
                                    "2. (EnchantmentCategory, EquipmentSlot[]) 构造函数",
                            enchantmentClass.getSimpleName())
            );
        }
    }

    /**
     * 将附魔添加到对应分类列表
     *
     * @param registration 附魔注册信息
     */
    private static void addToCategory(@NotNull EnchantmentRegistration registration) {
        pers.roinflam.carianstyle.annotation.EnchantmentCategory category = registration.annotation.category();
        Enchantment enchantment = registration.enchantment;

        switch (category) {
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

    /**
     * 通过附魔ID获取附魔实例
     *
     * @param id 附魔ID
     * @return 附魔实例，不存在则返回null
     */
    @Nullable
    public static Enchantment getEnchantment(@NotNull String id) {
        return REGISTERED_ENCHANTMENTS.get(id);
    }

    /**
     * 通过附魔类获取附魔实例
     *
     * @param clazz 附魔类
     * @return 附魔实例，不存在则返回null
     */
    @Nullable
    public static Enchantment getEnchantmentByClass(@NotNull Class<? extends EnchantmentBase> clazz) {
        return CLASS_TO_INSTANCE.get(clazz);
    }

    /**
     * 获取所有已注册的附魔ID
     *
     * @return 不可修改的附魔ID集合
     */
    @NotNull
    public static Set<String> getAllEnchantmentIds() {
        return Collections.unmodifiableSet(REGISTERED_ENCHANTMENTS.keySet());
    }

    /**
     * 附魔注册信息
     */
    private static class EnchantmentRegistration {
        final Class<?> enchantmentClass;
        final Enchantment enchantment;
        final AutoRegisterEnchantment annotation;

        EnchantmentRegistration(Class<?> enchantmentClass, Enchantment enchantment, AutoRegisterEnchantment annotation) {
            this.enchantmentClass = enchantmentClass;
            this.enchantment = enchantment;
            this.annotation = annotation;
        }
    }
}