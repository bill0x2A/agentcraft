package dev.bill.minecraft.client;

import dev.bill.minecraft.OfficeFurnitureBlock.Kind;
import dev.bill.minecraft.ProjectBoardEntity;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Furniture controls operate on terminal IDs. Filing never ends a process. */
final class FurnitureScreen extends Screen {
    private final Screen previous;
    private final BlockPos pos;
    private final Kind kind;
    private EditBox projectName;
    private String draft, notice = "";
    private boolean all, busy;
    private int page, ticks;

    FurnitureScreen(Screen previous, BlockPos pos, Kind kind) {
        super(Component.literal(switch (kind) { case DESK -> "Agent desk"; case BOARD -> "Project board"; default -> "Filing cabinet"; }));
        this.previous = previous; this.pos = pos.immutable(); this.kind = kind;
    }
    private ProjectBoardEntity board() {
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof ProjectBoardEntity board ? board : null;
    }
    private OfficeWorld.Binding deskAgent() { return OfficeWorld.atDesk(minecraft.level.dimension().identifier().toString(), pos); }
    private String label(String id) {
        var binding = OfficeWorld.forTerminal(id); var session = OfficeClient.session(id);
        return binding != null ? binding.label() : session != null ? session.label() : "Unavailable session " + id.substring(0, Math.min(8, id.length()));
    }
    private List<String> entries() {
        var ids = new LinkedHashSet<String>();
        if (kind == Kind.BOARD && !all) { if (board() != null) ids.addAll(board().terminals()); }
        else if (kind == Kind.CABINET) {
            OfficeWorld.bindings().stream().filter(b -> b.archived() != all).forEach(b -> ids.add(b.terminal()));
        } else if (kind == Kind.DESK && deskAgent() != null) ids.add(deskAgent().terminal());
        else {
            OfficeClient.sessions.forEach(s -> ids.add(s.terminal()));
            OfficeWorld.bindings().forEach(b -> ids.add(b.terminal()));
        }
        return ids.stream().sorted(Comparator.comparing(this::label)).toList();
    }
    private void button(String text, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(text), b -> action.run()).bounds(x,y,w,20).build()).active = !busy;
    }
    @Override protected void init() {
        int cursor = projectName == null ? 0 : projectName.getCursorPosition();
        boolean focused = projectName != null && projectName.isFocused();
        if (projectName != null) draft = projectName.getValue();
        clearWidgets();
        int w = Math.min(540, width-24), left = (width-w)/2;
        if (kind == Kind.BOARD) {
            if (draft == null) draft = board() == null ? "My project" : board().title();
            projectName = new EditBox(font,left,43,w-192,20,Component.literal("Project name"));
            projectName.setMaxLength(48); projectName.setValue(draft); addRenderableWidget(projectName);
            projectName.setCursorPosition(Math.min(cursor,draft.length())); projectName.setHighlightPos(Math.min(cursor,draft.length()));
            if (focused) setInitialFocus(projectName);
            button("Save name",left+w-188,43,78,() -> {
                String name = projectName.getValue(); change(owner -> ((ProjectBoardEntity)owner.level().getBlockEntity(pos)).title(name));
            });
            button(all ? "Project sessions" : "Add sessions",left+w-106,43,106,() -> { all=!all; page=0; init(); });
        } else if (kind == Kind.CABINET) {
            button(all ? "Show filed sessions" : "File away an agent",left,43,160,() -> { all=!all; page=0; init(); });
        } else button("Refresh sessions",left,43,130,() -> OfficeClient.refresh(minecraft));
        var entries = entries(); int perPage = Math.max(1,(height-158)/34);
        int lastPage = Math.max(0,(entries.size()-1)/perPage); page = Math.min(page,lastPage);
        for (int i=page*perPage; i<Math.min(entries.size(),(page+1)*perPage); i++) {
            String terminal = entries.get(i); int y = 82+(i-page*perPage)*34;
            var binding = OfficeWorld.forTerminal(terminal);
            button("Open",left+w-184,y,48,() -> minecraft.gui.setScreen(new TerminalScreen(this,terminal)));
            if (kind == Kind.BOARD) {
                button("Locate",left+w-132,y,57,() -> locate(terminal));
                boolean member = board()!=null && board().terminals().contains(terminal);
                button(member ? "Remove" : "Add",left+w-71,y,71,() -> change(owner -> ((ProjectBoardEntity)owner.level().getBlockEntity(pos)).toggle(terminal)));
            } else if (kind == Kind.CABINET) {
                button(all ? "File away" : "Restore to desk",left+w-132,y,132,() -> {
                    if (all) change(owner -> OfficeWorld.archive(owner.level().getServer(),terminal));
                    else FurnitureClient.chooseRestore(terminal);
                });
            } else {
                boolean occupied = deskAgent()!=null;
                button(occupied ? "File away" : binding!=null && binding.archived() ? "Restore here" : "Assign here",left+w-132,y,132,
                    () -> change(owner -> {
                        if (occupied) OfficeWorld.archive(owner.level().getServer(),terminal);
                        else OfficeWorld.assignDesk(owner.level().getServer(),owner,pos,terminal);
                    }));
            }
        }
        button("Previous",left,height-61,76,() -> { page=Math.max(0,page-1); init(); });
        button("Next",left+80,height-61,60,() -> { page=Math.min(lastPage,page+1); init(); });
        button("Close",left+w-64,height-61,64,this::onClose);
    }
    private void change(FurnitureClient.Action action) {
        busy=true; init(); FurnitureClient.mutate(this,pos,kind,action,() -> {});
    }
    void finished(String message) { busy=false; notice=message; if(minecraft.gui.screen()==this) init(); }
    private void locate(String terminal) {
        var b=OfficeWorld.forTerminal(terminal);
        notice=b==null ? "Assign this session to a desk first." : b.archived() ? "This agent is filed away. Restore it at a desk."
            : b.label()+": "+b.dimension()+" · "+(int)b.x()+", "+(int)b.y()+", "+(int)b.z();
        OfficeClient.message(minecraft,notice);
    }
    @Override public void tick() { if(!busy && ++ticks%20==0) init(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        g.fill(0,0,width,height,0xee0a101a);
        int w=Math.min(540,width-24),left=(width-w)/2;
        g.text(font,title,left,14,0xff7cdbb6,false);
        String hint=switch(kind) {
            case DESK -> "Work spot: directly beside the desk, on its front side. Add a chair there, facing the desk.";
            case BOARD -> "Group sessions by project. Open their terminals or locate their agents.";
            default -> "Filed agents leave the world. Their terminal processes keep running.";
        };
        g.text(font,font.plainSubstrByWidth(hint,w),left,29,0xffbdcce0,false);
        var entries=entries(); int perPage=Math.max(1,(height-158)/34);
        for(int i=page*perPage;i<Math.min(entries.size(),(page+1)*perPage);i++) {
            String id=entries.get(i);int y=82+(i-page*perPage)*34;
            g.text(font,font.plainSubstrByWidth(label(id),w-194),left,y,0xffe8f1f0,false);
            var b=OfficeWorld.forTerminal(id);
            String status=(b!=null && b.archived()?"Filed · ":"")+OfficeClient.status(id);
            g.text(font,font.plainSubstrByWidth(status,w-194),left,y+12,0xff8c9cb2,false);
        }
        if(entries.isEmpty())g.text(font,"No sessions here yet.",left,85,0xffbdcce0,false);
        super.extractRenderState(g,mouseX,mouseY,delta);
        g.text(font,font.plainSubstrByWidth(notice,w),left,height-34,0xffe5c07b,false);
        g.text(font,"Page "+(page+1)+" · Closing a view never ends its session.",left,height-17,0xff8c9cb2,false);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(previous); }
}
