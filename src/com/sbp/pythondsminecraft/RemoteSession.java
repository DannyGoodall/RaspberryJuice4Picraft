package com.sbp.pythondsminecraft;

//https://getbukkit.org/download/craftbukkit  version 1.15.1
// Python dans Serveur Minecraft
// SBP - JANVIER 2019

import java.io.BufferedReader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang.BooleanUtils;
import org.bukkit.*;
//import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.type.*;
import org.bukkit.block.data.type.Comparator;
import org.bukkit.entity.*;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;


public class RemoteSession {

    private final LocationType locationType;

    private Location origin;

    private Socket socket;

    private BufferedReader in;

    private BufferedWriter out;

    private Thread inThread;

    private Thread outThread;

    private ArrayDeque<String> inQueue = new ArrayDeque<String>();

    private ArrayDeque<String> outQueue = new ArrayDeque<String>();

    public boolean running = true;

    public boolean pendingRemoval = false;

    public pythondsminecraft plugin;

    protected ArrayDeque<PlayerInteractEvent> interactEventQueue = new ArrayDeque<PlayerInteractEvent>();

    protected ArrayDeque<AsyncPlayerChatEvent> chatPostedQueue = new ArrayDeque<AsyncPlayerChatEvent>();

    protected ArrayDeque<ProjectileHitEvent> projectileHitQueue = new ArrayDeque<ProjectileHitEvent>();

    private int maxCommandsPerTick = 9000;

    private boolean closed = false;

    private Player attachedPlayer = null;

    public RemoteSession(pythondsminecraft plugin, Socket socket) throws IOException {
        this.socket = socket;
        this.plugin = plugin;
        this.locationType = plugin.getLocationType();
        init();
    }

    public void init() throws IOException {
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        socket.setTrafficClass(0x10);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        startThreads();
        plugin.getLogger().info("Opened connection to" + socket.getRemoteSocketAddress() + ".");
    }

    protected void startThreads() {
        inThread = new Thread(new InputThread());
        inThread.start();
        outThread = new Thread(new OutputThread());
        outThread.start();
    }


    public Location getOrigin() {
        return origin;
    }

    public void setOrigin(Location origin) {
        this.origin = origin;
    }

    public Socket getSocket() {
        return socket;
    }

    public void queuePlayerInteractEvent(PlayerInteractEvent event) {
        //plugin.getLogger().info(event.toString());
        interactEventQueue.add(event);
    }

    public void queueChatPostedEvent(AsyncPlayerChatEvent event) {
        //plugin.getLogger().info(event.toString());
        chatPostedQueue.add(event);
    }

    public void queueProjectileHitEvent(ProjectileHitEvent event) {
        //plugin.getLogger().info(event.toString());

        if (event.getEntityType() == EntityType.ARROW) {
            Arrow arrow = (Arrow) event.getEntity();
            if (arrow.getShooter() instanceof Player) {
                projectileHitQueue.add(event);
            }
        }
    }

    /**
     * called from the server main thread
     */
    public void tick() {
        if (origin == null) {
            switch (locationType) {
                case ABSOLUTE:
                    this.origin = new Location(plugin.getServer().getWorlds().get(0), 0, 0, 0);
                    break;
                case RELATIVE:
                    this.origin = plugin.getServer().getWorlds().get(0).getSpawnLocation();
                    break;
                default:
                    throw new IllegalArgumentException("Unknown location type " + locationType);
            }
        }
        int processedCount = 0;
        String message;
        while ((message = inQueue.poll()) != null) {
            handleLine(message);
            processedCount++;
            if (processedCount >= maxCommandsPerTick) {
                plugin.getLogger().warning("Over " + maxCommandsPerTick +
                        " commands were queued - deferring " + inQueue.size() + " to next tick");
                break;
            }
        }

        if (!running && inQueue.size() <= 0) {
            pendingRemoval = true;
        }
    }

    protected void handleLine(String line) {
        // GSON - https://futurestud.io/tutorials/gson-getting-started-with-java-json-serialization-deserialization
        // HOW TO HANDLE THIS IF IT WERE A JSON ARRAY - TOO BULKY?
        //
        // {
        //  "cmd": "getBlock",
        //  "sig": "BlockDescriptor:int:int:int",
        //  "args": [
        //      250,
        //      20,
        //      -2312
        //  ],
        // }
        //
        // or
        //
        // {
        //  "cmd": "setColor",
        //  "sig": "void:Color",
        //  "args": [
        //      {
        //          "_name": "SetColor",
        //          "red": 20,
        //          "green": 220,
        //          "blue": 92,
        //      }
        //  ]
        // }

        plugin.getLogger().warning("handleLine: Got: " + line);
        // Let's try to convert the line to JSON and if it fails we assume we've got an old style call
        // Is this expensive? Should I just test for a { as the first char of the string? #TODO
        // https://howtodoinjava.com/gson/gson-jsonparser/
        if (line.startsWith("{")) {
            Gson gson = new Gson();
            try {
                PythonCommand pythonCommand = gson.fromJson(line, PythonCommand.class);
                PyComplex pyComplex = new PyComplex(plugin, this);
                pyComplex.handleCommand(pythonCommand);
                plugin.getLogger().warning("handleLine: Converted: " + pythonCommand.toString());
            } catch (IllegalStateException | JsonSyntaxException e) {
                plugin.getLogger().warning("An error occurred whilst parsing the JSON command: " + line);
                e.printStackTrace();
                send("Fail");
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else {
            // We don't have a JSON line - process on old / standard basis
            String methodName = line.substring(0, line.indexOf("("));
            //split string into args, handles , inside " i.e. ","
            String[] args = line.substring(line.indexOf("(") + 1, line.length() - 1).split(",");
            //System.out.println(methodName + ":" + Arrays.toString(args));

            handleCommand(methodName, args);

        }
    }

    protected void handleLineOLD(String line) {
        //System.out.println("ligne de commande : " + line);
        //

        String methodName = line.substring(0, line.indexOf("("));
        //split string into args, handles , inside " i.e. ","
        String[] args = line.substring(line.indexOf("(") + 1, line.length() - 1).split(",");
        //System.out.println(methodName + ":" + Arrays.toString(args));

        handleCommand(methodName, args);
    }

    protected void handleCommand(String c, String[] args) {

        try {
            // get the server
            Server server = plugin.getServer();

            // get the world
            World world = origin.getWorld();

            // world.getBlock
            if (c.equals("world.getBlock")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                //send(world.getBlockTypeIdAt(loc));
                //send(world.getBlockAt(loc));  fonctionne renvoie coord et type dans une longue chaîne de car
                Block block = world.getBlockAt(loc);
                send(block.getType());

                // world.getBlocks
            } else if (c.equals("world.getBlocks")) {
                Location loc1 = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Location loc2 = parseRelativeBlockLocation(args[3], args[4], args[5]);
                send(getBlocks(loc1, loc2));

                // world.getBlockWithData
            } else if (c.equals("world.getBlockWithData")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                org.bukkit.block.Block block = world.getBlockAt(loc);
                org.bukkit.block.data.BlockData blocdata = block.getBlockData();

                StringBuilder b = new StringBuilder();

                // dans le cas d'une banière recherche de ses paramètres et ajout à b
                org.bukkit.block.BlockState etatblock = block.getState();

                if (etatblock instanceof org.bukkit.block.Banner) {
                    // paramètres de la banière
                    org.bukkit.block.Banner banner = (org.bukkit.block.Banner) block.getState();
                    //List<org.bukkit.> patternlist = (org.bukkit.block.Banner) banner.getPatterns();
                    List<org.bukkit.block.banner.Pattern> listepattern = banner.getPatterns();

                    b.append("; Patterns {");
                    for (int i = 0; i < listepattern.size(); i++) {
                        b.append(" [");
                        b.append(listepattern.get(i).getPattern());
                        b.append(",");
                        //plugin.getLogger().info(listepattern.get(i).getPattern().toString());
                        b.append(listepattern.get(i).getColor());
                        //plugin.getLogger().info(listepattern.get(i).getColor().toString());
                        b.append("] ");
                    }
                    b.append("}");
                }
                send(blocdata.toString() + b);

                // world.setBlock
            } else if (c.equals("world.setBlock")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                //updateBlock(world, loc, args[3], (args.length > 4? Byte.parseByte(args[4]) : (byte) 0));
                // Block block = world.getBlockAt(loc);
                Material blockType = Material.getMaterial(args[3]);
                // BlockData blocData = blockType.createBlockData(args[4]); erreur

                updateBlock(world, loc, blockType);

                // world.setBlockWithBlocData - plugins version 2.2 - juillet 2020
            } else if (c.equals("world.setBlockWithBlocData") || c.equals("world.setBlockWithData")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);

                // affichage de controle
                //plugin.getLogger().info("Material : " + args[3]);
                Material blockType = Material.getMaterial(args[3]);

                // Gestion des BlockDatas :
                // remplacement des '/'  { introduits pour le détrompage entre ',' des arguments principaux
                // et ',' des arguments de BlockDtata}  par des ','
                String chain = args[4];
                String chaine = chain.replaceAll("/", ",");
                String dataString = chaine;

                // affichage de controle
                //plugin.getLogger().info("Blockdata : " + dataString);

                // création du Block data
                org.bukkit.block.data.BlockData data = Bukkit.createBlockData(dataString);

                // affichage de controle
                //plugin.getLogger().info(data.toString());
                updateBlockWithData(world, loc, blockType, data);


                // world.setBlocks
            } else if (c.equals("world.setBlocks")) {
                Location loc1 = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Location loc2 = parseRelativeBlockLocation(args[3], args[4], args[5]);
                Material blockType = Material.getMaterial(args[6]);
                //byte data = args.length > 7? Byte.parseByte(args[7]) : (byte) 0;  on ne s'occupe pas des arguments supplémentaires
                setCuboid(loc1, loc2, blockType);

            } else if (c.equals("world.setBlocksWithData")) {
                Location loc1 = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Location loc2 = parseRelativeBlockLocation(args[3], args[4], args[5]);
                Material blockType = Material.getMaterial(args[6]);
                String blockDataArg = args[7];
                String blockDataDescription = blockDataArg.replaceAll("/", ",");

                plugin.getLogger().info("BlockDataDescription : " + blockDataDescription);

                // création du Block data
                org.bukkit.block.data.BlockData data = Bukkit.createBlockData(blockDataDescription);

                setCuboidWithData(loc1, loc2, blockType, data);

                // world.getPlayerIds
            } else if (c.equals("world.getPlayerIds")) {
                StringBuilder bdr = new StringBuilder();
                Collection<? extends Player> players = Bukkit.getOnlinePlayers();
                if (players.size() > 0) {
                    for (Player p : players) {
                        bdr.append(p.getEntityId());
                        bdr.append("|");
                    }
                    bdr.deleteCharAt(bdr.length() - 1);
                    send(bdr.toString());
                } else {
                    send("Fail");
                }

                // world.getPlayerId
            } else if (c.equals("world.getPlayerId")) {
                Player p = plugin.getNamedPlayer(args[0]);
                if (p != null) {
                    send(p.getEntityId());
                } else {
                    plugin.getLogger().info("Player [" + args[0] + "] not found.");
                    send("Fail");
                }

                // entity.getListName
            } else if (c.equals("entity.getName")) {
                Entity e = plugin.getEntity(Integer.parseInt(args[0]));
                if (e == null) {
                    plugin.getLogger().info("Player (or Entity) [" + args[0] + "] not found in entity.getName.");
                } else if (e instanceof Player) {
                    Player p = (Player) e;
                    //sending list name because plugin.getNamedPlayer() uses list name
                    send(p.getPlayerListName());
                } else if (e != null) {
                    send(e.getName());
                }

                // world.getEntities
                // version initiale
			/*} else if (c.equals("world.getEntities")) {
				int entityType = Integer.parseInt(args[0]);
				send(getEntities(world, entityType)); */

            } else if (c.equals("world.getEntities")) {
                String entityType = args[0];
                send(getEntities(world, entityType));    // entityType est une chaîne de caractères


                // world.removeEntity
            } else if (c.equals("world.removeEntity")) {
                int result = 0;
                for (Entity e : world.getEntities()) {
                    if (e.getEntityId() == Integer.parseInt(args[0])) {
                        e.remove();
                        result = 1;
                        break;
                    }
                }
                send(result);

                // world.removeEntities
            } else if (c.equals("world.removeEntities")) {
                org.bukkit.entity.EntityType entityType = org.bukkit.entity.EntityType.valueOf(args[0]);
                int removedEntitiesCount = 0;
                for (Entity e : world.getEntities()) {
                    if (e.getType() == entityType) {
                        e.remove();
                        removedEntitiesCount++;
                    }
                }
                send(removedEntitiesCount);

                // world.setEntityName
            } else if (c.equals("world.setEntityName")) {
                int result = 0;
                for (Entity e : world.getEntities()) {
                    if (e.getEntityId() == Integer.parseInt(args[0])) {
                        e.setCustomName(args[1]);
                        e.setCustomNameVisible(true);
                        result = 1;
                        break;
                    }
                }
                send(result);

                // chat.post
            } else if (c.equals("chat.post")) {
                //create chat message from args as it was split by ,
                String chatMessage = "";
                int count;
                for (count = 0; count < args.length; count++) {
                    chatMessage = chatMessage + args[count] + ",";
                }
                chatMessage = chatMessage.substring(0, chatMessage.length() - 1);
                server.broadcastMessage(chatMessage);

                // events.clear
            } else if (c.equals("events.clear")) {
                interactEventQueue.clear();
                chatPostedQueue.clear();

                // events.block.hits
            } else if (c.equals("events.block.hits")) {
                send(getBlockHits());

                // events.chat.posts
            } else if (c.equals("events.chat.posts")) {
                send(getChatPosts());

                // events.projectile.hits
            } else if (c.equals("events.projectile.hits")) {
                send(getProjectileHits());

                // entity.events.clear
            } else if (c.equals("entity.events.clear")) {
                int entityId = Integer.parseInt(args[0]);
                clearEntityEvents(entityId);

                // entity.events.block.hits
            } else if (c.equals("entity.events.block.hits")) {
                int entityId = Integer.parseInt(args[0]);
                send(getBlockHits(entityId));

                // entity.events.chat.posts
            } else if (c.equals("entity.events.chat.posts")) {
                int entityId = Integer.parseInt(args[0]);
                send(getChatPosts(entityId));

                // entity.events.projectile.hits
            } else if (c.equals("entity.events.projectile.hits")) {
                int entityId = Integer.parseInt(args[0]);
                send(getProjectileHits(entityId));

                // player.getTile
            } else if (c.equals("player.getTile")) {
                Player currentPlayer = getCurrentPlayer();
                send(blockLocationToRelative(currentPlayer.getLocation()));

                // player.setTile
            } else if (c.equals("player.setTile")) {
                String x = args[0], y = args[1], z = args[2];
                Player currentPlayer = getCurrentPlayer();
                //get players current location, so when they are moved we will use the same pitch (inclinaison) and yaw (rotation)
                Location loc = currentPlayer.getLocation();
                currentPlayer.teleport(parseRelativeBlockLocation(x, y, z, loc.getPitch(), loc.getYaw()));

                // player.getAbsPos
            } else if (c.equals("player.getAbsPos")) {
                Player currentPlayer = getCurrentPlayer();
                send(currentPlayer.getLocation());

                // player.setAbsPos
            } else if (c.equals("player.setAbsPos")) {
                String x = args[0], y = args[1], z = args[2];
                Player currentPlayer = getCurrentPlayer();
                //get players current location, so when they are moved we will use the same pitch and yaw (rotation)
                Location loc = currentPlayer.getLocation();
                loc.setX(Double.parseDouble(x));
                loc.setY(Double.parseDouble(y));
                loc.setZ(Double.parseDouble(z));
                currentPlayer.teleport(loc);

                // player.getPos
            } else if (c.equals("player.getPos")) {
                Player currentPlayer = getCurrentPlayer();
                send(locationToRelative(currentPlayer.getLocation()));

                // player.setPos
            } else if (c.equals("player.setPos")) {
                String x = args[0], y = args[1], z = args[2];
                Player currentPlayer = getCurrentPlayer();
                //get players current location, so when they are moved we will use the same pitch and yaw (rotation)
                Location loc = currentPlayer.getLocation();
                currentPlayer.teleport(parseRelativeLocation(x, y, z, loc.getPitch(), loc.getYaw()));

                // player.setDirection
            } else if (c.equals("player.setDirection")) {
                Double x = Double.parseDouble(args[0]);
                Double y = Double.parseDouble(args[1]);
                Double z = Double.parseDouble(args[2]);
                Player currentPlayer = getCurrentPlayer();
                Location loc = currentPlayer.getLocation();
                loc.setDirection(new Vector(x, y, z));
                currentPlayer.teleport(loc);

                // player.getDirection
            } else if (c.equals("player.getDirection")) {
                Player currentPlayer = getCurrentPlayer();
                send(currentPlayer.getLocation().getDirection().toString());

                // player.setRotation
            } else if (c.equals("player.setRotation")) {
                float yaw = Float.parseFloat(args[0]);
                org.bukkit.entity.Player currentPlayer = getCurrentPlayer();
                org.bukkit.Location loc = currentPlayer.getLocation();
                loc.setYaw(yaw);

                //plugin.getLogger().info("setRotation : " + yaw);
                //plugin.getLogger().info("Location : " + loc.toString());

                currentPlayer.teleport(loc);
                //Boolean teleport = currentPlayer.teleport(loc);
                //org.bukkit.Location local =  (org.bukkit.Location) currentPlayer.getLocation();
                //float orient = local.getYaw();

                //plugin.getLogger().info("teleportation : " + teleport);
                //plugin.getLogger().info("getRotation after set : " + orient);


                // player.getRotation
            } else if (c.equals("player.getRotation")) {
                Player currentPlayer = getCurrentPlayer();
                float yaw = currentPlayer.getLocation().getYaw();
                // turn bukkit's 0 - -360 to positive numbers
                //if (yaw < 0) yaw = yaw * -1;
                plugin.getLogger().info("getRotation : " + yaw);
                send(yaw);

                // player.setPitch
            } else if (c.equals("player.setPitch")) {
                Float pitch = Float.parseFloat(args[0]);
                Player currentPlayer = getCurrentPlayer();
                Location loc = currentPlayer.getLocation();
                loc.setPitch(pitch);
                currentPlayer.teleport(loc);

                // player.getPitch
            } else if (c.equals("player.getPitch")) {
                Player currentPlayer = getCurrentPlayer();
                send(currentPlayer.getLocation().getPitch());

                // player.getEntities
            } else if (c.equals("player.getEntities")) {
                plugin.getLogger().info("args: (" + Arrays.toString(args) + ")");
                Player currentPlayer = getCurrentPlayer();
                int distance = Integer.parseInt(args[0]);
                String entityType = args[1];
                plugin.getLogger().info("entityType: (" + entityType + ")");
                plugin.getLogger().info("currentPlayer.getEntityId(): (" + currentPlayer.getEntityId() + ")");

                send(getEntities(world, currentPlayer.getEntityId(), distance, entityType));

                // player.removeEntities
            } else if (c.equals("player.removeEntities")) {
                Player currentPlayer = getCurrentPlayer();
                int distance = Integer.parseInt(args[0]);
                org.bukkit.entity.EntityType entityType = org.bukkit.entity.EntityType.valueOf(args[1]);

                send(removeEntities(world, currentPlayer.getEntityId(), distance, entityType));

                // player.events.block.hits
            } else if (c.equals("player.events.block.hits")) {
                Player currentPlayer = getCurrentPlayer();
                send(getBlockHits(currentPlayer.getEntityId()));

                // player.events.chat.posts
            } else if (c.equals("player.events.chat.posts")) {
                Player currentPlayer = getCurrentPlayer();
                send(getChatPosts(currentPlayer.getEntityId()));

                // player.events.projectile.hits
            } else if (c.equals("player.events.projectile.hits")) {
                Player currentPlayer = getCurrentPlayer();
                send(getProjectileHits(currentPlayer.getEntityId()));

                // player.events.clear
            } else if (c.equals("player.events.clear")) {
                Player currentPlayer = getCurrentPlayer();
                clearEntityEvents(currentPlayer.getEntityId());

                // world.getHeight
            } else if (c.equals("world.getHeight")) {
                send(world.getHighestBlockYAt(parseRelativeBlockLocation(args[0], "0", args[1])) - origin.getBlockY());

                // entity.getTile
            } else if (c.equals("entity.getTile")) {
                //get entity based on id
                Entity entity = plugin.getEntity(Integer.parseInt(args[0]));
                if (entity != null) {
                    send(blockLocationToRelative(entity.getLocation()));
                } else {
                    plugin.getLogger().info("Entity [" + args[0] + "] not found.");
                    send("Fail");
                }

                // entity.setTile
            } else if (c.equals("entity.setTile")) {
                String x = args[1], y = args[2], z = args[3];
                //get entity based on id
                Entity entity = plugin.getEntity(Integer.parseInt(args[0]));
                if (entity != null) {
                    //get entity's current location, so when they are moved we will use the same pitch and yaw (rotation)
                    Location loc = entity.getLocation();
                    entity.teleport(parseRelativeBlockLocation(x, y, z, loc.getPitch(), loc.getYaw()));
                } else {
                    plugin.getLogger().info("Entity [" + args[0] + "] not found.");
                    send("Fail");
                }

                // entity.getPos
            } else if (c.equals("entity.getPos")) {
                //get entity based on id
                Entity entity = plugin.getEntity(Integer.parseInt(args[0]));
                //Player entity = plugin.getEntity(Integer.parseInt(args[0]));
                if (entity != null) {
                    send(locationToRelative(entity.getLocation()));
                } else {
                    plugin.getLogger().info("Entity [" + args[0] + "] not found.");
                    send("Fail");
                }

                // entity.setPos
            } else if (c.equals("entity.setPos")) {
                String x = args[1], y = args[2], z = args[3];
                //get entity based on id
                Entity entity = plugin.getEntity(Integer.parseInt(args[0]));
                if (entity != null) {
                    //get entity's current location, so when they are moved we will use the same pitch and yaw (rotation)
                    Location loc = entity.getLocation();
                    entity.teleport(parseRelativeLocation(x, y, z, loc.getPitch(), loc.getYaw()));
                } else {
                    plugin.getLogger().info("Entity [" + args[0] + "] not found.");
                    send("Fail");
                }

                // entity.setDirection
            } else if (c.equals("entity.setDirection")) {
                Entity entity = plugin.getEntity(Integer.parseInt(args[0]));
                if (entity != null) {
                    Double x = Double.parseDouble(args[1]);
                    Double y = Double.parseDouble(args[2]);
                    Double z = Double.parseDouble(args[3]);
                    Location loc = entity.getLocation();
                    loc.setDirection(new Vector(x, y, z));
                    entity.teleport(loc);
                } else {
                    plugin.getLogger().info("Entity [" + args[0] + "] not found.");
                }

                // entity.getDirection
            } else if (c.equals("entity.getDirection")) {
                //get entity based on id
                Entity entity = plugin.getEntity(Integer.parseInt(args[0]));
                if (entity != null) {
                    send(entity.getLocation().getDirection().toString());
                } else {
                    plugin.getLogger().info("Entity [" + args[0] + "] not found.");
                    send("Fail");
                }

                // entity.setRotation
            } else if (c.equals("entity.setRotation")) {
                Entity entity = plugin.getEntity(Integer.parseInt(args[0]));
                if (entity != null) {
                    Float yaw = Float.parseFloat(args[1]);
                    Location loc = entity.getLocation();
                    loc.setYaw(yaw);
                    entity.teleport(loc);
                } else {
                    plugin.getLogger().info("Entity [" + args[0] + "] not found.");
                }

                // entity.getRotation
            } else if (c.equals("entity.getRotation")) {
                //get entity based on id
                Entity entity = plugin.getEntity(Integer.parseInt(args[0]));
                if (entity != null) {
                    send(entity.getLocation().getYaw());
                } else {
                    plugin.getLogger().info("Entity [" + args[0] + "] not found.");
                    send("Fail");
                }

                // entity.setPitch
            } else if (c.equals("entity.setPitch")) {
                Entity entity = plugin.getEntity(Integer.parseInt(args[0]));
                if (entity != null) {
                    Float pitch = Float.parseFloat(args[1]);
                    Location loc = entity.getLocation();
                    loc.setPitch(pitch);
                    entity.teleport(loc);
                } else {
                    plugin.getLogger().info("Entity [" + args[0] + "] not found.");
                }

                // entity.getPitch
            } else if (c.equals("entity.getPitch")) {
                //get entity based on id
                Entity entity = plugin.getEntity(Integer.parseInt(args[0]));
                if (entity != null) {
                    send(entity.getLocation().getPitch());
                } else {
                    plugin.getLogger().info("Entity [" + args[0] + "] not found.");
                    send("Fail");
                }

                // entity.getEntities
            } else if (c.equals("entity.getEntities")) {
                int entityId = Integer.parseInt(args[0]);
                int distance = Integer.parseInt(args[1]);
                String entityType = args[2];

                send(getEntities(world, entityId, distance, entityType));

                // entity.removeEntities
            } else if (c.equals("entity.removeEntities")) {
                plugin.getLogger().info(args[0] + args[1] + args[2]);
                int entityId = Integer.parseInt(args[0]);
                int distance = Integer.parseInt(args[1]);
                org.bukkit.entity.EntityType entityType = org.bukkit.entity.EntityType.valueOf(args[2]);

                send(removeEntities(world, entityId, distance, entityType));


                // 	world setBlockAge  - Set Block  BlockData : ageable type
            } else if (c.equals("world.setBlockAge")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                org.bukkit.block.Block thisBlock = world.getBlockAt(loc);

                // We assign the Material to the block
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                // We create the character string which contains the age
                String chaine = "[age=" + args[4] + "]";
                // control display
                //plugin.getLogger().info(" valeur.toString() : " + chaine);

                // We create the BlockData databloc with the material and the age
                org.bukkit.block.data.BlockData databloc = Bukkit.createBlockData(matiere, chaine);

                // we assign the blockdata to the current block
                thisBlock.setBlockData(databloc);

                // Check display - Debug - check the created block
                //org.bukkit.block.BlockState etat = thisBlock.getState();
                //plugin.getLogger().info("BlockState : " + etat.toString());
                //org.bukkit.block.data.BlockData blockData = etat.getBlockData();
                //plugin.getLogger().info("BlockData : " + blockData.toString());

                //int an = (int) Double.parseDouble(args[4]);

                // extraction of the corresponding data block - be careful, you must declare:
                //import org.bukkit.block.data.Ageable
                //org.bukkit.block.data.Ageable age = (Ageable)blockData;
                //int ageMax = age.getMaximumAge();
                //age.setAge(an);
                //plugin.getLogger().info("Age max : " + ageMax);
                //plugin.getLogger().info("           ");


                // 	world setBlockBisected - Set Block de type block.data.Bisected
            } else if (c.equals("world.setBlockBisected")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                org.bukkit.block.Block thisBlock = world.getBlockAt(loc);

                //On attribue le Material au bloc
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                // On crée la chaîne de caractère qui contient la moitié "half"
                String chaine = "[half=" + args[4].toLowerCase() + "]";
                // affichage de contrôle
                //plugin.getLogger().info(" valeur.toString() : " + chaine);

                // On crée le BlockData databloc avec la matière et la direction axiale
                org.bukkit.block.data.BlockData databloc = Bukkit.createBlockData(matiere, chaine);

                // on attibue le blocdata au bloc courant
                thisBlock.setBlockData(databloc);

                // Affichage de vérification - contrôle du bloc créé
                //org.bukkit.block.BlockState etat = thisBlock.getState();
                //plugin.getLogger().info("BlockState : " + etat.toString());
                //org.bukkit.block.data.BlockData valeur = etat.getBlockData();
                //plugin.getLogger().info("BlockData : " + valeur.toString());


                // 	world setBlockDir  - Set Block  BlockData : directional type
            } else if (c.equals("world.setBlockDir")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //On attribue le Material au bloc
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                // On crée la chaîne de caractère qui contient la direction facing "esat", "west", ...
                String chaine = "[facing=" + args[4].toLowerCase() + "]";
                // affichage de contrôle
                //plugin.getLogger().info(" valeur.toString() : " + chaine);

                // On crée le BlockData databloc avec la matière et l'orientation choisie
                org.bukkit.block.data.BlockData databloc = Bukkit.createBlockData(matiere, chaine);

                // on attibue le blocdata au bloc courant
                thisBlock.setBlockData(databloc);

                // Affichage de vérification - contrôle du bloc créé
                //org.bukkit.block.BlockState etat = thisBlock.getState();
                //plugin.getLogger().info("BlockState : " + etat.toString());
                //org.bukkit.block.data.BlockData valeur = etat.getBlockData();
                //plugin.getLogger().info("BlockData : " + valeur.toString());


                // 	world setBlockDistrib  - Set Block  BlockData : Dispenser type - version 1.16.1
            } else if (c.equals("world.setBlockDistrib")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //On fixe le Material au bloc
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                // Gestion de l'aspect pour les faces
                org.bukkit.block.data.BlockData databloc = thisBlock.getBlockData();
                // affichage de controle
                //plugin.getLogger().info("databloc Hooper " + databloc.toString());

                if (databloc instanceof org.bukkit.block.data.type.Dispenser) {
                    org.bukkit.block.data.type.Dispenser distributeur = (org.bukkit.block.data.type.Dispenser) thisBlock.getBlockData();

                    //plugin.getLogger().info("bloc de type Dispenser");
                    org.bukkit.block.data.Directional direction = (org.bukkit.block.data.Directional) distributeur;

                    // récupération des arguments à partir du 4ème
                    int nbr_arg = args.length;
                    if (nbr_arg >= 5) {  // on définit l'orientation nord, sud, est, ouest
                        //facing direction  : NORTH SOUTH WEST EAST
                        BlockFace face = BlockFace.valueOf(args[4]);
                        direction.setFacing(face);
                        //plugin.getLogger().info("Faces possibles" + direction.getFaces().toString());

                        // vérification du choix
                        //plugin.getLogger().info("face choisie " + args[4]);
                    }
                    if (nbr_arg == 6) {  // on définit si le bloc est Activé ou non
                        if (args[5].contentEquals("True")) {
                            distributeur.setTriggered(true);
                            // affichage de controle
                            //plugin.getLogger().info(args[4]+" : "+args[5]);
                        } else distributeur.setTriggered(false);
                    }

                    // on attibue le blocdata au bloc courant
                    thisBlock.setBlockData(distributeur);
                }

                // mise à jour par défaut
                else updateBlock(world, loc, matiere);


                // 	world setBlockLevel - Set Block  BlockData : Levelled type
            } else if (c.equals("world.setBlockLevel")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //On attribue le Material au bloc
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                // on fixe la valeur de level
                // In the case of water and lava blocks the levels have special meanings:
                // a level of 0 corresponds to a source block,
                // 1-7 regular fluid heights,
                // and 8-15 to "falling" fluids.
                // All falling fluids have the same behaviour, but the level corresponds to that of the block above them,
                //equal to this.level - 8 Note that counterintuitively, an adjusted level of 1 is the highest level,
                // whilst 7 is the lowest.
                org.bukkit.block.data.Levelled blocData = (org.bukkit.block.data.Levelled) thisBlock.getBlockData();
                int level = (int) Double.parseDouble(args[4]);
                blocData.setLevel(level);

                // Affichage de vérification - Mise au point - contrôle du bloc créé
                //int levelMax = blocData.getMaximumLevel();
                //org.bukkit.block.BlockState etat = thisBlock.getState();
                //plugin.getLogger().info("BlockState : " + etat.toString());
                //org.bukkit.block.data.BlockData blockData = etat.getBlockData();
                //plugin.getLogger().info("BlockData : " + blockData.toString());
                //plugin.getLogger().info("Level max : " + levelMax);
                //plugin.getLogger().info("           ");

                // on attibue le blocdata au bloc courant
                thisBlock.setBlockData(blocData);


                // 	world setBlockMultiFace  - Set Block  BlockData : MultipleFacing type
            } else if (c.equals("world.setBlockMultiFace")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                org.bukkit.block.Block thisBlock = world.getBlockAt(loc);

                //On attribue le Material au bloc
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                org.bukkit.block.data.BlockData databloc = thisBlock.getBlockData();
                //plugin.getLogger().info("orientation choisie " + databloc.toString());

                if (databloc instanceof org.bukkit.block.data.MultipleFacing) {
                    //plugin.getLogger().info("Multifacing bloc");
                    org.bukkit.block.data.MultipleFacing multiface = (org.bukkit.block.data.MultipleFacing) databloc;

                    // récupération des arguments à partir du 4ème
                    for (int i = 4; i - 4 < 5 && i < args.length; i++) {
                        //facing direction  : NORTH SOUTH WEST EAST UP
                        BlockFace face = BlockFace.valueOf(args[i]);
                        //multietat.setFace(face, true);
                        ((org.bukkit.block.data.MultipleFacing) multiface).setFace(face, true);
                        // vérification du choix
                        //plugin.getLogger().info("orientation choisie " + args[i]);
                    }
                }
                // On crée le BlockData databloc avec la matière et l'orientation choisie
                //org.bukkit.block.data.BlockData databloc = Bukkit.createBlockData(matiere,chaine);

                // on attibue le blocdata au bloc courant
                thisBlock.setBlockData(databloc);

                // Affichage de vérification - contrôle du bloc créé
                //org.bukkit.block.BlockState etat = thisBlock.getState();
                //plugin.getLogger().info("BlockState : " + etat.toString());
                //org.bukkit.block.data.BlockData valeur = etat.getBlockData();
                //plugin.getLogger().info("BlockData : " + valeur.toString());


                // 	world setBlockOrient  - Set Block de type block.data.Orientable
            } else if (c.equals("world.setBlockOrient")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                org.bukkit.block.Block thisBlock = world.getBlockAt(loc);

                //On attribue le Material au bloc
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                // On crée la chaîne de caractère qui contient l'axe d'orientation x,y ou z
                String chaine = "[axis=" + args[4].toLowerCase() + "]";
                // affichage de contrôle
                //plugin.getLogger().info(" valeur.toString() : " + chaine);

                // On crée le BlockData databloc avec la matière et la direction axiale
                org.bukkit.block.data.BlockData databloc = Bukkit.createBlockData(matiere, chaine);

                // on attibue le blocdata au bloc courant
                thisBlock.setBlockData(databloc);

                // Affichage de vérification - contrôle du bloc créé
                //org.bukkit.block.BlockState etat = thisBlock.getState();
                //plugin.getLogger().info("BlockState : " + etat.toString());
                //org.bukkit.block.data.BlockData valeur = etat.getBlockData();
                //plugin.getLogger().info("BlockData : " + valeur.toString());


                // 	world setBlockPower  - Set Block  BlockData : Powerable  type - version 1.16.1
            } else if (c.equals("world.setBlockPower")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //On fixe le Material au bloc
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                // Gestion de l'aspect pour les faces
                org.bukkit.block.data.BlockData databloc = thisBlock.getBlockData();
                // affichage de controle
                //plugin.getLogger().info("databloc Powerable " + databloc.toString());

                if (databloc instanceof org.bukkit.block.data.Powerable) {
                    org.bukkit.block.data.Powerable power = (org.bukkit.block.data.Powerable) thisBlock.getBlockData();

                    // récupération des arguments à partir du 4ème
                    int nbr_arg = args.length;
                    //plugin.getLogger().info("nbre arguments : "+nbr_arg);
                    if (nbr_arg >= 4) {  // on définit si le bloc est Activé ou non
                        // affichage de controle
                        //plugin.getLogger().info("Power : "+args[4]);

                        if (args[4].contentEquals("True")) {
                            power.setPowered(true);
                        } else power.setPowered(false);
                    }

                    // on attibue le blocdata au bloc courant
                    thisBlock.setBlockData(power);
                }

                // mise à jour par défaut
                else updateBlock(world, loc, matiere);


                // 	world setBlockRotat  - Set Block  BlockData : Rotatable type
            } else if (c.equals("world.setBlockRotat")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                org.bukkit.block.Block thisBlock = world.getBlockAt(loc);

                //On fixe le Material au bloc
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                org.bukkit.block.data.BlockData databloc = thisBlock.getBlockData();
                //plugin.getLogger().info("rotation choisie " + databloc.toString());

                // Orientation du Bloc
                if (databloc instanceof org.bukkit.block.data.Rotatable) {
                    //plugin.getLogger().info("Rotatable bloc");
                    org.bukkit.block.data.Rotatable rotation = (org.bukkit.block.data.Rotatable) databloc;

                    // On paramètre la rotation du bloc
                    //plugin.getLogger().info("rotation choisie " + args[4]);
                    BlockFace face = BlockFace.valueOf(args[4]);
                    ((org.bukkit.block.data.Rotatable) rotation).setRotation(face);
                }
                // mise à jour du bloc
                thisBlock.setBlockData(databloc);

                // gestion du motif du bloc
                org.bukkit.block.BlockState etatblock = thisBlock.getState();
                if (etatblock instanceof org.bukkit.block.Banner) {

                    // paramètres de la banière
                    org.bukkit.block.Banner banner = (org.bukkit.block.Banner) thisBlock.getState();


                    // tableau de patterns
                    int dim = args.length - 5;
                    org.bukkit.block.banner.Pattern[] patterns = (new org.bukkit.block.banner.Pattern[dim]);


                    //Motifs en couleur sur la bannière
                    int j = 0;
                    for (int i = 5; i < args.length; i = i + 2) {
                        org.bukkit.block.banner.PatternType motif = org.bukkit.block.banner.PatternType.valueOf(args[i]);
                        org.bukkit.DyeColor color = org.bukkit.DyeColor.valueOf(args[i + 1]);
                        //plugin.getLogger().info(" PatternType : "+ motif.toString() );
                        //plugin.getLogger().info(" PatternType : "+ color.toString() );

                        patterns[j] = new org.bukkit.block.banner.Pattern(color, motif);

                        banner.addPattern(patterns[j]);
                        j = j + 1;
                    }


                    // Mise à jour du bloc
                    banner.update();
                    banner.setBlockData(databloc);
                }

                // Affichage de vérification - contrôle du bloc créé
                //org.bukkit.block.BlockState etat = thisBlock.getState();
                //plugin.getLogger().info("BlockState : " + etat.toString());
                //org.bukkit.block.data.BlockData valeur = etat.getBlockData();
                //plugin.getLogger().info("BlockData : " + valeur.toString());


                // 	world setBlockSapl  - Set Block  BlockData : sapling type
            } else if (c.equals("world.setBlockSapl")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //On attribue le Material au bloc
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                // on fixe la valeur de saple - taille
                org.bukkit.block.data.type.Sapling blocData = (org.bukkit.block.data.type.Sapling) thisBlock.getBlockData();
                int stage = (int) Double.parseDouble(args[4]);
                blocData.setStage(stage);

                // Affichage de vérification - Mise au point - contrôle du bloc créé
                //int stageMax = blocData.getMaximumStage();
                //org.bukkit.block.BlockState etat = thisBlock.getState();
                //plugin.getLogger().info("BlockState : " + etat.toString());
                //org.bukkit.block.data.BlockData blockData = etat.getBlockData();
                //plugin.getLogger().info("BlockData : " + blockData.toString());
                //plugin.getLogger().info("Stage max : " + stageMax);
                //plugin.getLogger().info("           ");


                // on attibue le blocdata au bloc courant
                thisBlock.setBlockData(blocData);


                // 	world setBed
            } else if (c.equals("world.setBed")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //blockType of bed
                Material blocType = Material.getMaterial(args[3]);
                thisBlock.setType(blocType);

                //part of the Bed "FOOT" or "HEAD"
                org.bukkit.block.data.type.Bed.Part partie = org.bukkit.block.data.type.Bed.Part.valueOf(args[4]);
                org.bukkit.block.data.type.Bed lit = (org.bukkit.block.data.type.Bed) thisBlock.getBlockData();
                lit.setPart(partie);

                //facing direction  : NORTH SOUTH WEST EAST
                BlockFace face = BlockFace.valueOf(args[5]);
                lit.setFacing(face);
                thisBlock.setBlockData(lit);


                // 	world setFurnace
            } else if (c.equals("world.setFurnace")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //blockType of furnace
                Material blocType = Material.getMaterial(args[3]);
                thisBlock.setType(blocType);

                org.bukkit.block.data.type.Furnace furnace = (org.bukkit.block.data.type.Furnace) thisBlock.getBlockData();

                //facing direction  : NORTH SOUTH WEST EAST
                BlockFace face = BlockFace.valueOf(args[4]);
                furnace.setFacing(face);

                //Lightedor not Lighted Furnace
                //plugin.getLogger().info("allume : " + args[5]);
                if (args[5].equals("False")) {
                    furnace.setLit(false);
                    //plugin.getLogger().info("allume : " + args[5]);
                } else furnace.setLit(true);


                thisBlock.setBlockData(furnace);
                //plugin.getLogger().info("allume : " + furnace.isLit());


                // 	world setGate
            } else if (c.equals("world.setGate")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //blockType of gate
                Material blocType = Material.getMaterial(args[3]);
                thisBlock.setType(blocType);

                org.bukkit.block.data.type.Gate gate = (org.bukkit.block.data.type.Gate) thisBlock.getBlockData();

                //facing direction  : NORTH SOUTH WEST EAST
                BlockFace face = BlockFace.valueOf(args[4]);
                //plugin.getLogger().info("orientation choisie " + args[4]);
                //plugin.getLogger().info("orientations possibles " + gate.getFaces());
                gate.setFacing(face);

                //Attacher au mur
                //plugin.getLogger().info("attachee au mur " + args[5]);
                if (args[5].contentEquals("True")) {
                    gate.setInWall(true);
                } else gate.setInWall(false);

                //Close door
                gate.setOpen(false);

                thisBlock.setBlockData(gate);

                // 	world setPane
            } else if (c.equals("world.setPane")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //blockType of gate
                Material blocType = Material.getMaterial(args[3]);
                thisBlock.setType(blocType);

                org.bukkit.block.data.type.GlassPane pane = (org.bukkit.block.data.type.GlassPane) thisBlock.getBlockData();

                for (int i = 4; i - 4 < 4 && i < args.length; i++) {
                    //facing direction  : NORTH SOUTH WEST EAST
                    BlockFace face = BlockFace.valueOf(args[i]);
                    pane.setFace(face, true);
                    //plugin.getLogger().info("orientation choisie " + args[i]);
                }

                //plugin.getLogger().info("orientations possibles " + pane.getFaces());
                thisBlock.setBlockData(pane);


                // 	world setBRail  - Set Block  BlockData : RedstoneRail  type - version 1.16.1
            } else if (c.equals("world.setRail")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //On fixe le Material au bloc
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                // Gestion de l'aspect pour les faces
                org.bukkit.block.data.BlockData databloc = thisBlock.getBlockData();
                // affichage de controle
                plugin.getLogger().info("databloc RedstoneRail " + databloc.toString());

                if (databloc instanceof org.bukkit.block.data.type.RedstoneRail) {
                    org.bukkit.block.data.type.RedstoneRail railredstone = (org.bukkit.block.data.type.RedstoneRail) thisBlock.getBlockData();

                    plugin.getLogger().info("RedstoneRail bloc");
                    org.bukkit.block.data.Rail rail = (org.bukkit.block.data.Rail) railredstone;

                    // récupération des arguments à partir du 4ème
                    int nbr_arg = args.length;
                    if (nbr_arg >= 5) {  // on définit la forme Rail.Shape
                        //facing direction  : ASCENDING_EAST _NORTH _SOUTH ...
                        org.bukkit.block.data.Rail.Shape forme = org.bukkit.block.data.Rail.Shape.valueOf(args[4]);
                        rail.setShape(forme);
                        plugin.getLogger().info("forme possibles" + rail.getShapes().toString());

                        // vérification du choix
                        plugin.getLogger().info("forme choisie " + args[4]);
                    }
                    if (nbr_arg == 6) {  // on définit si le bloc est Activé ou non
                        if (args[5].contentEquals("True")) {
                            railredstone.setPowered(true);
                            // affichage de controle
                            plugin.getLogger().info(args[4] + " : " + args[5]);
                        } else railredstone.setPowered(false);
                    }

                    // on attibue le blocdata au bloc courant
                    thisBlock.setBlockData(railredstone);
                }

                // mise à jour par défaut
                else updateBlock(world, loc, matiere);


                // 	world setStairs
            } else if (c.equals("world.setStairs")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //blockType of stairs
                Material blocType = Material.getMaterial(args[3]);
                thisBlock.setType(blocType);

                org.bukkit.block.data.type.Stairs escalier = (org.bukkit.block.data.type.Stairs) thisBlock.getBlockData();

                //facing direction  : NORTH SOUTH WEST EAST
                BlockFace face = BlockFace.valueOf(args[4]);
                escalier.setFacing(face);

                //Shape of the stairs : STRAIGHT, INNER_LEFT, INNER_RIGHT, OUTER_LEFT, OUTER_RIGHT
                org.bukkit.block.data.type.Stairs.Shape forme = org.bukkit.block.data.type.Stairs.Shape.valueOf(args[5]);
                escalier.setShape(forme);

                //Half of the stairs : BOTTOM, TOP
                org.bukkit.block.data.Bisected.Half half = org.bukkit.block.data.Bisected.Half.valueOf(args[6]);
                escalier.setHalf(half);

                thisBlock.setBlockData(escalier);

                // 	world setTrapDoor
            } else if (c.equals("world.setTrapDoor")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //blockType of gate
                Material blocType = Material.getMaterial(args[3]);
                thisBlock.setType(blocType);

                org.bukkit.block.data.type.TrapDoor trapdoor = (org.bukkit.block.data.type.TrapDoor) thisBlock.getBlockData();

                //facing direction qd la trappe est ouverte : NORTH SOUTH WEST EAST
                BlockFace face = BlockFace.valueOf(args[4]);
                trapdoor.setFacing(face);

                // Half : TOP ou BOTTOM
                org.bukkit.block.data.Bisected.Half half = org.bukkit.block.data.Bisected.Half.valueOf(args[5]);
                trapdoor.setHalf(half);


                //position ouverte True ou fermée False
                if (args[6].equals("True")) {
                    trapdoor.setOpen(true);
                } else trapdoor.setOpen(false);

                thisBlock.setBlockData(trapdoor);


                // 	world setFence
            } else if (c.equals("world.setFence")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //blockType of BlockData : fence
                Material blocType = Material.getMaterial(args[3]);
                thisBlock.setType(blocType);

                org.bukkit.block.data.type.Fence fence = (org.bukkit.block.data.type.Fence) thisBlock.getBlockData();

                for (int i = 4; i - 4 < 4 && i < args.length; i++) {
                    //facing direction  : NORTH SOUTH WEST EAST
                    BlockFace face = BlockFace.valueOf(args[i]);
                    fence.setFace(face, true);
                    //plugin.getLogger().info("orientation choisie " + args[i]);
                }

                thisBlock.setBlockData(fence);

                // 	world setDoor
            } else if (c.equals("world.setDoor")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //blockType of gate
                Material blocType = Material.getMaterial(args[3]);
                thisBlock.setType(blocType);

                org.bukkit.block.data.type.Door porte = (org.bukkit.block.data.type.Door) thisBlock.getBlockData();

                //facing direction  : NORTH SOUTH WEST EAST
                BlockFace face = BlockFace.valueOf(args[4]);
                porte.setFacing(face);

                //Hinge  : LEFT or RIGHT
                org.bukkit.block.data.type.Door.Hinge lien = org.bukkit.block.data.type.Door.Hinge.valueOf(args[5]);
                porte.setHinge(lien);

                //Bisected : BOTTOM or TOP
                org.bukkit.block.data.Bisected.Half position = org.bukkit.block.data.Bisected.Half.valueOf(args[6]);
                porte.setHalf(position);

                //Close door
                porte.setOpen(false);

                //Powerable
                porte.setPowered(true);

                thisBlock.setBlockData(porte);

                // 	world setEntonnoir  - Set Block  BlockData : Hooper type - version 1.16.1
            } else if (c.equals("world.setEntonnoir")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //On fixe le Material au bloc
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                // Gestion de l'aspect pour les faces
                org.bukkit.block.data.BlockData databloc = thisBlock.getBlockData();
                // affichage de controle
                //plugin.getLogger().info("databloc Hooper " + databloc.toString());

                if (databloc instanceof org.bukkit.block.data.type.Hopper) {
                    org.bukkit.block.data.type.Hopper hopper = (org.bukkit.block.data.type.Hopper) thisBlock.getBlockData();

                    //plugin.getLogger().info("Observer bloc");
                    org.bukkit.block.data.Directional direction = (org.bukkit.block.data.Directional) hopper;

                    // récupération des arguments à partir du 4ème
                    int nbr_arg = args.length;
                    if (nbr_arg >= 5) {  // on définit l'orientation nord, sud, est, ouest
                        //facing direction  : NORTH SOUTH WEST EAST
                        BlockFace face = BlockFace.valueOf(args[4]);
                        direction.setFacing(face);
                        //plugin.getLogger().info("Faces possibles" + direction.getFaces().toString());

                        // vérification du choix
                        //plugin.getLogger().info("face choisie " + args[4]);
                    }
                    if (nbr_arg == 6) {  // on définit si le bloc est Activé ou non
                        if (args[5].contentEquals("True")) {
                            hopper.setEnabled(true);
                            // affichage de controle
                            //plugin.getLogger().info(args[4]+" : "+args[5]);
                        } else hopper.setEnabled(false);
                    }

                    // on attibue le blocdata au bloc courant
                    thisBlock.setBlockData(hopper);
                }

                // mise à jour par défaut
                else updateBlock(world, loc, matiere);


                // 	world setChest
            } else if (c.equals("world.setChest")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //blockType of Chest
                Material blocType = Material.getMaterial(args[3]);
                thisBlock.setType(blocType);

                org.bukkit.block.data.type.Chest chest = (org.bukkit.block.data.type.Chest) thisBlock.getBlockData();

                //chest type  : RIGHT, LEFT, SINGLE
                org.bukkit.block.data.type.Chest.Type chestType = org.bukkit.block.data.type.Chest.Type.valueOf(args[4]);
                chest.setType(chestType);

                //facing direction  : NORTH SOUTH WEST EAST
                BlockFace face = BlockFace.valueOf(args[5]);
                chest.setFacing(face);

                thisBlock.setBlockData(chest);


                // 	world setGrindstone  - Place un BlockData de type Grindstone - Meule v1.16
            } else if (c.equals("world.setGrindstone")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                org.bukkit.block.Block thisBlock = world.getBlockAt(loc);

                //On attribue le Material au bloc
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                org.bukkit.block.data.type.Grindstone grindstone = (org.bukkit.block.data.type.Grindstone) thisBlock.getBlockData();
                // affichage de controle
                //plugin.getLogger().info("databloc Grindstone " + grindstone.toString());

                if (grindstone instanceof org.bukkit.block.data.type.Grindstone) {
                    //plugin.getLogger().info("Grindstone bloc");
                    org.bukkit.block.data.Directional direction = (org.bukkit.block.data.Directional) grindstone;
                    org.bukkit.block.data.FaceAttachable attache = (org.bukkit.block.data.FaceAttachable) grindstone;

                    // récupération des arguments à partir du 4ème
                    int nbr_arg = args.length;
                    if (nbr_arg >= 5) {  // on définit l'orientation nord, sud, est, ouest
                        //facing direction  : NORTH SOUTH WEST EAST
                        BlockFace face = BlockFace.valueOf(args[4]);
                        direction.setFacing(face);

                        // vérification du choix
                        //plugin.getLogger().info("face choisie " + args[4]);
                    }
                    if (nbr_arg == 6) {  // on définit la face d'attachement
                        //CEILING, FLOOR, WALL
                        org.bukkit.block.data.FaceAttachable.AttachedFace face_attachee = org.bukkit.block.data.FaceAttachable.AttachedFace.valueOf(args[5]);
                        attache.setAttachedFace(face_attachee);

                        // vérification du choix
                        //plugin.getLogger().info("face attachée " + args[5]);
                    }

                    // on attibue le blocdata au bloc courant
                    thisBlock.setBlockData(grindstone);
                } else updateBlock(world, loc, matiere);


                // 	world setObserver  - Set Block  BlockData : Observer type - version 1.16.1
            } else if (c.equals("world.setObserver")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //On fixe le Material au bloc
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                // Gestion de l'aspect pour les faces
                org.bukkit.block.data.BlockData databloc = thisBlock.getBlockData();
                // affichage de controle
                //plugin.getLogger().info("databloc Observer " + databloc.toString());

                if (databloc instanceof org.bukkit.block.data.type.Observer) {
                    org.bukkit.block.data.type.Observer observateur = (org.bukkit.block.data.type.Observer) thisBlock.getBlockData();

                    //plugin.getLogger().info("Observer bloc");
                    org.bukkit.block.data.Directional direction = (org.bukkit.block.data.Directional) observateur;

                    // récupération des arguments à partir du 4ème
                    int nbr_arg = args.length;
                    if (nbr_arg >= 5) {  // on définit l'orientation nord, sud, est, ouest
                        //facing direction  : NORTH SOUTH WEST EAST
                        BlockFace face = BlockFace.valueOf(args[4]);
                        direction.setFacing(face);
                        //plugin.getLogger().info("Faces possibles" + direction.getFaces().toString());

                        // vérification du choix
                        //plugin.getLogger().info("face choisie " + args[4]);
                    }
                    if (nbr_arg == 6) {  // on définit si le bloc est Activé ou non
                        if (args[5].contentEquals("True")) {
                            observateur.setPowered(true);
                            // affichage de controle
                            //plugin.getLogger().info(args[4]+" : "+args[5]);
                        } else observateur.setPowered(false);
                    }

                    // on attibue le blocdata au bloc courant
                    thisBlock.setBlockData(observateur);
                }

                // mise à jour par défaut
                else updateBlock(world, loc, matiere);

                // 	world setSlab
            } else if (c.equals("world.setSlab")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //BlockType Material de la plaque
                Material blocType = Material.getMaterial(args[3]);
                thisBlock.setType(blocType);

                //type de la plaque : BOTTOM, DOUBLE, TOP
                org.bukkit.block.data.type.Slab.Type partie = org.bukkit.block.data.type.Slab.Type.valueOf(args[4]);
                org.bukkit.block.data.type.Slab slab = (org.bukkit.block.data.type.Slab) thisBlock.getBlockData();
                slab.setType(partie);

                thisBlock.setBlockData(slab);


                // world.setSign
            } else if (c.equals("world.setSign")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //blockType wall sign or standing sign
                Material blocType = Material.getMaterial(args[3]);
                thisBlock.setType(blocType);


                //facing direction for wall sign : NORTH SOUTH WEST EAST
                //rotation SOUTH SOUTH_SOUTH_WEST SOUTH_WEST SOUTH_WEST WEST_SOUTH_WEST .... NORTH ... NORTH_EAST ....
                BlockFace face = BlockFace.valueOf(args[4]);
                //plugin.getLogger().info(args[4]);

                //if ((thisBlock.getTypeId() != blockType) || (thisBlock.getData() != blockData)) {
                //	thisBlock.setTypeIdAndData(blockType, blockData, true);
                //}

                //plugin.getLogger().info("Creating sign at " + loc);
                if (thisBlock.getState() instanceof Sign) {

                    org.bukkit.block.Sign sign = (Sign) thisBlock.getState();
                    for (int i = 5; i - 5 < 4 && i < args.length; i++) {
                        sign.setLine(i - 5, args[i]);
                    }

                    if (sign.getBlockData() instanceof org.bukkit.block.data.type.WallSign) {
                        org.bukkit.block.data.type.WallSign signData = (org.bukkit.block.data.type.WallSign) sign.getBlockData();
                        signData.setFacing(face);
                        sign.setBlockData(signData);
                        if (!thisBlock.getBlockData().equals(sign.getBlockData()))
                            thisBlock.setBlockData(sign.getBlockData());
                        sign.update();

                    }

                    if (sign.getBlockData() instanceof org.bukkit.block.data.type.Sign) {
                        org.bukkit.block.data.type.Sign signData = (org.bukkit.block.data.type.Sign) sign.getBlockData();
                        signData.setRotation(face);
                        sign.setBlockData(signData);
                        if (!thisBlock.getBlockData().equals(sign.getBlockData()))
                            thisBlock.setBlockData(sign.getBlockData());
                        sign.update();
                    }
                    sign.update();
                }


                // 	world setSwitch  - Place un BlockData de type Switch - Meule v1.16
            } else if (c.equals("world.setSwitch")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                org.bukkit.block.Block thisBlock = world.getBlockAt(loc);

                //On attribue le Material au bloc
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                org.bukkit.block.data.type.Switch interrupteur = (org.bukkit.block.data.type.Switch) thisBlock.getBlockData();
                // affichage de controle
                //plugin.getLogger().info("databloc Switch " + interrupteur.toString());

                if (interrupteur instanceof org.bukkit.block.data.type.Switch) {
                    //plugin.getLogger().info("Switch bloc");
                    org.bukkit.block.data.Directional direction = (org.bukkit.block.data.Directional) interrupteur;
                    org.bukkit.block.data.FaceAttachable attache = (org.bukkit.block.data.FaceAttachable) interrupteur;
                    //plugin.getLogger().info("Faces possibles" + direction.getFaces().toString());


                    // récupération des arguments à partir du 4ème
                    int nbr_arg = args.length;
                    if (nbr_arg >= 5) {  // on définit l'orientation nord, sud, est, ouest
                        //facing direction  : NORTH SOUTH WEST EAST

                        BlockFace face = BlockFace.valueOf(args[4]);
                        direction.setFacing(face);

                        // vérification du choix
                        //plugin.getLogger().info("face choisie " + args[4]);
                    }
                    if (nbr_arg >= 6) {  // on définit la face d'attachement
                        //CEILING, FLOOR, WALL
                        org.bukkit.block.data.FaceAttachable.AttachedFace face_attachee = org.bukkit.block.data.FaceAttachable.AttachedFace.valueOf(args[5]);
                        attache.setAttachedFace(face_attachee);

                        // vérification du choix
                        //plugin.getLogger().info("face attachée " + args[5]);
                    }

                    if (nbr_arg == 7) {  // on définit si le bloc est Activé ou non
                        if (args[6].contentEquals("True")) {
                            interrupteur.setPowered(true);
                            // affichage de controle
                            //plugin.getLogger().info("Activé : "+args[6]);
                        } else interrupteur.setPowered(false);
                    }

                    // on attibue le blocdata au bloc courant
                    thisBlock.setBlockData(interrupteur);
                } else updateBlock(world, loc, matiere);


                // 	world setWall  - Set Block  BlockData : Wall type - version 1.16.1
            } else if (c.equals("world.setWall")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                Block thisBlock = world.getBlockAt(loc);

                //On fixe le Material au bloc
                org.bukkit.Material matiere = Material.getMaterial(args[3]);
                thisBlock.setType(matiere);

                // Gestion de l'aspect pour les faces
                org.bukkit.block.data.BlockData databloc = thisBlock.getBlockData();

                if (databloc instanceof org.bukkit.block.data.type.Wall) {
                    org.bukkit.block.data.type.Wall mur = (org.bukkit.block.data.type.Wall) thisBlock.getBlockData();
                    for (int i = 4; i < args.length; i = i + 2) {

                        // test de la présence de la proprété up - booléen
                        if (args[i].contentEquals("UP")) {
                            if (args[i + 1].contentEquals("True")) {
                                mur.setUp(true);
                                // affichage de controle
                                //plugin.getLogger().info(args[i]+" : "+args[i+1]);
                            } else mur.setUp(false);
                        } else {
                            // affichage de controle
                            //plugin.getLogger().info(args[i]+" : "+args[i+1]);
                            org.bukkit.block.BlockFace face = org.bukkit.block.BlockFace.valueOf(args[i]);
                            org.bukkit.block.data.type.Wall.Height hauteur = org.bukkit.block.data.type.Wall.Height.valueOf(args[i + 1]);

                            // affichage de controle
                            //plugin.getLogger().info(face.toString()+" : "+hauteur.toString());
                            mur.setHeight(face, hauteur);
                        }
                    }
                    // mise à jour du bloc
                    thisBlock.setBlockData(mur);
                }
                // mise à jour par défaut
                else updateBlock(world, loc, matiere);


                // world.spawnEntity
            } else if (c.equals("world.spawnEntity")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                org.bukkit.entity.EntityType typeanimal = org.bukkit.entity.EntityType.valueOf(args[3]);
                Entity entity = world.spawnEntity(loc, typeanimal);

                // on vérifie qu'il s'agit d'un animal
                if (entity instanceof org.bukkit.entity.Animals) {
                    // s'il y a un argument supplémentaire il indique qu'il s'agit ou non d'un bébé
                    org.bukkit.entity.Animals animal = (org.bukkit.entity.Animals) entity;
                    int nbr_arg = args.length - 4;
                    if (nbr_arg == 1) {
                        if (args[4].contentEquals("BABY")) {
                            animal.setBaby();
                        }
                    }
                }

                send(entity.getEntityId());

                // world.spawnCat
            } else if (c.equals("world.spawnCat")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                org.bukkit.entity.EntityType typeanimal = org.bukkit.entity.EntityType.CAT;
                Entity entity = world.spawnEntity(loc, typeanimal);

                // on vérifie qu'il s'agit d'un chat
                if (entity instanceof org.bukkit.entity.Cat) {
                    // on place les arguments supplémentaires : bébé, couleur pelage, couleur collier
                    org.bukkit.entity.Cat chat = (org.bukkit.entity.Cat) entity;
                    int nbr_arg = args.length - 3;
                    if (nbr_arg >= 1) {  // couleur du pelage
                        org.bukkit.entity.Cat.Type typeDeChat = org.bukkit.entity.Cat.Type.valueOf(args[3]);
                        chat.setCatType(typeDeChat);
                    }
                    if (nbr_arg >= 2) { // bébé
                        if (args[4].contentEquals("BABY")) {
                            chat.setBaby();
                        } else {
                            chat.setAdult();
                        }
                    }
                    if (nbr_arg >= 3) { // couleur du collier
                        // on apprivoise le chat
                        chat.setTamed(true);
                        org.bukkit.DyeColor couleur = org.bukkit.DyeColor.valueOf(args[5]);
                        chat.setCollarColor(couleur);
                        //plugin.getLogger().info("Collier "  + chat.getCollarColor().toString() );
                    }
                }

                // on renvoie l'identifiant de l'animal
                send(entity.getEntityId());


                // world.spawnHorse
            } else if (c.equals("world.spawnHorse")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                org.bukkit.entity.EntityType typeanimal = org.bukkit.entity.EntityType.HORSE;
                Entity entity = world.spawnEntity(loc, typeanimal);

                // on vérifie qu'il s'agit d'un cheval
                if (entity instanceof org.bukkit.entity.Horse) {
                    // on place les arguments supplémentaires : couleur - marques - age (baby ou adult) - domestication - puissance de saut
                    org.bukkit.entity.Horse cheval = (org.bukkit.entity.Horse) entity;
                    int nbr_arg = args.length - 3;
                    if (nbr_arg >= 1) {  // couleur de la robe
                        org.bukkit.entity.Horse.Color robeDeCheval = org.bukkit.entity.Horse.Color.valueOf(args[3]);
                        cheval.setColor(robeDeCheval);
                        //plugin.getLogger().info("robe "  + cheval.getColor().toString() );
                    }
                    if (nbr_arg >= 2) { // marques de la robe
                        org.bukkit.entity.Horse.Style marques = org.bukkit.entity.Horse.Style.valueOf(args[4]);
                        cheval.setStyle(marques);
                        //plugin.getLogger().info("marques "  + cheval.getStyle().toString() );
                    }
                    if (nbr_arg >= 3) { //
                        if (args[5].contentEquals("BABY")) {
                            cheval.setBaby();
                        } else {
                            cheval.setAdult();
                        }
                    }
                    if (nbr_arg >= 4) { // puissance de saut
                        double saut = (double) Double.parseDouble(args[6]);
                        if (saut > 2.0 || saut < 0.0) {
                            saut = 1.0;
                        }
                        cheval.setJumpStrength(saut);
                    }
                    cheval.setTamed(true);
                    //plugin.getLogger().info("apprivoise / Tamed :  "  + cheval.isTamed() );

                }
                // on renvoie l'identifiant de l'animal
                send(entity.getEntityId());

                // world.spawnParrot
            } else if (c.equals("world.spawnParrot")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                org.bukkit.entity.EntityType typeanimal = org.bukkit.entity.EntityType.PARROT;
                Entity entity = world.spawnEntity(loc, typeanimal);

                // on vérifie qu'il s'agit d'un perroquet
                if (entity instanceof org.bukkit.entity.Parrot) {
                    // on place les arguments supplémentaires : variant
                    org.bukkit.entity.Parrot perroquet = (org.bukkit.entity.Parrot) entity;
                    int nbr_arg = args.length - 3;
                    if (nbr_arg >= 1) {  // couleur du perroquet
                        org.bukkit.entity.Parrot.Variant typeDeperroquet = org.bukkit.entity.Parrot.Variant.valueOf(args[3]);
                        perroquet.setVariant(typeDeperroquet);
                    }
                    if (nbr_arg >= 2) { //
                        if (args[4].contentEquals("BABY")) {
                            perroquet.setBaby();
                        } else {
                            perroquet.setAdult();
                        }
                    }
                }
                // on renvoie l'identifiant de l'animal
                send(entity.getEntityId());


                // world.spawnRabbit
            } else if (c.equals("world.spawnRabbit")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                org.bukkit.entity.EntityType typeanimal = org.bukkit.entity.EntityType.RABBIT;
                Entity entity = world.spawnEntity(loc, typeanimal);

                // on vérifie qu'il s'agit d'un lapin
                if (entity instanceof org.bukkit.entity.Rabbit) {
                    // on place les arguments supplémentaires : variant
                    org.bukkit.entity.Rabbit lapin = (org.bukkit.entity.Rabbit) entity;
                    int nbr_arg = args.length - 3;
                    if (nbr_arg >= 1) {  // pelage
                        org.bukkit.entity.Rabbit.Type typeDeLapin = org.bukkit.entity.Rabbit.Type.valueOf(args[3]);
                        lapin.setRabbitType(typeDeLapin);
                    }
                    if (nbr_arg >= 2) { //
                        if (args[4].contentEquals("BABY")) {
                            lapin.setBaby();
                        } else {
                            lapin.setAdult();
                        }
                    }
                }
                // on renvoie l'identifiant de l'animal
                send(entity.getEntityId());


                // world.spawnWolf
            } else if (c.equals("world.spawnWolf")) {
                Location loc = parseRelativeBlockLocation(args[0], args[1], args[2]);
                org.bukkit.entity.EntityType typeanimal = org.bukkit.entity.EntityType.WOLF;
                Entity entity = world.spawnEntity(loc, typeanimal);

                // on vérifie qu'il s'agit d'un loup
                if (entity instanceof org.bukkit.entity.Wolf) {
                    // on place les arguments supplémentaires : bébé,  couleur collier - anima apprivoisé
                    org.bukkit.entity.Wolf loup = (org.bukkit.entity.Wolf) entity;
                    int nbr_arg = args.length - 3;
                    if (nbr_arg >= 1) { // bébé
                        if (args[3].contentEquals("BABY")) {
                            loup.setBaby();
                        } else {
                            loup.setAdult();
                        }
                    }
                    if (nbr_arg >= 2) { // couleur du collier
                        // le loup est apprivoisé
                        loup.setAngry(false);
                        loup.setTamed(true);
                        org.bukkit.DyeColor couleur = org.bukkit.DyeColor.valueOf(args[4]);
                        loup.setCollarColor(couleur);
                        //plugin.getLogger().info("Collier "  + loup.getCollarColor().toString() );
                    } else { // le loup est sauvage
                        loup.setAngry(true);
                        loup.setTamed(false);
                    }
                }

                // on renvoie l'identifiant de l'animal
                send(entity.getEntityId());


                // world.getEntityTypes
            } else if (c.equals("world.getEntityTypes")) {
                StringBuilder bdr = new StringBuilder();

                for (EntityType entityType : org.bukkit.entity.EntityType.values()) { // values() renvoie un tableau de EntityType
                    if (entityType.isSpawnable() && entityType.isAlive()) {
                        // on transforme le tableau en chaîne de caractères dont les champs sont séparés par une ','
                        bdr.append(entityType.toString());
                        //plugin.getLogger().info("entityType : " + entityType.toString());
                        bdr.append(",");
                    }
                }
                send(bdr.toString());

            } else if (c.equals("world.getEntitiesInCuboid")) {
                // DG - Find all entities inside the cuboid described by x,y,z -> x2, y2, z2
                plugin.getLogger().warning("Inside world.getEntitiesInCuboid");
                for (int i = 0; i < args.length; i++) {
                    plugin.getLogger().warning("args[" + Integer.toString(i) + "]" + args[i]);
                }

                Location loc1 = parseRelativeBlockLocation(args[0], args[1], args[2]);
                plugin.getLogger().warning("loc1:" + loc1.toString());
                Location loc2 = parseRelativeBlockLocation(args[3], args[4], args[5]);
                plugin.getLogger().warning("loc2:" + loc2.toString());

                send(getEntities(loc1, loc2));

                // DG - Attempt to call generic method against an block
            } else if (c.equals("world.getAllEntities")) {
                // DG - Find all entities inside the world
                plugin.getLogger().warning("Inside world.getAllEntities");
                send(getEntities());
            } else if (c.equals("block.material.invokeMethod")) {
                // Generically call one of the material methods
                // args[0], args[1], args[2] = location of block (x,y,z)
                // args[3] contains the name of the method - isSolid, isOccluiding, etc.
                plugin.getLogger().warning("Inside block.material.invokeMethod");

                int x = Integer.parseInt(args[0]), y = Integer.parseInt(args[1]), z = Integer.parseInt(args[2]);

                String methodName, methodSignature, methodReturns, methodParams;
                methodName = args[3];

                Block blockRecipient = world.getBlockAt(x, y, z);

                plugin.getLogger().warning("blockRecipient: " + blockRecipient.toString());
                Boolean boolReturn = null;
                Float floatReturn;
                Short shortReturn;
                Integer intReturn;

                plugin.getLogger().warning("methodName: " + methodName);
                switch (methodName) {
                    case "isSolid": {
                        boolReturn = blockRecipient.getType().isSolid();
                        send(boolReturn.toString());
                        break;
                    }
                    case "isBlock": {
                        boolReturn = blockRecipient.getType().isBlock();
                        send(boolReturn.toString());
                        break;
                    }
                    case "isAir": {
                        boolReturn = blockRecipient.getType().isAir();
                        send(boolReturn.toString());
                        break;
                    }
                    case "isBurnable": {
                        boolReturn = blockRecipient.getType().isBurnable();
                        send(boolReturn.toString());
                        break;
                    }
                    case "isEdible": {
                        boolReturn = blockRecipient.getType().isEdible();
                        send(boolReturn.toString());
                        break;
                    }
                    case "isFlammable": {
                        boolReturn = blockRecipient.getType().isFlammable();
                        send(boolReturn.toString());
                        break;
                    }
                    case "isFuel": {
                        boolReturn = blockRecipient.getType().isFuel();
                        send(boolReturn.toString());
                        break;
                    }
                    case "isInteractable": {
                        boolReturn = blockRecipient.getType().isInteractable();
                        send(boolReturn.toString());
                        break;
                    }
                    case "isItem": {
                        boolReturn = blockRecipient.getType().isItem();
                        send(boolReturn.toString());
                        break;
                    }
                    case "isOccluding": {
                        boolReturn = blockRecipient.getType().isOccluding();
                        send(boolReturn.toString());
                        break;
                    }
                    case "isRecord": {
                        boolReturn = blockRecipient.getType().isRecord();
                        send(boolReturn.toString());
                        break;
                    }
                    case "getBlastResistance": {
                        floatReturn = blockRecipient.getType().getBlastResistance();
                        send(floatReturn.toString());
                        break;
                    }
                    case "getHardness": {
                        floatReturn = blockRecipient.getType().getHardness();
                        send(floatReturn.toString());
                        break;
                    }
                    case "getMaxDurability": {
                        shortReturn = blockRecipient.getType().getMaxDurability();
                        send(shortReturn.toString());
                        break;
                    }
                    case "getMaxStackSize": {
                        intReturn = blockRecipient.getType().getMaxStackSize();
                        send(intReturn.toString());
                        break;
                    }
                    default: {
                        plugin.getLogger().warning("methodName: " + methodName + " NOT FOUND OR NOT IMPLEMENTED.");
                        send(null);
                        break;
                    }
                }
            } else if (c.equals("block.isSolid")) {
                plugin.getLogger().warning("Inside block.isSolid");
                int x = Integer.parseInt(args[0]), y = Integer.parseInt(args[1]), z = Integer.parseInt(args[2]);

                Block blockRecipient = world.getBlockAt(x, y, z);

                plugin.getLogger().warning("blockRecipient: " + blockRecipient.toString());
                Boolean boolReturn = null;

                boolReturn = (boolean) blockRecipient.getType().isSolid();
                send(boolReturn.toString());
            } else if (c.equals("block.setSignText")) {
                // Set the sign text
                // args[0], args[1], args[2] = location of block (x,y,z)
                // args[3], args[4], args[5], args[6] contain the 4 (or fewer) lines of text
                plugin.getLogger().warning("block.signText");

                int x = Integer.parseInt(args[0]), y = Integer.parseInt(args[1]), z = Integer.parseInt(args[2]);

                Block blockRecipient = world.getBlockAt(x, y, z);

                plugin.getLogger().warning("blockRecipient: " + blockRecipient.toString());
                plugin.getLogger().warning("blockLocation: (" + Integer.toString(x) + "," + Integer.toString(y) + "," + Integer.toString(z) + ")");
                BlockData blockData = blockRecipient.getBlockData();
                plugin.getLogger().warning("blockData: " + blockData.toString());

                if (blockRecipient.getState() instanceof Sign) {
                    Sign sign = (Sign) blockRecipient.getState();
                    for (int i = 3; i - 3 < 4 && i < args.length; i++) {
                        sign.setLine(i - 3, args[i]);
                        plugin.getLogger().warning("Line: " + Integer.toString(i - 3) + " - " + args[i]);
                    }
                    sign.update();
                } else {
                    plugin.getLogger().warning("Cannot set sign text of a block that is NOT a sign.");
                }
            } else if (c.equals("block.invokeMethod")) {
                // Following the concept of reflection - https://stackoverflow.com/questions/18778819/dynamically-calling-a-class-method-in-java
                // args[0], args[1], args[2] = location of block (x,y,z)
                // args[3] contains the name of the method
                // args[4..n] contain the parameters being passed to
                plugin.getLogger().warning("Inside block.invokeMethod");

                int x = Integer.parseInt(args[0]), y = Integer.parseInt(args[1]), z = Integer.parseInt(args[2]);

                Block blockRecipient = world.getBlockAt(x, y, z);

                plugin.getLogger().warning("blockRecipient: " + blockRecipient.toString());

                BlockData blockData = blockRecipient.getBlockData();
                plugin.getLogger().warning("blockData: " + blockData.toString());

                //                org.bukkit.block.data.type.TNT typeSpecific = (org.bukkit.block.data.type.TNT) blockData;
                //                typeSpecific.isUnstable();

                // Here we either have a method name and no signature or we have both
                // We will split it on "|" and see what we have
                // For example
                // exampleMethod|void:Location:Location
                //
                // would signify that we have a method called exampleMethod that returns void and is passed
                // two parameters, both of type Location

                String[] parts;
                String methodName, methodSignature, methodReturns, methodParams;
                parts = args[3].split("\\|");
                methodName = parts[0];
                if (parts.length > 1) {
                    methodSignature = parts[1];
                } else {
                    methodSignature = "";
                }

                plugin.getLogger().warning("methodName: " + methodName);


                Class classRecipient = blockRecipient.getType().data;
                plugin.getLogger().warning("classRecipient: " + classRecipient.toString());

                int additionalAgs = args.length - 2;
                Method gotMethod = null;
                Integer intReturn;
                Set<Integer> setOfInteger;
                Boolean boolReturn;
                Boolean boolValue;
                Double doubleReturn;
                String stringReturn;
                Bamboo.Leaves leavesReturn;
                Axis axisValue;
                Set<Axis> axesValue;
                String newStringValue;
                BlockFace blockFaceValue;
                Set<BlockFace> blockFacesValue;
                Instrument instrumentValue;
                Note noteValue;
                Rail.Shape railShapeValue;
                Set<Rail.Shape> railShapesValue;
                Slab.Type slabTypeValue;
                boolean booleanArg;
                Bisected.Half bisectedHalfValue;
                Stairs.Shape stairsShapeValue;
                Chest.Type chestTypeValue;
                FaceAttachable.AttachedFace attachedFaceValue;
                Wall.Height wallHeightValue;
                Comparator.Mode comparatorModeValue;
                StructureBlock.Mode structureBlockModeValue;
                Jigsaw.Orientation jigsawOrientationValue;
                Bed.Part bedPartValue;
                int intValue;
                Bell.Attachment bellAttachmentValue;
                TechnicalPiston.Type technicalPistonTypeValue;
                RedstoneWire.Connection redstoneWireConnectionValue;
                Door.Hinge doorHingeValue;

                plugin.getLogger().warning("---------> We have a method Signature : " + methodSignature.toString());
                switch (methodSignature) {
                    case "boolean:void":
                        // Used for isState() type of calls
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        boolReturn = (boolean) gotMethod.invoke(classRecipient.cast(blockData));
                        send(Boolean.toString(boolReturn));
                        break;
                    case "void:boolean":
                        // Used for setState() type of calls
                        gotMethod = classRecipient.getMethod(methodName, boolean.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        booleanArg = BooleanUtils.toBoolean(args[4]);
                        plugin.getLogger().warning("booleanArg: " + Boolean.toString(booleanArg));
                        gotMethod.invoke(classRecipient.cast(blockData), booleanArg);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "int:void":
                        // Used for getValue() type of calls
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        intReturn = (int) gotMethod.invoke(classRecipient.cast(blockData));
                        send(intReturn.toString());
                        break;
                    case "void:int":
                        // Used for setValie() type of calls
                        gotMethod = classRecipient.getMethod(methodName, int.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        int intArg = Integer.parseInt(args[4]);
                        plugin.getLogger().warning("intArg: " + Integer.toString(intArg));
                        gotMethod.invoke(classRecipient.cast(blockData), intArg);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "string:void":
                        // Used for getValue() type of calls
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        stringReturn = (String) gotMethod.invoke(classRecipient.cast(blockData));
                        send(stringReturn);
                        break;
                    case "void:string":
                        // Used for setValue() type of calls
                        gotMethod = classRecipient.getMethod(methodName, String.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        String stringArg = args[4];
                        plugin.getLogger().warning("stringArg: " + stringArg);
                        gotMethod.invoke(classRecipient.cast(blockData), stringArg);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "Leaves:void":
                        // Used for getLeaves in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        leavesReturn = (Bamboo.Leaves) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(leavesReturn.toString());
                        break;
                    case "void:Leaves":
                        // Used for setLeaves in Bamboo
                        leavesReturn = Bamboo.Leaves.NONE;
                        newStringValue = args[4].toUpperCase();
                        if (newStringValue.equals("SMALL")) {
                            leavesReturn = Bamboo.Leaves.SMALL;
                        } else if (newStringValue.equals("LARGE")) {
                            leavesReturn = Bamboo.Leaves.LARGE;
                        }
                        gotMethod = classRecipient.getMethod(methodName, Bamboo.Leaves.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("newLeavesValue: " + leavesReturn.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), leavesReturn);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "void:Axis":
                        // Used for setLeaves in Bamboo
                        newStringValue = args[4].toUpperCase();
                        axisValue = Axis.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, Axis.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("axisReturn: " + axisValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), axisValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "Axis:void":
                        // Used for getAxis in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        axisValue = (Axis) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(axisValue.toString());
                        break;
                    case "Axes:void":
                        // Used for getAxes in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        axesValue = (Set<Axis>) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(axesValue.toString());
                        break;
                    case "boolean:BlockFace":
                        // Used for Multiplefacing
                        // Used for isState() type of calls
                        newStringValue = args[4].toUpperCase();
                        blockFaceValue = BlockFace.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, BlockFace.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("blockFace: " + blockFaceValue.toString());
                        boolReturn = (boolean) gotMethod.invoke(classRecipient.cast(blockData), blockFaceValue);
                        send(Boolean.toString(boolReturn));
                        break;
                    case "void:BlockFace":
                        // Used for setLeaves in Bamboo
                        newStringValue = args[4].toUpperCase();
                        blockFaceValue = BlockFace.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, BlockFace.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("blockFace: " + blockFaceValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), blockFaceValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "void:BlockFace:boolean":
                        // Used for Multiple facing
                        newStringValue = args[4].toUpperCase();
                        booleanArg = BooleanUtils.toBoolean(args[5]);
                        blockFaceValue = BlockFace.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, BlockFace.class, boolean.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("blockFace: " + blockFaceValue.toString());
                        plugin.getLogger().warning("booleanArg: " + Boolean.toString(booleanArg));
                        gotMethod.invoke(classRecipient.cast(blockData), blockFaceValue, booleanArg);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "BlockFace:void":
                        // Used for getAxis in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        blockFaceValue = (BlockFace) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(blockFaceValue.toString());
                        break;
                    case "BlockFaces:void":
                        // Used for getAxes in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        blockFacesValue = (Set<BlockFace>) gotMethod.invoke(classRecipient.cast(blockData));
                        // org.bukkit.block.data.FaceAttachable
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(blockFacesValue.toString());
                        break;
                    case "void:Instrument":
                        // Used for setLeaves in Bamboo
                        newStringValue = args[4].toUpperCase();
                        instrumentValue = Instrument.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, Instrument.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("instrumentValue: " + instrumentValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), instrumentValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "Instrument:void":
                        // Used for getAxis in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        instrumentValue = (Instrument) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(instrumentValue.toString());
                        break;
                    case "void:Note":
                        // Used for setNote in NoteBlock
                        newStringValue = args[4].toUpperCase();
                        noteValue = new Note(Integer.valueOf(newStringValue));
                        gotMethod = classRecipient.getMethod(methodName, Note.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("noteValue: " + noteValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), noteValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "Note:void":
                        // Used for getAxis in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        noteValue = (Note) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(noteValue.toString());
                        break;
                    case "void:Rail":
                        // Used for setNote in NoteBlock
                        newStringValue = args[4].toUpperCase();
                        railShapeValue = Rail.Shape.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, Rail.Shape.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("railShapeValue: " + railShapeValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), railShapeValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "Rail:void":
                        // Used for getAxis in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        railShapeValue = (Rail.Shape) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(railShapeValue.toString());
                        break;
                    case "Rails:void":
                        // Used for getAxis in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        railShapesValue = (Set<Rail.Shape>) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(railShapesValue.toString());
                        break;
                    case "void:Bisected":
                        // Used for setNote in NoteBlock
                        newStringValue = args[4].toUpperCase();
                        bisectedHalfValue = Bisected.Half.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, Bisected.Half.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("bisectedHalfValue: " + bisectedHalfValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), bisectedHalfValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "Bisected:void":
                        // Used for getAxis in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        bisectedHalfValue = (Bisected.Half) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(bisectedHalfValue.toString());
                        break;
                    case "void:Stairs":
                        // Used for setNote in NoteBlock
                        newStringValue = args[4].toUpperCase();
                        stairsShapeValue = Stairs.Shape.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, Stairs.Shape.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("stairsShapeValue: " + stairsShapeValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), stairsShapeValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "Stairs:void":
                        // Used for getAxis in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        stairsShapeValue = (Stairs.Shape) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(stairsShapeValue.toString());
                        break;
                    case "void:Chest":
                        // Used for setNote in NoteBlock
                        newStringValue = args[4].toUpperCase();
                        chestTypeValue = Chest.Type.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, Chest.Type.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("chestTypeValue: " + chestTypeValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), chestTypeValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "Chest:void":
                        // Used for getAxis in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        chestTypeValue = (Chest.Type) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(chestTypeValue.toString());
                        break;
                    case "void:Slab":
                        // Used for setNote in NoteBlock
                        newStringValue = args[4].toUpperCase();
                        slabTypeValue = Slab.Type.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, Slab.Type.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("slabTypeValue: " + slabTypeValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), slabTypeValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "Slab:void":
                        // Used for getAxis in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        slabTypeValue = (Slab.Type) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(slabTypeValue.toString());
                        break;
                    case "void:ComparatorMode":
                        // Used for setNote in NoteBlock
                        newStringValue = args[4].toUpperCase();
                        comparatorModeValue = Comparator.Mode.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, Comparator.Mode.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("comparatorModeValue: " + comparatorModeValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), comparatorModeValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "ComparatorMode:void":
                        // Used for getAxis in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        comparatorModeValue = (Comparator.Mode) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(comparatorModeValue.toString());
                        break;
                    case "void:StructureBlockMode":
                        // Used for setNote in NoteBlock
                        newStringValue = args[4].toUpperCase();
                        structureBlockModeValue = StructureBlock.Mode.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, StructureBlock.Mode.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("structureBlockModeValue: " + structureBlockModeValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), structureBlockModeValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "StructureBlockMode:void":
                        // Used for getAxis in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        structureBlockModeValue = (StructureBlock.Mode) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(structureBlockModeValue.toString());
                        break;
                    case "void:JigsawOrientation":
                        // Used for setNote in NoteBlock
                        newStringValue = args[4].toUpperCase();
                        jigsawOrientationValue = Jigsaw.Orientation.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, Jigsaw.Orientation.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("jigsawOrientationValue: " + jigsawOrientationValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), jigsawOrientationValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "JigsawOrientation:void":
                        // Used for getAxis in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        jigsawOrientationValue = (Jigsaw.Orientation) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(jigsawOrientationValue.toString());
                        break;
                    case "void:BedPart":
                        // Used for setNote in NoteBlock
                        newStringValue = args[4].toUpperCase();
                        bedPartValue = Bed.Part.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, Bed.Part.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("bedPartValue: " + bedPartValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), bedPartValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "BedPart:void":
                        // Used for getAxis in Bamboo
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        bedPartValue = (Bed.Part) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(bedPartValue.toString());
                        break;
                    case "void:AttachedFace":
                        // Used for switch
                        newStringValue = args[4].toUpperCase();
                        attachedFaceValue = FaceAttachable.AttachedFace.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, FaceAttachable.AttachedFace.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("attachedFaceValue: " + attachedFaceValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), attachedFaceValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "AttachedFace:void":
                        // Used for switch
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        attachedFaceValue = (FaceAttachable.AttachedFace) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(attachedFaceValue.toString());
                        break;
                    case "void:BellAttachment":
                        // Used for switch
                        newStringValue = args[4].toUpperCase();
                        bellAttachmentValue = Bell.Attachment.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, Bell.Attachment.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("bellAttachmentValue: " + bellAttachmentValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), bellAttachmentValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "BellAttachment:void":
                        // Used for switch
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        bellAttachmentValue = (Bell.Attachment) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(bellAttachmentValue.toString());
                        break;
                    case "void:TechnicalPiston":
                        // Used for switch
                        newStringValue = args[4].toUpperCase();
                        technicalPistonTypeValue = TechnicalPiston.Type.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, TechnicalPiston.Type.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("technicalPistonTypeValue: " + technicalPistonTypeValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), technicalPistonTypeValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "TechnicalPiston:void":
                        // Used for switch
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        technicalPistonTypeValue = (TechnicalPiston.Type) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(technicalPistonTypeValue.toString());
                        break;
                    case "void:DoorHinge":
                        // Used for switch
                        newStringValue = args[4].toUpperCase();
                        doorHingeValue = Door.Hinge.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, Door.Hinge.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("doorHingeValue: " + doorHingeValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), doorHingeValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "DoorHinge:void":
                        // Used for switch
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        doorHingeValue = (Door.Hinge) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(doorHingeValue.toString());
                        break;
                    case "void:BlockFace:WallHeight":
                        // Used for wall
                        newStringValue = args[4].toUpperCase();
                        blockFaceValue = BlockFace.valueOf(newStringValue);
                        newStringValue = args[5].toUpperCase();
                        wallHeightValue = Wall.Height.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, BlockFace.class, Wall.Height.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("wallHeightValue: " + wallHeightValue.toString());
                        plugin.getLogger().warning("blockFaceValue: " + blockFaceValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), blockFaceValue, wallHeightValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "WallHeight:BlockFace":
                        // Used for wall
                        newStringValue = args[4].toUpperCase();
                        blockFaceValue = BlockFace.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, BlockFace.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("blockFaceValue: " + blockFaceValue.toString());
                        wallHeightValue = (Wall.Height) gotMethod.invoke(classRecipient.cast(blockData), blockFaceValue);
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(wallHeightValue.toString());
                        break;
                    case "void:BlockFace:RedstoneWire":
                        // Used for wall
                        newStringValue = args[4].toUpperCase();
                        blockFaceValue = BlockFace.valueOf(newStringValue);
                        newStringValue = args[5].toUpperCase();
                        redstoneWireConnectionValue = RedstoneWire.Connection.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, BlockFace.class, RedstoneWire.Connection.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("redstoneWireConnectionValue: " + redstoneWireConnectionValue.toString());
                        plugin.getLogger().warning("blockFaceValue: " + blockFaceValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), blockFaceValue, redstoneWireConnectionValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "RedstoneWire:BlockFace":
                        // Used for wall
                        newStringValue = args[4].toUpperCase();
                        blockFaceValue = BlockFace.valueOf(newStringValue);
                        gotMethod = classRecipient.getMethod(methodName, BlockFace.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("blockFaceValue: " + blockFaceValue.toString());
                        redstoneWireConnectionValue = (RedstoneWire.Connection) gotMethod.invoke(classRecipient.cast(blockData), blockFaceValue);
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(redstoneWireConnectionValue.toString());
                        break;
                    case "void:int:boolean":
                        // Used for brewing stand
                        intValue = Integer.parseInt(args[4]);
                        boolValue = BooleanUtils.toBoolean(args[5]);
                        gotMethod = classRecipient.getMethod(methodName, int.class, boolean.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("intValue: " + Integer.toString(intValue));
                        plugin.getLogger().warning("boolValue: " + boolValue.toString());
                        gotMethod.invoke(classRecipient.cast(blockData), intValue, boolValue);
                        // Now associated the block data with the block
                        blockRecipient.setBlockData(blockData);
                        break;
                    case "boolean:int":
                        // Used for brewing stand
                        intValue = Integer.parseInt(args[4]);
                        gotMethod = classRecipient.getMethod(methodName, int.class);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        plugin.getLogger().warning("intValue: " + Integer.toString(intValue));
                        boolReturn = (boolean) gotMethod.invoke(classRecipient.cast(blockData), intValue);
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(Boolean.toString(boolReturn));
                        break;
                    case "SetOfInt:void":
                        // Used for brewing stand
                        gotMethod = classRecipient.getMethod(methodName);
                        plugin.getLogger().warning("gotMethod: " + gotMethod.toString());
                        setOfInteger = (Set<Integer>) gotMethod.invoke(classRecipient.cast(blockData));
                        // Now associate the block data with the block
                        blockRecipient.setBlockData(blockData);
                        send(setOfInteger.toString());
                        break;
                    case "void:double":
                        // setHealth
                        gotMethod = classRecipient.getMethod(methodName, double.class);
                        doubleReturn = Double.parseDouble(args[3]);
                        gotMethod.invoke(blockRecipient, doubleReturn);
                        break;
                    case "Double:void":
                        // getHealth
                        gotMethod = classRecipient.getMethod(methodName);
                        doubleReturn = (Double) gotMethod.invoke(blockRecipient);
                        send(doubleReturn.toString());
                        break;
                    default:
                        plugin.getLogger().warning("Didn't find the correct method signature for[" + methodSignature + "]");
                }

                // DG - Attempt to call generic method against an entity
            } else if (c.equals("world.getPlayerEntityID")) {
                Player player;
                player = plugin.getHostPlayer();
                int playerEntityID = player.getEntityId();
                send(Integer.toString(playerEntityID));
            } else if (c.equals("entity.invokeMethodXXX")) {
                // Following the concept of reflection - https://stackoverflow.com/questions/18778819/dynamically-calling-a-class-method-in-java
                // argv[0] contains the entity
                // argv[1] contains the name of the method
                plugin.getLogger().warning("--------------------------------->");
                plugin.getLogger().warning("Inside entity.invokeMethod");
                for (int i = 0; i < args.length; i++) {
                    plugin.getLogger().warning("Args(" + Integer.toString(i) + ") = " + args[i]);
                }

                // org.bukkit.entity.Entity entityRecipient = (org.bukkit.entity.Entity) plugin.getEntity(Integer.parseInt(args[0]));
                org.bukkit.entity.Entity entityRecipient = plugin.getEntity(Integer.parseInt(args[0]));

                //Entity entityRecipient = plugin.getEntity(Integer.parseInt(args[0]));
                plugin.getLogger().warning("entityRecipient: " + entityRecipient.toString());

                // Here we either have a method name and no signature or we have both
                // We will split it on "|" and see what we have
                // For example
                // exampleMethod|void:Location:Location
                //
                // would signify that we have a method called exampleMethod that returns void and is passed
                // two parameters, both of type Location

                String[] parts;
                String methodName, methodSignature, methodReturns, methodParams;
                parts = args[1].split("\\|");
                methodName = parts[0];
                if (parts.length > 1) {
                    methodSignature = parts[1];
                } else {
                    methodSignature = "";
                }

                plugin.getLogger().warning("methodName: " + methodName);

                int additionalAgs = args.length - 2;

                EntityType entityType = entityRecipient.getType();
                plugin.getLogger().warning("entityType: " + entityType.toString());

                // Class classRecipient = entityType.getEntityClass();
                // Class classRecipient = org.bukkit.entity.Entity;
                Class classRecipient = entityRecipient.getClass();
                plugin.getLogger().warning("classRecipient: " + classRecipient.toString());
                // org.bukkit.craftbukkit.v1_16_R3.entity.CraftWolf
                // Method methodActual = classRecipient.getDeclaredMethod(methodName);

                // Method methodActual = org.bukkit.entity.Wolf.class.getMethod(methodName, (Class<?>) null);
                // Method methodActual = org.bukkit.entity.Wolf.class.getMethod(methodName);
                Method methodActual = null;
                Integer intReturn;
                Boolean boolReturn;
                boolean booleanReturn;
                Double doubleReturn;
                double doubleReturn2;
                DyeColor dyeColorReturn;
                UUID uuidReturn;
                Integer entityID;
                Entity entityParameter;
                EntityType entityTypeReturn;
                String stringReturn, stringParameter;
                Set<String> stringsReturn;
                BlockFace blockfaceReturn, blockFaceParameter;
                Location locationReturn, locationParameter;
                Vector vectorReturn, vectorParameter;
                Double x, y, z;
                World worldReturn;

                float floatReturn, floatParamter;
                // String[] methodArgs = {};

                if (methodSignature.isEmpty()) {
                    // Old method for calling methods
                    switch (methodName) {
                        case "damage":
                            methodActual = classRecipient.getMethod(methodName, double.class);
                            doubleReturn = Double.parseDouble(args[2]);
                            methodActual.invoke(entityRecipient, doubleReturn);
                            break;
                        case "getHealth":
                            methodActual = classRecipient.getMethod(methodName);
                            doubleReturn = (Double) methodActual.invoke(entityRecipient);
                            send(doubleReturn.toString());
                            break;
                        case "setBaby", "setAdult":
                            methodActual = classRecipient.getMethod(methodName);
                            methodActual.invoke(entityRecipient);
                            break;
                        case "getAge":
                            methodActual = classRecipient.getMethod(methodName);
                            intReturn = (Integer) methodActual.invoke(entityRecipient);
                            send(Integer.toString(intReturn));
                            break;
                        case "setAge":
                            methodActual = classRecipient.getMethod(methodName, int.class);
                            intReturn = Integer.parseInt(args[2]);
                            methodActual.invoke(entityRecipient, intReturn);
                            break;
                        case "isAdult", "isBaby":
                            methodActual = classRecipient.getMethod(methodName);
                            boolReturn = (Boolean) methodActual.invoke(entityRecipient);
                            send(Boolean.toString(boolReturn));
                            break;
                        case "setTamed", "setAngry":
                            // Object[] arguments = new Object[] {Object.class};
                            // methodActual = classRecipient.getMethod(methodName, Class.forName("boolean"));
                            methodActual = classRecipient.getMethod(methodName, boolean.class);
                            boolean state = BooleanUtils.toBoolean(args[2]);
                            methodActual.invoke(entityRecipient, state);
                            break;
                        case "setCollarColor":
                            org.bukkit.DyeColor colour = org.bukkit.DyeColor.valueOf(args[2].toUpperCase());
                            methodActual = classRecipient.getMethod(methodName, Class.forName("org.bukkit.DyeColor"));
                            methodActual.invoke(entityRecipient, colour);
                            break;
                        case "setOwner":
                            org.bukkit.entity.AnimalTamer owner = getCurrentPlayer();
                            methodActual = classRecipient.getMethod(methodName, Class.forName("org.bukkit.entity.AnimalTamer"));
                            methodActual.invoke(entityRecipient, owner);
                    }
                } else {
                    // Here we have a method signature
                    plugin.getLogger().warning("We have a method Signature: (" + methodSignature + ")");
                    switch (methodSignature) {

                        case "void:void":
                            // OK
                            // setHealth
                            methodActual = classRecipient.getMethod(methodName);
                            methodActual.invoke(entityRecipient);
                            break;
                        case "void:double":
                            // OK
                            // setHealth
                            methodActual = classRecipient.getMethod(methodName, double.class);
                            doubleReturn = Double.parseDouble(args[2]);
                            methodActual.invoke(entityRecipient, doubleReturn);
                            break;
//                        case "Double:void":
//                            // OK
//                            // getHealth
//                            methodActual = classRecipient.getMethod(methodName);
//                            doubleReturn = (Double) methodActual.invoke(entityRecipient);
//                            send(doubleReturn.toString());
//                            break;
                        case "double:void":
                            // getHeight
                            // Is this needed (i.e. double and Double?)
                            methodActual = classRecipient.getMethod(methodName);
                            doubleReturn2 = (double) methodActual.invoke(entityRecipient);
                            send(Double.toString(doubleReturn2));
                            break;
                        case "boolean:void":
                            // OK
                            // getHealth
                            methodActual = classRecipient.getMethod(methodName);
                            boolReturn = (boolean) methodActual.invoke(entityRecipient);
                            send(boolReturn.toString());
                            break;
                        case "void:boolean":
                            // OK
                            // setHealth
                            methodActual = classRecipient.getMethod(methodName, boolean.class);
                            boolReturn = (boolean) BooleanUtils.toBoolean(args[2]);
                            methodActual.invoke(entityRecipient, boolReturn);
                            break;
                        case "int:void":
                            // OK
                            // getHealth
                            methodActual = classRecipient.getMethod(methodName);
                            intReturn = (int) methodActual.invoke(entityRecipient);
                            send(intReturn.toString());
                            break;
                        case "void:int":
                            // OK
                            // setHealth
                            methodActual = classRecipient.getMethod(methodName, int.class);
                            intReturn = (int) Integer.parseInt(args[2]);
                            methodActual.invoke(entityRecipient, intReturn);
                            break;
                        case "DyeColor:void":
                            // getHealth
                            methodActual = classRecipient.getMethod(methodName);
                            dyeColorReturn = (DyeColor) methodActual.invoke(entityRecipient);
                            send(dyeColorReturn.toString());
                            break;
                        case "void:DyeColor":
                            // setHealth
                            methodActual = classRecipient.getMethod(methodName, DyeColor.class);
                            dyeColorReturn = DyeColor.valueOf(args[2]);
                            methodActual.invoke(entityRecipient, dyeColorReturn);
                            break;
                        case "UUID:void":
                            // getHealth
                            methodActual = classRecipient.getMethod(methodName);
                            uuidReturn = (UUID) methodActual.invoke(entityRecipient);
                            send(uuidReturn.toString());
                            break;
                        case "void:UUID":
                            // setHealth
                            methodActual = classRecipient.getMethod(methodName, UUID.class);
                            uuidReturn = UUID.fromString(args[2]);
                            methodActual.invoke(entityRecipient, uuidReturn);
                            break;
                        case "EntityType:void":
                            // getHealth
                            methodActual = classRecipient.getMethod(methodName);
                            entityTypeReturn = (EntityType) methodActual.invoke(entityRecipient);
                            send(entityTypeReturn.toString());
                            break;
                        case "boolean:Entity":
                            // addPassenger
                            entityID = Integer.parseInt(args[2]);
                            entityParameter = plugin.getEntity(entityID);
                            methodActual = classRecipient.getMethod(methodName, Entity.class);
                            boolReturn = (boolean) methodActual.invoke(entityRecipient, entityParameter);
                            send(boolReturn.toString());
                            break;
                        case "boolean:String":
                            // addScoreboardTag
                            stringParameter = args[2];
                            methodActual = classRecipient.getMethod(methodName, String.class);
                            boolReturn = (boolean) methodActual.invoke(entityRecipient, args[2]);
                            send(boolReturn.toString());
                            break;
                        case "boolean:Location":
                            // addScoreboardTag
                            x = Double.parseDouble(args[2]);
                            y = Double.parseDouble(args[3]);
                            z = Double.parseDouble(args[4]);
                            plugin.getLogger().warning("We got parameters: x: ( " + Double.toString(x) + " )");
                            plugin.getLogger().warning("We got parameters: y: ( " + Double.toString(y) + " )");
                            plugin.getLogger().warning("We got parameters: z: ( " + Double.toString(z) + " )");

                            methodActual = classRecipient.getMethod(methodName, Location.class);
                            worldReturn = origin.getWorld();
                            plugin.getLogger().warning("worldReturn: ( " + worldReturn.toString() + " )");
                            locationReturn = new Location(worldReturn, x, y, z);
                            plugin.getLogger().warning("locationReturn: ( " + locationReturn.toString() + " )");
                            boolReturn = (boolean) methodActual.invoke(entityRecipient, locationReturn);
                            send(boolReturn.toString());
                            break;
                        case "Strings:void":
                            methodActual = classRecipient.getMethod(methodName);
                            stringsReturn = (Set<String>) methodActual.invoke(entityRecipient);
                            send(stringsReturn.toString());
                            break;
                        case "BlockFace:void":
                            //OK
                            methodActual = classRecipient.getMethod(methodName);
                            blockfaceReturn = (BlockFace) methodActual.invoke(entityRecipient);
                            send(blockfaceReturn.toString());
                            break;
                        case "Location:void":
                            // getHealth
                            methodActual = classRecipient.getMethod(methodName);
                            locationReturn = (Location) methodActual.invoke(entityRecipient);
                            send(locationReturn.toString());
                            break;
                        case "Vector:void":
                            // getVelocity
                            methodActual = classRecipient.getMethod(methodName);
                            vectorReturn = (Vector) methodActual.invoke(entityRecipient);
                            send(vectorReturn.toString());
                            break;
                        case "void:Vector":
                            // setVelocity
                            methodActual = classRecipient.getMethod(methodName, Vector.class);
                            x = (double) Double.parseDouble(args[2]);
                            y = (double) Double.parseDouble(args[3]);
                            z = (double) Double.parseDouble(args[4]);
                            vectorReturn = new Vector(x, y, z);
                            methodActual.invoke(entityRecipient, vectorReturn);
                            break;
                        case "void:Float":
                            // getRotation
                            methodActual = classRecipient.getMethod(methodName, float.class);
                            floatParamter = (float) Float.parseFloat(args[2]);
                            methodActual.invoke(entityRecipient, floatParamter);
                            break;
                        default:
                            plugin.getLogger().warning("Didn't find the correct method signature for[" + methodSignature + "]");
                    }
                }
                // not a command which is supported
            } else if (c.equals("world.invokeMethod") || c.equals("entity.invokeMethod")) {
                // Following the concept of reflection - https://stackoverflow.com/questions/18778819/dynamically-calling-a-class-method-in-java
                // argv[0] contains the entity
                // argv[1] contains the name of the method

                plugin.getLogger().warning("--------------------------------->");
                plugin.getLogger().warning("Inside " + c);

                int firstArg;
                World worldRecipient = origin.getWorld();
                Class classRecipient;
                Entity entityRecipient = null;
                EntityType entityType;
                String iam;

                if (c.equals("world.invokeMethod")) {
                    iam = "world";
                    classRecipient = worldRecipient.getClass();
                    firstArg = 1;
                } else {
                    iam = "entity";
                    entityRecipient = plugin.getEntity(Integer.parseInt(args[0]));
                    plugin.getLogger().warning("entityRecipient: " + entityRecipient.toString());
                    entityType = entityRecipient.getType();
                    plugin.getLogger().warning("entityType: " + entityType.toString());
                    classRecipient = entityRecipient.getClass();
                    firstArg = 2;
                }
                plugin.getLogger().warning("worldRecipient: " + worldRecipient.toString());
                plugin.getLogger().warning("classRecipient: " + classRecipient.toString());
                plugin.getLogger().warning("firstArg: " + Integer.toString(firstArg));
                for (int i = 0; i < args.length; i++) {
                    plugin.getLogger().warning("Args(" + Integer.toString(i) + ") = " + args[i]);
                }

                // Here we either have a method name and no signature or we have both
                // We will split it on "|" and see what we have
                // For example
                // exampleMethod|void:Location:Location
                //
                // would signify that we have a method called exampleMethod that returns void and is passed
                // two parameters, both of type Location

                String[] parts;
                String methodName, methodSignature;
                parts = args[firstArg - 1].split("\\|");
                methodName = parts[0];
                if (parts.length > 1) {
                    methodSignature = parts[1];
                } else {
                    methodSignature = "";
                }

                plugin.getLogger().warning("methodName: " + methodName);

                Method methodActual = null;
                Integer intReturn;
                Boolean boolReturn;
                boolean booleanReturn;
                Double doubleReturn;
                double doubleReturn2;
                DyeColor dyeColorReturn;
                UUID uuidReturn;
                Integer entityID;
                Entity entityParameter;
                EntityType entityTypeReturn;
                String stringReturn, stringParameter;
                String[] stringsReturn;
                Set<String> stringSetReturn;
                BlockFace blockfaceReturn, blockFaceParameter;
                Location locationReturn, locationParameter;
                Vector vectorReturn, vectorParameter;
                Double x, y, z;
                World worldReturn;
                float floatReturn, floatParamter;
                LightningStrike lightningStrike;
                long longParameter, longReturn;
                TreeType treeTypeParameter, treeTypeReturn;
                LivingEntity livingEntityParameter, liveEntityReturn;
                AnimalTamer animalTamerParameter, animalTamerReturn;
                Horse.Color horseColorParameter, horseColorReturn;
                Horse.Style horseStyleParameter, horseStyleReturn;
                String serializedObject, serializedObjectReturn;
                PotionEffect potionEffectParameter, potionEffectReturn;
                boolean booleanParameter;
                Color colorParameter, colorReturn;

                // Here we have a method signature
                plugin.getLogger().warning("We have a method Signature: (" + methodSignature + ")");
                switch (methodSignature) {

                    case "void:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //methodActual.invoke(worldRecipient);
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        break;
                    case "void:double":
                        // OK
                        methodActual = classRecipient.getMethod(methodName, double.class);
                        doubleReturn = Double.parseDouble(args[firstArg]);
                        //methodActual.invoke(worldRecipient, doubleReturn);
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, doubleReturn);
                        break;
//                    case "Double:void":
//                        // getHealth
//                        methodActual = classRecipient.getMethod(methodName);
//                        //doubleReturn = (Double) methodActual.invoke(worldRecipient);
//                        doubleReturn = (Double) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
//                        send(doubleReturn.toString());
//                        break;
                    case "double:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //doubleReturn2 = (double) methodActual.invoke(worldRecipient);
                        doubleReturn2 = (double) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(Double.toString(doubleReturn2));
                        break;
                    case "boolean:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //boolReturn = (boolean) methodActual.invoke(worldRecipient);
                        boolReturn = (boolean) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(boolReturn.toString());
                        break;
                    case "void:boolean":
                        // OK
                        methodActual = classRecipient.getMethod(methodName, boolean.class);
                        boolReturn = (boolean) BooleanUtils.toBoolean(args[firstArg]);
                        //methodActual.invoke(worldRecipient, boolReturn);
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, boolReturn);
                        break;
                    case "string:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //boolReturn = (boolean) methodActual.invoke(worldRecipient);
                        stringReturn = (String) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(stringReturn);
                        break;
                    case "void:string":
                        // OK
                        methodActual = classRecipient.getMethod(methodName, String.class);
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, args[firstArg]);
                        break;
                    case "int:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //intReturn = (int) methodActual.invoke(worldRecipient);
                        intReturn = (int) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(intReturn.toString());
                        break;
                    case "void:int":
                        // OK
                        methodActual = classRecipient.getMethod(methodName, int.class);
                        intReturn = (int) Integer.parseInt(args[firstArg]);
                        //methodActual.invoke(worldRecipient, intReturn);
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, intReturn);
                        break;
                    case "long:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        longReturn = (long) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(Long.toString(longReturn));
                        break;
                    case "void:long":
                        // OK
                        methodActual = classRecipient.getMethod(methodName, long.class);
                        longParameter = (long) Long.parseLong(args[firstArg]);
                        //methodActual.invoke(worldRecipient, intReturn);
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, longParameter);
                        break;
                    case "DyeColor:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //dyeColorReturn = (DyeColor) methodActual.invoke(worldRecipient);
                        dyeColorReturn = (DyeColor) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(dyeColorReturn.toString());
                        break;
                    case "void:DyeColor":
                        // OK
                        methodActual = classRecipient.getMethod(methodName, DyeColor.class);
                        dyeColorReturn = DyeColor.valueOf(args[firstArg]);
                        //methodActual.invoke(worldRecipient, dyeColorReturn);
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, dyeColorReturn);
                        break;
                    case "UUID:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //uuidReturn = (UUID) methodActual.invoke(worldRecipient);
                        uuidReturn = (UUID) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(uuidReturn.toString());
                        break;
                    case "void:UUID":
                        // OK
                        methodActual = classRecipient.getMethod(methodName, UUID.class);
                        uuidReturn = UUID.fromString(args[firstArg]);
                        //methodActual.invoke(worldRecipient, uuidReturn);
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, uuidReturn);
                        break;
//                    case "EntityType:void":
//                        // getHealth
//                        methodActual = classRecipient.getMethod(methodName);
//                        //entityTypeReturn = (EntityType) methodActual.invoke(worldRecipient);
//                        entityTypeReturn = (EntityType) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
//                        send(entityTypeReturn.toString());
//                        break;
                    case "EntityType:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //entityTypeReturn = (EntityType) methodActual.invoke(worldRecipient);
                        entityTypeReturn = (EntityType) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(entityTypeReturn.toString());
                        break;
                    case "LivingEntity:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //entityTypeReturn = (EntityType) methodActual.invoke(worldRecipient);
                        liveEntityReturn = (LivingEntity) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(liveEntityReturn.toString());
                        break;
                    case "void:LivingEntity":
                        // OK
                        entityID = Integer.parseInt(args[firstArg]);
                        entityParameter = plugin.getEntity(entityID);
                        plugin.getLogger().warning("entityID: " + Integer.toString(entityID));
                        plugin.getLogger().warning("entityParameter: " + entityParameter.toString());
                        methodActual = classRecipient.getMethod(methodName, LivingEntity.class);
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, entityParameter);
                        break;
                    case "AnimalTamer:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //entityTypeReturn = (EntityType) methodActual.invoke(worldRecipient);
                        animalTamerReturn = (AnimalTamer) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(animalTamerReturn.toString());
                        break;
                    case "void:AnimalTamer":
                        // OK
                        entityID = Integer.parseInt(args[firstArg]);
                        animalTamerParameter = (AnimalTamer) plugin.getEntity(entityID);
                        plugin.getLogger().warning("entityID: " + Integer.toString(entityID));
                        plugin.getLogger().warning("animalTamerParameter: " + animalTamerParameter.toString());
                        methodActual = classRecipient.getMethod(methodName, AnimalTamer.class);
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, animalTamerParameter);
                        break;
                    case "HorseColor:void":
                        // getColor
                        methodActual = classRecipient.getMethod(methodName);
                        //entityTypeReturn = (EntityType) methodActual.invoke(worldRecipient);
                        horseColorReturn = (Horse.Color) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(horseColorReturn.toString());
                        break;
                    case "void:HorseColor":
                        // setColor
                        horseColorParameter = Horse.Color.valueOf(args[firstArg].toUpperCase());
                        plugin.getLogger().warning("horseColorParameter: " + horseColorParameter.toString());
                        methodActual = classRecipient.getMethod(methodName, Horse.Color.class);
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, horseColorParameter);
                        break;
                    case "HorseStyle:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //entityTypeReturn = (EntityType) methodActual.invoke(worldRecipient);
                        horseStyleReturn = (Horse.Style) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(horseStyleReturn.toString());
                        break;
                    case "void:HorseStyle":
                        // OK
                        horseStyleParameter = Horse.Style.valueOf(args[firstArg].toUpperCase());
                        plugin.getLogger().warning("horseStyleParameter: " + horseStyleParameter.toString());
                        methodActual = classRecipient.getMethod(methodName, Horse.Style.class);
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, horseStyleParameter);
                        break;
                    case "void:Entity":
                        // attack
                        entityID = Integer.parseInt(args[firstArg]);
                        entityParameter = plugin.getEntity(entityID);
                        plugin.getLogger().warning("entityID: " + Integer.toString(entityID));
                        plugin.getLogger().warning("entityParameter: " + entityParameter.toString());
                        methodActual = classRecipient.getMethod(methodName, Entity.class);
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, entityParameter);
                        break;
                    case "boolean:Entity":
                        // OK
                        entityID = Integer.parseInt(args[firstArg]);
                        entityParameter = plugin.getEntity(entityID);
                        methodActual = classRecipient.getMethod(methodName, Entity.class);
                        //boolReturn = (boolean) methodActual.invoke(worldRecipient, entityParameter);
                        boolReturn = (boolean) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, entityParameter);
                        send(boolReturn.toString());
                        break;
                    case "boolean:String":
                        // OK
                        stringParameter = args[firstArg];
                        methodActual = classRecipient.getMethod(methodName, String.class);
                        //boolReturn = (boolean) methodActual.invoke(worldRecipient, args[firstArg]);
                        boolReturn = (boolean) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, args[firstArg]);
                        send(boolReturn.toString());
                        break;
                    case "boolean:Location":
                        // OK
                        x = Double.parseDouble(args[firstArg]);
                        y = Double.parseDouble(args[firstArg + 1]);
                        z = Double.parseDouble(args[firstArg + 2]);
                        plugin.getLogger().warning("We got parameters: x: ( " + Double.toString(x) + " )");
                        plugin.getLogger().warning("We got parameters: y: ( " + Double.toString(y) + " )");
                        plugin.getLogger().warning("We got parameters: z: ( " + Double.toString(z) + " )");

                        methodActual = classRecipient.getMethod(methodName, Location.class);
                        worldReturn = origin.getWorld();
                        plugin.getLogger().warning("worldReturn: ( " + worldReturn.toString() + " )");
                        locationReturn = new Location(worldReturn, x, y, z);
                        plugin.getLogger().warning("locationReturn: ( " + locationReturn.toString() + " )");
                        //boolReturn = (boolean) methodActual.invoke(worldRecipient, locationReturn);
                        boolReturn = (boolean) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, locationReturn);
                        send(boolReturn.toString());
                        break;
                    case "void:Location":
                        // OK
                        x = Double.parseDouble(args[firstArg]);
                        y = Double.parseDouble(args[firstArg + 1]);
                        z = Double.parseDouble(args[firstArg + 2]);
                        plugin.getLogger().warning("We got parameters: x: ( " + Double.toString(x) + " )");
                        plugin.getLogger().warning("We got parameters: y: ( " + Double.toString(y) + " )");
                        plugin.getLogger().warning("We got parameters: z: ( " + Double.toString(z) + " )");

                        methodActual = classRecipient.getMethod(methodName, Location.class);
                        worldReturn = origin.getWorld();
                        plugin.getLogger().warning("worldReturn: ( " + worldReturn.toString() + " )");
                        locationReturn = new Location(worldReturn, x, y, z);
                        plugin.getLogger().warning("locationReturn: ( " + locationReturn.toString() + " )");
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, locationReturn);
                        break;
                    case "LightningStrike:Location":
                        // OK
                        x = Double.parseDouble(args[firstArg]);
                        y = Double.parseDouble(args[firstArg + 1]);
                        z = Double.parseDouble(args[firstArg + 2]);
                        plugin.getLogger().warning("We got parameters: x: ( " + Double.toString(x) + " )");
                        plugin.getLogger().warning("We got parameters: y: ( " + Double.toString(y) + " )");
                        plugin.getLogger().warning("We got parameters: z: ( " + Double.toString(z) + " )");

                        methodActual = classRecipient.getMethod(methodName, Location.class);
                        worldReturn = origin.getWorld();
                        plugin.getLogger().warning("worldReturn: ( " + worldReturn.toString() + " )");
                        locationReturn = new Location(worldReturn, x, y, z);
                        plugin.getLogger().warning("locationReturn: ( " + locationReturn.toString() + " )");
                        //lightningStrike = (LightningStrike) methodActual.invoke(worldRecipient, locationReturn);
                        lightningStrike = (LightningStrike) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, locationReturn);
                        plugin.getLogger().warning("lightningString: ( " + lightningStrike.toString() + " )");
                        send(lightningStrike.toString());
                        break;
                    case "boolean:Location:TreeType":
                        // OK
                        locationParameter = toLocation(
                                args[firstArg],
                                args[firstArg + 1],
                                args[firstArg + 2],
                                origin.getWorld()
                        );

                        treeTypeParameter = TreeType.valueOf(args[firstArg + 3].toUpperCase());
                        plugin.getLogger().warning("treeTypeParameter: ( " + treeTypeParameter.toString() + " )");

                        methodActual = classRecipient.getMethod(methodName, Location.class, TreeType.class);

                        boolReturn = (boolean) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, locationParameter, treeTypeParameter);
                        plugin.getLogger().warning("boolReturn: ( " + boolReturn.toString() + " )");
                        send(boolReturn.toString());
                        break;
                    case "Strings:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //stringsReturn = (Set<String>) methodActual.invoke(worldRecipient);
                        stringsReturn = (String[]) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(Arrays.toString(stringsReturn));
                        break;
                    case "StringSet:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //stringsReturn = (Set<String>) methodActual.invoke(worldRecipient);
                        stringSetReturn = (Set<String>) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(stringSetReturn.toString());
                        break;
                    case "BlockFace:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //blockfaceReturn = (BlockFace) methodActual.invoke(worldRecipient);
                        blockfaceReturn = (BlockFace) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(blockfaceReturn.toString());
                        break;
                    case "Location:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //locationReturn = (Location) methodActual.invoke(worldRecipient);
                        locationReturn = (Location) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(locationReturn.toString());
                        break;
                    case "Vector:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //vectorReturn = (Vector) methodActual.invoke(worldRecipient);
                        vectorReturn = (Vector) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(vectorReturn.toString());
                        break;
                    case "void:Vector":
                        // OK
                        methodActual = classRecipient.getMethod(methodName, Vector.class);
                        x = (double) Double.parseDouble(args[firstArg]);
                        y = (double) Double.parseDouble(args[firstArg + 1]);
                        z = (double) Double.parseDouble(args[firstArg + 2]);
                        vectorReturn = new Vector(x, y, z);
                        //methodActual.invoke(worldRecipient, vectorReturn);
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, vectorReturn);
                        break;
                    case "float:void":
                        // getWalkSpeed
                        methodActual = classRecipient.getMethod(methodName);
                        //vectorReturn = (Vector) methodActual.invoke(worldRecipient);
                        floatReturn = (float) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(Float.toString(floatReturn));
                        break;
                    case "void:float":
                        // OK
                        methodActual = classRecipient.getMethod(methodName, float.class);
                        floatParamter = (float) Float.parseFloat(args[firstArg]);
                        //methodActual.invoke(worldRecipient, floatParamter);
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, floatParamter);
                        break;
                    case "boolean:PotionEffect":
                        // OK
                        methodActual = classRecipient.getMethod(methodName, PotionEffect.class);
                        serializedObject = args[firstArg];
                        plugin.getLogger().warning("We got: " + serializedObject);
                        potionEffectParameter = (PotionEffect) new PyHelper().toPotionEffect(serializedObject);
                        plugin.getLogger().warning("As object: " + potionEffectParameter.toString());
                        booleanReturn = (boolean) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, potionEffectParameter);
                        plugin.getLogger().warning("Returning: " + Boolean.toString(booleanReturn));
                        send(booleanReturn);
                        break;
                    case "boolean:PotionEffect:boolean":
                        // OK
                        methodActual = classRecipient.getMethod(methodName, PotionEffect.class, boolean.class);
                        serializedObject = args[firstArg];
                        booleanParameter = BooleanUtils.toBoolean(args[firstArg + 1]);

                        plugin.getLogger().warning("We got: " + serializedObject);
                        potionEffectParameter = (PotionEffect) new PyHelper().toPotionEffect(serializedObject);
                        plugin.getLogger().warning("As object: " + potionEffectParameter.toString());
                        booleanReturn = (boolean) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, potionEffectParameter, booleanParameter);
                        plugin.getLogger().warning("Returning: " + Boolean.toString(booleanReturn));
                        send(booleanReturn);
                    case "Color:void":
                        // OK
                        methodActual = classRecipient.getMethod(methodName);
                        //vectorReturn = (Vector) methodActual.invoke(worldRecipient);
                        colorReturn = (Color) methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient);
                        send(colorReturn.toString());
                        break;
                    //PotionType.AWKWARD
                    case "void:Color":
                        // OK
                        methodActual = classRecipient.getMethod(methodName, Color.class);
                        serializedObject = args[firstArg];
                        plugin.getLogger().warning("We got: " + serializedObject);
                        colorParameter = (Color) new PyHelper().toColor(serializedObject.getBytes(StandardCharsets.UTF_8));
                        methodActual.invoke(iam.equals("entity") ? entityRecipient : worldRecipient, colorParameter);
                        break;
                    default:
                        plugin.getLogger().warning("Didn't find the correct method signature for[" + methodSignature + "]");
                }
            } else {
                plugin.getLogger().warning(c + " is not supported.");
                send("Fail");
            }
        } catch (Exception e) {

            plugin.getLogger().warning("Error occurred handling command");
            e.printStackTrace();
            send("Fail");

        }
    }


    private Location toLocation(String argX, String argY, String argZ, World world) {
        double x, y, z;
        Location locationReturn;

        x = Double.parseDouble(argX);
        y = Double.parseDouble(argY);
        z = Double.parseDouble(argZ);
        plugin.getLogger().warning("We got parameters: x: ( " + Double.toString(x) + " )");
        plugin.getLogger().warning("We got parameters: y: ( " + Double.toString(y) + " )");
        plugin.getLogger().warning("We got parameters: z: ( " + Double.toString(z) + " )");

        locationReturn = new Location(world, x, y, z);
        plugin.getLogger().warning("locationReturn: ( " + locationReturn.toString() + " )");

        return locationReturn;
    }

    // create a cuboid of lots of blocks
    private void setCuboid(Location pos1, Location pos2, Material blockType) {
        int minX, maxX, minY, maxY, minZ, maxZ;
        World world = pos1.getWorld();
        minX = pos1.getBlockX() < pos2.getBlockX() ? pos1.getBlockX() : pos2.getBlockX();
        maxX = pos1.getBlockX() >= pos2.getBlockX() ? pos1.getBlockX() : pos2.getBlockX();
        minY = pos1.getBlockY() < pos2.getBlockY() ? pos1.getBlockY() : pos2.getBlockY();
        maxY = pos1.getBlockY() >= pos2.getBlockY() ? pos1.getBlockY() : pos2.getBlockY();
        minZ = pos1.getBlockZ() < pos2.getBlockZ() ? pos1.getBlockZ() : pos2.getBlockZ();
        maxZ = pos1.getBlockZ() >= pos2.getBlockZ() ? pos1.getBlockZ() : pos2.getBlockZ();

        for (int x = minX; x <= maxX; ++x) {
            for (int z = minZ; z <= maxZ; ++z) {
                for (int y = minY; y <= maxY; ++y) {
                    updateBlock(world, x, y, z, blockType);
                }
            }
        }
    }

    // DG Added to speed up setting mass blocks when block data is involved
    private void setCuboidWithData(Location pos1, Location pos2, Material blockType, org.bukkit.block.data.BlockData data) {
        int minX, maxX, minY, maxY, minZ, maxZ;
        World world = pos1.getWorld();
        minX = pos1.getBlockX() < pos2.getBlockX() ? pos1.getBlockX() : pos2.getBlockX();
        maxX = pos1.getBlockX() >= pos2.getBlockX() ? pos1.getBlockX() : pos2.getBlockX();
        minY = pos1.getBlockY() < pos2.getBlockY() ? pos1.getBlockY() : pos2.getBlockY();
        maxY = pos1.getBlockY() >= pos2.getBlockY() ? pos1.getBlockY() : pos2.getBlockY();
        minZ = pos1.getBlockZ() < pos2.getBlockZ() ? pos1.getBlockZ() : pos2.getBlockZ();
        maxZ = pos1.getBlockZ() >= pos2.getBlockZ() ? pos1.getBlockZ() : pos2.getBlockZ();

        for (int x = minX; x <= maxX; ++x) {
            for (int z = minZ; z <= maxZ; ++z) {
                for (int y = minY; y <= maxY; ++y) {
                    updateBlockWithData(world, x, y, z, blockType, data);
                }
            }
        }
    }

    // get a cuboid of lots of blocks
    private String getBlocks(Location pos1, Location pos2) {
        StringBuilder blockData = new StringBuilder();

        int minX, maxX, minY, maxY, minZ, maxZ;
        World world = pos1.getWorld();
        minX = pos1.getBlockX() < pos2.getBlockX() ? pos1.getBlockX() : pos2.getBlockX();
        maxX = pos1.getBlockX() >= pos2.getBlockX() ? pos1.getBlockX() : pos2.getBlockX();
        minY = pos1.getBlockY() < pos2.getBlockY() ? pos1.getBlockY() : pos2.getBlockY();
        maxY = pos1.getBlockY() >= pos2.getBlockY() ? pos1.getBlockY() : pos2.getBlockY();
        minZ = pos1.getBlockZ() < pos2.getBlockZ() ? pos1.getBlockZ() : pos2.getBlockZ();
        maxZ = pos1.getBlockZ() >= pos2.getBlockZ() ? pos1.getBlockZ() : pos2.getBlockZ();

        for (int y = minY; y <= maxY; ++y) {
            for (int x = minX; x <= maxX; ++x) {
                for (int z = minZ; z <= maxZ; ++z) {
                    Block currentBlock = world.getBlockAt(x, y, z);
                    blockData.append(currentBlock.getType().toString() + ",");
                }
            }
        }

        return blockData.substring(0, blockData.length() > 0 ? blockData.length() - 1 : 0);    // We don't want last comma
    }

    // updates a block
    private void updateBlock(World world, Location loc, Material blockType) {
        Block thisBlock = world.getBlockAt(loc);
        updateBlock(thisBlock, blockType);
    }

    private void updateBlock(World world, int x, int y, int z, Material blockType) {
        Block thisBlock = world.getBlockAt(x, y, z);
        updateBlock(thisBlock, blockType);
    }

    private void updateBlock(Block thisBlock, Material blockType) {
        // check to see if the block is different - otherwise leave it
        thisBlock.setType(blockType);
        //if ((thisBlock.getType() != blockType)) {
        //	thisBlock.setType(blockType);
        //}
    }


    // updates a block with data
    private void updateBlockWithData(World world, Location loc, Material blockType, org.bukkit.block.data.BlockData data) {
        Block thisBlock = world.getBlockAt(loc);
        updateBlockWithData(thisBlock, blockType, data);
    }

    private void updateBlockWithData(World world, int x, int y, int z, Material blockType, org.bukkit.block.data.BlockData data) {
        Block thisBlock = world.getBlockAt(x, y, z);
        updateBlockWithData(thisBlock, blockType, data);
    }

    private void updateBlockWithData(Block thisBlock, Material blockType, org.bukkit.block.data.BlockData data) {
        // check to see if the block is different - otherwise leave it
        thisBlock.setType(blockType);
        thisBlock.setBlockData(data);
    }


    // gets the current player
    public Player getCurrentPlayer() {
        Player player = attachedPlayer;
        // if the player hasnt already been retreived for this session, go and get it.
        if (player == null) {
            player = plugin.getHostPlayer();
            attachedPlayer = player;
        }
        return player;
    }

    public Player getCurrentPlayer(String name) {
        // if a named player is returned use that
        Player player = plugin.getNamedPlayer(name);
        // otherwise if there is an attached player for this session use that
        if (player == null) {
            player = attachedPlayer;
            // otherwise go and get the host player and make that the attached player
            if (player == null) {
                player = plugin.getHostPlayer();
                attachedPlayer = player;
            }
        }
        return player;
    }


    public Location parseRelativeBlockLocation(String xstr, String ystr, String zstr) {
        int x = (int) Double.parseDouble(xstr);
        int y = (int) Double.parseDouble(ystr);
        int z = (int) Double.parseDouble(zstr);
        return parseLocation(origin.getWorld(), x, y, z, origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
    }

    public Location parseRelativeLocation(String xstr, String ystr, String zstr) {
        double x = Double.parseDouble(xstr);
        double y = Double.parseDouble(ystr);
        double z = Double.parseDouble(zstr);
        return parseLocation(origin.getWorld(), x, y, z, origin.getX(), origin.getY(), origin.getZ());
    }

    public Location parseRelativeBlockLocation(String xstr, String ystr, String zstr, float pitch, float yaw) {
        Location loc = parseRelativeBlockLocation(xstr, ystr, zstr);
        loc.setPitch(pitch);
        loc.setYaw(yaw);
        return loc;
    }

    public Location parseRelativeLocation(String xstr, String ystr, String zstr, float pitch, float yaw) {
        Location loc = parseRelativeLocation(xstr, ystr, zstr);
        loc.setPitch(pitch);
        loc.setYaw(yaw);
        return loc;
    }

    public String blockLocationToRelative(Location loc) {
        return parseLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
    }

    public String locationToRelative(Location loc) {
        return parseLocation(loc.getX(), loc.getY(), loc.getZ(), origin.getX(), origin.getY(), origin.getZ());
    }

    private String parseLocation(double x, double y, double z, double originX, double originY, double originZ) {
        return (x - originX) + "," + (y - originY) + "," + (z - originZ);
    }

    private Location parseLocation(World world, double x, double y, double z, double originX, double originY, double originZ) {
        return new Location(world, originX + x, originY + y, originZ + z);
    }

    private String parseLocation(int x, int y, int z, int originX, int originY, int originZ) {
        return (x - originX) + "," + (y - originY) + "," + (z - originZ);
    }

    private Location parseLocation(World world, int x, int y, int z, int originX, int originY, int originZ) {
        return new Location(world, originX + x, originY + y, originZ + z);
    }

    private double getDistance(Entity ent1, Entity ent2) {
        if (ent1 == null || ent2 == null)
            return -1;
        double dx = ent2.getLocation().getX() - ent1.getLocation().getX();
        double dy = ent2.getLocation().getY() - ent1.getLocation().getY();
        double dz = ent2.getLocation().getZ() - ent1.getLocation().getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }


    private String getEntities(World world, String typeEntite) {
        StringBuilder bdr = new StringBuilder();

        // DG Changed to "all" as passing null values seems to be problematic
        if ("all".equals(typeEntite)) {  // chaine vide on recherche toutes les entité
            for (org.bukkit.entity.Entity e : world.getEntities()) {   // on recherche toutes les entités dans le monde
                if (e.getType().isSpawnable()) {
                    bdr.append(getEntityMsg(e));
                }
            }
        } else {  // on ne recherche que les entités du type demandé
            org.bukkit.entity.EntityType entityType = org.bukkit.entity.EntityType.valueOf(typeEntite);
            for (org.bukkit.entity.Entity e : world.getEntities()) {   // on ne recherche que l'entité du type demandé
                if ((e.getType() == entityType) && e.getType().isSpawnable()) {
                    bdr.append(getEntityMsg(e));
                }
            }
        }
        return bdr.toString();
    }

    private String getEntities(World world, int entityId, int distance, String typeEntite) {
        Entity playerEntity = plugin.getEntity(entityId);
        StringBuilder bdr = new StringBuilder();

        plugin.getLogger().info("getEntities: (" + typeEntite + ")");

        // DG Change this as sending a null string through the API seems to cause problem.
        if ("all".equalsIgnoreCase(typeEntite)) {  // chaine vide on recherche toutes les entités à proximité de entityId
            for (org.bukkit.entity.Entity e : world.getEntities()) {   // on recherche toutes les entités dans le monde
                if (e.getType().isSpawnable() && getDistance(playerEntity, e) <= distance) {
                    bdr.append(getEntityMsg(e));
                }
            }
        } else {  // on ne recherche que les entités du type demandé
            org.bukkit.entity.EntityType entityType = org.bukkit.entity.EntityType.valueOf(typeEntite);
            for (org.bukkit.entity.Entity e : world.getEntities()) {   // on ne recherche que l'entité du type demandé
                if ((e.getType() == entityType) && e.getType().isSpawnable() && getDistance(playerEntity, e) <= distance) {
                    bdr.append(getEntityMsg(e));
                }
            }
        }
        return bdr.toString();
    }

    private Integer[] getMinMaxLocations(Location pos1, Location pos2) {
        int minX, maxX, minY, maxY, minZ, maxZ;

        minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        Integer[] returnValues = {minX, maxX, minY, maxY, minZ, maxZ};

        return (returnValues);
    }

    private Set getMinMaxSet(World world, Location pos1, Location pos2) {
        int minX, maxX, minY, maxY, minZ, maxZ;
        Integer[] minMax = getMinMaxLocations(pos1, pos2);
        Set allLocations = new HashSet();
        Location tempLocation;

        minX = minMax[0];
        maxX = minMax[1];
        minY = minMax[2];
        maxY = minMax[3];
        minZ = minMax[4];
        maxZ = minMax[5];

        for (int x = minX; x <= maxX; ++x) {
            for (int z = minZ; z <= maxZ; ++z) {
                for (int y = minY; y <= maxY; ++y) {
                    tempLocation = new Location(world, (double) x, (double) y, (double) z);
                    allLocations.add(serialisedLocation(tempLocation));
                }
            }
        }
        return (allLocations);
    }

    private String serialisedLocation(Location location) {
        StringBuilder result = new StringBuilder();
        result.append("[");
        result.append(location.getBlockX());
        result.append(location.getBlockY());
        result.append(location.getBlockZ());
        return (result.toString());
    }

    private String getEntities() {
        plugin.getLogger().warning("Inside getEntities: ()");

        StringBuilder result = new StringBuilder();

        World world = origin.getWorld();

        Integer entityCount = 0;
        for (org.bukkit.entity.Entity e : world.getEntities()) { // we get all of the entities in the world - expensive
            if (e.getType().isSpawnable()) {
                entityCount++;
                result.append(getEntityMsg(e));
            }
        }
        plugin.getLogger().warning("Entities processed: " + Integer.toString(entityCount));
        return (result.toString());
    }

    private String getEntities(Location pos1, Location pos2) {
        // DG Want to find all entities in the cuboid from pos1 to pos2
        // Decided that looping through all entities and then looping from all x, y and z
        // in the cuboid would be too expensive so have created a set of the locations
        // in pos1 -> pos2 and will loop through entities and check if their location is
        // in the set
        plugin.getLogger().warning("Insde getEntities: (" + pos1.toString() + ", " + pos2.toString() + ")");

        StringBuilder result = new StringBuilder();
        World world = pos1.getWorld();
        // Create a set of all the blocks between the two locations
        Set locationBounds = getMinMaxSet(world, pos1, pos2);
        plugin.getLogger().warning("Length of locationBounds: " + Integer.toString(locationBounds.size()));
        Integer entityCount = 0;
        for (org.bukkit.entity.Entity e : world.getEntities()) { // we get all of the entities in the world - expensive
            if (e.getType().isSpawnable()) {
                entityCount++;
                if (locationBounds.contains(serialisedLocation(e.getLocation()))) {
                    result.append(getEntityMsg(e));
                }
            }
        }
        plugin.getLogger().warning("Entities processed: " + Integer.toString(entityCount));
        return (result.toString());
    }


    private String getEntityMsg(Entity entity) {
        StringBuilder bdr = new StringBuilder();
        bdr.append(entity.getEntityId());
        bdr.append(",");
        bdr.append(entity.getType().toString());
        bdr.append(",");
        bdr.append(entity.getLocation().getX());
        bdr.append(",");
        bdr.append(entity.getLocation().getY());
        bdr.append(",");
        bdr.append(entity.getLocation().getZ());
        bdr.append("|");
        return bdr.toString();
    }

    private int removeEntities(World world, int entityId, int distance, org.bukkit.entity.EntityType entityType) {
        int removedEntitiesCount = 0;
        Entity playerEntityId = plugin.getEntity(entityId);
        for (Entity e : world.getEntities()) {
            if ((e.getType() == entityType) && getDistance(playerEntityId, e) <= distance) {
                e.remove();
                removedEntitiesCount++;
            }
        }
        return removedEntitiesCount;
    }

    private String getBlockHits() {
        return getBlockHits(-1);
    }

    private String getBlockHits(int entityId) {
        StringBuilder b = new StringBuilder();
        for (Iterator<PlayerInteractEvent> iter = interactEventQueue.iterator(); iter.hasNext(); ) {
            PlayerInteractEvent event = iter.next();
            if (entityId == -1 || event.getPlayer().getEntityId() == entityId) {
                Block block = event.getClickedBlock();
                //plugin.getLogger().info("bloc touche avec epee");
                Location loc = block.getLocation();
                b.append(blockLocationToRelative(loc));
                b.append(",");
                // face du block touché
                b.append(blockFaceToNotch(event.getBlockFace())); // face convertie en entier
                b.append(",");
                b.append(event.getPlayer().getEntityId());
                b.append("|");
                iter.remove();

            }
        }


        if (b.length() > 0)
            b.deleteCharAt(b.length() - 1);

        return b.toString();
    }

    private String getChatPosts() {
        return getChatPosts(-1);
    }

    private String getChatPosts(int entityId) {
        StringBuilder b = new StringBuilder();
        for (Iterator<AsyncPlayerChatEvent> iter = chatPostedQueue.iterator(); iter.hasNext(); ) {
            AsyncPlayerChatEvent event = iter.next();
            if (entityId == -1 || event.getPlayer().getEntityId() == entityId) {
                b.append(event.getPlayer().getEntityId());
                b.append(",");
                b.append(event.getMessage());
                b.append("|");
                iter.remove();
            }
        }
        if (b.length() > 0)
            b.deleteCharAt(b.length() - 1);
        return b.toString();
    }

    private String getProjectileHits() {
        return getProjectileHits(-1);
    }

    private String getProjectileHits(int entityId) {
        StringBuilder b = new StringBuilder();
        for (Iterator<ProjectileHitEvent> iter = projectileHitQueue.iterator(); iter.hasNext(); ) {
            ProjectileHitEvent event = iter.next();
            Arrow arrow = (Arrow) event.getEntity();
            LivingEntity shooter = (LivingEntity) arrow.getShooter();
            if (entityId == -1 || shooter.getEntityId() == entityId) {
                if (shooter instanceof Player) {
                    Player player = (Player) shooter;
                    Block block = arrow.getAttachedBlock();
                    if (block == null)
                        block = arrow.getLocation().getBlock();
                    Location loc = block.getLocation();
                    b.append(blockLocationToRelative(loc));
                    b.append(",");
                    //b.append(1); //blockFaceToNotch(event.getBlockFace()), but don't really care
                    //b.append(",");
                    b.append(player.getPlayerListName());   // nom du joueur

                    Entity hitEntity = event.getHitEntity();
                    if (hitEntity != null) {
                        if (hitEntity instanceof Player) {
                            b.append(",");
                            Player hitPlayer = (Player) hitEntity;
                            b.append(hitPlayer.getEntityId());
                            b.append(",");
                            b.append(hitPlayer.getPlayerListName());
                        } else {
                            b.append(",");
                            b.append(hitEntity.getEntityId());
                            b.append(",");
                            b.append(hitEntity.getType().toString());
                            //plugin.getLogger().info("Entité touchée : " + b.toString());
                        }
                    } else {  // on ajoute une information par défaut : identifiacteur 0 et "" nom de l'entité
                        b.append(",");
                        b.append(0);
                        b.append(",");
                        b.append("");
                    }
                }
                b.append("|");
                arrow.remove();
                iter.remove();
            }
        }
        if (b.length() > 0)
            b.deleteCharAt(b.length() - 1);
        //plugin.getLogger().info("Entité touchée : " + b.toString());
        return b.toString();

    }

    private void clearEntityEvents(int entityId) {
        for (Iterator<PlayerInteractEvent> iter = interactEventQueue.iterator(); iter.hasNext(); ) {
            PlayerInteractEvent event = iter.next();
            if (event.getPlayer().getEntityId() == entityId)
                iter.remove();
        }
        for (Iterator<AsyncPlayerChatEvent> iter = chatPostedQueue.iterator(); iter.hasNext(); ) {
            AsyncPlayerChatEvent event = iter.next();
            if (event.getPlayer().getEntityId() == entityId)
                iter.remove();
        }
        for (Iterator<ProjectileHitEvent> iter = projectileHitQueue.iterator(); iter.hasNext(); ) {
            ProjectileHitEvent event = iter.next();
            Arrow arrow = (Arrow) event.getEntity();
            LivingEntity shooter = (LivingEntity) arrow.getShooter();
            if (shooter.getEntityId() == entityId)
                iter.remove();
        }
    }

    public void send(Object a) {
        send(a.toString());
    }

    public void send(String a) {
        if (pendingRemoval) return;
        synchronized (outQueue) {
            outQueue.add(a);
        }
    }

    public void close() {
        if (closed) return;
        running = false;
        pendingRemoval = true;

        //wait for threads to stop
        try {
            inThread.join(2000);
            outThread.join(2000);
        } catch (InterruptedException e) {
            plugin.getLogger().warning("Failed to stop in/out thread");
            e.printStackTrace();
        }

        try {
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        plugin.getLogger().info("Closed connection to" + socket.getRemoteSocketAddress() + ".");
    }

    public void kick(String reason) {
        try {
            out.write(reason);
            out.flush();
        } catch (Exception e) {
        }
        close();
    }

    /**
     * socket listening thread
     */
    private class InputThread implements Runnable {
        public void run() {
            plugin.getLogger().info("Starting input thread");
            while (running) {
                try {
                    String newLine = in.readLine();
                    //System.out.println(newLine);
                    if (newLine == null) {
                        running = false;
                    } else {
                        inQueue.add(newLine);
                        //System.out.println("Added to in queue");
                    }
                } catch (Exception e) {
                    // if its running raise an error
                    if (running) {
                        if (e.getMessage().equals("Connection reset")) {
                            plugin.getLogger().info("Connection reset");
                        } else {
                            e.printStackTrace();
                        }
                        running = false;
                    }
                }
            }
            //close in buffer
            try {
                in.close();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to close in buffer");
                e.printStackTrace();
            }
        }
    }

    private class OutputThread implements Runnable {
        public void run() {
            plugin.getLogger().info("Starting output thread!");
            while (running) {
                try {
                    String line;
                    while ((line = outQueue.poll()) != null) {
                        out.write(line);
                        out.write('\n');
                    }
                    out.flush();
                    Thread.yield();
                    Thread.sleep(1L);
                } catch (Exception e) {
                    // if its running raise an error
                    if (running) {
                        e.printStackTrace();
                        running = false;
                    }
                }
            }
            //close out buffer
            try {
                out.close();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to close out buffer");
                e.printStackTrace();
            }
        }

    }

    /**
     * from CraftBukkit's org.bukkit.craftbukkit.block.CraftBlock.blockFactToNotch
     */
    public static int blockFaceToNotch(BlockFace face) {
        switch (face) {
            case DOWN:
                return 0;
            case UP:
                return 1;
            case NORTH:
                return 2;
            case SOUTH:
                return 3;
            case WEST:
                return 4;
            case EAST:
                return 5;
            default:
                return 7; // Good as anything here, but technically invalid
        }
    }

}
