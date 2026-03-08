package pers.roinflam.carianstyle.enchantment.registry;

import net.minecraft.enchantment.Enchantment;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * 附魔注册管理器
 * <p>
 * 使用Forge的{@link ASMDataTable}扫描所有带有{@link AutoRegisterEnchantment}注解的类并完成注册，
 * 兼容开发环境和生产环境（jar包）
 * </p>
 */
public class EnchantmentRegistry {

    /** 已注册附魔的ID到实例映射 */
    private static final Map<String, Enchantment> REGISTERED_ENCHANTMENTS = new HashMap<>();

    /** 已注册附魔的类到实例映射 */
    private static final Map<Class<?>, Enchantment> CLASS_TO_INSTANCE = new HashMap<>();

    /** 所有注册信息的列表 */
    private static final List<EnchantmentRegistration> ALL_REGISTRATIONS = new ArrayList<>();

    /**
     * 使用Forge ASMDataTable扫描并注册所有带有@AutoRegisterEnchantment注解的附魔类
     * <p>
     * ASMDataTable由Forge在mod加载阶段构建，能正确扫描LaunchClassLoader下的所有类，
     * 解决了Guava ClassPath在Forge环境下无法扫描jar内类的问题
     * </p>
     *
     * @param asmDataTable Forge提供的ASM数据表，从FMLPreInitializationEvent获取
     */
    public static void scanAndRegister(@Nonnull ASMDataTable asmDataTable) {
        LogUtil.info("卡利亚式附魔 - 开始通过ASMDataTable扫描附魔注解");
        long startTime = System.currentTimeMillis();

        // 获取所有标注了@AutoRegisterEnchantment的类信息
        String annotationName = AutoRegisterEnchantment.class.getName();
        Set<ASMDataTable.ASMData> asmDataSet = asmDataTable.getAll(annotationName);

        int scannedCount = 0;
        int registeredCount = 0;

        for (ASMDataTable.ASMData asmData : asmDataSet) {
            String className = asmData.getClassName();
            scannedCount++;

            try {
                Class<?> clazz = Class.forName(className);

                // 验证是EnchantmentBase的子类
                if (!EnchantmentBase.class.isAssignableFrom(clazz)) {
                    LogUtil.warn("卡利亚式附魔 - %s 带有@AutoRegisterEnchantment但不是EnchantmentBase的子类，跳过",
                            className);
                    continue;
                }

                // 跳过接口和抽象类
                if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Class<? extends EnchantmentBase> enchantmentClass = (Class<? extends EnchantmentBase>) clazz;

                if (registerSingle(enchantmentClass)) {
                    registeredCount++;
                }

            } catch (ClassNotFoundException e) {
                LogUtil.warn("卡利亚式附魔 - 无法加载类：%s", className);
            } catch (Exception e) {
                LogUtil.error("卡利亚式附魔 - 处理类时出错：%s", e, className);
            }
        }

        long endTime = System.currentTimeMillis();
        LogUtil.info("卡利亚式附魔 - 扫描完成，发现 %d 个注解类，注册了 %d 个附魔，耗时 %d 毫秒",
                scannedCount, registeredCount, endTime - startTime);
    }

    /**
     * 注册单个附魔类
     *
     * @param enchantmentClass 附魔类
     * @return 注册成功返回true
     */
    private static boolean registerSingle(@Nonnull Class<? extends EnchantmentBase> enchantmentClass) {
        try {
            AutoRegisterEnchantment annotation = enchantmentClass.getAnnotation(AutoRegisterEnchantment.class);
            if (annotation == null) {
                return false;
            }

            LogUtil.debug("卡利亚式附魔 - 正在注册：%s", annotation.id());

            // 实例化附魔
            Enchantment enchantment = enchantmentClass.newInstance();

            // 添加到内部映射
            REGISTERED_ENCHANTMENTS.put(annotation.id(), enchantment);
            CLASS_TO_INSTANCE.put(enchantmentClass, enchantment);

            // 添加到主附魔列表
            CarianStyleEnchantments.ENCHANTMENTS.add(enchantment);

            // 创建注册信息
            EnchantmentRegistration registration = new EnchantmentRegistration(enchantmentClass, enchantment, annotation);
            ALL_REGISTRATIONS.add(registration);

            // 添加到对应分类
            addToCategory(registration);

            LogUtil.debug("卡利亚式附魔 - %s 注册成功", annotation.id());

            return true;

        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 注册失败：%s", e, enchantmentClass.getSimpleName());
            return false;
        }
    }

    /**
     * 将附魔添加到对应的分类列表
     *
     * @param registration 注册信息
     */
    private static void addToCategory(@Nonnull EnchantmentRegistration registration) {
        EnchantmentCategory category = registration.annotation.category();
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
                // 通用类不加入特定分类列表
                break;
        }
    }

    /**
     * 通过附魔ID获取附魔实例
     *
     * @param id 附魔ID
     * @return 附魔实例，未找到返回null
     */
    @Nullable
    public static Enchantment getEnchantment(@Nonnull String id) {
        return REGISTERED_ENCHANTMENTS.get(id);
    }

    /**
     * 通过附魔类获取附魔实例
     *
     * @param clazz 附魔类
     * @return 附魔实例，未找到返回null
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
     * 附魔注册信息内部类
     */
    private static class EnchantmentRegistration {

        /** 附魔类 */
        final Class<?> enchantmentClass;

        /** 附魔实例 */
        final Enchantment enchantment;

        /** 注解信息 */
        final AutoRegisterEnchantment annotation;

        /**
         * 构造附魔注册信息
         *
         * @param enchantmentClass 附魔类
         * @param enchantment      附魔实例
         * @param annotation       注解信息
         */
        EnchantmentRegistration(Class<?> enchantmentClass, Enchantment enchantment, AutoRegisterEnchantment annotation) {
            this.enchantmentClass = enchantmentClass;
            this.enchantment = enchantment;
            this.annotation = annotation;
        }
    }
}