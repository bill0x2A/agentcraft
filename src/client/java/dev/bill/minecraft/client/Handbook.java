package dev.bill.minecraft.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The pages of the Office Handbook: one page for each office item. Keep each page within 14 book lines. */
final class Handbook {
    private static final String[][] PAGES = {
        {"Agent Office", "Each mob is one terminal session.\n\nRight-click a mob to open its terminal.\n\nF7: office screen\nF8: default terminal\n\nItems: Agent Office tab, or craft them."},
        {"Nameplate colors", "Green: working\n\nGold: the agent needs you. The mob comes to you.\n\nGrey: resting\n\nRed: unavailable"},
        {"Claude Egg, Codex Egg", "Use on a block. A new shell session starts there as a crab or a robot.\n\nThe egg starts no agent. Start it in the terminal.\n\nCraft: egg + orange or cyan dye"},
        {"Name Badge", "Right-click an agent mob and type a new name.\n\nOnly the nameplate changes.\n\nCraft: paper + string"},
        {"Home Flag", "1. Right-click an agent mob.\n2. Right-click a block.\n\nThe mob walks to its new home and faces you.\n\nCraft: stick + white wool"},
        {"Memo", "Right-click an agent mob and type a text.\n\nSend puts the text in the terminal and presses Enter.\n\nCraft: paper + feather"},
        {"Status Tablet", "Hold it to see each agent, its status and its distance.\n\nRight-click the air to open the office screen.\n\nCraft: iron ingot + redstone"},
        {"Terminal Display", "Use on a block, then choose a terminal.\n\nRight-click the display to type. Sneak and right-click to choose again.\n\nCraft: glass pane + redstone"},
        {"Dismissal Slip", "Right-click an agent mob to remove it. Its session continues. Link it again with F7.\n\nIt also removes a terminal display.\n\nCraft: paper + red dye"},
    };

    static List<Component> pages() {
        List<Component> pages = new ArrayList<>();
        for (String[] page : PAGES) {
            MutableComponent text = Component.literal(page[0] + "\n\n").withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_AQUA);
            pages.add(text.append(Component.literal(page[1]).withStyle(style -> style.withBold(false).withColor(ChatFormatting.BLACK))));
        }
        return pages;
    }
}
