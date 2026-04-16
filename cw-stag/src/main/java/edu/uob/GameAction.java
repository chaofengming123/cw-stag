package edu.uob;

import java.util.HashSet;

public class GameAction {
    private HashSet<String> triggers = new HashSet<>();
    private HashSet<String> subjects = new HashSet<>();
    private HashSet<String> consumed = new HashSet<>();
    private HashSet<String> produced = new HashSet<>();
    private String narration;

    public void addTrigger(String trigger) { triggers.add(trigger.toLowerCase()); }
    public void addSubject(String subject) { subjects.add(subject.toLowerCase()); }
    public void addConsumed(String entity) { consumed.add(entity.toLowerCase()); }
    public void addProduced(String entity) { produced.add(entity.toLowerCase()); }
    public void setNarration(String narration) { this.narration = narration; }

    public HashSet<String> getTriggers() { return triggers; }
    public HashSet<String> getSubjects() { return subjects; }
    public HashSet<String> getConsumed() { return consumed; }
    public HashSet<String> getProduced() { return produced; }
    public String getNarration() { return narration; }
}