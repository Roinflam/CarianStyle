package pers.roinflam.carianstyle.annotation.registry;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.*;

/**
 * 附魔注册管理器
 * 用注解扫描结果找出全部附魔类并注册
 * <p>
 * 1.12.2 版：扫描结果用 FML 在 preInit 给的 {@link ASMDataTable}（1.20.1 是 ModFileScanData），
 * 所以 {@link #scanAndRegister} 多一个参数。找到的类按类名排序后再实例化，保证每次启动注册顺序一致
 * （新存档里附魔的数字 id 按注册顺序分配）。
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
public class EnchantmentRegistry {

    private static final Map<String, Enchantment> REGISTERED_ENCHANTMENTS = new HashMap<>();
    private static final Map<Class<?>, Enchantment> CLASS_TO_INSTANCE = new HashMap<>();
    private static final List<EnchantmentRegistration> ALL_REGISTRATIONS = new ArrayList<>();

    /**
     * 扫描并注册所有附魔
     *
     * @param asm         FML 在 preInit 给的注解扫描结果（{@code event.getAsmData()}）
     * @param packageName 只登记这个包（含子包）下的类
     */
    public static void scanAndRegister(@Nonnull ASMDataTable asm, @Nonnull String packageName) {
        LogUtil.info("卡利亚式附魔 - 开始扫描包：%s", packageName);
        long startTime = System.currentTimeMillis();

        try {
            // 获取所有带有AutoRegisterEnchantment注解的类（按类名排序，保证注册顺序固定）
            String prefix = packageName + ".";
            List<String> classNames = new ArrayList<>();
            for (ASMDataTable.ASMData data : asm.getAll(AutoRegisterEnchantment.class.getName())) {
                String className = data.getClassName();
                if (className.startsWith(prefix) && !classNames.contains(className)) {
                    classNames.add(className);
                }
            }
            Collections.sort(classNames);

            int scannedCount = classNames.size();
            int registeredCount = 0;

            LogUtil.debug("卡利亚式附魔 - 找到 %d 个带注解的类", scannedCount);

            for (String className : classNames) {
                try {
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
                    LogUtil.error("卡利亚式附魔 - 无法加载类：%s", e, className);
                } catch (Exception e) {
                    LogUtil.error("卡利亚式附魔 - 处理类时出错：%s", e, className);
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
    private static boolean registerSingle(@Nonnull Class<? extends EnchantmentBase> enchantmentClass) {
        try {
            AutoRegisterEnchantment annotation = enchantmentClass.getAnnotation(AutoRegisterEnchantment.class);
            if (annotation == null) {
                LogUtil.warn("卡利亚式附魔 - 类 %s 没有AutoRegisterEnchantment注解", enchantmentClass.getSimpleName());
                return false;
            }

            LogUtil.debug("卡利亚式附魔 - 正在注册：%s", annotation.id());

            // 解析附魔类型
            EnumEnchantmentType category = resolveEnchantmentCategory(annotation);

            // 使用正确的附魔类型创建附魔实例
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
     * 解析注解中的附魔类型
     * <p>
     * 优先使用 customType，如果为空则使用 type
     * </p>
     *
     * @param annotation 附魔注解
     * @return 解析后的附魔类型
     * @throws IllegalArgumentException 如果 customType 无效
     */
    private static EnumEnchantmentType resolveEnchantmentCategory(@Nonnull AutoRegisterEnchantment annotation) {
        String customType = annotation.customType();

        // 情况1：使用了自定义类型（customType 不为空）
        if (customType != null && !customType.isEmpty()) {
            EnumEnchantmentType category = CarianStyleEnchantments.getCustomEnchantmentCategory(customType);

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
        EnumEnchantmentType category = annotation.type();
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
            @Nonnull Class<? extends EnchantmentBase> enchantmentClass,
            @Nonnull AutoRegisterEnchantment annotation,
            @Nonnull EnumEnchantmentType category
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

        // 尝试2：(EnumEnchantmentType, EntityEquipmentSlot[]) 构造函数
        try {
            Constructor<? extends EnchantmentBase> constructor = enchantmentClass.getDeclaredConstructor(
                    EnumEnchantmentType.class,
                    EntityEquipmentSlot[].class
            );
            constructor.setAccessible(true);

            Enchantment enchantment = constructor.newInstance(category, annotation.slots());
            LogUtil.debug("卡利亚式附魔 - 使用 (EnumEnchantmentType, EntityEquipmentSlot[]) 构造函数创建：%s",
                    enchantmentClass.getSimpleName());

            return enchantment;

        } catch (NoSuchMethodException e) {
            // 没有找到合适的构造函数
            throw new IllegalStateException(
                    String.format("附魔类 %s 必须提供以下构造函数之一：\n" +
                                    "1. 无参构造函数\n" +
                                    "2. (EnumEnchantmentType, EntityEquipmentSlot[]) 构造函数",
                            enchantmentClass.getSimpleName())
            );
        }
    }

    /**
     * 将附魔添加到对应分类列表
     *
     * @param registration 附魔注册信息
     */
    private static void addToCategory(@Nonnull EnchantmentRegistration registration) {
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
    public static Enchantment getEnchantment(@Nonnull String id) {
        return REGISTERED_ENCHANTMENTS.get(id);
    }

    /**
     * 通过附魔类获取附魔实例
     *
     * @param clazz 附魔类
     * @return 附魔实例，不存在则返回null
     */
    @Nullable
    public static Enchantment getEnchantmentByClass(@Nonnull Class<? extends EnchantmentBase> clazz) {
        return CLASS_TO_INSTANCE.get(clazz);
    }

    /**
     * 获取所有已注册的附魔ID
     *
     * @return 不可修改的附魔ID集合
     */
    @Nonnull
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