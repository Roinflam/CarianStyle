package pers.roinflam.carianstyle.visual.effect;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import pers.roinflam.carianstyle.network.AoeEffectPacket;
import pers.roinflam.carianstyle.network.VisualNetwork;

/**
 * 服务端 AOE 特效触发入口（纯自绘版）。
 * <p>
 * <b>不再发射任何原版粒子。</b>本类现在只负责把「在某触发点播放哪种 AOE 特效」这件事，通过
 * {@link VisualNetwork#sendToNearby} 以 {@link AoeEffectPacket} 广播给附近客户端；真正的视觉由
 * 客户端 {@code AoeEffectRenderer} 用纯顶点几何（与光环同款 {@code Tesselator + POSITION_COLOR}
 * 管线）自绘完成——无贴图、无原版粒子。
 * </p>
 * <p>
 * <b>兼容设计：</b>{@link #shockwaveRing} / {@link #burst} 的原方法签名与历史版本完全一致，按调用方
 * 传入的主题粒子自动分发到对应的专属演出类型，因此各附魔触发点<b>无需任何改动</b>——它们传入的
 * {@code ParticleTypes.*} 现在只作为「选哪套自绘演出」的标识，不会真的生成原版粒子。
 * </p>
 * <p>
 * <b>特效半径（v4 放大）：</b>猩红罗妮亚 / 癫火蔓延原先固定 1.5 / 2.0 格，观感偏小、与附魔实际作用
 * 范围（[等级]×N 格）脱节，现放大为 {@link #SCARLET_BLOOM_RADIUS} / {@link #FRENZIED_FLAME_RADIUS}。
 * 同时各提供一个<b>带 radius 的重载</b>（{@link #scarletBloom(ServerLevel, double, double, double, float)}
 * 等）：若希望特效大小随附魔等级变化，附魔触发处改调带 radius 的重载、传入「等级×N」即可；
 * 现有无参调用保持不变、用放大后的默认半径。
 * </p>
 * <p>
 * <b>v6.1（跟随）：</b>为 {@link #burst} / {@link #scarletBloom} / {@link #frenziedFlame} / {@link #send}
 * 各新增一个<b>带 {@link Entity} 参数的重载</b>。传入实体时，特效包携带该实体 id，客户端会让特效每帧
 * 跟随实体的实时位置（用于绑定濒死实体的死亡演出）；不传 / 传 {@code null} 时退化为定点（行为与历史
 * 完全一致）。仅猩红立体花 / 癫火扩散需要跟随，排斥 / 因果律 / 冻结地震仍为定点。
 * </p>
 * <p>
 * 特效包只广播给附近玩家、且每次触发仅发一个轻量包，对崩服敏感的 AOE 触发点安全。
 * 所有方法均应只在<b>服务端</b>调用（调用方自行用 {@code instanceof ServerLevel} 守卫）。
 * </p>
 *
 * @author RoinFlam
 * @version 6.1
 */
public final class CarianStyleBurstParticles {

    /** 特效广播范围（格）：只有该范围内的客户端会收到自绘特效包 */
    private static final double BROADCAST_RANGE = 64.0;

    /** 通用回退默认半径（格） */
    private static final float DEFAULT_RADIUS = 3.0f;

    /** 猩红罗妮亚默认特效半径（格）。原 1.5，放大以匹配观感与作用范围。 */
    private static final float SCARLET_BLOOM_RADIUS = 5.0f;

    /** 癫火蔓延默认特效半径（格）。原 2.0，放大以匹配观感与作用范围。 */
    private static final float FRENZIED_FLAME_RADIUS = 5.0f;

    /** 排斥默认特效半径（格） */
    private static final float REPULSION_RADIUS = 2.4f;

    private CarianStyleBurstParticles() {
    }

    // ============================== 兼容分发层（签名与历史版本一致） ==============================

    /**
     * 环形冲击波兼容入口：按主题粒子分发到对应演出（END_ROD→因果律 / SNOWFLAKE→冻结地震），其余走通用。
     *
     * @param level    服务端世界
     * @param centerX  中心 X
     * @param centerY  中心 Y（特效贴地基准高度）
     * @param centerZ  中心 Z
     * @param radius   半径（格）
     * @param points   历史参数，自绘版忽略（仅作签名兼容）
     * @param particle 主题粒子（作为演出选择依据，不生成原版粒子）
     */
    public static void shockwaveRing(ServerLevel level,
                                     double centerX, double centerY, double centerZ,
                                     double radius, int points, ParticleOptions particle) {
        if (radius <= 0.0) {
            return;
        }
        if (particle == ParticleTypes.SNOWFLAKE) {
            frostQuake(level, centerX, centerY, centerZ, radius);
        } else if (particle == ParticleTypes.END_ROD) {
            causalitySeal(level, centerX, centerY, centerZ, radius);
        } else {
            send(level, centerX, centerY, centerZ, (float) radius, AoeEffectPacket.TYPE_GENERIC);
        }
    }

    /**
     * 团状爆发兼容入口（定点版）：按主题粒子分发到对应演出（CRIMSON_SPORE→猩红 / FLAME→癫火 /
     * CLOUD→排斥），其余走通用。特效锁死在传入坐标，不跟随实体。
     *
     * @param level    服务端世界
     * @param centerX  中心 X
     * @param centerY  中心 Y（特效贴地基准高度）
     * @param centerZ  中心 Z
     * @param count    历史参数，自绘版忽略（仅作签名兼容）
     * @param particle 主题粒子（作为演出选择依据，不生成原版粒子）
     * @param spread   历史参数，自绘版忽略
     * @param speed    历史参数，自绘版忽略
     */
    public static void burst(ServerLevel level,
                             double centerX, double centerY, double centerZ,
                             int count, ParticleOptions particle, double spread, double speed) {
        burst(level, null, centerX, centerY, centerZ, count, particle, spread, speed);
    }

    /**
     * 团状爆发兼容入口（跟随版）：分发逻辑同定点版，但额外接受一个绑定实体。
     * <p>CRIMSON_SPORE → 猩红立体花、FLAME → 癫火扩散会让特效跟随 {@code entity} 的实时位置；
     * CLOUD（排斥）/ 通用回退本身是瞬时定点演出，不跟随（忽略 {@code entity}）。
     * {@code entity} 为 {@code null} 时整体退化为定点。</p>
     *
     * @param level    服务端世界
     * @param entity   绑定实体（特效跟随其实时位置）；{@code null} 表示定点
     * @param centerX  中心 X（实体消失后的回退坐标）
     * @param centerY  中心 Y（特效贴地基准高度）
     * @param centerZ  中心 Z
     * @param count    历史参数，自绘版忽略（仅作签名兼容）
     * @param particle 主题粒子（作为演出选择依据，不生成原版粒子）
     * @param spread   历史参数，自绘版忽略
     * @param speed    历史参数，自绘版忽略
     */
    public static void burst(ServerLevel level, Entity entity,
                             double centerX, double centerY, double centerZ,
                             int count, ParticleOptions particle, double spread, double speed) {
        if (count <= 0) {
            return;
        }
        if (particle == ParticleTypes.CRIMSON_SPORE) {
            scarletBloom(level, entity, centerX, centerY, centerZ);
        } else if (particle == ParticleTypes.FLAME) {
            frenziedFlame(level, entity, centerX, centerY, centerZ);
        } else if (particle == ParticleTypes.CLOUD) {
            // 排斥为瞬时定点演出，不跟随实体
            repulsionWave(level, centerX, centerY, centerZ);
        } else {
            send(level, entity, centerX, centerY, centerZ, DEFAULT_RADIUS, AoeEffectPacket.TYPE_GENERIC);
        }
    }

    // ================================ 各附魔专属演出（广播对应类型）================================

    /**
     * 因果律：地面金紫六芒星法阵 + 因果之线放射。半径取附魔搜索半径。
     */
    public static void causalitySeal(ServerLevel level, double cx, double cy, double cz, double radius) {
        send(level, cx, cy, cz, (float) radius, AoeEffectPacket.TYPE_CAUSALITY);
    }

    /**
     * 冻结地震：放射地裂逐帧外延 + 霜环外滚 + 中心冰花。半径取附魔搜索半径。
     */
    public static void frostQuake(ServerLevel level, double cx, double cy, double cz, double radius) {
        send(level, cx, cy, cz, (float) radius, AoeEffectPacket.TYPE_FROST_QUAKE);
    }

    /**
     * 排斥：双环猛烈外推（短促）。默认半径。
     */
    public static void repulsionWave(ServerLevel level, double cx, double cy, double cz) {
        send(level, cx, cy, cz, REPULSION_RADIUS, AoeEffectPacket.TYPE_REPULSION);
    }

    /**
     * 猩红罗妮亚（定点）：玫瑰曲线五瓣花绽放 -> 炸裂红环。使用放大后的默认半径
     * {@link #SCARLET_BLOOM_RADIUS}。
     */
    public static void scarletBloom(ServerLevel level, double cx, double cy, double cz) {
        scarletBloom(level, cx, cy, cz, SCARLET_BLOOM_RADIUS);
    }

    /**
     * 猩红罗妮亚（定点 + 带半径重载）：若希望特效大小随附魔等级变化，附魔触发处改调本重载、传入「等级×N」即可。
     *
     * @param radius 特效半径（格）
     */
    public static void scarletBloom(ServerLevel level, double cx, double cy, double cz, float radius) {
        send(level, cx, cy, cz, radius, AoeEffectPacket.TYPE_SCARLET_BLOOM);
    }

    /**
     * 猩红罗妮亚（跟随）：特效跟随 {@code entity} 实时位置。使用放大后的默认半径
     * {@link #SCARLET_BLOOM_RADIUS}。{@code entity} 为 {@code null} 时退化为定点。
     *
     * @param entity 绑定实体；{@code null} 表示定点
     */
    public static void scarletBloom(ServerLevel level, Entity entity, double cx, double cy, double cz) {
        scarletBloom(level, entity, cx, cy, cz, SCARLET_BLOOM_RADIUS);
    }

    /**
     * 猩红罗妮亚（跟随 + 带半径重载）。
     *
     * @param entity 绑定实体；{@code null} 表示定点
     * @param radius 特效半径（格）
     */
    public static void scarletBloom(ServerLevel level, Entity entity, double cx, double cy, double cz, float radius) {
        send(level, entity, cx, cy, cz, radius, AoeEffectPacket.TYPE_SCARLET_BLOOM);
    }

    /**
     * 癫火蔓延（定点）：混乱裂纹 + 颤动黄橙焰。使用放大后的默认半径 {@link #FRENZIED_FLAME_RADIUS}。
     */
    public static void frenziedFlame(ServerLevel level, double cx, double cy, double cz) {
        frenziedFlame(level, cx, cy, cz, FRENZIED_FLAME_RADIUS);
    }

    /**
     * 癫火蔓延（定点 + 带半径重载）：若希望特效大小随附魔等级变化，附魔触发处改调本重载、传入「等级×N」即可。
     *
     * @param radius 特效半径（格）
     */
    public static void frenziedFlame(ServerLevel level, double cx, double cy, double cz, float radius) {
        send(level, cx, cy, cz, radius, AoeEffectPacket.TYPE_FRENZIED_FLAME);
    }

    /**
     * 癫火蔓延（跟随）：特效跟随 {@code entity} 实时位置。使用放大后的默认半径
     * {@link #FRENZIED_FLAME_RADIUS}。{@code entity} 为 {@code null} 时退化为定点。
     *
     * @param entity 绑定实体；{@code null} 表示定点
     */
    public static void frenziedFlame(ServerLevel level, Entity entity, double cx, double cy, double cz) {
        frenziedFlame(level, entity, cx, cy, cz, FRENZIED_FLAME_RADIUS);
    }

    /**
     * 癫火蔓延（跟随 + 带半径重载）。
     *
     * @param entity 绑定实体；{@code null} 表示定点
     * @param radius 特效半径（格）
     */
    public static void frenziedFlame(ServerLevel level, Entity entity, double cx, double cy, double cz, float radius) {
        send(level, entity, cx, cy, cz, radius, AoeEffectPacket.TYPE_FRENZIED_FLAME);
    }

    // ================================ 发包辅助 ================================

    /**
     * 向触发点附近广播一个 AOE 自绘特效包（定点版）。委托给跟随版传 {@code null}。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y
     * @param z      中心 Z
     * @param radius 半径（格）
     * @param type   特效类型（见 {@link AoeEffectPacket}）
     */
    private static void send(ServerLevel level, double x, double y, double z, float radius, int type) {
        send(level, null, x, y, z, radius, type);
    }

    /**
     * 向触发点附近广播一个 AOE 自绘特效包（跟随版）。
     * <p>{@code entity} 非空时包携带其实体 id，客户端据此每帧取实体实时位置作为特效中心；
     * {@code entity} 为 {@code null} 时携带 {@link AoeEffectPacket#NO_ENTITY}，特效锁死在 (x,y,z)。</p>
     *
     * @param level  服务端世界
     * @param entity 绑定实体；{@code null} 表示定点
     * @param x      中心 X
     * @param y      中心 Y
     * @param z      中心 Z
     * @param radius 半径（格）
     * @param type   特效类型（见 {@link AoeEffectPacket}）
     */
    private static void send(ServerLevel level, Entity entity, double x, double y, double z, float radius, int type) {
        int entityId = (entity == null) ? AoeEffectPacket.NO_ENTITY : entity.getId();
        VisualNetwork.sendToNearby(level, x, y, z, BROADCAST_RANGE,
                new AoeEffectPacket(type, x, y, z, radius, entityId));
    }
}
