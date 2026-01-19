package pers.roinflam.carianstyle.annotation.registry;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

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

            // 创建附魔实例
            Enchantment enchantment = enchantmentClass.getDeclaredConstructor().newInstance();

            // ⭐ 关键修改：通过 DeferredRegister 注册到游戏
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
     * 将附魔添加到对应分类列表
     *
     * @param registration 附魔注册信息
     */
    private static void addToCategory(@NotNull EnchantmentRegistration registration) {
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