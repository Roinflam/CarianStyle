package pers.roinflam.carianstyle.init;

import com.google.common.collect.ImmutableList;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemSword;
import net.minecraftforge.common.util.EnumHelper;
import pers.roinflam.carianstyle.enchantment.*;
import pers.roinflam.carianstyle.enchantment.combatskill.*;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentAncientDragonLightning;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentEpilepsySpread;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentScarletLonia;
import pers.roinflam.carianstyle.enchantment.law.EnchantmentStarsLaw;
import pers.roinflam.carianstyle.enchantment.recollect.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CarianStyleEnchantments {
    public static final EnumEnchantmentType SHIELD = EnumHelper.addEnchantmentType("cs_shield", item -> item instanceof ItemShield);
    public static final EnumEnchantmentType ARMS = EnumHelper.addEnchantmentType("cs_arms", item -> item instanceof ItemSword || item instanceof ItemBow);
    public static final int RECOLLECT_ENCHANTABILITY = 38;
    public static final List<Enchantment> ENCHANTMENTS = new ArrayList<Enchantment>();

    public static final EnchantmentDoomedDeath DOOMED_DEATH = new EnchantmentDoomedDeath(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentOfferSword OFFER_SWORD = new EnchantmentOfferSword(
            Enchantment.Rarity.UNCOMMON,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentCorruptedWingSword CORRUPTED_WING_SWORD = new EnchantmentCorruptedWingSword(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentBrokenStar BROKEN_STAR = new EnchantmentBrokenStar(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentEpilepsyFire EPILEPSY_FIRE = new EnchantmentEpilepsyFire(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentBlasphemy BLASPHEMY = new EnchantmentBlasphemy(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentFullMoon FULL_MOON = new EnchantmentFullMoon(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentLivingCorpse LIVING_CORPSE = new EnchantmentLivingCorpse(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentMikaelaBlade MIKAELA_BLADE = new EnchantmentMikaelaBlade(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentBadOmen BAD_OMEN = new EnchantmentBadOmen(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentGiantFlame GIANT_FLAME = new EnchantmentGiantFlame(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentBlood BLOOD = new EnchantmentBlood(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentTimeReversal TIME_REVERSAL = new EnchantmentTimeReversal(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentWarrior WARRIOR = new EnchantmentWarrior(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentGoldenLaw GOLDEN_LAW = new EnchantmentGoldenLaw(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentScarletCorruption SCARLET_ROT = new EnchantmentScarletCorruption(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentDeathBlade DEATH_BLADE = new EnchantmentDeathBlade(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentWaterfowlFlurry WATERFOWL_FLURRY = new EnchantmentWaterfowlFlurry(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentAncestralSpirits ANCESTRAL_SPIRITS = new EnchantmentAncestralSpirits(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.ARMOR,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentDarkAbandonedChild DARK_ABANDONED_CHILD = new EnchantmentDarkAbandonedChild(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentFireGivesPower FIRE_GIVES_POWER = new EnchantmentFireGivesPower(
            Enchantment.Rarity.UNCOMMON,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentHealingByFire HEALING_BY_FIRE = new EnchantmentHealingByFire(
            Enchantment.Rarity.UNCOMMON,
            EnumEnchantmentType.ARMOR,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentShelterOfFire SHELTER_OF_FIRE = new EnchantmentShelterOfFire(
            Enchantment.Rarity.UNCOMMON,
            EnumEnchantmentType.ARMOR,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentExclude EXCLUDE = new EnchantmentExclude(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_LEGS,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.LEGS}
    );
    public static final EnchantmentToppsStand TOPPS_STAND = new EnchantmentToppsStand(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_HEAD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD}
    );
    public static final EnchantmentCausalityPrinciple CAUSALITY_PRINCIPLE = new EnchantmentCausalityPrinciple(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_HEAD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD}
    );
    public static final EnchantmentBeastVitality BEAST_VITALITY = new EnchantmentBeastVitality(
            Enchantment.Rarity.UNCOMMON,
            EnumEnchantmentType.ARMOR_LEGS,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.LEGS}
    );
    public static final EnchantmentFreezingEarthquake FREEZING_EARTHQUAKE = new EnchantmentFreezingEarthquake(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_FEET,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentPreciseLightning PRECISE_LIGHTNING = new EnchantmentPreciseLightning(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_HEAD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD}
    );
    public static final EnchantmentAncientDragonLightning ANCIENT_DRAGON_LIGHTNING = new EnchantmentAncientDragonLightning(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentCalamity CALAMITY = new EnchantmentCalamity(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.ARMOR_HEAD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD}
    );
    public static final EnchantmentFurnaceFeather FURNACE_FEATHER = new EnchantmentFurnaceFeather(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_FEET,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentLorettaBigBow LORETTA_BIG_BOW = new EnchantmentLorettaBigBow(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentLorettaTrick LORETTA_TRICK = new EnchantmentLorettaTrick(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentEmptyEpilepsyFire EMPTY_EPILEPSY_FIRE = new EnchantmentEmptyEpilepsyFire(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentCallStar CALL_STAR = new EnchantmentCallStar(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentInvisibleWeapon INVISIBLE_WEAPON = new EnchantmentInvisibleWeapon(
            Enchantment.Rarity.RARE,
            ARMS,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentHardArrow HARD_ARROW = new EnchantmentHardArrow(
            Enchantment.Rarity.UNCOMMON,
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentEatShit EAT_SHIT = new EnchantmentEatShit(
            Enchantment.Rarity.UNCOMMON,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentMoonOfNoxtura MOON_OF_NOXTURA = new EnchantmentMoonOfNoxtura(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentVicDragonThunder VIC_DRAGON_THUNDER = new EnchantmentVicDragonThunder(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentDarkMoon DARK_MOON = new EnchantmentDarkMoon(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentBloodSlash BLOOD_SLASH = new EnchantmentBloodSlash(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentBloodCollection BLOOD_COLLECTION = new EnchantmentBloodCollection(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentScarletLonia SCARLET_LONIA = new EnchantmentScarletLonia(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentStarsLaw STARS_LAW = new EnchantmentStarsLaw(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentDragonBreathCorruption DRAGON_BREATH_CORRUPTION = new EnchantmentDragonBreathCorruption(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentGoldenDungTurtle GOLDEN_DUNG_TURTLE = new EnchantmentGoldenDungTurtle(
            Enchantment.Rarity.UNCOMMON,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentHypnoticSmoke HYPNOTIC_SMOKE = new EnchantmentHypnoticSmoke(
            Enchantment.Rarity.UNCOMMON,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentHypnoticArrow HYPNOTIC_ARROW = new EnchantmentHypnoticArrow(
            Enchantment.Rarity.UNCOMMON,
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentLungeUp LUNGE_UP = new EnchantmentLungeUp(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentVowedRevenge VOWED_REVENGE = new EnchantmentVowedRevenge(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentPatience PATIENCE = new EnchantmentPatience(
            Enchantment.Rarity.UNCOMMON,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentEpilepsySpread EPILEPSY_SPREAD = new EnchantmentEpilepsySpread(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentAduraMoonlightSword ADURA_MOONLIGHT_SWORD = new EnchantmentAduraMoonlightSword(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentPyroxeneIce PYROXENE_ICE = new EnchantmentPyroxeneIce(
            Enchantment.Rarity.UNCOMMON,
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentWaveStoneMagic WAVE_STONE_MAGIC = new EnchantmentWaveStoneMagic(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentFireDevoured FIRE_DEVOURED = new EnchantmentFireDevoured(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentBlackFlameShelter BLACK_FLAME_SHELTER = new EnchantmentBlackFlameShelter(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_LEGS,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.LEGS}
    );
    public static final EnchantmentAncestralSpiritHorn ANCESTRAL_SPIRIT_HORN = new EnchantmentAncestralSpiritHorn(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_LEGS,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.LEGS}
    );
    public static final EnchantmentRegressivePrinciple REGRESSIVE_PRINCIPLE = new EnchantmentRegressivePrinciple(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_HEAD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD}
    );
    public static final EnchantmentBlackFlameBlade BLACK_FLAME_BLADE = new EnchantmentBlackFlameBlade(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentContinuousShooting CONTINUOUS_SHOOTING = new EnchantmentContinuousShooting(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.BOW,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentLongTailCat LONG_TAIL_CAT = new EnchantmentLongTailCat(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentGodskinSwaddling GODSKIN_SWADDLING = new EnchantmentGodskinSwaddling(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentRealmOfMagic REALM_OF_MAGIC = new EnchantmentRealmOfMagic(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_HEAD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD}
    );
    public static final EnchantmentBeastRobust BEAST_ROBUST = new EnchantmentBeastRobust(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.ARMOR_LEGS,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.LEGS}
    );
    public static final EnchantmentGoldenVow GOLDEN_VOW = new EnchantmentGoldenVow(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentBlessingOfTheErdtree BLESSING_OF_THE_ERDTREE = new EnchantmentBlessingOfTheErdtree(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentProtectionOfTheErdtree PROTECTION_OF_THE_ERDTREE = new EnchantmentProtectionOfTheErdtree(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR_CHEST,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}
    );
    public static final EnchantmentRedFeatheredBranchsword RED_FEATHERED_BRANCHSWORD = new EnchantmentRedFeatheredBranchsword(
            Enchantment.Rarity.UNCOMMON,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentBlueFeatheredBranchsword BLUE_FEATHERED_BRANCHSWORD = new EnchantmentBlueFeatheredBranchsword(
            Enchantment.Rarity.UNCOMMON,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentDaedicarWoe DAEDICAR_WOE = new EnchantmentDaedicarWoe(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.ARMOR,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentScholarShield SCHOLAR_SHIELD = new EnchantmentScholarShield(
            Enchantment.Rarity.RARE,
            SHIELD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND}
    );
    public static final EnchantmentShieldBash SHIELD_BASH = new EnchantmentShieldBash(
            Enchantment.Rarity.RARE,
            SHIELD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND}
    );
    public static final EnchantmentImmutableShield IMMUTABLE_SHIELD = new EnchantmentImmutableShield(
            Enchantment.Rarity.RARE,
            SHIELD,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND}
    );

    public static final Set<Enchantment> RECOLLECT = new HashSet<>(ImmutableList.of(BROKEN_STAR, BLASPHEMY, DOOMED_DEATH, FULL_MOON, LIVING_CORPSE, MIKAELA_BLADE, BAD_OMEN, GIANT_FLAME, BLOOD, TIME_REVERSAL, WARRIOR, GOLDEN_LAW, ANCESTRAL_SPIRITS, DARK_ABANDONED_CHILD));
    public static final Set<Enchantment> LAW = new HashSet<>(ImmutableList.of(GOLDEN_LAW, STARS_LAW));
    public static final Set<Enchantment> COMBAT_SKILL = new HashSet<>(ImmutableList.of(OFFER_SWORD, LUNGE_UP, VOWED_REVENGE, PATIENCE, CONTINUOUS_SHOOTING));
    public static final Set<Enchantment> DEAD = new HashSet<>(ImmutableList.of(FULL_MOON, LIVING_CORPSE, TIME_REVERSAL, ANCIENT_DRAGON_LIGHTNING, SCARLET_LONIA, EPILEPSY_SPREAD));
}