package edu.uob;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Paths;
import java.io.IOException;
import java.time.Duration;

class ExampleSTAGTests {

  private GameServer server;

  // Create a new server _before_ every @Test
  @BeforeEach
  void setup() {
      File entitiesFile = Paths.get("config" + File.separator + "basic-entities.dot").toAbsolutePath().toFile();
      File actionsFile = Paths.get("config" + File.separator + "basic-actions.xml").toAbsolutePath().toFile();
      server = new GameServer(entitiesFile, actionsFile);
  }

  String sendCommandToServer(String command) {
      // Try to send a command to the server - this call will timeout if it takes too long (in case the server enters an infinite loop)
      return assertTimeoutPreemptively(Duration.ofMillis(1000), () -> { return server.handleCommand(command);},
      "Server took too long to respond (probably stuck in an infinite loop)");
  }

  // A lot of tests will probably check the game state using 'look' - so we better make sure 'look' works well !
  @Test
  void testLook() {
    String response = sendCommandToServer("simon: look");
    response = response.toLowerCase();
    assertTrue(response.contains("cabin"), "Did not see the name of the current room in response to look");
    assertTrue(response.contains("log cabin"), "Did not see a description of the room in response to look");
    assertTrue(response.contains("magic potion"), "Did not see a description of artifacts in response to look");
    assertTrue(response.contains("wooden trapdoor"), "Did not see description of furniture in response to look");
    assertTrue(response.contains("forest"), "Did not see available paths in response to look");
  }

  // Test that we can pick something up and that it appears in our inventory
  @Test
  void testGet()
  {
      String response;
      sendCommandToServer("simon: get potion");
      response = sendCommandToServer("simon: inv");
      response = response.toLowerCase();
      assertTrue(response.contains("potion"), "Did not see the potion in the inventory after an attempt was made to get it");
      response = sendCommandToServer("simon: look");
      response = response.toLowerCase();
      assertFalse(response.contains("potion"), "Potion is still present in the room after an attempt was made to get it");
  }

  // Test that we can goto a different location (we won't get very far if we can't move around the game !)
  @Test
  void testGoto()
  {
      sendCommandToServer("simon: goto forest");
      String response = sendCommandToServer("simon: look");
      response = response.toLowerCase();
      assertTrue(response.contains("key"), "Failed attempt to use 'goto' command to move to the forest - there is no key in the current location");
  }

  // Add more unit tests or integration tests here.

  // Check if the game map is correctly loaded
  @Test
  void testMapLoading() {
      java.util.HashMap<String, Location> map = server.getGameMap();

      assertTrue(map.containsKey("cabin"), "Map should contain 'cabin'");
      assertTrue(map.containsKey("forest"), "Map should contain 'forest'");
      assertTrue(map.containsKey("storeroom"), "Map should contain 'storeroom'");

      Location cabin = map.get("cabin");
      assertEquals("A log cabin in the woods", cabin.getDescription(), "Cabin description is wrong");

      boolean foundAxe = false;
      boolean foundTrapdoor = false;
      for (GameEntity entity : cabin.getEntities()) {
          if (entity.getName().equals("axe") && entity instanceof Artefact) foundAxe = true;
          if (entity.getName().equals("trapdoor") && entity instanceof Furniture) foundTrapdoor = true;
      }
      assertTrue(foundAxe, "Cabin should contain the artefact 'axe'");
      assertTrue(foundTrapdoor, "Cabin should contain the furniture 'trapdoor'");

      assertTrue(cabin.getPaths().containsKey("forest"), "Cabin should have a path to 'forest'");
  }

  // Check if GameAction correctly processes triggers into lowercase, stores subjects and narration, and initializes with an empty list of consumed entities
  @Test
  void testGameActionModel() {
        GameAction action = new GameAction();

        action.addTrigger("OPEN");
        action.addSubject("trapdoor");
        action.setNarration("You open the door.");

        assertTrue(action.getTriggers().contains("open"), "Trigger should be converted to lowercase 'open'");
        assertFalse(action.getTriggers().contains("OPEN"), "Uppercase trigger should not exist");
        assertTrue(action.getSubjects().contains("trapdoor"), "Subject should be added");
        assertEquals("You open the door.", action.getNarration(), "Narration should match");
        assertTrue(action.getConsumed().isEmpty(), "Consumed should be empty if nothing added");
    }

  // Check if the server correctly parses game actions from XML
  @Test
  void testServerActionParsing() {
        java.util.HashSet<GameAction> actions = server.getValidActions();

        assertFalse(actions.isEmpty(), "Server should have parsed some actions from XML");

        boolean foundOpenAction = false;
        boolean foundChopAction = false;

        for (GameAction action : actions) {
            if (action.getTriggers().contains("open") && action.getTriggers().contains("unlock")) {
                foundOpenAction = true;
                assertTrue(action.getSubjects().contains("trapdoor"), "Open action needs trapdoor");
                assertTrue(action.getConsumed().contains("key"), "Open action consumes key");
                assertTrue(action.getProduced().contains("cellar"), "Open action produces cellar");
            }

            if (action.getTriggers().contains("chop")) {
                foundChopAction = true;
                assertTrue(action.getSubjects().contains("tree") && action.getSubjects().contains("axe"));
                assertTrue(action.getConsumed().contains("tree"), "Chop action should consume tree");
            }
        }

        assertTrue(foundOpenAction, "The 'open' action was not parsed correctly.");
        assertTrue(foundChopAction, "The 'chop' action was not parsed correctly.");
    }

  // Verifies custom action execution including fuzzy matching, entity consumption, and path production.
  @Test
  void testCustomActionExecution() {
        sendCommandToServer("simon: get potion");
        sendCommandToServer("simon: goto forest");
        sendCommandToServer("simon: get key");
        sendCommandToServer("simon: goto cabin");

        String response = sendCommandToServer("simon: please open the trapdoor using the key!!");

        assertTrue(response.toLowerCase().contains("unlock the trapdoor"), "Narration mismatch");
        assertTrue(sendCommandToServer("simon: look").toLowerCase().contains("cellar"), "Produced path missing");
        assertFalse(sendCommandToServer("simon: inv").toLowerCase().contains("key"), "Consumed entity remains");
    }

  // Test health reduction and respawn effects after drinking poison
  @Test
  void testHealthAndDeathMechanics() {
    sendCommandToServer("simon: goto forest");
    sendCommandToServer("simon: get key");
    sendCommandToServer("simon: goto cabin");

    String healthRes = sendCommandToServer("simon: health");
    assertTrue(healthRes.contains("3"), "Initial health should be 3");

    server.getGameMap().get("cabin").addEntity(new Artefact("poison", "Deadly poison"));
    GameAction poisonAction = new GameAction();
    poisonAction.addTrigger("drink");
    poisonAction.addSubject("poison");
    poisonAction.addConsumed("poison");
    poisonAction.addConsumed("health");
    poisonAction.setNarration("You drank the poison and feel terrible.");
    server.getValidActions().add(poisonAction);

    sendCommandToServer("simon: drink poison");
    assertTrue(sendCommandToServer("simon: health").contains("2"), "Health should drop to 2");

    server.getGameMap().get("cabin").addEntity(new Artefact("poison", "Deadly poison"));
    sendCommandToServer("simon: drink poison");

    server.getGameMap().get("cabin").addEntity(new Artefact("poison", "Deadly poison"));
    String finalDrink = sendCommandToServer("simon: drink poison");

    assertTrue(finalDrink.contains("You died"), "Should receive death message");
    assertTrue(sendCommandToServer("simon: health").contains("3"), "Health should reset to 3 after respawn");
    assertFalse(sendCommandToServer("simon: inv").contains("key"), "Inventory should be empty");
  }

  @Test
  void testEdgeCasesAndRobustness() {
    // Test empty commands and invalid inputs
    String emptyRes = sendCommandToServer("simon: ");
    assertTrue(emptyRes.contains("Error") || emptyRes.contains("Invalid"), "Should handle empty commands");

    String nonsenseRes = sendCommandToServer("simon: asdfghjkl");
    assertTrue(nonsenseRes.contains("You cannot do that") || nonsenseRes.contains("Unknown"), "Should handle nonsense commands");

    // Test dropping items not in inventory
    String dropRes = sendCommandToServer("simon: drop dragon");
    assertTrue(dropRes.contains("do not have"), "Should handle dropping non-existent items");

    // Test going to non-existent places
    String gotoRes = sendCommandToServer("simon: goto mars");
    assertTrue(gotoRes.contains("cannot go"), "Should handle invalid paths");
  }

  // Test when multiple actions match the user's input
  @Test
  void testAmbiguousCommands() {
    GameAction hitAction1 = new GameAction();
    hitAction1.addTrigger("hit");
    hitAction1.addSubject("elf");

    GameAction hitAction2 = new GameAction();
    hitAction2.addTrigger("hit");
    hitAction2.addSubject("elf");
    hitAction2.addSubject("potion");

    server.getValidActions().add(hitAction1);
    server.getValidActions().add(hitAction2);

    server.getGameMap().get("cabin").addEntity(new Character("elf", "An angry elf"));
    server.getGameMap().get("cabin").addEntity(new Artefact("potion", "Magic potion"));

    String response = sendCommandToServer("simon: hit the elf with the potion");
    assertTrue(response.toLowerCase().contains("ambiguous"), "Should detect and reject ambiguous commands");
  }
}
