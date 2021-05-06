package com.sbp.pythondsminecraft;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.potion.PotionEffect;
import org.apache.commons.lang.BooleanUtils;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    public JsonElement argToJSONElement(int index){
        JsonElement jsonElement = gson.fromJson((String) args.get(index), JsonElement.class);
        getLogger().warning("PyHelper: jsonElement: "+ jsonElement.toString());
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

    public String argToStringUpper(int index){
        return argToString(index).toUpperCase();
    }

    public String argToStringLower(int index){
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

    public PyLocation getThingLocation() {
        return thingLocation;
    }
}

class PySerializedObject {
    // Transient will stop the property from being output to JSON
    private transient Gson gson = new Gson();

    String toJson() {
        return gson.toJson(this);
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

class PyLocation extends PySerializedObject {
    public String _name = "MCLocation";
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

    public Vector toVector(){
        return new Vector(this.x, this.y, this.z);
    }

    public int getBlockX(){
        return (int) this.x;
    }

    public int getBlockY(){
        return (int) this.y;
    }

    public int getBlockZ(){
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
