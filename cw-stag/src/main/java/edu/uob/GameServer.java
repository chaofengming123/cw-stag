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
        File entitiesFile = Paths.get("config" + File.separator + "extended-entities.dot").toAbsolutePath().toFile();
        File actionsFile = Paths.get("config" + File.separator + "extended-actions.xml").toAbsolutePath().toFile();
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
        // Split player and command
        String[] parts = command.split(":", 2);
        if (parts.length < 2) return "Error: Invalid command format. Expected 'username: command'.";

        String username = parts[0].trim();
        String rawCommand = parts[1].trim();

        // Check if the command is empty
        if (rawCommand.isEmpty()) {
            return "Error: Command cannot be empty";
        }

        // Get or creat player
        if (!players.containsKey(username)) {
            Player newPlayer = new Player(username, "A traveler in this world");
            newPlayer.setCurrentLocation(startLocation);
            players.put(username, newPlayer);
        }
        Player currentPlayer = players.get(username);

        // Handle command
        String cmdLower = rawCommand.toLowerCase();

        if (cmdLower.equals("look")) {
            return performLook(currentPlayer);
        } else if (cmdLower.startsWith("goto ")) {
            String destination = rawCommand.substring(5).trim();
            return performGoto(currentPlayer, destination);
        } else if (cmdLower.equals("inv") || cmdLower.equals("inventory")) {
            return performInventory(currentPlayer);
        } else if (cmdLower.equals("health")) {
            return "Your health is at level " + currentPlayer.getHealth() + ".";
        } else if (cmdLower.startsWith("get ")) {
            String itemName = rawCommand.substring(4).trim();
            return performGet(currentPlayer, itemName);
        } else if (cmdLower.startsWith("drop ")) {
            String itemName = rawCommand.substring(5).trim();
            return performDrop(currentPlayer, itemName);
        } else {
            return processCustomAction(currentPlayer, cmdLower);
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

        // Check if the next location exists
        Location nextLoc = null;
        for (String pathName : availablePaths.keySet()) {
            if (pathName.equalsIgnoreCase(destination)) {
                nextLoc = availablePaths.get(pathName);
                break;
            }
        }

        if (nextLoc != null) {
            player.setCurrentLocation(nextLoc);
            return performLook(player);
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

        // 1. Find this item in the current room
        for (GameEntity entity : currentLoc.getEntities()) {
            if (entity.getName().equalsIgnoreCase(itemName)) {
                targetEntity = entity;
                break;
            }
        }

        // 2. Check if found and verify if it is an Artefact
        if (targetEntity == null) {
            return "There is no " + itemName + " here.";
        }
        if (!(targetEntity instanceof Artefact)) {
            return "You cannot pick up " + itemName + "!"; // 比如你想 get tree 或者 get trapdoor
        }

        // 3. Add to inventory
        currentLoc.removeEntity(targetEntity);
        player.addArtefactToInventory((Artefact) targetEntity);

        return "You picked up the " + itemName + ".";
    }
    // Drop
    private String performDrop(Player player, String itemName) {
        Artefact targetArtefact = null;

        // 1. Find this item in inventory
        for (Artefact artefact : player.getInventory()) {
            if (artefact.getName().equalsIgnoreCase(itemName)) {
                targetArtefact = artefact;
                break;
            }
        }

        // 2. If not found
        if (targetArtefact == null) {
            return "You do not have " + itemName + " in your inventory.";
        }

        // 3. If found
        player.getInventory().remove(targetArtefact);
        player.getCurrentLocation().addEntity(targetArtefact);

        return "You dropped the " + itemName + ".";
    }
    // Parse action
    private void parseActionsFile(File actionsFile) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(actionsFile);
            Element root = document.getDocumentElement();

            // Get all 'action' elements
            NodeList actionsList = root.getElementsByTagName("action");

            for (int i = 0; i < actionsList.getLength(); i++) {
                Element actionElement = (Element) actionsList.item(i);
                GameAction newAction = new GameAction();

                // 1. Parse triggers
                Element triggers = (Element) actionElement.getElementsByTagName("triggers").item(0);
                NodeList keyphrases = triggers.getElementsByTagName("keyphrase");
                for (int j = 0; j < keyphrases.getLength(); j++) {
                    newAction.addTrigger(keyphrases.item(j).getTextContent());
                }

                // 2. Parse subjects
                Element subjects = (Element) actionElement.getElementsByTagName("subjects").item(0);
                NodeList subjectEntities = subjects.getElementsByTagName("entity");
                for (int j = 0; j < subjectEntities.getLength(); j++) {
                    newAction.addSubject(subjectEntities.item(j).getTextContent());
                }

                // 3. Parse consumed
                NodeList consumedNodes = actionElement.getElementsByTagName("consumed");
                if (consumedNodes.getLength() > 0) {
                    Element consumed = (Element) consumedNodes.item(0);
                    NodeList consumedEntities = consumed.getElementsByTagName("entity");
                    for (int j = 0; j < consumedEntities.getLength(); j++) {
                        newAction.addConsumed(consumedEntities.item(j).getTextContent());
                    }
                }

                // 4. Parse produced
                NodeList producedNodes = actionElement.getElementsByTagName("produced");
                if (producedNodes.getLength() > 0) {
                    Element produced = (Element) producedNodes.item(0);
                    NodeList producedEntities = produced.getElementsByTagName("entity");
                    for (int j = 0; j < producedEntities.getLength(); j++) {
                        newAction.addProduced(producedEntities.item(j).getTextContent());
                    }
                }

                // 5. Parse narration
                String narration = actionElement.getElementsByTagName("narration").item(0).getTextContent();
                newAction.setNarration(narration);

                validActions.add(newAction);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse actions XML file: " + e.getMessage());
        }
    }
    // Process action
    private String processCustomAction(Player player, String command) {
        Location currentLoc = player.getCurrentLocation();
        ArrayList<GameAction> possibleActions = new ArrayList<>();

        // Filter by triggers and subjects mentioned in the command
        for (GameAction action : validActions) {
            boolean hasTrigger = false;
            for (String trigger : action.getTriggers()) {
                if (command.contains(trigger)) {
                    hasTrigger = true;
                    break;
                }
            }

            if (hasTrigger) {
                boolean hasAllSubjects = true;
                for (String subject : action.getSubjects()) {
                    if (!command.contains(subject)) {
                        hasAllSubjects = false;
                        break;
                    }
                }
                if (hasAllSubjects) {
                    possibleActions.add(action);
                }
            }
        }

        if (possibleActions.isEmpty()) {
            return "You cannot do that here.";
        }

        // Check entity availability
        ArrayList<GameAction> executableActions = new ArrayList<>();
        for (GameAction action : possibleActions) {
            boolean allAvailable = true;
            for (String subject : action.getSubjects()) {
                boolean found = false;
                // Check inventory
                for (Artefact a : player.getInventory()) {
                    if (a.getName().equals(subject))
                        found = true;
                }
                // Check current location entities
                for (GameEntity e : currentLoc.getEntities()) {
                    if (e.getName().equals(subject))
                        found = true;
                }

                if (!found) {
                    allAvailable = false;
                    break;
                }
            }
            if (allAvailable) {
                executableActions.add(action);
            }
        }

        // Check ambiguity
        if (executableActions.size() > 1) {
            return "Ambiguous command. Please be more specific.";
        } else if (executableActions.isEmpty()) {
            return "You don't have the necessary items to do that.";
        }

        // Check extraneous entities
        GameAction finalAction = executableActions.get(0);
        for (Location loc : gameMap.values()) {
            for (GameEntity e : loc.getEntities()) {
                String entityName = e.getName();
                // If the command mentions an entity that is not a subject of the matched action
                if (command.contains(entityName) && !finalAction.getSubjects().contains(entityName)) {
                    return "You cannot use the " + entityName + " like that.";
                }
            }
        }

        // Execute the valid action
        return executeAction(player, finalAction);
    }
    // Execute action
    private String executeAction(Player player, GameAction action) {
        Location currentLoc = player.getCurrentLocation();
        Location storeroom = gameMap.get("storeroom");

        // Consume entities(remove them from game map/inventory and put them in storeroom)
        for (String consumedName : action.getConsumed()) {
            // Not handle health
            if (consumedName.equals("health")) {
                player.decreaseHealth();
                continue;
            }
            // Remove paths
            if (gameMap.containsKey(consumedName) && !consumedName.equals("storeroom")) {
                currentLoc.getPaths().remove(consumedName);
                continue;
            }

            GameEntity entityToConsume = null;
            // Search in inventory
            for (Artefact a : player.getInventory()) {
                if (a.getName().equals(consumedName)) entityToConsume = a;
            }
            if (entityToConsume != null) {
                player.getInventory().remove((Artefact) entityToConsume);
            } else {
                // Search in current location
                for (GameEntity e : currentLoc.getEntities()) {
                    if (e.getName().equals(consumedName)) entityToConsume = e;
                }
                if (entityToConsume != null) {
                    currentLoc.removeEntity(entityToConsume);
                }
            }

            // Move to storeroom
            if (entityToConsume != null && storeroom != null) {
                storeroom.addEntity(entityToConsume);
            }
        }

        // Produce entities(bring them from storeroom to the current location)
        for (String producedName : action.getProduced()) {
            // Not handle health
            if (producedName.equals("health")) {
                player.increaseHealth();
                continue;
            }
            // Add paths
            if (gameMap.containsKey(producedName) && !producedName.equals("storeroom")) {
                currentLoc.addPath(producedName, gameMap.get(producedName));
                continue;
            }

            // Search in storeroom
            if (storeroom != null) {
                GameEntity entityToProduce = null;
                for (GameEntity e : storeroom.getEntities()) {
                    if (e.getName().equals(producedName)) {
                        entityToProduce = e;
                        break;
                    }
                }
                // Move to current location
                if (entityToProduce != null) {
                    storeroom.removeEntity(entityToProduce);
                    currentLoc.addEntity(entityToProduce);
                }
            }
        }

        // Check if the player has died
        if (player.getHealth() <= 0) {
            Location deathLocation = player.getCurrentLocation();

            // Drop all inventory items
            for (Artefact item : player.getInventory()) {
                deathLocation.addEntity(item);
            }
            player.getInventory().clear();

            // Respawn
            player.setCurrentLocation(startLocation);
            player.resetHealth();

            return action.getNarration() + "\nYou died and lost all your items. You have been returned to the start.";
        }

        return action.getNarration();
    }
}
