package pers.roinflam.carianstyle.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.init.CarianStylePotion;

/**
 * PORT-STUB(B2b)
 *
 * 客户端专用 Mixin：抑制「本模组每 tick 持续掉血效果」引发的受伤镜头倾斜与本地无敌帧重置。
 *
 * <h3>补丁的对象与原因</h3>
 * <p>
 * {@code MobEffectIncision}（切腹）与 {@code MobEffectHemorrhage}（出血）的
 * {@code isDurationEffectTick} 恒返回 {@code true}，即<b>每 tick</b> 都会在服务端执行一次
 * {@code setHealth(当前生命 - 伤害)}。这会触发下面这条完全在原版内部的链路：
 * </p>
 * <ol>
 *     <li>服务端 {@code ServerPlayer.doTick()} 发现 {@code getHealth() != lastSentHealth}，
 *         于是<b>每 tick</b> 下发一个 {@code ClientboundSetHealthPacket}；</li>
 *     <li>客户端 {@code ClientPacketListener.handleSetHealth} 调用
 *         {@code LocalPlayer.hurtTo(新生命值)}；</li>
 *     <li>原版 {@code hurtTo} 在「生命值下降」时会把
 *         {@code hurtTime} / {@code hurtDuration} 重置为 10、
 *         {@code invulnerableTime} 重置为 20，并额外调用一次
 *         {@code hurt(damageSources().generic(), 差值)}；</li>
 *     <li>{@code GameRenderer.bobHurt()} 依据 {@code hurtTime} 对摄像机做 Z 轴 roll
 *         （最大 14°）。正常受击时 {@code hurtTime} 由 10 递减到 0（约 0.5 秒）晃一下就停，
 *         但切腹期间它<b>每 tick 被重置回 10</b>，那条 sin 曲线永远从头播——
 *         于是表现为持续的视角抖动 / 抽搐。</li>
 * </ol>
 * <p>
 * 顺带的副作用：{@code invulnerableTime} 也被客户端每 tick 顶回 20，
 * 期间真实受击的本地表现（红闪判定）会被吞掉。
 * </p>
 *
 * <h3>补丁做法</h3>
 * <p>
 * 在 {@code hurtTo} 的 HEAD 注入：当玩家身上带有本模组的持续掉血效果、
 * 且本次是「生命值下降」时，只做纯粹的 {@code setHealth} 同步、然后取消原版方法，
 * 从而完全跳过 {@code hurtTime} / {@code invulnerableTime} 的赋值与那次 {@code hurt} 调用。
 * </p>
 * <p>
 * <b>为什么不会丢失真实受击的镜头反馈：</b>真实受击的晃动<b>不是</b>走 {@code hurtTo} 的，
 * 而是服务端 {@code LivingEntity.hurt} 通过
 * {@code ServerLevel.broadcastEntityEvent(实体, 事件 2)} 下发实体事件，
 * 客户端在 {@code LivingEntity.handleEntityEvent} 里另行设置 {@code hurtTime} 并播放动画；
 * 该广播使用的是 {@code broadcastAndSend}，会把包也发给被打的玩家自己。
 * 因此切腹期间被怪物打到，红闪与镜头晃动依旧正常，只有「自我消耗掉血」不再晃。
 * 受击音效同理由服务端 {@code playHurtSound} 广播，也不受影响。
 * </p>
 *
 * <h3>注入目标为什么是 LocalPlayer 而不是 Player（v1.1 修正）</h3>
 * <p>
 * v1.0 曾把目标写成 {@code net.minecraft.world.entity.player.Player}，注入失败并崩溃：
 * </p>
 * <pre>
 * Critical injection failure: @Inject ... could not find any targets
 * matching 'hurtTo(F)V' in net.minecraft.world.entity.player.Player
 * </pre>
 * <p>
 * {@code hurtTo(float)} 实际定义在客户端类 {@link LocalPlayer} 上，
 * {@code Player} 基类并没有这个方法——{@code ClientPacketListener} 里
 * {@code this.minecraft.player} 的静态类型正是 {@code LocalPlayer}。
 * 目标类改正后即可命中。
 * </p>
 *
 * <h3>关于 flashOnSetHealth 首包分支</h3>
 * <p>
 * 原版 {@code hurtTo} 内部有一个「首次收到生命值包」的分支（{@code flashOnSetHealth} 为 false 时
 * 只 setHealth、不播受伤动画）。本注入取消原版方法时不会去改那个私有标志，
 * 但这不构成问题：玩家登录后的第一个生命值包必然发生在进入世界的瞬间，
 * 那时不可能已经处于切腹 / 出血状态，因此本注入永远不会在首包时命中；
 * 即便在极端情况下命中，效果也只是让该标志晚一个包置位，行为等价。
 * 故无需 {@code @Shadow} 该私有字段，减少一处对映射名的依赖。
 * </p>
 *
 * <h3>为什么放在 client 列表</h3>
 * <p>
 * {@link LocalPlayer} 本身就是纯客户端类，物理服务端不存在该类。
 * 注册在 {@code carianstyle.mixins.json} 的 {@code client} 数组中，
 * 可保证 Mohist 等混合端的服务端根本不会尝试加载与应用本 Mixin，零风险、零开销。
 * 也正因目标恒为客户端实体，方法内不再需要 {@code isClientSide} 守卫。
 * </p>
 *
 * @author RoinFlam
 * @version 1.1
 */
@Mixin(LocalPlayer.class)
@Deprecated
public class MixinPlayerHurtTo {
}

