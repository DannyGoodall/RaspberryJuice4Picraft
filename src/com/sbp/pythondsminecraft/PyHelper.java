package com.sbp.pythondsminecraft;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.apache.commons.lang.BooleanUtils;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

import static org.bukkit.Bukkit.getLogger;

class PythonCommand {
    String thing;
    int thingID;
    PyLocation thingLocation;
    String method;
    String signature;
    // https://github.com/google/gson/blob/ceae88bd6667f4263bbe02e6b3710b8a683906a2/extras/src/main/java/com/google/gson/extras/examples/rawcollections/RawCollectionsExample.java
    List<Object> args;
    //JsonArray args;

    Gson gson = new Gson();

    public JsonElement argToJSONElement(int index) {
        JsonElement jsonElement = gson.fromJson((String) args.get(index), JsonElement.class);
        getLogger().warning("PyHelper: jsonElement: " + jsonElement.toString());
        return jsonElement;
    }

    public JsonObject argToJSON(int index) {
        JsonObject jsonObject = gson.fromJson((String) args.get(index), JsonObject.class);
        getLogger().warning("PyHelper: jsonObject: " + jsonObject.toString());
        return jsonObject;
    }

//    public argToPyLocation(int index){
//
//    }

    public boolean argToBoolean(int index) {
        //JsonObject jsonObject = argToJSON(index);
        JsonElement jsonElement = argToJSONElement(index);
        //boolean test = jsonObject.getAsBoolean();
        boolean test = jsonElement.getAsBoolean();
        getLogger().warning("PyHelper: argToBoolean: " + Boolean.toString(test));
        return test;
        // return BooleanUtils.toBoolean( (boolean) args.get(index));
    }

    public int argToInteger(int index) {
        JsonElement jsonElement = argToJSONElement(index);
        int intReturn = jsonElement.getAsInt();
        return intReturn;
    }

    public long argToLong(int index) {
        JsonElement jsonElement = argToJSONElement(index);
        long longReturn = jsonElement.getAsLong();
        return longReturn;
    }

    public float argToFloat(int index) {
        JsonElement jsonElement = argToJSONElement(index);
        float floatReturn = jsonElement.getAsFloat();
        return floatReturn;
    }

    public double argToDouble(int index) {
        JsonElement jsonElement = argToJSONElement(index);
        double doubleReturn = jsonElement.getAsDouble();
        return doubleReturn;
    }

    public String argToString(int index) {
        JsonElement jsonElement = argToJSONElement(index);
        String stringReturn = jsonElement.getAsString();
        return stringReturn;
    }

    public String argToStringUpper(int index) {
        return argToString(index).toUpperCase();
    }

    public String argToStringLower(int index) {
        return argToString(index).toLowerCase();
    }

    public Color argToColor(int index) {
        PyColor pyColor = gson.fromJson((String) args.get(index), PyColor.class);
        Color color = pyColor.toColor();
        return color;
    }

    public Location argToLocation(int index, World world) {
        //getLogger().warning("argToLocation: Got index: " + index + " world: "+world);
        PyLocation locationParameter = gson.fromJson((String) args.get(index), PyLocation.class);
        //getLogger().warning("argToLocation: Got locationParameter: " + locationParameter.toString());

        Location location = locationParameter.toLocation(world);
        return location;
    }

    public Vector argToVector(int index) {
        PyLocation locationParameter = gson.fromJson((String) args.get(index), PyLocation.class);
        getLogger().warning("argToLocation: Got locationParameter: " + locationParameter.toString());

        Vector vector = locationParameter.toVector();
        return vector;
    }

    public PotionEffect argToPotionEffect(int index) {
        PyPotionEffect potionEffectParameter = gson.fromJson((String) args.get(index), PyPotionEffect.class);
        getLogger().warning("argToPotionEffect: Got potionEffectParameter:" + potionEffectParameter.toString());
        PotionEffect potionEffect = potionEffectParameter.toPotionEffect();
        return potionEffect;
    }

    public PotionData argToPotionData(int index) {
        PyPotionData potionDataParameter = gson.fromJson((String) args.get(index), PyPotionData.class);
        getLogger().warning("potionDataParameter: " + potionDataParameter);
        PotionData potionData = potionDataParameter.toPotionData();
        return potionData;
    }

    public BoundingBox argToBoundingBox(int index) {
        PyBoundingBox boundingBoxParameter = gson.fromJson((String) args.get(index), PyBoundingBox.class);
        getLogger().warning("boundBoxParameter: " + boundingBoxParameter);
        BoundingBox boundingBox = boundingBoxParameter.toBoundingBox();
        return boundingBox;
    }

    public Block argToBlock(int index, World world){
        PyBlock blockParameter = gson.fromJson((String) args.get(index), PyBlock.class);
        return (Block) blockParameter.toBlock(world);
    }

    public Biome argToBiome(int index){
        PyBiome biomeParameter = gson.fromJson((String) args.get(index), PyBiome.class);
        Biome biome = biomeParameter.toBiome();
        return biome;
    }

    public Particle argToParticle(int index) {
        PyParticle pyParticle = gson.fromJson((String) args.get(index), PyParticle.class);
        getLogger().warning("pyParticle: " + pyParticle);
        Particle particle = pyParticle.toParticle();
        return particle;
    }

    public EntityType argToProjectileEntityType(int index) {
        PyProjectile pyProjectile = gson.fromJson((String) args.get(index), PyProjectile.class);
        getLogger().warning("pyProjectile: " + pyProjectile);
        EntityType projectileEntityType = pyProjectile.toProjectileEntityType();
        return projectileEntityType;
    }

    public Villager.Type argToVillagerType(int index) {
        PyVillagerType pyVillagerType = gson.fromJson((String) args.get(index), PyVillagerType.class);
        Villager.Type villagerType = pyVillagerType.toVillagerType();
        return villagerType;
    }

    public Villager.Profession argToVillagerProfession(int index) {
        PyVillagerProfession pyVillagerProfession = gson.fromJson((String) args.get(index), PyVillagerProfession.class);
        Villager.Profession villagerProfession = pyVillagerProfession.toVillagerProfession();
        return villagerProfession;
    }

    public MetadataValue argToMetadataValue(int index, Plugin plugin) {
        PyMetadataValue pyMetadataValue = gson.fromJson((String) args.get(index), PyMetadataValue.class);
        MetadataValue metadataValue = pyMetadataValue.toMetadataValue(plugin);
        return metadataValue;
    }

    public Collection<String> argToStringList(int index) {
        // Used to parse an array of string or to parse an array of JSON (as string) objects before they are converted
        List<String> paramList = (List<String>) args.get(index);
        return (List<String>) args.get(0);
    }

    public Collection<PotionEffect> argToPotionEffects(int index) {
        //Class<Collection<PyPotionEffect>> cls = (Class<Collection<PyPotionEffect>>)(Object)Collection.class;
        Class<List<PyPotionEffect>> cls = (Class<List<PyPotionEffect>>) (Object) List.class;
        getLogger().warning("argToPortionEffects: args" + args + "(" + args.getClass() + ")");
        getLogger().warning("argToPortionEffects: args[0]" + args.get(0) + "(" + args.get(0).getClass() + ")");

        // https://mkyong.com/java/gson-how-to-parse-json-arrays-an-array-of-arrays/
        // No idea what this means
        List<PotionEffect> potionEffects = new ArrayList<>();
        Type listType = new TypeToken<List<PyPotionEffect>>() {
        }.getType();
        // List<String> paramList = (List<String>) args.get(0);
        List<String> paramList = (List<String>) argToStringList(0);
        getLogger().warning("argToPortionEffects: paramList" + paramList + "(" + paramList.getClass() + ")");

        paramList.forEach(p -> {
            PyPotionEffect pyPotionEffect = gson.fromJson(p, PyPotionEffect.class);
            getLogger().warning("argToPotionEffect: p:" + p + " (" + p.getClass() + ").");
            potionEffects.add(pyPotionEffect.toPotionEffect());
        });
        return potionEffects;
    }

    public PyLocation getThingLocation() {
        return thingLocation;
    }

    public Method getMethodFromList(String methodName, Method[] methodList, int parameterCount) {
        // Parameter count < 0 means we don't check parameters
        for (Method method : methodList) {
            if (method.getName().equals(methodName)) {
                if (parameterCount < 0 || parameterCount == method.getParameterCount()) {
                    return method;
                }
            }
        }
        return null;
    }
}

class PySerializedObject {
    // Transient will stop the property from being output to JSON
    private transient Gson gson = new Gson();

    String toJson() {
        return gson.toJson(this);
    }
}

class PyBoundingBox extends PySerializedObject {
    public String _name = "BoundingBox";
    public double x1 = 0;
    public double y1 = 0;
    public double z1 = 0;
    public double x2 = 0;
    public double y2 = 0;
    public double z2 = 0;

    PyBoundingBox() {

    }

    PyBoundingBox(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
        this.z2 = Math.max(z1, z2);
    }

    PyBoundingBox(BoundingBox boundingBox) {
        this.x1 = boundingBox.getMinX();
        this.y1 = boundingBox.getMinY();
        this.z1 = boundingBox.getMinZ();
        this.x2 = boundingBox.getMaxX();
        this.y2 = boundingBox.getMaxY();
        this.z2 = boundingBox.getMaxZ();
    }

    public String toString() {
        return "PyBoundingBox("
                + this.x1 + ","
                + this.y1 + ","
                + this.z1 + ","
                + this.x2 + ","
                + this.y2 + ","
                + this.z2 +
                ")";
    }

    public BoundingBox toBoundingBox() {
        return new BoundingBox(this.x1, this.y1, this.z1, this.x2, this.y2, this.z2);
    }
}

class PyBlock extends PySerializedObject {
    public String _name = "Block";
    public String material = "";
    public String data = "";
    public PyLocation location = new PyLocation(0, 0, 0);

    PyBlock() {

    }

    PyBlock(Location location) {
        Block block = location.getBlock();
        this.material = (String) block.getType().toString();
        this.data = (String) block.getBlockData().toString();
        this.location = (PyLocation) new PyLocation(block.getLocation());
    }

    @Override
    public String toString() {
        return "PyBlock{" +
                "material='" + material + '\'' +
                ", data='" + data + '\'' +
                ", location=" + location +
                '}';
    }

    public Block toBlock(World world){
        Block block = this.location.toLocation(world).getBlock();
        return block;
    }
}

class PyBiome extends PySerializedObject {
    public String _name = "Biome";
    public String name = "";

    PyBiome(){

    }

    PyBiome(String biomeName){
        Biome biome = Biome.valueOf(biomeName);
        this.name = biome.name();
    }

    PyBiome(Biome biome){
        this.name = biome.name();
    }

    public Biome toBiome(){
        Biome biome = Biome.valueOf(this.name);
        return biome;
    }
}

class PyColor extends PySerializedObject {
    public String _name = "Color";
    public int red = 0;
    public int green = 0;
    public int blue = 0;

    PyColor() {

    }

    PyColor(int red, int green, int blue) {
        this.red = red;
        this.blue = blue;
        this.green = green;
    }

    PyColor(Color color) {
        this.red = color.getRed();
        this.green = color.getGreen();
        this.blue = color.getBlue();
    }

    public String toString() {
        return "PyColor(" + red + "," + green + "," + blue + ")";
    }

    Color toColor() {
        return Color.fromRGB(this.red, this.green, this.blue);
    }
}

class PyPotionType extends PySerializedObject {
    public String _name = "PotionType";
    public String type;
    public boolean extendable;
    public boolean upgradeable;

    PyPotionType() {
    }

    PyPotionType(PotionType potionType) {
        getLogger().warning("potionType: " + potionType);
        this.type = (potionType.getEffectType() != null) ? potionType.getEffectType().getName() : "";
        this.extendable = potionType.isExtendable();
        this.upgradeable = potionType.isUpgradeable();
    }

    public String toString() {
        return "PyPotionType("
                + this.type
                + "," + this.extendable
                + "," + this.upgradeable
                + ")";
    }

    public PotionType toPotionType() {
        return PotionType.valueOf(this.type.toUpperCase());
    }
}

class PyPotionData extends PySerializedObject {
    public String _name = "PotionData";
    public PyPotionType type;
    public boolean extended;
    public boolean upgraded;

    PyPotionData() {
    }

    PyPotionData(PotionData potionData) {
        getLogger().warning("potionData: " + potionData);
        this.extended = potionData.isExtended();
        this.upgraded = potionData.isUpgraded();
        this.type = new PyPotionType(potionData.getType());

    }

    public String toString() {
        return "PyPotionData("
                + this.type
                + "," + this.extended
                + "," + this.upgraded
                + ")";
    }

    public PotionData toPotionData() {
        return new PotionData(this.type.toPotionType(), this.extended, this.upgraded);
    }
}

class PyVillagerType extends PySerializedObject {
    public String _name = "VillagerType";
    public String type;

    PyVillagerType() {
    }

    PyVillagerType(String type) {
        this.type = type;
    }

    PyVillagerType(Villager.Type villagerType) {
        this.type = villagerType.name();
    }

    public String toString() {
        return "PyVillagerType("
                + this.type
                + ")";
    }

    Villager.Type toVillagerType() {
        return Villager.Type.valueOf(this.type.toUpperCase());
    }
}

class PyVillagerProfession extends PySerializedObject {
    public String _name = "VillagerProfession";
    public String profession;

    PyVillagerProfession() {
    }

    PyVillagerProfession(String type) {
        this.profession = type;
    }

    PyVillagerProfession(Villager.Profession villagerProfession) {
        this.profession = villagerProfession.name();
    }

    public String toString() {
        return "PyVillagerProfession("
                + this.profession
                + ")";
    }

    Villager.Profession toVillagerProfession() {
        return Villager.Profession.valueOf(this.profession.toUpperCase());
    }
}

class PyParticle extends PySerializedObject {
    public String _name = "Particle";
    public String name;

    PyParticle() {
    }

    PyParticle(String name) {
        this.name = name;
    }

    PyParticle(Particle particle) {
        this.name = particle.name();
    }

    public String toString() {
        return "PyParticle("
                + this.name
                + ")";
    }

    Particle toParticle() {
        return Particle.valueOf(this.name.toUpperCase());
    }
}

class PyProjectile extends PySerializedObject {
    public String _name = "Projectile";
    public String name;

    PyProjectile() {

    }

    public String toString() {
        return "PyProjectile("
                + this.name
                + ")";
    }

    public EntityType toProjectileEntityType() {
        // Here I need to get a class i.e. Arrow.class
        EntityType entityType = (EntityType) EntityType.valueOf(this.name.toUpperCase());

        return entityType;
    }

}

class PyMetadataValue extends PySerializedObject {
    public String _name = "MetadataValue";
    public String value;
    public String type;

    PyMetadataValue() {

    }

    PyMetadataValue(String value) {
        this.value = value;
    }

    PyMetadataValue(String value, String type) {
        this.value = value;
        this.type = type;
    }

    public String toString() {
        return "PyMetadataValue("
                + this.value
                + ")";
    }

    public PyMetadataValue fromMetadataValue(MetadataValue metadataValue) {
        return new PyMetadataValue(metadataValue.asString(), "string");
    }

    public MetadataValue toMetadataValue(Plugin plugin) {
        Object castValue;
        switch (this.type.toLowerCase()) {
            case "int" -> {
                castValue = Integer.parseInt(this.value);
            }
            case "long" -> {
                castValue = Long.parseLong(this.value);
            }
            case "float" -> {
                castValue = Float.parseFloat(this.value);
            }
            case "double" -> {
                castValue = Double.parseDouble(this.value);
            }
            case "boolean" -> {
                castValue = BooleanUtils.toBoolean(this.value);
            }
            default -> {
                castValue = (String) this.value;
            }
        }
        MetadataValue metadataValue = (MetadataValue) new FixedMetadataValue(plugin, castValue);

        return metadataValue;
    }
}

class PyPotionEffect extends PySerializedObject {
    public String _name = "PotionEffect";
    public String potionEffectType;
    public int duration;
    public int amplifier;
    public boolean ambient;
    public boolean particles;
    public boolean icon;

    PyPotionEffect() {

    }

    PyPotionEffect(PotionEffect potionEffect) {
        this.potionEffectType = potionEffect.getType().getName();
        this.duration = potionEffect.getDuration();
        this.amplifier = potionEffect.getAmplifier();
        this.ambient = potionEffect.isAmbient();
        this.particles = potionEffect.hasParticles();
        this.icon = potionEffect.hasIcon();
    }

    public String toString() {
        return "PyPotionEffect("
                + this.potionEffectType
                + "," + this.duration
                + "," + this.amplifier
                + "," + this.ambient
                + "," + this.particles
                + "," + this.icon
                + ")";
    }

    public PotionEffectType getPotionEffectType() {
        return PotionEffectType.getByName(this.potionEffectType.toUpperCase());
    }

    public PotionEffect toPotionEffect() {
        PotionEffect potionEffect = new PotionEffect(this.getPotionEffectType(), this.duration, this.amplifier, this.ambient, this.particles, this.icon);
        getLogger().warning("toPotionEffect: " + potionEffect + toString());
        return potionEffect;
    }
}

//class PyPotionEffectType extends PySerializedObject {
//    public String name;
//    public int id;
//
//    PyPotionEffectType() {
//
//    }
//
//    public String toString() {
//        return "PyPotionEffectType(" + this.name + "," + this.id + ")";
//    }
//
//
//    public PotionEffectType toPotionEffectType() {
//        return PotionEffectType.getByName(this.name.toUpperCase());
//    }
//}


class PyLocation extends PySerializedObject {
    public String _name = "Location";
    public double x = 0;
    public double y = 0;
    public double z = 0;

    PyLocation(int x, int y, int z) {
        this.x = (double) x;
        this.y = (double) y;
        this.z = (double) z;
    }

    PyLocation(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    PyLocation(Location location) {
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
    }

    PyLocation(Vector vector) {
        this.x = vector.getX();
        this.y = vector.getY();
        this.z = vector.getZ();
    }

    public String toString() {
        return "PyLocation(" + this.x + "," + this.y + "," + this.z + ")";
    }

    public Location toLocation(World world) {
        return new Location(world, (double) this.x, (double) this.y, (double) this.z);
    }

    public Vector toVector() {
        return new Vector(this.x, this.y, this.z);
    }

    public int getBlockX() {
        return (int) this.x;
    }

    public int getBlockY() {
        return (int) this.y;
    }

    public int getBlockZ() {
        return (int) this.z;
    }

}

public class PyHelper {
    // No idea how any of this works but here goes...
    String nameDelimeter = "!";
    String fieldDelimeter = "@";

    public PyHelper() {

    }

    public PyHelper(String nameDelimeter, String fieldDelimeter) {
        this.nameDelimeter = nameDelimeter;
        this.fieldDelimeter = fieldDelimeter;
    }

    public String buildSerialized(String... parts) {
        // Return a string in the serialised format : Name!Field&Field&Field
        String nameDelimeter = "!";
        String fieldDelimeter = "@";

        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            stringBuilder.append(parts[i]);
            // If we're not at the end
            if (i < parts.length - 1) {
                // Add either a name delimiter or a field delimiter
                stringBuilder.append(i == 0 ? nameDelimeter : fieldDelimeter);
            }
        }
        return (String) stringBuilder.toString();
    }

    // Convert from MC types to serialized versions
    public String fromPotionEffect(PotionEffect potionEffect) {
        String r = buildSerialized(
                "PotionEffect",
                potionEffect.getType().toString(),
                Integer.toString(potionEffect.getDuration()),
                Integer.toString(potionEffect.getAmplifier()),
                Boolean.toString(potionEffect.isAmbient()),
                Boolean.toString(potionEffect.hasParticles()),
                Boolean.toString(potionEffect.hasIcon())
        );
        getLogger().warning("fromPotionEffect: Got: " + potionEffect.toString() + " Returned: " + r);
        return r;
    }

    public String fromColor(Color color) {
        // Format is Color!255&255&255
        String r = buildSerialized(
                "Color",
                Integer.toString(color.getRed()),
                Integer.toString(color.getGreen()),
                Integer.toString(color.getBlue())
        );
        getLogger().warning("fromColor: Got: " + color.toString() + " Returned: " + r);

        return r;
    }


//    public String fromPotionData(PotionData potionData) {
//        // Format is PotionData
//        String r = buildSerialized(
//                "PotionData",
//
//        )
//    }

    //
    // Convert to MC types from serialized versions
    //
    public PotionEffect toPotionEffect(String potionEffectString) {
        // Format is PotionEffect!type@duration@amplifier@ambient@particles@icon
        getLogger().warning("toPotionEffect: Got: " + potionEffectString);
        String[] all = nameAndParameters(potionEffectString);
        String name = all[0];

        PotionEffectType potionEffectType = PotionEffectType.getByName(all[1].toUpperCase());

        return new PotionEffect(
                potionEffectType,
                Integer.parseInt(all[2]),
                Integer.parseInt(all[3]),
                (boolean) BooleanUtils.toBoolean(all[4]),
                (boolean) BooleanUtils.toBoolean(all[5]),
                (boolean) BooleanUtils.toBoolean(all[6])
        );
    }

    public Color toColor(byte[] colorString) {
//        getLogger().warning("toColor: Got: " + Arrays.toString(colorString));
//        // Format is {red: 23, green: 123, blue: 2}
//        BSONObject object = new BasicBSONDecoder().readObject(colorString);
//        getLogger().warning("object: " + object.toString());
//        //String[] all = nameAndParameters(colorString);
//
//        //String name = all[0];
//        return Color.fromRGB(
//                Integer.parseInt((String) object.get("red")),
//                Integer.parseInt((String) object.get("blue")),
//                Integer.parseInt((String) object.get("green"))
//        );
        return Color.fromRGB(1, 2, 3);
    }

    private String[] serializedOuter(String serializedString) {
        return serializedString.split(this.nameDelimeter);
    }

    private String[] serializedInner(String parameterString) {
        return parameterString.split(this.fieldDelimeter);
    }
//[10:03:33 WARN]: toPotionEffect: Got: PotionEffect!levitation@30@20@true@true@true
//[10:03:33 WARN]: nameAndParameters: Got: PotionEffect!levitation@30@20@true@true@true
//[10:03:33 WARN]: nameAndParameters: outerComponentst: [PotionEffect, levitation@30@20@true@true@true]
//[10:03:33 WARN]: nameAndParameters: name: PotionEffect
//[10:03:33 WARN]: nameAndParameters: innerComponents: [levitation@30@20@true@true@true]
//[10:03:33 WARN]: nameAndParameters: r: [PotionEffect, levitation@30@20@true@true@true]
//[10:03:33 WARN]: nameAndParameters: realReturn: [PotionEffect, levitation@30@20@true@true@true]


    public String[] nameAndParameters(String serialisedString) {
        ArrayList<String> r = new ArrayList<String>();
        getLogger().warning("nameAndParameters: Got: " + serialisedString);
        String[] outerComponents = serializedOuter(serialisedString);
        getLogger().warning("nameAndParameters: outerComponentst: " + Arrays.toString(outerComponents));
        String name = outerComponents[0];
        getLogger().warning("nameAndParameters: name: " + name);
        String[] innerComponents = serializedInner(outerComponents[1]);
        getLogger().warning("nameAndParameters: innerComponents: " + Arrays.toString(innerComponents));

        r.add(name);
        r.addAll(Arrays.asList(innerComponents));
        getLogger().warning("nameAndParameters: r: " + r.toString());
        String[] realReturn = r.toArray(new String[0]);
        getLogger().warning("nameAndParameters: realReturn: " + Arrays.toString(realReturn));

        return realReturn;
    }
}
