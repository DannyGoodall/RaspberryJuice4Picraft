package com.sbp.pythondsminecraft;

//import net.minecraft.server.v1_16_R3.HorseColor;
//import net.minecraft.server.v1_16_R3.HorseStyle;

import com.google.gson.Gson;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.type.*;
import org.bukkit.block.data.type.Comparator;
// import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import org.bukkit.entity.EntityType;

public class PyComplex {
    public pythondsminecraft plugin;
    public RemoteSession session;

    public PyComplex(pythondsminecraft plugin, RemoteSession session) {

        this.plugin = plugin;
        this.session = session;
    }

    public void updateIfBlock(Block blockRecipient, BlockData blockData) {
        if (blockRecipient != null) {
            blockRecipient.setBlockData(blockData);
        }
    }

    public void handleCommand(PythonCommand pythonCommand) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // https://howtodoinjava.com/gson/gson-jsonparser/
        // We've got a JsonObject which should have keys:
        //  example = {
        //      "thing": "world",
        //      "thingID": 1249,
        //      "method": "setColor",
        //      "signature": "void:Color",
        //      "args": [
        //          {
        //              "_name": "Color",
        //              "red": 212,
        //              "green": 21,
        //              "blue": 92
        //          }
        //      ]
        //  }
        plugin.getLogger().warning("PyComplex.handleCommand: thing:          " + pythonCommand.thing);
        plugin.getLogger().warning("PyComplex.handleCommand: thingID:        " + pythonCommand.thingID);
        plugin.getLogger().warning("PyComplex.handleCommand: thingLocation:  " + pythonCommand.thingLocation);
        plugin.getLogger().warning("PyComplex.handleCommand: method:         " + pythonCommand.method);
        plugin.getLogger().warning("PyComplex.handleCommand: signature:      " + pythonCommand.signature);
        plugin.getLogger().warning("PyComplex.handleCommand: args:           " + pythonCommand.args);
        pythonCommand.args.forEach((temp) -> {
            plugin.getLogger().warning("PyComplex.handleCommand: args[]:   " + temp.toString() + " (class):" + temp.getClass());
        });

        String iam = pythonCommand.thing.toLowerCase();
        Class classRecipient = null;
        Block blockRecipient = null;
        Entity entityRecipient = null;
        EntityType entityType = null;
        BlockData blockData = null;
        World world = session.getOrigin().getWorld();
        World worldRecipient = world;
        Method methodToUse = null;

        switch (iam) {
            case "block" -> {
                PyLocation pyLocation = pythonCommand.getThingLocation();
                blockRecipient = world.getBlockAt(pyLocation.getBlockX(), pyLocation.getBlockY(), pyLocation.getBlockZ());
                blockData = blockRecipient.getBlockData();
                classRecipient = blockRecipient.getType().data;
                plugin.getLogger().warning("BLOCK");
                plugin.getLogger().warning("blockRecipient: " + blockRecipient);
                plugin.getLogger().warning("blockData: " + blockData);
            }
            case "entity" -> {
                entityRecipient = plugin.getEntity(pythonCommand.thingID);
                entityType = entityRecipient.getType();
                classRecipient = entityRecipient.getClass();
                plugin.getLogger().warning("ENTITY: ");
                plugin.getLogger().warning("entityRecipient: " + entityRecipient);
                plugin.getLogger().warning("entityType: " + entityType);
            }
            case "world" -> {
                classRecipient = worldRecipient.getClass();
                plugin.getLogger().warning("WORLD: ");
            }
        }
        plugin.getLogger().warning("classRecipient: " + classRecipient);

        // Grab the method to use based on the parameters
        // signature looks like this void:int or int:int:string. I want juse the params, not the return type. i.e.
        //                                int or     int:string
        // Use substring to find the first colon and take the rest of the string after it
        String justArgsSignature = pythonCommand.signature.substring(pythonCommand.signature.indexOf(":") + 1);
        plugin.getLogger().warning("justArgsSignature: " + justArgsSignature);

        // Set methodToUse based on the argument signature
        switch (justArgsSignature) {
            case "void" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method);
            }
            case "int" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, int.class);
            }
            case "string" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, String.class);
            }
            case "boolean" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, boolean.class);
            }
            case "Color" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Color.class);
            }
            case "Leaves" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Bamboo.Leaves.class);
            }
            case "Axis" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Axis.class);
            }
            case "BlockFace" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, BlockFace.class);
            }
            case "BlockFace:boolean" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, BlockFace.class, boolean.class);
            }
            case "Instrument" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Instrument.class);
            }
            case "Note" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Note.class);
            }
            case "Rail" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Rail.Shape.class);
            }
            case "Biscected" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Bisected.class);
            }
            case "Stairs" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Stairs.Shape.class);
            }
            case "Chest" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Chest.Type.class);
            }
            case "Slab" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Slab.Type.class);
            }
            case "ComparatorMode" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Comparator.Mode.class);
            }
            case "StructureBlockMode" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, StructureBlock.Mode.class);
            }
            case "JigsawOrientation" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Jigsaw.Orientation.class);
            }
            case "BedPart" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Bed.Part.class);
            }
            case "AttachedFace" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, FaceAttachable.AttachedFace.class);
            }
            case "BellAttachment" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Bell.Attachment.class);
            }
            case "TechnicalPiston" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, TechnicalPiston.Type.class);
            }
            case "DoorHinge" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Door.Hinge.class);
            }
            case "BlockFace:WallHeight" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, BlockFace.class, Wall.Height.class);
            }
            case "BlockFace:RedstoneWire" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, BlockFace.class, RedstoneWire.Connection.class);
            }
            case "RedstoneWire" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, RedstoneWire.Connection.class);
            }
            case "int:boolean" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, int.class, boolean.class);
            }
            case "double" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, double.class);
            }
            case "DyeColor" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, DyeColor.class);
            }
            case "UUID" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, UUID.class);
            }
            case "EntityType" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, EntityType.class);
            }
            case "Entity" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Entity.class);
            }
            case "Location" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Location.class);
            }
            case "LivingEntity" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, LivingEntity.class);
            }
            case "long" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, long.class);
            }
            case "AnimalTamer" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, AnimalTamer.class);
            }
            case "HorseColor" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Horse.Color.class);
            }
            case "HorseStyle" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Horse.Style.class);
            }
            case "Location:TreeType" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Location.class, TreeType.class);
            }
            case "float" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, float.class);
            }
            case "PotionEffect" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, PotionEffect.class);
            }
            case "PotionEffect:boolean" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, PotionEffect.class, boolean.class);
            }
            case "PotionEffects" -> {
                // Class<?>: (Class<Collection<PotionEffect>>)(Class<?>)Collection.class
                // https://stackoverflow.com/a/30754982/1300916
                Class<Collection<PotionEffect>> cls = (Class<Collection<PotionEffect>>) (Object) Collection.class;
                methodToUse = classRecipient.getMethod(pythonCommand.method, cls);
            }
            case "PotionEffectType" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, PotionEffectType.class);
            }
            case "PotionData" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, PotionData.class);
            }
            case "Particle" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Particle.class);
            }
//            case "Projectile" -> {
//                methodToUse = classRecipient.getMethod(pythonCommand.method, Entity.class);
//            }
//            case "Projectile:Velocity" -> {
//                methodToUse = classRecipient.getMethod(pythonCommand.method, Entity.class, Vector.class);
//            }
            case "ProjectileSource" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, ProjectileSource.class);
            }
            case "VillagerType" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Villager.Type.class);
            }
            case "VillagerProfession" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, Villager.Profession.class);
            }
            case "String:MetadataValue" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, String.class, MetadataValue.class);
            }
            case "String:Plugin" -> {
                methodToUse = classRecipient.getMethod(pythonCommand.method, String.class, Plugin.class);
            }
            case "Location:double:double:double" -> {
                methodToUse = classRecipient.getMethod(
                        pythonCommand.method,
                        Location.class,
                        double.class,
                        double.class,
                        double.class
                );
            }
            case "BoundingBox" -> {
                methodToUse = classRecipient.getMethod(
                        pythonCommand.method,
                        BoundingBox.class
                );
            }
            case "int:int" -> {
                methodToUse = classRecipient.getMethod(
                        pythonCommand.method,
                        int.class,
                        int.class
                );
            }
            case "int:int:int" -> {
                methodToUse = classRecipient.getMethod(
                        pythonCommand.method,
                        int.class,
                        int.class,
                        int.class
                );
            }
            case "int:int:int:Biome" -> {
                methodToUse = classRecipient.getMethod(
                        pythonCommand.method,
                        int.class,
                        int.class,
                        int.class,
                        Biome.class
                );
            }
        }
        plugin.getLogger().warning("methodToUse: " + methodToUse);

        switch (pythonCommand.signature) {
            case "void:void" -> {
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
            }
            case "boolean:void" -> {
                boolean booleanReturn = (boolean) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendBoolean(booleanReturn);
            }
            case "void:boolean" -> {
                boolean booleanParameter = pythonCommand.argToBoolean(0);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        booleanParameter
                );
            }
            case "int:void" -> {
                int intReturn = (int) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendInt(intReturn);
            }
            case "void:int" -> {
                int intParameter = pythonCommand.argToInteger(0);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        intParameter
                );
            }
            case "void:double" -> {
                double doubleParameter = (double) pythonCommand.argToDouble(0);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        doubleParameter
                );
            }
            case "double:void" -> {
                double doubleReturn = (double) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendDouble(doubleReturn);
            }
            case "string:void" -> {
                String stringReturn = (String) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(stringReturn);
            }
            case "void:string" -> {
                String stringParameter = pythonCommand.argToString(0);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        stringParameter
                );
            }
            case "Leaves:void" -> {
                Bamboo.Leaves leavesReturn = (Bamboo.Leaves) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendBambooLeaves(leavesReturn);
            }
            case "void:Leaves" -> {
                Bamboo.Leaves leavesParameter = Bamboo.Leaves.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        leavesParameter
                );
            }
            case "void:Color" -> {
                Color colorParameter = pythonCommand.argToColor(0);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        colorParameter
                );
            }
            case "Color:void" -> {
                Color colorReturn = (Color) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendColor(colorReturn);
            }
            case "void:Axis" -> {
                Axis axisParameter = Axis.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        axisParameter
                );
            }
            case "Axis:void" -> {
                Axis axisReturn = (Axis) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(axisReturn.toString());
            }
            case "Axes:void" -> {
                Set<Axis> axesReturn = (Set<Axis>) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendAxes(axesReturn);
            }
            case "void:BlockFace" -> {
                BlockFace blockFaceParameter = BlockFace.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        blockFaceParameter
                );
            }
            case "BlockFace:void" -> {
                BlockFace blockFaceReturn = (BlockFace) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(blockFaceReturn.toString());
            }
            case "boolean:BlockFace" -> {
                BlockFace blockFaceParameter = BlockFace.valueOf(pythonCommand.argToStringUpper(0));
                BlockFace blockFaceReturn = (BlockFace) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        blockFaceParameter
                );
                sendString(blockFaceReturn.toString());
            }
            case "void:BlockFace:boolean" -> {
                BlockFace blockFaceParameter = BlockFace.valueOf(pythonCommand.argToStringUpper(0));
                boolean booleanParameter = pythonCommand.argToBoolean(1);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        blockFaceParameter,
                        booleanParameter
                );
            }
            case "BlockFaces:void" -> {
                Set<BlockFace> blockFaces = (Set<BlockFace>) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendBlockFaces(blockFaces);
            }
            case "void:Instrument" -> {
                Instrument instrumentParameter = (Instrument) Instrument.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        instrumentParameter
                );
            }
            case "Instrument:void" -> {
                Instrument instrumentReturn = (Instrument) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(instrumentReturn.toString());
            }
            case "void:Note" -> {
                Note noteParameter = (Note) new Note(pythonCommand.argToInteger(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        noteParameter
                );
            }
            case "Note:void" -> {
                Note noteReturn = (Note) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(noteReturn.toString());
            }
            case "void:Rail" -> {
                Rail.Shape railParameter = (Rail.Shape) Rail.Shape.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        railParameter
                );
            }
            case "Rail:void" -> {
                Rail.Shape railReturn = (Rail.Shape) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(railReturn.toString());
            }
            case "Rails:void" -> {
                Set<Rail.Shape> railsReturn = (Set<Rail.Shape>) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendRails(railsReturn);
            }
            case "void:Biscected" -> {
                Bisected.Half biscectedParameter = (Bisected.Half) Bisected.Half.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        biscectedParameter
                );
            }
            case "Biscected:void" -> {
                Bisected.Half bisectedReturn = (Bisected.Half) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(bisectedReturn.toString());
            }
            case "void:Stairs" -> {
                Stairs.Shape stairsParameter = (Stairs.Shape) Stairs.Shape.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        stairsParameter
                );
            }
            case "Stairs:void" -> {
                Stairs.Shape stairsReturn = (Stairs.Shape) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(stairsReturn.toString());
            }
            case "void:Chest" -> {
                Chest.Type chestParameter = (Chest.Type) Chest.Type.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        chestParameter
                );
            }
            case "Chest:void" -> {
                Chest.Type chestReturn = (Chest.Type) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(chestReturn.toString());
            }
            case "void:Slab" -> {
                Slab.Type slabParameter = (Slab.Type) Slab.Type.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        slabParameter
                );
            }
            case "Slab:void" -> {
                Slab.Type slabReturn = (Slab.Type) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(slabReturn.toString());
            }
            case "void:ComparatorMode" -> {
                Comparator.Mode comparatorModeParameter = (Comparator.Mode) Comparator.Mode.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        comparatorModeParameter
                );
            }
            case "ComparatorMode:void" -> {
                Comparator.Mode comparatorModeReturn = (Comparator.Mode) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(comparatorModeReturn.toString());
            }
            case "void:StructureBlockMode" -> {
                StructureBlock.Mode structureBlockModeParameter = (StructureBlock.Mode) StructureBlock.Mode.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        structureBlockModeParameter
                );
            }
            case "StructureBlockMode:void" -> {
                StructureBlock.Mode structureBlockModeReturn = (StructureBlock.Mode) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(structureBlockModeReturn.toString());
            }
            case "void:JigsawOrientation" -> {
                Jigsaw.Orientation jigsawOrientationParameter = (Jigsaw.Orientation) Jigsaw.Orientation.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        jigsawOrientationParameter
                );
            }
            case "JigsawOrientation:void" -> {
                Jigsaw.Orientation jigsawOrientationReturn = (Jigsaw.Orientation) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(jigsawOrientationReturn.toString());
            }
            case "void:BedPart" -> {
                Bed.Part bedPartParameter = (Bed.Part) Bed.Part.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        bedPartParameter
                );
            }
            case "BedPart:void" -> {
                Bed.Part bedPartReturn = (Bed.Part) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(bedPartReturn.toString());
            }
            case "void:AttachedFace" -> {
                FaceAttachable.AttachedFace attachedFaceParameter = (FaceAttachable.AttachedFace) FaceAttachable.AttachedFace.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        attachedFaceParameter
                );
            }
            case "AttachedFace:void" -> {
                FaceAttachable.AttachedFace attachedFaceReturn = (FaceAttachable.AttachedFace) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(attachedFaceReturn.toString());
            }
            case "void:BellAttachment" -> {
                Bell.Attachment bellAttachmentParameter = (Bell.Attachment) Bell.Attachment.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        bellAttachmentParameter
                );
            }
            case "BellAttachment:void" -> {
                Bell.Attachment bellAttachmentReturn = (Bell.Attachment) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(bellAttachmentReturn.toString());
            }
            case "void:TechnicalPiston" -> {
                TechnicalPiston.Type technicalPistonParameter = (TechnicalPiston.Type) TechnicalPiston.Type.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        technicalPistonParameter
                );
            }
            case "TechnicalPiston:void" -> {
                TechnicalPiston.Type technicalPistonReturn = (TechnicalPiston.Type) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(technicalPistonReturn.toString());
            }
            case "void:DoorHinge" -> {
                Door.Hinge doorHingeParameter = (Door.Hinge) Door.Hinge.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        doorHingeParameter
                );
            }
            case "DoorHinge:void" -> {
                Door.Hinge doorHingeReturn = (Door.Hinge) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendString(doorHingeReturn.toString());
            }
            case "void:BlockFace:WallHeight" -> {
                BlockFace blockFaceParameter = (BlockFace) BlockFace.valueOf(pythonCommand.argToStringUpper(0));
                Wall.Height wallHeightParameter = (Wall.Height) Wall.Height.valueOf(pythonCommand.argToStringUpper(1));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        blockFaceParameter,
                        wallHeightParameter
                );
            }
            case "WallHeight:BlockFace" -> {
                BlockFace blockFaceParameter = (BlockFace) BlockFace.valueOf(pythonCommand.argToStringUpper(0));
                Wall.Height wallHeightReturn = (Wall.Height) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        blockFaceParameter
                );
                sendString(wallHeightReturn.toString());
            }
            case "void:BlockFace:RedstoneWire" -> {
                BlockFace blockFaceParameter = (BlockFace) BlockFace.valueOf(pythonCommand.argToStringUpper(0));
                RedstoneWire.Connection redstoneWireConnectionParameter =
                        (RedstoneWire.Connection) RedstoneWire.Connection.valueOf(pythonCommand.argToStringUpper(1));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        blockFaceParameter,
                        redstoneWireConnectionParameter
                );
            }
            case "RedstoneWire:BlockFace" -> {
                BlockFace blockFaceParameter = (BlockFace) BlockFace.valueOf(pythonCommand.argToStringUpper(0));
                RedstoneWire.Connection redstoneWireConnectionReturn = (RedstoneWire.Connection) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        blockFaceParameter
                );
                sendString(redstoneWireConnectionReturn.toString());
            }
            case "void:int:boolean" -> {
                int intParameter = (int) pythonCommand.argToInteger(0);
                boolean booleanParameter = (boolean) pythonCommand.argToBoolean(1);
                RedstoneWire.Connection redstoneWireConnectionReturn = (RedstoneWire.Connection) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        intParameter,
                        booleanParameter
                );
            }
            case "boolean:int" -> {
                int intParameter = (int) pythonCommand.argToInteger(0);
                boolean booleanReturn = (boolean) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        intParameter
                );
                sendBoolean(booleanReturn);
            }
            case "Ints:void" -> {
                Set<Integer> intsReturn = (Set<Integer>) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendInts(intsReturn);
            }
            // Entity-based methods here
            case "void:DyeColor" -> {
                DyeColor dyeColorParameter = (DyeColor) DyeColor.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        dyeColorParameter
                );
            }
            case "DyeColor:void" -> {
                DyeColor dyeColorReturn = (DyeColor) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendDyeColor(dyeColorReturn);
            }
            case "void:UUID" -> {
                UUID uuidParameter = (UUID) UUID.fromString(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        uuidParameter
                );
            }
            case "UUID:void" -> {
                UUID uuidReturn = (UUID) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendUUID(uuidReturn);
            }
            case "void:EntityType" -> {
                EntityType entityTypeParameter = EntityType.valueOf(pythonCommand.argToStringUpper(0));
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        entityTypeParameter
                );
            }
            case "EntityType:void" -> {
                EntityType entityTypeReturn = (EntityType) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendEntityType(entityTypeReturn);
            }
            case "boolean:Entity" -> {
                int intParameter = (int) pythonCommand.argToInteger(0);
                Entity entityParameter = plugin.getEntity(intParameter);
                boolean booleanReturn = (boolean) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        entityParameter
                );
                sendBoolean(booleanReturn);
            }
            case "boolean:string" -> {
                String stringParameter = (String) pythonCommand.argToStringUpper(0);
                boolean booleanReturn = (boolean) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        stringParameter
                );
                sendBoolean(booleanReturn);
            }
            case "boolean:Location" -> {
                Location locationParameter = (Location) pythonCommand.argToLocation(0, world);
                boolean booleanReturn = (boolean) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        locationParameter
                );
                sendBoolean(booleanReturn);
            }
            case "void:Location" -> {
                Location locationParameter = (Location) pythonCommand.argToLocation(0, world);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        locationParameter
                );
            }
            case "Location:void" -> {
                Location locationReturn = (Location) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendLocation(locationReturn);
            }
            case "long:void" -> {
                long longReturn = (long) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendLong(longReturn);
            }
            case "void:long" -> {
                long longParameter = (long) pythonCommand.argToLong(0);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        longParameter
                );
            }
            case "LivingEntity:void" -> {
                LivingEntity livingEntityReturn = (LivingEntity) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendLivingEntity(livingEntityReturn);
            }
            case "void:LivingEntity" -> {
                int intParameter = (int) pythonCommand.argToInteger(0);
                LivingEntity livingEntity = (LivingEntity) plugin.getEntity(intParameter);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        livingEntity
                );
            }
            case "AnimalTamer:void" -> {
                // NEEDS TESTING - ANIMAL TAMER DOESN'T PRESENT a getEntityID() method - is it derived from Entity?
                AnimalTamer animalTamerReturn = (AnimalTamer) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendAnimalTamer(animalTamerReturn);
            }
            case "void:AnimalTamer" -> {
                // NEEDS TESTING - ANIMAL TAMER DOESN'T PRESENT a getEntityID() method - is it derived from Entity?
                int intParameter = (int) pythonCommand.argToInteger(0);
                AnimalTamer animalTamer = (AnimalTamer) plugin.getEntity(intParameter);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        animalTamer
                );
            }
            case "HorseColor:void" -> {
                Horse.Color horseColor = (Horse.Color) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendHorseColor(horseColor);
            }
            case "void:HorseColor" -> {
                String stringParameter = (String) pythonCommand.argToStringUpper(0);
                Horse.Color horseColor = (Horse.Color) Horse.Color.valueOf(stringParameter);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        horseColor
                );
            }
            case "HorseStyle:void" -> {
                Horse.Style horseStyle = (Horse.Style) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendHorseStyle(horseStyle);
            }
            case "void:HorseStyle" -> {
                String stringParameter = (String) pythonCommand.argToStringUpper(0);
                Horse.Style horseStyle = (Horse.Style) Horse.Style.valueOf(stringParameter);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        horseStyle
                );
            }
            case "Entity:void" -> {
                Entity entityReturn = (Entity) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendEntity(entityReturn);
            }
            case "void:Entity" -> {
                int intParameter = (int) pythonCommand.argToInteger(0);
                Entity entity = (Entity) plugin.getEntity(intParameter);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        entity
                );
            }
            case "LightningStrike:Location" -> {
                Location locationParameter = (Location) pythonCommand.argToLocation(0, world);
                LightningStrike lightningStrike = (LightningStrike) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        locationParameter
                );
                sendLightningString(lightningStrike);
            }
            case "boolean:Location:TreeType" -> {
                Location locationParameter = (Location) pythonCommand.argToLocation(0, world);
                String stringParameter = (String) pythonCommand.argToStringUpper(1);
                TreeType treeType = (TreeType) TreeType.valueOf(stringParameter);

                boolean booleanReturn = (boolean) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        locationParameter,
                        treeType
                );
                sendBoolean(booleanReturn);
            }
            case "Strings:void" -> {
                String[] stringsReturn = (String[]) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendStrings(stringsReturn);
            }
            case "StringSet:void" -> {
                Set<String> stringSet = (Set<String>) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendStringSet(stringSet);
            }
            case "Vector:void" -> {
                Vector vector = (Vector) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendVector(vector);
            }
            case "void:Vector" -> {
                Vector vectorParameter = pythonCommand.argToVector(0);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        vectorParameter
                );
            }
            case "float:void" -> {
                float floatReturn = (float) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                sendFloat(floatReturn);
            }
            case "void:float" -> {
                float floatParameter = pythonCommand.argToFloat(0);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        floatParameter
                );
            }
            case "boolean:PotionEffect" -> {
                PotionEffect potionEffectParameter = pythonCommand.argToPotionEffect(0);
                boolean booleanReturn = (boolean) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        potionEffectParameter
                );
                sendBoolean(booleanReturn);
            }
            case "boolean:PotionEffect:boolean" -> {
                PotionEffect potionEffectParameter = pythonCommand.argToPotionEffect(0);
                boolean booleanParameter = pythonCommand.argToBoolean(1);
                boolean booleanReturn = (boolean) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        potionEffectParameter,
                        booleanParameter
                );
                sendBoolean(booleanReturn);
            }
            case "boolean:PotionEffects" -> {
                Collection<PotionEffect> potionEffectsParameter = pythonCommand.argToPotionEffects(0);
                boolean booleanReturn = (boolean) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        potionEffectsParameter
                );
                sendBoolean(booleanReturn);
            }
            case "PotionEffects:void" -> {
                Collection<PotionEffect> potionEffects = (Collection<PotionEffect>) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                ArrayList<String> potionNames = new ArrayList<>();
                for (PotionEffect p : potionEffects) {
                    potionNames.add(p.getType().getName().toUpperCase());
                }
                sendStrings(potionNames);
            }
            case "Entities:Location:double:double:double" -> {
                Location locationParameter = (Location) pythonCommand.argToLocation(0, world);
                double doubleParameterX = (double) pythonCommand.argToDouble(1);
                double doubleParameterY = (double) pythonCommand.argToDouble(2);
                double doubleParameterZ = (double) pythonCommand.argToDouble(3);

                Collection<Entity> entities = (Collection<Entity>) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        locationParameter,
                        doubleParameterX,
                        doubleParameterY,
                        doubleParameterZ
                );
                sendEntities(entities);
            }
            case "Entities:BoundingBox" -> {
                BoundingBox boundingBoxParameter = (BoundingBox) pythonCommand.argToBoundingBox(0);
                plugin.getLogger().warning("ENTITIES:BoundingBox: " + boundingBoxParameter.toString());
                Collection<Entity> entities = (Collection<Entity>) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        boundingBoxParameter
                );
                plugin.getLogger().warning("entities: " + entities.toString());
                sendEntities(entities);
            }
            case "Block:int:int" -> {
                Integer xParameter = (int) pythonCommand.argToInteger(0);
                Integer zParameter = (int) pythonCommand.argToInteger(1);
                //plugin.getLogger().warning("ENTITIES:BoundingBox: " + boundingBoxParameter.toString());
                Block block  = (Block) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        xParameter,
                        zParameter
                );
                plugin.getLogger().warning("block: " + block.toString());
                sendBlock(block.getLocation());
            }
            case "int:int:int" -> {
                Integer xParameter = (int) pythonCommand.argToInteger(0);
                Integer zParameter = (int) pythonCommand.argToInteger(1);
                //plugin.getLogger().warning("ENTITIES:BoundingBox: " + boundingBoxParameter.toString());
                Integer integerReturn  = (Integer) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        xParameter,
                        zParameter
                );
                plugin.getLogger().warning("int: " + integerReturn.toString());
                sendInt(integerReturn);
            }
            case "void:int:int:int:Biome" -> {
                Integer xParameter = (int) pythonCommand.argToInteger(0);
                Integer yParameter = (int) pythonCommand.argToInteger(1);
                Integer zParameter = (int) pythonCommand.argToInteger(2);
                Biome biomeParameter = (Biome) pythonCommand.argToBiome(3);
                //plugin.getLogger().warning("ENTITIES:BoundingBox: " + boundingBoxParameter.toString());
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        xParameter,
                        yParameter,
                        zParameter,
                        biomeParameter
                );
            }
            case "Biome:int:int:int" -> {
                Integer xParameter = (int) pythonCommand.argToInteger(0);
                Integer yParameter = (int) pythonCommand.argToInteger(1);
                Integer zParameter = (int) pythonCommand.argToInteger(2);
                //plugin.getLogger().warning("ENTITIES:BoundingBox: " + boundingBoxParameter.toString());
                Biome biomeReturn  = (Biome) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        xParameter,
                        yParameter,
                        zParameter
                );
                plugin.getLogger().warning("biome: " + biomeReturn.toString());
                sendBiome(biomeReturn);
            }
            case "double:int:int:int" -> {
                Integer xParameter = (int) pythonCommand.argToInteger(0);
                Integer yParameter = (int) pythonCommand.argToInteger(1);
                Integer zParameter = (int) pythonCommand.argToInteger(2);
                //plugin.getLogger().warning("ENTITIES:BoundingBox: " + boundingBoxParameter.toString());
                Double doubleReturn  = (Double) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        xParameter,
                        yParameter,
                        zParameter
                );
                plugin.getLogger().warning("doubleReturn: " + doubleReturn.toString());
                sendDouble(doubleReturn);
            }
            case "PotionEffect:PotionEffectType" -> {
                String potionEffectTypeName = pythonCommand.argToStringUpper(0);
                PotionEffectType potionEffectType = PotionEffectType.getByName(potionEffectTypeName);

                PotionEffect potionEffect = (PotionEffect) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        potionEffectType
                );
                PyPotionEffect pyPotionEffect = new PyPotionEffect(potionEffect);
                sendPotionEffect(pyPotionEffect);
            }
            case "boolean:PotionEffectType" -> {
                String potionEffectTypeName = pythonCommand.argToStringUpper(0);
                PotionEffectType potionEffectType = PotionEffectType.getByName(potionEffectTypeName);

                boolean booleanReturn = (boolean) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        potionEffectType
                );
                sendBoolean(booleanReturn);
            }
            case "void:PotionEffectType" -> {
                String potionEffectTypeName = pythonCommand.argToStringUpper(0);
                PotionEffectType potionEffectType = PotionEffectType.getByName(potionEffectTypeName);

                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        potionEffectType
                );
            }
            case "PotionData:void" -> {
                PotionData potionData = (PotionData) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                PyPotionData pyPotionData = new PyPotionData(potionData);
                sendPotionData(pyPotionData);
            }
            case "void:PotionData" -> {
                PotionData potionData = (PotionData) pythonCommand.argToPotionData(0);

                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        potionData
                );
            }
            case "void:Particle" -> {
                Particle particle = (Particle) pythonCommand.argToParticle(0);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        particle
                );
            }
            case "Particle:void" -> {
                Particle particle = (Particle) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                PyParticle pyParticle = new PyParticle(particle);
                sendParticle(pyParticle);
            }
//            case "ProjectileSource:void" -> {
//                ProjectileSource projectileSource = (ProjectileSource) methodToUse.invoke(
//                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
//                );
//                PyParticle pyParticle = new PyParticle(particle);
//                sendParticle(pyParticle);
//            }
            case "void:Projectile" -> {
                EntityType projectileEntityType = pythonCommand.argToProjectileEntityType(0);
                // THERE MUST BE A WAY TO GET THIS METHOD THROUGH REFLECTION?
                Method[] methodList = classRecipient.getMethods();
                methodToUse = pythonCommand.getMethodFromList(pythonCommand.method, methodList, 1);

                Projectile shot = (Projectile) methodToUse.invoke(
                        (Entity) entityRecipient,
                        // (Class<? extends Projectile>) DragonFireball.class
                        projectileEntityType.getEntityClass()
                );
            }
            case "void:Projectile:Velocity" -> {
                EntityType projectileEntityType = pythonCommand.argToProjectileEntityType(0);
                Vector vector = pythonCommand.argToVector(1);
                plugin.getLogger().warning("projectileEntityType: " + projectileEntityType);
                plugin.getLogger().warning("vector: " + vector);


                // THERE MUST BE A WAY TO GET THIS METHOD THROUGH REFLECTION?
                Method[] methodList = classRecipient.getMethods();
                methodToUse = pythonCommand.getMethodFromList(pythonCommand.method, methodList, 2);

                Projectile shot = (Projectile) methodToUse.invoke(
                        (Entity) entityRecipient,
                        // (Class<? extends Projectile>) DragonFireball.class
                        projectileEntityType.getEntityClass(),
                        vector
                );
                plugin.getLogger().warning("shot: " + shot);
            }
            case "void:VillagerType" -> {
                Villager.Type villagerType = pythonCommand.argToVillagerType(0);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        villagerType
                );
            }
            case "VillagerType:void" -> {
                Villager.Type villagerType = (Villager.Type) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                PyVillagerType pyVillagerType = new PyVillagerType(villagerType);
                sendVillagerType(pyVillagerType);
            }
            case "void:VillagerProfession" -> {
                Villager.Profession villagerProfession = pythonCommand.argToVillagerProfession(0);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        villagerProfession
                );
            }
            case "VillagerProfession:void" -> {
                Villager.Profession villagerProfession = (Villager.Profession) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient
                );
                PyVillagerProfession pyVillagerProfession = new PyVillagerProfession(villagerProfession);
                sendVillagerProfession(pyVillagerProfession);
            }
            case "void:String:MetadataValue" -> {
                String metadataKey = pythonCommand.argToString(0);
                MetadataValue metadataValue = pythonCommand.argToMetadataValue(1, plugin);
                methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        metadataKey,
                        metadataValue
                );
            }
            case "MetadataValues:string" -> {
                String metadataKey = pythonCommand.argToString(0);
                plugin.getLogger().warning("metadataKey: " + metadataKey);
                //List<MetadataValue> dgMetaData = (List<MetadataValue>) plugin.getHostPlayer().getMetadata(metadataKey);

                List<MetadataValue> metadataValues = (List<MetadataValue>) methodToUse.invoke(
                        iam.equals("block") ? classRecipient.cast(blockData) : iam.equals("entity") ? entityRecipient : worldRecipient,
                        metadataKey
                );
                plugin.getLogger().warning("metadataValues[]: " + metadataValues.toString());
                ArrayList<PyMetadataValue> pyMetadataValues = new ArrayList<>();
                for (MetadataValue m : metadataValues) {
                    pyMetadataValues.add(new PyMetadataValue().fromMetadataValue(m));
                }
                plugin.getLogger().warning("pyMetadataValues: " + pyMetadataValues.toString());
                sendMetadataValues(pyMetadataValues);

            }
            default -> {
                plugin.getLogger().warning("Didn't find the correct method signature for[" + pythonCommand.signature + "]");
            }
        }
        // If we sent a parameter then we should update the block (if this is a block method)
        if (!justArgsSignature.equals("void")) {
            updateIfBlock(blockRecipient, blockData);
        }
    }

    public void sendBoolean(boolean b) {
        session.send(Boolean.toString(b));
    }

    public void sendInt(int i) {
        session.send(Integer.toString(i));
    }

    public void sendLong(long l) {
        session.send(Long.toString(l));
    }

    public void sendFloat(float f) {
        session.send(Float.toString(f));
    }

    public void sendDouble(double d) {
        session.send(Double.toString(d));
    }

    public void sendString(String s) {
        session.send(s);
    }

    public void sendStrings(String[] s) {
        session.send(Arrays.toString(s));
    }

    public void sendStrings(ArrayList<String> s) {
        session.send(Arrays.toString(s.toArray()));
    }

    public void sendStringSet(Set<String> s) {
        session.send(s.toString());
    }

    public void sendBambooLeaves(Bamboo.Leaves l) {
        session.send(l.toString());
    }

    public void sendAxes(Set<Axis> a) {
        session.send(a.toString());
    }

    public void sendRails(Set<Rail.Shape> r) {
        session.send(r.toString());
    }

    public void sendBlockFaces(Set<BlockFace> b) {
        session.send(b.toString());
    }

    public void sendInts(Set<Integer> i) {
        session.send(i.toString());
    }

    public void sendColor(Color color) {
        PyColor pyColor = new PyColor(color);
        session.send(pyColor.toJson());
    }

    public void sendLocation(Location location) {
        PyLocation pyLocation = new PyLocation(location);
        session.send(pyLocation.toJson());
    }

    public void sendVector(Vector vector) {
        PyLocation pyLocation = new PyLocation(vector);
        session.send(pyLocation.toJson());
    }

    public void sendBoundingBox(BoundingBox boundingBox) {
        // NOT TESTED
        PyBoundingBox pyBoundingBox = new PyBoundingBox(boundingBox);
        session.send(pyBoundingBox.toJson());
    }

    public void sendBlock(Location location){
        PyBlock pyBlock = new PyBlock(location);
        session.send(pyBlock.toJson());
    }

    public void sendBiome(Biome biome){
        PyBiome pyBiome = new PyBiome(biome);
        session.send(pyBiome.toJson());
    }

    public void sendDyeColor(DyeColor dyeColor) {
        session.send(dyeColor.toString());
    }

    public void sendHorseColor(Horse.Color horseColor) {
        session.send(horseColor.toString());
    }

    public void sendHorseStyle(Horse.Style horseStyle) {
        session.send(horseStyle.toString());
    }

    public void sendUUID(UUID uuid) {
        session.send(uuid.toString());
    }

    public void sendLightningString(LightningStrike lightningStrike) {
        session.send(lightningStrike.toString());
    }

    public void sendEntityType(EntityType entityType) {
        session.send(entityType.toString());
    }

    public void sendEntity(Entity entity) {
        //Send back the entity ID
        int entityID = entity.getEntityId();
        session.send(Integer.toString(entityID));
    }

    public void sendEntities(Collection<Entity> entities) {
        ArrayList<String> entityIds = new ArrayList<>();

        for (Entity e : entities) {
            entityIds.add(Integer.toString(e.getEntityId()));
        }
        session.send(String.join(",", entityIds));
    }

    public void sendLivingEntity(LivingEntity livingEntity) {
        //Send back the entity ID
        int entityID = livingEntity.getEntityId();
        session.send(Integer.toString(entityID));
    }

    public void sendAnimalTamer(AnimalTamer animalTamer) {
        //Send back the entity ID
        Entity e = (Entity) animalTamer;
        int entityID = e.getEntityId();
        session.send(Integer.toString(entityID));
    }

    public void sendPotionEffect(PyPotionEffect pyPotionEffect) {
        session.send(pyPotionEffect.toJson());
    }

    public void sendPotionData(PyPotionData pyPotionData) {
        session.send(pyPotionData.toJson());
    }

    public void sendParticle(PyParticle pyParticle) {
        session.send(pyParticle.toJson());
    }

    public void sendVillagerProfession(PyVillagerProfession pyVillagerProfession) {
        session.send(pyVillagerProfession.toJson());
    }

    public void sendVillagerType(PyVillagerType pyVillagerType) {
        session.send(pyVillagerType.toJson());
    }

    public void sendMetadataValues(ArrayList<PyMetadataValue> pyMetadataValues) {
        Gson gson = new Gson();
        String r = gson.toJson(pyMetadataValues);
        session.send(r);
    }

}
