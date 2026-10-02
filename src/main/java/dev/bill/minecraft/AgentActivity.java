package dev.bill.minecraft;

/** Activity is supplied by Herdr; animation never guesses from terminal output. */
public enum AgentActivity {
    RESTING("Resting"), WORKING("Working"), ATTENTION("Needs attention"), OFFLINE("Unavailable"), UNKNOWN("Unknown");
    public final String label;
    AgentActivity(String label) { this.label = label; }
    public static AgentActivity fromId(int id) { return id >= 0 && id < values().length ? values()[id] : UNKNOWN; }
}
