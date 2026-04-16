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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class GameServer {

    private static final char END_OF_TRANSMISSION = 4;

    // Save locations
    private HashMap<String, Location> gameMap;
    // Sava players
    private HashMap<String, Player> players = new HashMap<>();
    // Record initial location
    private Location startLocation;
    // Store all parsed actions
    private HashSet<GameAction> validActions;

    // For testing
    public HashSet<GameAction> getValidActions() {
        return validActions;
    }
    public HashMap<String, Location> getGameMap() {
        return gameMap;
    }

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
        gameMap = new HashMap<>();
        // Initialize
        validActions = new HashSet<>();

        parseEntitiesFile(entitiesFile);
        // Parse action
        parseActionsFile(actionsFile);
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
            newPlayer.setCurrentLocation(startLocation);
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

    // Read DOT file
    private void parseEntitiesFile(File entitiesFile) {
        try {
            Parser parser = new Parser();
            FileReader reader = new FileReader(entitiesFile);
            parser.parse(reader);
            Graph wholeDocument = parser.getGraphs().get(0);
            ArrayList<Graph> sections = wholeDocument.getSubgraphs();

            Graph locations = sections.get(0);
            Graph paths = sections.get(1);

            // 1. Parse all locations and their internal entities
            boolean isFirstLocation = true;
            for (Graph locationGraph : locations.getSubgraphs()) {
                // Get location nodes
                Node locationDetails = locationGraph.getNodes(false).get(0);
                String locName = locationDetails.getId().getId();
                String locDesc = locationDetails.getAttribute("description");

                Location newLocation = new Location(locName, locDesc);
                gameMap.put(locName, newLocation);

                // Set initial location
                if (isFirstLocation) {
                    startLocation = newLocation;
                    isFirstLocation = false;
                }

                // Get categories inside the location (artefacts, furniture, characters)
                for (Graph entityCategory : locationGraph.getSubgraphs()) {
                    String categoryName = entityCategory.getId().getId();

                    // Iterate through entities
                    for (Node entityNode : entityCategory.getNodes(false)) {
                        String entityName = entityNode.getId().getId();
                        String entityDesc = entityNode.getAttribute("description");

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

            // 2. Check "storeroom"
            if (!gameMap.containsKey("storeroom")) {
                gameMap.put("storeroom", new Location("storeroom", "Storage for any entities not placed in the game"));
            }

            // 3. Parse paths
            for (Edge pathEdge : paths.getEdges()) {
                String fromName = pathEdge.getSource().getNode().getId().getId();
                String toName = pathEdge.getTarget().getNode().getId().getId();

                Location fromLoc = gameMap.get(fromName);
                Location toLoc = gameMap.get(toName);

                if (fromLoc != null && toLoc != null) {
                    fromLoc.addPath(toName, toLoc);
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to parse entities file: " + e.getMessage());
        }
    }
    // Look
    private String performLook(Player player) {
        Location loc = player.getCurrentLocation();
        StringBuilder result = new StringBuilder();

        // 1. Location
        result.append("You are in ").append(loc.getName()).append(". ").append(loc.getDescription()).append("\n");

        // 2. Entities (Artefacts, Furniture, Characters)
        result.append("You can see:\n");
        for (GameEntity entity : loc.getEntities()) {
            result.append("- ").append(entity.getDescription()).append(" (").append(entity.getName()).append(")\n");
        }

        // 3. Other Players
        for (Player other : players.values()) {
            if (other != player && other.getCurrentLocation() == loc) {
                result.append("- Another player is here: ").append(other.getName()).append("\n");
            }
        }

        // 4. Paths
        result.append("You can go to:\n");
        for (String pathName : loc.getPaths().keySet()) {
            result.append("- ").append(pathName).append("\n");
        }

        return result.toString();
    }
    // Goto
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
    // Inv
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
    // Get
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
    // Drop
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
    // Parse actions
    private void parseActionsFile(File actionsFile) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(actionsFile);
            Element root = document.getDocumentElement();

            // 获取所有的 <action> 节点
            NodeList actionsList = root.getElementsByTagName("action");

            for (int i = 0; i < actionsList.getLength(); i++) {
                Element actionElement = (Element) actionsList.item(i);
                GameAction newAction = new GameAction();

                // 1. 解析 triggers
                Element triggers = (Element) actionElement.getElementsByTagName("triggers").item(0);
                NodeList keyphrases = triggers.getElementsByTagName("keyphrase");
                for (int j = 0; j < keyphrases.getLength(); j++) {
                    newAction.addTrigger(keyphrases.item(j).getTextContent());
                }

                // 2. 解析 subjects
                Element subjects = (Element) actionElement.getElementsByTagName("subjects").item(0);
                NodeList subjectEntities = subjects.getElementsByTagName("entity");
                for (int j = 0; j < subjectEntities.getLength(); j++) {
                    newAction.addSubject(subjectEntities.item(j).getTextContent());
                }

                // 3. 解析 consumed (小心：这个标签可能不存在！)
                NodeList consumedNodes = actionElement.getElementsByTagName("consumed");
                if (consumedNodes.getLength() > 0) {
                    Element consumed = (Element) consumedNodes.item(0);
                    NodeList consumedEntities = consumed.getElementsByTagName("entity");
                    for (int j = 0; j < consumedEntities.getLength(); j++) {
                        newAction.addConsumed(consumedEntities.item(j).getTextContent());
                    }
                }

                // 4. 解析 produced (小心：这个标签也可能不存在！)
                NodeList producedNodes = actionElement.getElementsByTagName("produced");
                if (producedNodes.getLength() > 0) {
                    Element produced = (Element) producedNodes.item(0);
                    NodeList producedEntities = produced.getElementsByTagName("entity");
                    for (int j = 0; j < producedEntities.getLength(); j++) {
                        newAction.addProduced(producedEntities.item(j).getTextContent());
                    }
                }

                // 5. 解析 narration
                String narration = actionElement.getElementsByTagName("narration").item(0).getTextContent();
                newAction.setNarration(narration);

                // 把组装好的 Action 存入服务器字典
                validActions.add(newAction);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse actions XML file: " + e.getMessage());
        }
    }
}
