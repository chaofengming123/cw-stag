package edu.uob;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import com.alexmerz.graphviz.Parser;
import com.alexmerz.graphviz.objects.Edge;
import com.alexmerz.graphviz.objects.Graph;
import com.alexmerz.graphviz.objects.Node;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

public final class GameServer {

    private static final char END_OF_TRANSMISSION = 4;

    // 保存游戏中所有的地点 (名称 -> 地点对象)
    private HashMap<String, Location> gameMap;
    // 保存所有在游戏中的玩家 (用户名 -> 玩家对象)
    private HashMap<String, Player> players = new HashMap<>();
    // 记录玩家第一次进入游戏的初始地点
    private Location startLocation;

    public static void main(String[] args) throws IOException {
        File entitiesFile = Paths.get("config" + File.separator + "basic-entities.dot").toAbsolutePath().toFile();
        File actionsFile = Paths.get("config" + File.separator + "basic-actions.xml").toAbsolutePath().toFile();
        GameServer server = new GameServer(entitiesFile, actionsFile);
        server.blockingListenOn(8888);
    }

    /**
    * Do not change the following method signature or we won't be able to mark your submission
    * Instanciates a new server instance, specifying a game with some configuration files
    *
    * @param entitiesFile The game configuration file containing all game entities to use in your game
    * @param actionsFile The game configuration file containing all game actions to use in your game
    */
    public GameServer(File entitiesFile, File actionsFile) {
        // 1. 初始化地图容器
        gameMap = new HashMap<>();

        // 2. 解析实体文件
        parseEntitiesFile(entitiesFile);
        // TODO implement your server logic here
    }

    // 为了方便我们后续做单元测试，临时添加一个 getter 方法
    public HashMap<String, Location> getGameMap() {
        return gameMap;
    }

    // --- 核心解析逻辑：读取 DOT 文件 ---
    private void parseEntitiesFile(File entitiesFile) {
        try {
            Parser parser = new Parser();
            FileReader reader = new FileReader(entitiesFile);
            parser.parse(reader);
            Graph wholeDocument = parser.getGraphs().get(0);
            ArrayList<Graph> sections = wholeDocument.getSubgraphs();

            // DOT 文件包含两个主要子图：locations(0) 和 paths(1)
            Graph locations = sections.get(0);
            Graph paths = sections.get(1);

            // 1. 解析所有的 Locations 及其内部的实体
            boolean isFirstLocation = true;
            for (Graph locationGraph : locations.getSubgraphs()) {
                // 获取地点节点 (比如 cabin, forest)
                Node locationDetails = locationGraph.getNodes(false).get(0);
                String locName = locationDetails.getId().getId();
                String locDesc = locationDetails.getAttribute("description");

                Location newLocation = new Location(locName, locDesc);
                gameMap.put(locName, newLocation);

                // 设置初始出生点（文件中的第一个地点）
                if (isFirstLocation) {
                    startLocation = newLocation;
                    isFirstLocation = false;
                }

                // 获取地点内部的分类（artefacts, furniture, characters）
                for (Graph entityCategory : locationGraph.getSubgraphs()) {
                    String categoryName = entityCategory.getId().getId();

                    // 遍历该分类下的所有具体实体
                    for (Node entityNode : entityCategory.getNodes(false)) {
                        String entityName = entityNode.getId().getId();
                        String entityDesc = entityNode.getAttribute("description");

                        // 根据分类实例化不同类型的对象，并放入当前地点
                        if (categoryName.equals("artefacts")) {
                            newLocation.addEntity(new Artefact(entityName, entityDesc));
                        } else if (categoryName.equals("furniture")) {
                            newLocation.addEntity(new Furniture(entityName, entityDesc));
                        } else if (categoryName.equals("characters")) {
                            newLocation.addEntity(new Character(entityName, entityDesc));
                        }
                    }
                }
            }

            // 2. 检查并确保 "storeroom" (储藏室) 存在
            if (!gameMap.containsKey("storeroom")) {
                gameMap.put("storeroom", new Location("storeroom", "Storage for any entities not placed in the game"));
            }

            // 3. 解析 Paths (连通路径)
            for (Edge pathEdge : paths.getEdges()) {
                String fromName = pathEdge.getSource().getNode().getId().getId();
                String toName = pathEdge.getTarget().getNode().getId().getId();

                Location fromLoc = gameMap.get(fromName);
                Location toLoc = gameMap.get(toName);

                // 将目的地添加到起始地点的路径中
                if (fromLoc != null && toLoc != null) {
                    fromLoc.addPath(toName, toLoc);
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to parse entities file: " + e.getMessage());
        }
    }

    /**
    * Do not change the following method signature or we won't be able to mark your submission
    * This method handles all incoming game commands and carries out the corresponding actions.</p>
    *
    * @param command The incoming command to be processed
    */
    public String handleCommand(String command) {
        // 1. 拆分用户名和指令 (例如 "simon: look")
        String[] parts = command.split(":", 2);
        if (parts.length < 2) return "Error: Invalid command format. Expected 'username: command'.";

        String username = parts[0].trim();
        String rawCommand = parts[1].trim();

        // 2. 获取或创建玩家
        if (!players.containsKey(username)) {
            Player newPlayer = new Player(username, "A traveler in this world");
            newPlayer.setCurrentLocation(startLocation); // startLocation 是你昨天在构造函数里记录的第一个地点
            players.put(username, newPlayer);
        }
        Player currentPlayer = players.get(username);

        // 3. 处理指令 (大小写不敏感)
        String cmdLower = rawCommand.toLowerCase();

        if (cmdLower.equals("look")) {
            return performLook(currentPlayer);
        } else if (cmdLower.startsWith("goto ")) {
            String destination = rawCommand.substring(5).trim();
            return performGoto(currentPlayer, destination);
        } else if (cmdLower.equals("inv") || cmdLower.equals("inventory")) {
            return performInventory(currentPlayer);
        } else if (cmdLower.startsWith("get ")) {
            String itemName = rawCommand.substring(4).trim();
            return performGet(currentPlayer, itemName);
        } else if (cmdLower.startsWith("drop ")) {
            String itemName = rawCommand.substring(5).trim();
            return performDrop(currentPlayer, itemName);
        } else {
            return "Error: Unknown command.";
        }
    }

    /**
    * Do not change the following method signature or we won't be able to mark your submission
    * Starts a *blocking* socket server listening for new connections.
    *
    * @param portNumber The port to listen on.
    * @throws IOException If any IO related operation fails.
    */
    public void blockingListenOn(int portNumber) throws IOException {
        try (ServerSocket s = new ServerSocket(portNumber)) {
            System.out.println("Server listening on port " + portNumber);
            while (!Thread.interrupted()) {
                try {
                    blockingHandleConnection(s);
                } catch (IOException e) {
                    System.out.println("Connection closed");
                }
            }
        }
    }

    /**
    * Do not change the following method signature or we won't be able to mark your submission
    * Handles an incoming connection from the socket server.
    *
    * @param serverSocket The client socket to read/write from.
    * @throws IOException If any IO related operation fails.
    */
    private void blockingHandleConnection(ServerSocket serverSocket) throws IOException {
        try (Socket s = serverSocket.accept();
        BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {
            System.out.println("Connection established");
            String incomingCommand = reader.readLine();
            if(incomingCommand != null) {
                System.out.println("Received message from " + incomingCommand);
                String result = handleCommand(incomingCommand);
                writer.write(result);
                writer.write("\n" + END_OF_TRANSMISSION + "\n");
                writer.flush();
            }
        }
    }

    private String performLook(Player player) {
        Location loc = player.getCurrentLocation();
        StringBuilder result = new StringBuilder();

        // 1. 地点名和描述
        result.append("You are in ").append(loc.getName()).append(". ").append(loc.getDescription()).append("\n");

        // 2. 这里的实体 (Artefacts, Furniture, Characters)
        result.append("You can see:\n");
        for (GameEntity entity : loc.getEntities()) {
            result.append("- ").append(entity.getDescription()).append(" (").append(entity.getName()).append(")\n");
        }

        // 3. 其他玩家
        for (Player other : players.values()) {
            if (other != player && other.getCurrentLocation() == loc) {
                result.append("- Another player is here: ").append(other.getName()).append("\n");
            }
        }

        // 4. 通往其他地点的路径
        result.append("You can go to:\n");
        for (String pathName : loc.getPaths().keySet()) {
            result.append("- ").append(pathName).append("\n");
        }

        return result.toString();
    }

    private String performGoto(Player player, String destination) {
        Location currentLoc = player.getCurrentLocation();
        HashMap<String, Location> availablePaths = currentLoc.getPaths();

        // 检查是否有该路径 (忽略大小写)
        Location nextLoc = null;
        for (String pathName : availablePaths.keySet()) {
            if (pathName.equalsIgnoreCase(destination)) {
                nextLoc = availablePaths.get(pathName);
                break;
            }
        }

        if (nextLoc != null) {
            player.setCurrentLocation(nextLoc);
            return performLook(player); // 移动成功后自动 look
        } else {
            return "You cannot go to " + destination + " from here.";
        }
    }

    private String performInventory(Player player) {
        ArrayList<Artefact> inv = player.getInventory();
        if (inv.isEmpty()) {
            return "Your inventory is empty.";
        }

        StringBuilder result = new StringBuilder("You are carrying:\n");
        for (Artefact artefact : inv) {
            result.append("- ").append(artefact.getDescription())
                    .append(" (").append(artefact.getName()).append(")\n");
        }
        return result.toString();
    }

    private String performGet(Player player, String itemName) {
        Location currentLoc = player.getCurrentLocation();
        GameEntity targetEntity = null;

        // 1. 在当前房间里寻找这个物品 (忽略大小写)
        for (GameEntity entity : currentLoc.getEntities()) {
            if (entity.getName().equalsIgnoreCase(itemName)) {
                targetEntity = entity;
                break;
            }
        }

        // 2. 判断是否找到，以及是否是可收集物品 (Artefact)
        if (targetEntity == null) {
            return "There is no " + itemName + " here.";
        }
        if (!(targetEntity instanceof Artefact)) {
            return "You cannot pick up " + itemName + "!"; // 比如你想 get tree 或者 get trapdoor
        }

        // 3. 转移物品
        currentLoc.removeEntity(targetEntity);
        player.addArtefactToInventory((Artefact) targetEntity);

        return "You picked up the " + itemName + ".";
    }

    private String performDrop(Player player, String itemName) {
        Artefact targetArtefact = null;

        // 1. 在玩家背包里寻找这个物品
        for (Artefact artefact : player.getInventory()) {
            if (artefact.getName().equalsIgnoreCase(itemName)) {
                targetArtefact = artefact;
                break;
            }
        }

        // 2. 如果没找到
        if (targetArtefact == null) {
            return "You do not have " + itemName + " in your inventory.";
        }

        // 3. 转移物品
        player.getInventory().remove(targetArtefact);
        player.getCurrentLocation().addEntity(targetArtefact);

        return "You dropped the " + itemName + ".";
    }
}
