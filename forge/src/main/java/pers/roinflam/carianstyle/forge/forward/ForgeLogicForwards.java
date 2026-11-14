package pers.roinflam.carianstyle.forge.forward;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import pers.roinflam.carianstyle.bus.ICancellableEvent;
import pers.roinflam.carianstyle.bus.WeakEventRef;
import pers.roinflam.carianstyle.event.BlockEvent;
import pers.roinflam.carianstyle.event.CriticalHitEvent;
import pers.roinflam.carianstyle.event.EntityJoinLevelEvent;
import pers.roinflam.carianstyle.event.EntityLeaveLevelEvent;
import pers.roinflam.carianstyle.event.LivingChangeTargetEvent;
import pers.roinflam.carianstyle.event.LivingDeathEvent;
import pers.roinflam.carianstyle.event.LivingExperienceDropEvent;
import pers.roinflam.carianstyle.event.LivingHealEvent;
import pers.roinflam.carianstyle.event.LivingKnockBackEvent;
import pers.roinflam.carianstyle.event.LivingTickEvent;
import pers.roinflam.carianstyle.event.MobEffectEvent;
import pers.roinflam.carianstyle.event.PlayerEvent;
import pers.roinflam.carianstyle.event.PlayerTickEvent;
import pers.roinflam.carianstyle.event.ProjectileImpactEvent;
import pers.roinflam.carianstyle.event.ServerStartedEvent;
import pers.roinflam.carianstyle.event.ServerStoppedEvent;
import pers.roinflam.carianstyle.event.ServerTickEvent;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;

import javax.annotation.Nullable;

import static pers.roinflam.carianstyle.forge.forward.ForgeForwarding.forward;
import static pers.roinflam.carianstyle.forge.forward.ForgeForwarding.forwardTick;
import static pers.roinflam.carianstyle.forge.forward.ForgeForwarding.target;

/**
 * 逻辑类（双端都有）Forge 事件的直通包装和转发登记。
 * <p>
 * 包装类只弱引用 Forge 事件（{@link WeakEventRef}），成员当场读写 Forge 事件；Forge 事件可取消的，包装类都实现取消接口，
 * 取消状态读写 Forge 事件本身。分发结束之后不能再读包装对象。
 * </p>
 */
final class ForgeLogicForwards {

    private ForgeLogicForwards() {
    }

    static void install() {
        forward(net.minecraftforge.event.entity.living.LivingAttackEvent.class, LivingAttackEvent.class, Attack.class, Attack::new);
        forward(net.minecraftforge.event.entity.living.LivingHurtEvent.class, LivingHurtEvent.class, Hurt.class, Hurt::new);
        forward(net.minecraftforge.event.entity.living.LivingDamageEvent.class, LivingDamageEvent.class, Damage.class, Damage::new);
        forward(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent.class, LivingTickEvent.class, LivingTick.class, LivingTick::new);
        forward(net.minecraftforge.event.entity.living.LivingDeathEvent.class, LivingDeathEvent.class, Death.class, Death::new);
        forward(net.minecraftforge.event.entity.living.LivingHealEvent.class, LivingHealEvent.class, Heal.class, Heal::new);
        forward(net.minecraftforge.event.entity.EntityJoinLevelEvent.class, EntityJoinLevelEvent.class, JoinLevel.class, JoinLevel::new);
        forward(net.minecraftforge.event.entity.EntityLeaveLevelEvent.class, EntityLeaveLevelEvent.class, LeaveLevel.class, LeaveLevel::new);
        forward(net.minecraftforge.event.entity.ProjectileImpactEvent.class, ProjectileImpactEvent.class, ProjectileImpact.class, ProjectileImpact::new);
        forward(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class,
                PlayerEvent.PlayerLoggedInEvent.class, LoggedIn.class, LoggedIn::new);
        forward(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class,
                PlayerEvent.PlayerLoggedOutEvent.class, LoggedOut.class, LoggedOut::new);
        forward(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent.class,
                PlayerEvent.PlayerRespawnEvent.class, Respawn.class, Respawn::new);
        forward(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent.class,
                PlayerEvent.PlayerChangedDimensionEvent.class, ChangedDimension.class, ChangedDimension::new);
        forward(net.minecraftforge.event.entity.player.PlayerEvent.Clone.class, PlayerEvent.Clone.class, Clone.class, Clone::new);
        // 玩家 tick 双端都发，模组只有 Pre，END 不转发
        forwardTick(TickEvent.PlayerTickEvent.class,
                target(PlayerTickEvent.Pre.class, PlayerTickPre.class, PlayerTickPre::new), null);
        forwardTick(TickEvent.ServerTickEvent.class,
                target(ServerTickEvent.Pre.class, ServerTickPre.class, ServerTickPre::new),
                target(ServerTickEvent.Post.class, ServerTickPost.class, ServerTickPost::new));
        forward(net.minecraftforge.event.entity.living.MobEffectEvent.Added.class, MobEffectEvent.Added.class, EffectAdded.class, EffectAdded::new);
        forward(net.minecraftforge.event.entity.living.MobEffectEvent.Remove.class, MobEffectEvent.Remove.class, EffectRemove.class, EffectRemove::new);
        forward(net.minecraftforge.event.entity.living.MobEffectEvent.Expired.class, MobEffectEvent.Expired.class, EffectExpired.class, EffectExpired::new);
        forward(net.minecraftforge.event.entity.player.CriticalHitEvent.class, CriticalHitEvent.class, CriticalHit.class, CriticalHit::new);
        forward(net.minecraftforge.event.entity.living.LivingChangeTargetEvent.class, LivingChangeTargetEvent.class, ChangeTarget.class, ChangeTarget::new);
        forward(net.minecraftforge.event.entity.living.LivingKnockBackEvent.class, LivingKnockBackEvent.class, KnockBack.class, KnockBack::new);
        forward(net.minecraftforge.event.entity.living.LivingExperienceDropEvent.class, LivingExperienceDropEvent.class, ExperienceDrop.class, ExperienceDrop::new);
        // 碎岩连挖的权限探测事件是 BreakEvent 的子类，Forge 总线照样分发到这里
        forward(net.minecraftforge.event.level.BlockEvent.BreakEvent.class, BlockEvent.BreakEvent.class, Break.class, Break::new);
        forward(net.minecraftforge.event.server.ServerStartedEvent.class, ServerStartedEvent.class, ServerStarted.class, ServerStarted::new);
        forward(net.minecraftforge.event.server.ServerStoppedEvent.class, ServerStoppedEvent.class, ServerStopped.class, ServerStopped::new);
    }

    // 伤害三段和生物 tick 在 common 里是带字段的具体类：构造时把 Forge 事件当时的值交给父类，之后的读写全部覆写成落到 Forge 事件上。

    static final class Attack extends LivingAttackEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.living.LivingAttackEvent> e;

        Attack(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
            super(e.getEntity(), e.getSource(), e.getAmount());
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public DamageSource getSource() {
            return e.get().getSource();
        }

        @Override
        public float getAmount() {
            return e.get().getAmount();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class Hurt extends LivingHurtEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.living.LivingHurtEvent> e;

        Hurt(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
            super(e.getEntity(), e.getSource(), e.getAmount());
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public DamageSource getSource() {
            return e.get().getSource();
        }

        @Override
        public float getAmount() {
            return e.get().getAmount();
        }

        @Override
        public void setAmount(float amount) {
            e.get().setAmount(amount);
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class Damage extends LivingDamageEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.living.LivingDamageEvent> e;

        Damage(net.minecraftforge.event.entity.living.LivingDamageEvent e) {
            super(e.getEntity(), e.getSource(), e.getAmount());
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public DamageSource getSource() {
            return e.get().getSource();
        }

        @Override
        public float getAmount() {
            return e.get().getAmount();
        }

        @Override
        public void setAmount(float amount) {
            e.get().setAmount(amount);
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class LivingTick extends LivingTickEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent> e;

        LivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e) {
            super(e.getEntity());
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class Death extends LivingDeathEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.living.LivingDeathEvent> e;

        Death(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public DamageSource getSource() {
            return e.get().getSource();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class Heal extends LivingHealEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.living.LivingHealEvent> e;

        Heal(net.minecraftforge.event.entity.living.LivingHealEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public float getAmount() {
            return e.get().getAmount();
        }

        @Override
        public void setAmount(float amount) {
            e.get().setAmount(amount);
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class JoinLevel extends EntityJoinLevelEvent implements ICancellableEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.EntityJoinLevelEvent> e;

        JoinLevel(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Entity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public Level getLevel() {
            return e.get().getLevel();
        }

        @Override
        public boolean loadedFromDisk() {
            return e.get().loadedFromDisk();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class LeaveLevel extends EntityLeaveLevelEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.EntityLeaveLevelEvent> e;

        LeaveLevel(net.minecraftforge.event.entity.EntityLeaveLevelEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Entity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public Level getLevel() {
            return e.get().getLevel();
        }
    }

    static final class ProjectileImpact extends ProjectileImpactEvent implements ICancellableEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.ProjectileImpactEvent> e;

        ProjectileImpact(net.minecraftforge.event.entity.ProjectileImpactEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Entity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public Projectile getProjectile() {
            return e.get().getProjectile();
        }

        @Override
        public HitResult getRayTraceResult() {
            return e.get().getRayTraceResult();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        // Forge 47 把 setCanceled 标成待删、推荐 setImpactResult，但取消仍然有效（ForgeEventFactory 取消时按 SKIP_ENTITY 处理），
        // 本模组原来的监听用的就是取消
        @SuppressWarnings("removal")
        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class LoggedIn extends PlayerEvent.PlayerLoggedInEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent> e;

        LoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Player getEntity() {
            return e.get().getEntity();
        }
    }

    static final class LoggedOut extends PlayerEvent.PlayerLoggedOutEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent> e;

        LoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Player getEntity() {
            return e.get().getEntity();
        }
    }

    static final class Respawn extends PlayerEvent.PlayerRespawnEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent> e;

        Respawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Player getEntity() {
            return e.get().getEntity();
        }

        @Override
        public boolean isEndConquered() {
            return e.get().isEndConquered();
        }
    }

    static final class ChangedDimension extends PlayerEvent.PlayerChangedDimensionEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent> e;

        ChangedDimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Player getEntity() {
            return e.get().getEntity();
        }
    }

    static final class Clone extends PlayerEvent.Clone {
        private final WeakEventRef<net.minecraftforge.event.entity.player.PlayerEvent.Clone> e;

        Clone(net.minecraftforge.event.entity.player.PlayerEvent.Clone e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Player getEntity() {
            return e.get().getEntity();
        }

        @Override
        public Player getOriginal() {
            return e.get().getOriginal();
        }

        @Override
        public boolean isWasDeath() {
            return e.get().isWasDeath();
        }
    }

    static final class PlayerTickPre extends PlayerTickEvent.Pre {
        private final WeakEventRef<TickEvent.PlayerTickEvent> e;

        PlayerTickPre(TickEvent.PlayerTickEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Player getEntity() {
            return e.get().player;
        }
    }

    static final class ServerTickPre extends ServerTickEvent.Pre {
        private final WeakEventRef<TickEvent.ServerTickEvent> e;

        ServerTickPre(TickEvent.ServerTickEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public MinecraftServer getServer() {
            return e.get().getServer();
        }
    }

    static final class ServerTickPost extends ServerTickEvent.Post {
        private final WeakEventRef<TickEvent.ServerTickEvent> e;

        ServerTickPost(TickEvent.ServerTickEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public MinecraftServer getServer() {
            return e.get().getServer();
        }
    }

    static final class EffectAdded extends MobEffectEvent.Added {
        private final WeakEventRef<net.minecraftforge.event.entity.living.MobEffectEvent.Added> e;

        EffectAdded(net.minecraftforge.event.entity.living.MobEffectEvent.Added e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public MobEffectInstance getEffectInstance() {
            return e.get().getEffectInstance();
        }
    }

    static final class EffectRemove extends MobEffectEvent.Remove implements ICancellableEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.living.MobEffectEvent.Remove> e;

        EffectRemove(net.minecraftforge.event.entity.living.MobEffectEvent.Remove e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public MobEffect getEffect() {
            return e.get().getEffect();
        }

        @Nullable
        @Override
        public MobEffectInstance getEffectInstance() {
            return e.get().getEffectInstance();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    // Forge 47 的 Expired 不可取消（对它 setCanceled 会抛异常），包装不实现取消接口
    static final class EffectExpired extends MobEffectEvent.Expired {
        private final WeakEventRef<net.minecraftforge.event.entity.living.MobEffectEvent.Expired> e;

        EffectExpired(net.minecraftforge.event.entity.living.MobEffectEvent.Expired e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public MobEffectInstance getEffectInstance() {
            return e.get().getEffectInstance();
        }
    }

    static final class CriticalHit extends CriticalHitEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.player.CriticalHitEvent> e;

        CriticalHit(net.minecraftforge.event.entity.player.CriticalHitEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Player getEntity() {
            return e.get().getEntity();
        }

        @Override
        public Entity getTarget() {
            return e.get().getTarget();
        }

        @Override
        public boolean isVanillaCritical() {
            return e.get().isVanillaCritical();
        }

        @Override
        public float getDamageMultiplier() {
            return e.get().getDamageModifier();
        }

        @Override
        public void setDamageMultiplier(float multiplier) {
            e.get().setDamageModifier(multiplier);
        }
    }

    static final class ChangeTarget extends LivingChangeTargetEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.living.LivingChangeTargetEvent> e;

        ChangeTarget(net.minecraftforge.event.entity.living.LivingChangeTargetEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Nullable
        @Override
        public LivingEntity getNewAboutToBeSetTarget() {
            return e.get().getNewTarget();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class KnockBack extends LivingKnockBackEvent implements ICancellableEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.living.LivingKnockBackEvent> e;

        KnockBack(net.minecraftforge.event.entity.living.LivingKnockBackEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public float getStrength() {
            return e.get().getStrength();
        }

        @Override
        public void setStrength(float strength) {
            e.get().setStrength(strength);
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class ExperienceDrop extends LivingExperienceDropEvent implements ICancellableEvent {
        private final WeakEventRef<net.minecraftforge.event.entity.living.LivingExperienceDropEvent> e;

        ExperienceDrop(net.minecraftforge.event.entity.living.LivingExperienceDropEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Nullable
        @Override
        public Player getAttackingPlayer() {
            return e.get().getAttackingPlayer();
        }

        @Override
        public int getDroppedExperience() {
            return e.get().getDroppedExperience();
        }

        @Override
        public void setDroppedExperience(int experience) {
            e.get().setDroppedExperience(experience);
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class Break extends BlockEvent.BreakEvent {
        private final WeakEventRef<net.minecraftforge.event.level.BlockEvent.BreakEvent> e;

        Break(net.minecraftforge.event.level.BlockEvent.BreakEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LevelAccessor getLevel() {
            return e.get().getLevel();
        }

        @Override
        public BlockPos getPos() {
            return e.get().getPos();
        }

        @Override
        public BlockState getState() {
            return e.get().getState();
        }

        @Override
        public Player getPlayer() {
            return e.get().getPlayer();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class ServerStarted extends ServerStartedEvent {
        private final WeakEventRef<net.minecraftforge.event.server.ServerStartedEvent> e;

        ServerStarted(net.minecraftforge.event.server.ServerStartedEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public MinecraftServer getServer() {
            return e.get().getServer();
        }
    }

    static final class ServerStopped extends ServerStoppedEvent {
        private final WeakEventRef<net.minecraftforge.event.server.ServerStoppedEvent> e;

        ServerStopped(net.minecraftforge.event.server.ServerStoppedEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public MinecraftServer getServer() {
            return e.get().getServer();
        }
    }
}
