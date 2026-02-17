package com.bko.config;

/**
 * Represents a skill that can be assigned to an agent.
 * Skills provide additional instructions and capabilities to agents.
 */
public class AgentSkill {

    private String name;
    private String description;
    private String instructions;

    public AgentSkill() {
    }

    public AgentSkill(String name, String description, String instructions) {
        this.name = name;
        this.description = description;
        this.instructions = instructions;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    @Override
    public String toString() {
        return "AgentSkill{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
