// 文件：LogUtil.java
// 路径：src/main/java/pers/roinflam/carianstyle/utils/util/LogUtil.java
package pers.roinflam.carianstyle.utils.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 日志工具类
 * <p>
 * 提供双层日志系统：
 * 1. 普通日志（info/warn/error）：总是输出，用于重要信息
 * 2. 详细日志（debug）：由配置文件控制是否输出，用于调试信息
 * </p>
 *
 * 使用示例：
 * <pre>
 * LogUtil.info("模组初始化完成");
 * LogUtil.debug("加载了 %d 个附魔", count);
 * LogUtil.warn("检测到配置项冲突");
 * LogUtil.error("加载资源失败", exception);
 * </pre>
 */
public class LogUtil {

    /**
     * 模组日志记录器
     */
    private static final Logger LOGGER = LogManager.getLogger(Reference.MOD_ID);

    /**
     * 日志前缀
     */
    private static final String PREFIX = "[卡利亚式附魔] ";

    /**
     * 输出普通信息日志（总是输出）
     *
     * @param message 日志消息
     */
    public static void info(@Nonnull String message) {
        LOGGER.info(PREFIX + message);
    }

    /**
     * 输出普通信息日志，带格式化参数（总是输出）
     *
     * @param format 格式化字符串
     * @param args 格式化参数
     */
    public static void info(@Nonnull String format, Object... args) {
        LOGGER.info(PREFIX + String.format(format, args));
    }

    /**
     * 输出警告日志（总是输出）
     *
     * @param message 警告消息
     */
    public static void warn(@Nonnull String message) {
        LOGGER.warn(PREFIX + message);
    }

    /**
     * 输出警告日志，带格式化参数（总是输出）
     *
     * @param format 格式化字符串
     * @param args 格式化参数
     */
    public static void warn(@Nonnull String format, Object... args) {
        LOGGER.warn(PREFIX + String.format(format, args));
    }

    /**
     * 输出错误日志（总是输出）
     *
     * @param message 错误消息
     */
    public static void error(@Nonnull String message) {
        LOGGER.error(PREFIX + message);
    }

    /**
     * 输出错误日志，带异常信息（总是输出）
     *
     * @param message 错误消息
     * @param throwable 异常对象
     */
    public static void error(@Nonnull String message, @Nonnull Throwable throwable) {
        LOGGER.error(PREFIX + message, throwable);
    }

    /**
     * 输出错误日志，带格式化参数（总是输出）
     *
     * @param format 格式化字符串
     * @param args 格式化参数
     */
    public static void error(@Nonnull String format, Object... args) {
        LOGGER.error(PREFIX + String.format(format, args));
    }

    /**
     * 输出详细调试日志（仅在配置开启时输出）
     * <p>
     * 该日志受配置文件中的 enableDetailedLogging 控制
     * </p>
     *
     * @param message 调试消息
     */
    public static void debug(@Nonnull String message) {
        if (ConfigLoader.enableDetailedLogging) {
            LOGGER.info(PREFIX + "[调试] " + message);
        }
    }

    /**
     * 输出详细调试日志，带格式化参数（仅在配置开启时输出）
     *
     * @param format 格式化字符串
     * @param args 格式化参数
     */
    public static void debug(@Nonnull String format, Object... args) {
        if (ConfigLoader.enableDetailedLogging) {
            LOGGER.info(PREFIX + "[调试] " + String.format(format, args));
        }
    }

    /**
     * 输出详细调试日志，带异常信息（仅在配置开启时输出）
     *
     * @param message 调试消息
     * @param throwable 异常对象
     */
    public static void debug(@Nonnull String message, @Nonnull Throwable throwable) {
        if (ConfigLoader.enableDetailedLogging) {
            LOGGER.info(PREFIX + "[调试] " + message, throwable);
        }
    }

    /**
     * 输出分隔线，用于区分不同的日志块
     */
    public static void separator() {
        LOGGER.info("========================================");
    }

}