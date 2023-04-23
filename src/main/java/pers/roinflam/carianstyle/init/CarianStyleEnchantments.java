package pers.roinflam.carianstyle.init;

import com.google.common.collect.ImmutableList;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemSword;
import net.minecraftforge.common.util.EnumHelper;
import pers.roinflam.carianstyle.enchantment.*;
import pers.roinflam.carianstyle.enchantment.combatskill.*;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentAncientDragonLightning;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentEpilepsySpread;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentGreatbladePhalanx;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentScarletLonia;
import pers.roinflam.carianstyle.enchantment.law.EnchantmentStarsLaw;
import pers.roinflam.carianstyle.enchantment.recollect.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CarianStyleEnchantments {
    @Nullable
    public static final EnumEnchantmentType SHIELD = EnumHelper.addEnchantmentType("cs_shield", item -> item instanceof ItemShield);
    @Nullable
    public static final EnumEnchantmentType ARMS = EnumHelper.addEnchantmentType("cs_arms", item -> item instanceof ItemSword || item instanceof ItemBow);
    @Nullable
    public static final EnumEnchantmentType PICKAEX = EnumHelper.addEnchantmentType("cs_pickaxe", item -> item instanceof ItemPickaxe);
    public static final int RECOLLECT_ENCHANTABILITY = 38;
    public static final List<Enchantment> ENCHANTMENTS = new ArrayList<Enchantment>();

    public static final EnchantmentDoomedDeath DOOMED_DEATH = new EnchantmentDoomedDeath(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentOfferSword OFFER_SWORD = new EnchantmentOfferSword(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentCorruptedWingSword CORRUPTED_WING_SWORD = new EnchantmentCorruptedWingSword(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentBrokenStar BROKEN_STAR = new EnchantmentBrokenStar(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentEpilepsyFire EPILEPSY_FIRE = new EnchantmentEpilepsyFire(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentBlasphemy BLASPHEMY = new EnchantmentBlasphemy(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentFullMoon FULL_MOON = new EnchantmentFullMoon(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentLivingCorpse LIVING_CORPSE = new EnchantmentLivingCorpse(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentMikaelaBlade MIKAELA_BLADE = new EnchantmentMikaelaBlade(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentBadOmen BAD_OMEN = new EnchantmentBadOmen(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentGiantFlame GIANT_FLAME = new EnchantmentGiantFlame(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentBlood BLOOD = new EnchantmentBlood(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentTimeReversal TIME_REVERSAL = new EnchantmentTimeReversal(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentWarrior WARRIOR = new EnchantmentWarrior(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentGoldenLaw GOLDEN_LAW = new EnchantmentGoldenLaw(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentScarletCorruption SCARLET_ROT = new EnchantmentScarletCorruption(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentDeathBlade DEATH_BLADE = new EnchantmentDeathBlade(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentWaterfowlFlurry WATERFOWL_FLURRY = new EnchantmentWaterfowlFlurry(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentAncestralSpirits ANCESTRAL_SPIRITS = new EnchantmentAncestralSpirits(
            EnumEnchantmentType.ARMOR,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentDarkAbandonedChild DARK_ABANDONED_CHILD = new EnchantmentDarkAbandonedChild(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentFireGivesPower FIRE_GIVES_POWER = new EnchantmentFireGivesPower(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentHealingByFire HEALING_BY_FIRE = new EnchantmentHealingByFire(
            EnumEnchantmentType.ARMOR,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentShelterOfFire SHELTER_OF_FIRE = new EnchantmentShelterOfFire(
            EnumEnchantmentType.ARMOR,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentExclude EXCLUDE = new EnchantmentExclude(
            EnumEnchantmentType.ARMOR_LEGS,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.LEGS}
    );
    public static final EnchantmentToppsStand TOPPS_STAND = new EnchantmentToppsStand(
            EnumEnchantmentType.ARMOR_HEAD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD}
    );
    public static final EnchantmentCausalityPrinciple CAUSALITY_PRINCIPLE = new EnchantmentCausalityPrinciple(
            EnumEnchantmentType.ARMOR_HEAD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD}
    );
    public static final EnchantmentBeastVitality BEAST_VITALITY = new EnchantmentBeastVitality(
            EnumEnchantmentType.ARMOR_LEGS,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.LEGS}
    );
    public static final EnchantmentFreezingEarthquake FREEZING_EARTHQUAKE = new EnchantmentFreezingEarthquake(
            EnumEnchantmentType.ARMOR_FEET,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentPreciseLightning PRECISE_LIGHTNING = new EnchantmentPreciseLightning(
            EnumEnchantmentType.ARMOR_HEAD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD}
    );
    public static final EnchantmentAncientDragonLightning ANCIENT_DRAGON_LIGHTNING = new EnchantmentAncientDragonLightning(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentCalamity CALAMITY = new EnchantmentCalamity(
            EnumEnchantmentType.ARMOR_HEAD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD}
    );
    public static final EnchantmentFurnaceFeather FURNACE_FEATHER = new EnchantmentFurnaceFeather(
            EnumEnchantmentType.ARMOR_FEET,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentLorettaBigBow LORETTA_BIG_BOW = new EnchantmentLorettaBigBow(
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentLorettaTrick LORETTA_TRICK = new EnchantmentLorettaTrick(
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentEmptyEpilepsyFire EMPTY_EPILEPSY_FIRE = new EnchantmentEmptyEpilepsyFire(
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentCallStar CALL_STAR = new EnchantmentCallStar(
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentInvisibleWeapon INVISIBLE_WEAPON = new EnchantmentInvisibleWeapon(
            ARMS,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentHardArrow HARD_ARROW = new EnchantmentHardArrow(
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentEatShit EAT_SHIT = new EnchantmentEatShit(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentMoonOfNoxtura MOON_OF_NOXTURA = new EnchantmentMoonOfNoxtura(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentVicDragonThunder VIC_DRAGON_THUNDER = new EnchantmentVicDragonThunder(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentDarkMoon DARK_MOON = new EnchantmentDarkMoon(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentBloodSlash BLOOD_SLASH = new EnchantmentBloodSlash(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentBloodCollection BLOOD_COLLECTION = new EnchantmentBloodCollection(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentScarletLonia SCARLET_LONIA = new EnchantmentScarletLonia(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentStarsLaw STARS_LAW = new EnchantmentStarsLaw(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentDragonBreathCorruption DRAGON_BREATH_CORRUPTION = new EnchantmentDragonBreathCorruption(
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentGoldenDungTurtle GOLDEN_DUNG_TURTLE = new EnchantmentGoldenDungTurtle(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentHypnoticSmoke HYPNOTIC_SMOKE = new EnchantmentHypnoticSmoke(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentHypnoticArrow HYPNOTIC_ARROW = new EnchantmentHypnoticArrow(
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentLungeUp LUNGE_UP = new EnchantmentLungeUp(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentVowedRevenge VOWED_REVENGE = new EnchantmentVowedRevenge(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentPatience PATIENCE = new EnchantmentPatience(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentEpilepsySpread EPILEPSY_SPREAD = new EnchantmentEpilepsySpread(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentAduraMoonlightSword ADURA_MOONLIGHT_SWORD = new EnchantmentAduraMoonlightSword(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentPyroxeneIce PYROXENE_ICE = new EnchantmentPyroxeneIce(
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentWaveStoneMagic WAVE_STONE_MAGIC = new EnchantmentWaveStoneMagic(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentFireDevoured FIRE_DEVOURED = new EnchantmentFireDevoured(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentBlackFlameShelter BLACK_FLAME_SHELTER = new EnchantmentBlackFlameShelter(
            EnumEnchantmentType.ARMOR_LEGS,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.LEGS}
    );
    public static final EnchantmentAncestralSpiritHorn ANCESTRAL_SPIRIT_HORN = new EnchantmentAncestralSpiritHorn(
            EnumEnchantmentType.ARMOR_LEGS,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.LEGS}
    );
    public static final EnchantmentRegressivePrinciple REGRESSIVE_PRINCIPLE = new EnchantmentRegressivePrinciple(
            EnumEnchantmentType.ARMOR_HEAD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD}
    );
    public static final EnchantmentBlackFlameBlade BLACK_FLAME_BLADE = new EnchantmentBlackFlameBlade(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentContinuousShooting CONTINUOUS_SHOOTING = new EnchantmentContinuousShooting(
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentLongTailCat LONG_TAIL_CAT = new EnchantmentLongTailCat(
            EnumEnchantmentType.ARMOR,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentGodskinSwaddling GODSKIN_SWADDLING = new EnchantmentGodskinSwaddling(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentRealmOfMagic REALM_OF_MAGIC = new EnchantmentRealmOfMagic(
            EnumEnchantmentType.ARMOR_HEAD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD}
    );
    public static final EnchantmentBeastRobust BEAST_ROBUST = new EnchantmentBeastRobust(
            EnumEnchantmentType.ARMOR_LEGS,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.LEGS}
    );
    public static final EnchantmentGoldenVow GOLDEN_VOW = new EnchantmentGoldenVow(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentBlessingOfTheErdtree BLESSING_OF_THE_ERDTREE = new EnchantmentBlessingOfTheErdtree(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentProtectionOfTheErdtree PROTECTION_OF_THE_ERDTREE = new EnchantmentProtectionOfTheErdtree(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentRedFeatheredBranchsword RED_FEATHERED_BRANCHSWORD = new EnchantmentRedFeatheredBranchsword(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentBlueFeatheredBranchsword BLUE_FEATHERED_BRANCHSWORD = new EnchantmentBlueFeatheredBranchsword(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentDaedicarWoe DAEDICAR_WOE = new EnchantmentDaedicarWoe(
            EnumEnchantmentType.ARMOR,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentScholarShield SCHOLAR_SHIELD = new EnchantmentScholarShield(
            SHIELD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND}
    );
    public static final EnchantmentShieldBash SHIELD_BASH = new EnchantmentShieldBash(
            SHIELD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND}
    );
    public static final EnchantmentImmutableShield IMMUTABLE_SHIELD = new EnchantmentImmutableShield(
            SHIELD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND}
    );
    public static final EnchantmentParry PARRY = new EnchantmentParry(
            SHIELD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND}
    );
    public static final EnchantmentCarianRetaliation CARIAN_RETALIATION = new EnchantmentCarianRetaliation(
            SHIELD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND}
    );
    public static final EnchantmentCarianPhalanx CARIAN_PHALANX = new EnchantmentCarianPhalanx(
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentGreatbladePhalanx GREATBLADE_PHALANX = new EnchantmentGreatbladePhalanx(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentCrucibleScaleTalisman CRUCIBLE_SCALE_TALISMAN = new EnchantmentCrucibleScaleTalisman(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentConcealingVeil CONCEALING_VEIL = new EnchantmentConcealingVeil(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentBlessedDewTalisman BLESSED_DEW_TALISMAN = new EnchantmentBlessedDewTalisman(
            EnumEnchantmentType.ARMOR,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentLucidity LUCIDITY = new EnchantmentLucidity(
            EnumEnchantmentType.ARMOR_LEGS,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.LEGS}
    );
    public static final EnchantmentRockBlaster ROCK_BLASTER = new EnchantmentRockBlaster(
            PICKAEX,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentLionClaw LION_CLAW = new EnchantmentLionClaw(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentCorpsePiler CORPSE_PILER = new EnchantmentCorpsePiler(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentQuickstep QUICKSTEP = new EnchantmentQuickstep(
            EnumEnchantmentType.ARMOR_FEET,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentMillicentProsthesis MILLICENT_PROSTHESIS = new EnchantmentMillicentProsthesis(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentPrayerfulStrike PRAYERFUL_STRIKE = new EnchantmentPrayerfulStrike(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentHolyGround HOLY_GROUND = new EnchantmentHolyGround(
            SHIELD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND}
    );
    public static final EnchantmentSacredBlade SACRED_BLADE = new EnchantmentSacredBlade(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentIndomitable INDOMITABLE = new EnchantmentIndomitable(
            EnumEnchantmentType.ARMOR,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentDoubleSlash DOUBLE_SLASH = new EnchantmentDoubleSlash(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentSwordDance SWORD_DANCE = new EnchantmentSwordDance(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentAssassinGambit ASSASSIN_GAMBIT = new EnchantmentAssassinGambit(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentBloodBlade BLOOD_BLADE = new EnchantmentBloodBlade(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentIncision INCISION = new EnchantmentIncision(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentGreenTurtle GREEN_TURTLE = new EnchantmentGreenTurtle(
            EnumEnchantmentType.ARMOR_LEGS,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.LEGS}
    );
    public static final EnchantmentGravitas GRAVITAS = new EnchantmentGravitas(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentHowlShabriri HOWL_SHABRIRI = new EnchantmentHowlShabriri(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentUnsheathe UNSHEATHE = new EnchantmentUnsheathe(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentStarlight STARLIGHT = new EnchantmentStarlight(
            EnumEnchantmentType.ARMOR_HEAD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD}
    );
    public static final EnchantmentBlackFlameRitual BLACK_FLAME_RITUAL = new EnchantmentBlackFlameRitual (
            EnumEnchantmentType.ARMOR_LEGS,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.LEGS}
    );
    public static final EnchantmentAeonia AEONIA = new EnchantmentAeonia(
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentSacredOrder SACRED_ORDER = new EnchantmentSacredOrder(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentDragoncrestGreatshield DRAGONCREST_GREATSHIELD = new EnchantmentDragoncrestGreatshield(
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final Set<Enchantment> RECOLLECT = new HashSet<>(ImmutableList.of(BROKEN_STAR, BLASPHEMY, DOOMED_DEATH, FULL_MOON, LIVING_CORPSE, MIKAELA_BLADE, BAD_OMEN, GIANT_FLAME, BLOOD, TIME_REVERSAL, WARRIOR, GOLDEN_LAW, ANCESTRAL_SPIRITS, DARK_ABANDONED_CHILD));

    public static final Set<Enchantment> COMBAT_SKILL = new HashSet<>(ImmutableList.of(OFFER_SWORD, LUNGE_UP, VOWED_REVENGE, PATIENCE, CONTINUOUS_SHOOTING, LION_CLAW, CORPSE_PILER, QUICKSTEP, PRAYERFUL_STRIKE, HOLY_GROUND, INDOMITABLE, UNSHEATHE));
    public static final Set<Enchantment> LAW = new HashSet<>(ImmutableList.of(GOLDEN_LAW, STARS_LAW));
    public static final Set<Enchantment> DEAD = new HashSet<>(ImmutableList.of(FULL_MOON, LIVING_CORPSE, TIME_REVERSAL, ANCIENT_DRAGON_LIGHTNING, SCARLET_LONIA, EPILEPSY_SPREAD, GREATBLADE_PHALANX));
}