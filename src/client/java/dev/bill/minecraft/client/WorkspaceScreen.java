package dev.bill.minecraft.client;

import com.google.gson.*;
import dev.bill.minecraft.ProjectBoardEntity;
import dev.bill.minecraft.Studio;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Same persistent group operations as scripts/workspace, with optional world furniture bindings. */
final class WorkspaceScreen extends Screen {
    private final Screen previous;
    private final BlockPos board;
    private JsonObject snapshot = new JsonObject();
    private String selected, tab="Agents", notice="Connecting…";
    private int page, ticks;
    private boolean busy, polling, addExisting;
    private final List<String[]> rows=new ArrayList<>();
    private int left,w;

    WorkspaceScreen(Screen previous) { this(previous,null,""); }
    WorkspaceScreen(Screen previous,BlockPos board,String selected) {
        super(Component.literal("Working groups")); this.previous=previous; this.board=board; this.selected=selected;
    }
    static JsonObject args(String... pairs) {
        var o=new JsonObject(); for(int i=0;i<pairs.length;i+=2)o.addProperty(pairs[i],pairs[i+1]); return o;
    }
    static String str(JsonObject o,String key) { return o.has(key) && !o.get(key).isJsonNull()?o.get(key).getAsString():""; }
    static List<JsonObject> objects(JsonObject o,String key) {
        if(!o.has(key))return List.of(); var result=new ArrayList<JsonObject>();
        for(var e:o.getAsJsonArray(key))result.add(e.getAsJsonObject()); return result;
    }
    static java.util.concurrent.CompletableFuture<JsonObject> call(String op,JsonObject args) {
        var request=TerminalConnection.event("workspace");request.addProperty("op",op);request.add("args",args);
        return OfficeBridge.request(request);
    }
    private JsonObject group() { return objects(snapshot,"groups").stream().filter(g->str(g,"id").equals(selected)).findFirst().orElse(null); }
    private String label(String terminal) {var s=OfficeClient.session(terminal);return s==null?terminal:s.label();}
    private void refresh() {
        if(polling)return;polling=true;
        call("snapshot",new JsonObject()).whenComplete((data,error)->minecraft.execute(()->{
            polling=false;
            if(error!=null)notice="Workspace unavailable: "+failure(error);
            else {snapshot=data; if(notice.equals("Connecting…"))notice="Groups work with or without tasks and issue trackers.";}
            if(minecraft.gui.screen()==this && !busy)init();
        }));
    }
    static String failure(Throwable error){while(error.getCause()!=null)error=error.getCause();return error.getMessage();}
    private void run(String op,JsonObject data) {run(op,data,result->{});}
    private void run(String op,JsonObject data,Consumer<JsonObject> done) {
        busy=true;init();call(op,data).whenComplete((result,error)->minecraft.execute(()->{
            busy=false;
            if(error!=null)notice=failure(error);
            else {notice="Saved";done.accept(result);OfficeClient.refresh(minecraft);}
            refresh();if(minecraft.gui.screen()==this)init();
        }));
    }
    private void button(String label,int x,int y,int width,Runnable action){
        addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,width,20).build()).active=!busy;
    }
    private void form(String title,LinkedHashMap<String,String> fields,Consumer<Map<String,String>> submit){
        minecraft.gui.setScreen(new Form(this,title,fields,values->{minecraft.gui.setScreen(this);submit.accept(values);}));
    }
    private static LinkedHashMap<String,String> fields(String... pairs){var m=new LinkedHashMap<String,String>();for(int i=0;i<pairs.length;i+=2)m.put(pairs[i],pairs[i+1]);return m;}
    private JsonObject ga(){return args("group",selected);}
    @Override protected void init(){
        clearWidgets();rows.clear();w=Math.min(660,width-24);left=(width-w)/2;
        JsonObject g=group();
        if(selected.isEmpty() || g==null){
            button("New group",left,42,108,()->form("Quick working group",fields("Name","","Directory",System.getProperty("user.dir")),v->run("create",args("name",v.get("Name"),"cwd",v.get("Directory")),r->{selected=str(r.getAsJsonObject("group"),"id");page=0;})));
            button("Enter studio",left+114,42,110,()->{
                var server=minecraft.getSingleplayerServer();var id=minecraft.player.getUUID();busy=true;
                server.execute(()->{try {Studio.enter(server.getPlayerList().getPlayer(id));minecraft.execute(()->{busy=false;onClose();});}
                    catch(Exception e){minecraft.execute(()->{busy=false;notice=e.getMessage();init();});}});
            });
            button("Sessions & displays",left+230,42,150,()->minecraft.gui.setScreen(new OfficeScreen(this)));
            var list=objects(snapshot,"groups");paginate(list.size());
            for(int i=page*perPage();i<Math.min(list.size(),(page+1)*perPage());i++){
                var item=list.get(i);int y=rowY(i);
                rows.add(new String[]{str(item,"name"),str(item,"cwd")+(str(item,"parent").isEmpty()?"":" · subgroup")});
                button("Open",left+w-134,y,62,()->{selected=str(item,"id");page=0;init();});
                if(board!=null)button("Link here",left+w-68,y,68,()->bind(str(item,"id")));
            }
        }else{
            String[] tabs={"Agents","Tasks","Issues","Settings"};
            for(int i=0;i<tabs.length;i++){String t=tabs[i];button((tab.equals(t)?"• ":"")+t,left+i*((w-98)/4),42,(w-98)/4-4,()->{tab=t;page=0;init();});}
            button("All groups",left+w-90,42,90,()->{selected="";page=0;init();});
            switch(tab){case "Agents"->agents(g);case "Tasks"->tasks(g);case "Issues"->sources(g);default->settings(g);}
        }
        button("Previous",left,height-54,76,()->{page=Math.max(0,page-1);init();});
        button("Next",left+80,height-54,58,()->{page++;init();});
        button("Refresh",left+142,height-54,68,this::refresh);
        button("Close",left+w-62,height-54,62,this::onClose);
        if(!snapshot.has("groups") && !polling)refresh();
    }
    private int perPage(){return Math.max(1,(height-176)/36);}
    private int rowY(int i){return (tab.equals("Settings")?138:106)+(i-page*perPage())*36;}
    private void paginate(int count){page=Math.min(page,Math.max(0,(count-1)/perPage()));}
    private void agents(JsonObject g){
        String[] choices={"shell","claude","codex"};
        for(int i=0;i<choices.length;i++){String kind=choices[i];button("New "+kind,left+i*94,72,90,()->form("Start "+kind+" in "+str(g,"name"),fields("Name",kind,"Role","member"),v->run("launch",args("group",selected,"agent",kind,"name",v.get("Name"),"role",v.get("Role")),r->notice="Session started. Open it to complete any login or trust prompts.")));}
        button(addExisting?"Group members":"Add existing",left+w-110,72,110,()->{addExisting=!addExisting;page=0;init();});
        var members=objects(g,"members");var ids=new LinkedHashSet<String>();
        if(addExisting)OfficeClient.sessions.forEach(s->ids.add(s.terminal()));else members.forEach(m->ids.add(str(m,"terminal")));
        var list=new ArrayList<>(ids);paginate(list.size());
        for(int i=page*perPage();i<Math.min(list.size(),(page+1)*perPage());i++){
            String id=list.get(i);int y=rowY(i);var member=members.stream().filter(m->str(m,"terminal").equals(id)).findFirst().orElse(null);
            rows.add(new String[]{label(id),OfficeClient.status(id)+(id.equals(str(g,"leader"))?" · Leader":member==null?"":" · "+str(member,"role"))});
            if(addExisting){button(member==null?"Add":"Remove",left+w-74,y,74,()->run(member==null?"join":"leave",args("group",selected,"terminal",id)));}
            else{
                button("Open",left+w-218,y,48,()->minecraft.gui.setScreen(new TerminalScreen(this,id)));
                button("Place",left+w-166,y,48,()->place(id));
                button("Role",left+w-114,y,48,()->form("Agent role",fields("Role",str(member,"role")),v->run("join",args("group",selected,"terminal",id,"role",v.get("Role")))));
                button(id.equals(str(g,"leader"))?"Unlead":"Lead",left+w-62,y,62,()->run("leader",args("group",selected,"terminal",id.equals(str(g,"leader"))?"":id)));
            }
        }
    }
    private void place(String id){
        var session=OfficeClient.session(id);if(session==null){notice="Session unavailable";return;}
        if(OfficeWorld.linked(id)){notice="Already placed. Use its desk or the office tools to move it.";return;}
        var server=minecraft.getSingleplayerServer();var player=minecraft.player.getUUID();
        server.execute(()->{try{OfficeWorld.spawn(server,player,session,session.agent().equals("claude")?"claude":"codex");minecraft.execute(()->notice="Agent placed nearby");}
            catch(Exception e){minecraft.execute(()->notice=e.getMessage());}});
    }
    private void tasks(JsonObject g){
        button("Add freeform task",left,72,140,()->form("New task",fields("Title","","Description",""),v->run("task",args("group",selected,"title",v.get("Title"),"description",v.get("Description")))));
        button("Message agent",left+146,72,120,()->minecraft.gui.setScreen(new MemberPicker(this,objects(g,"members"),id->form("Message "+label(id),fields("Message",""),v->run("send",args("group",selected,"terminal",id,"message",v.get("Message")))))));
        var tasks=objects(snapshot,"tasks").stream().filter(t->str(t,"group").equals(selected)).toList();paginate(tasks.size());
        for(int i=page*perPage();i<Math.min(tasks.size(),(page+1)*perPage());i++){
            var t=tasks.get(i);int y=rowY(i);String source=t.has("issue")?str(t.getAsJsonObject("issue"),"provider")+" "+str(t.getAsJsonObject("issue"),"identifier"):"Freeform";
            rows.add(new String[]{str(t,"title"),source+" · "+str(t,"status")+(str(t,"assignee").isEmpty()?"":" · "+label(str(t,"assignee")))});
            button("Details",left+w-74,y,74,()->minecraft.gui.setScreen(new TaskView(this,t,g)));
        }
    }
    private void sources(JsonObject g){
        button("Connect Linear",left,72,126,()->connect("linear"));
        button("Connect GitHub",left+132,72,126,()->connect("github"));
        var list=objects(g,"sources");paginate(list.size());
        for(int i=page*perPage();i<Math.min(list.size(),(page+1)*perPage());i++){
            var s=list.get(i);int y=rowY(i);rows.add(new String[]{str(s,"provider")+" · "+(str(s,"name").isEmpty()?str(s,"scope"):str(s,"name")),str(s,"scope")});
            button("Browse",left+w-160,y,72,()->minecraft.gui.setScreen(new IssueView(this,s)));
            button("Disconnect",left+w-84,y,84,()->run("disconnect",args("group",selected,"source",str(s,"id"))));
        }
    }
    private void connect(String provider){if(provider.equals("linear")){minecraft.gui.setScreen(new ProjectPicker(this));return;}form("Connect "+provider,fields(provider.equals("linear")?"Project UUID":"Owner/repository",""),v->run("source",args("group",selected,"provider",provider,"scope",v.values().iterator().next())));}
    private void browser(boolean place) {
        run("browser",ga(),r->{
            if(minecraft.gui.screen()!=this || !OfficeClient.localWorld(minecraft))return;
            String url=str(r,"url");
            if(!place){var view=new BrowserPreviewScreen(this);view.surface.navigate(url);minecraft.gui.setScreen(view);return;}
            var server=minecraft.getSingleplayerServer();var player=minecraft.player.getUUID();
            server.execute(()->{try{var owner=server.getPlayerList().getPlayer(player);if(owner==null || server.isPublished())return;var display=BrowserEngine.place(owner);display.url(url);minecraft.execute(()->notice="Group browser placed. It stays running when Minecraft closes.");}
                catch(Exception e){minecraft.execute(()->notice=e.getMessage());}});
        });
    }
    private void settings(JsonObject g){
        button("Edit group",left,72,w/3-4,()->form("Group settings",fields("Name",str(g,"name"),"Directory",str(g,"cwd"),"Instructions",str(g,"instructions"),"Parent group ID",str(g,"parent")),v->run("configure",args("group",selected,"name",v.get("Name"),"cwd",v.get("Directory"),"instructions",v.get("Instructions"),"parent",v.get("Parent group ID")))));
        button("New subgroup",left+w/3,72,w/3-4,()->form("New subgroup",fields("Name","","Directory",str(g,"cwd")),v->run("create",args("name",v.get("Name"),"cwd",v.get("Directory"),"parent",selected))));
        button("Worktrees: "+(str(g,"worktree").equals("agent-choice")?"agent choice":str(g,"worktree")),left+2*w/3,72,w/3-4,()->{
            String next=switch(str(g,"worktree")){case "agent-choice"->"shared";case "shared"->"isolated";default->"agent-choice";};run("configure",args("group",selected,"worktree",next));
        });
        button("Browser",left,100,w/2-4,()->browser(false));
        button("Place browser",left+w/2,100,w/2-4,()->browser(true));
        rows.add(new String[]{str(g,"cwd"),"New sessions start here; existing sessions keep their directory."});
        rows.add(new String[]{"Worktrees: "+str(g,"worktree"),"Preference for agents; the office never creates worktrees automatically."});
        rows.add(new String[]{str(g,"instructions"),"Terminal: workspace context "+selected});
    }
    private void bind(String id){
        var server=minecraft.getSingleplayerServer();var player=minecraft.player.getUUID();var dimension=minecraft.level.dimension();
        server.execute(()->{try{
            var owner=server.getPlayerList().getPlayer(player);
            if(owner==null || server.isPublished() || !owner.level().dimension().equals(dimension) || owner.distanceToSqr(board.getX()+.5,board.getY()+.5,board.getZ()+.5)>64)throw new IllegalStateException("Return to the table to link it");
            var entity=owner.level().getBlockEntity(board);
            if(entity instanceof ProjectBoardEntity b)b.group(id);
            else if(entity instanceof dev.agentcraft.block.entity.StationBlockEntity b)b.setBinding(id);
            else throw new IllegalStateException("Table is no longer here");
            minecraft.execute(()->{selected=id;notice="Table linked to group";init();});
        }catch(Exception e){minecraft.execute(()->notice=e.getMessage());}});
    }
    @Override public void tick(){if(++ticks%40==0 && !busy)refresh();}
    @Override public void extractRenderState(GuiGraphicsExtractor out,int x,int y,float delta){
        out.fill(0,0,width,height,0xee0a101a);var g=group();
        out.text(font,g==null?"WORKING GROUPS":str(g,"name"),left,14,0xff7cdbb6,false);
        out.text(font,font.plainSubstrByWidth(g==null?"Start with a name and directory. Add structure when you need it.":str(g,"cwd"),w),left,29,0xffaabbd0,false);
        int textWidth=tab.equals("Settings")?w:w-(tab.equals("Agents") && g!=null?228:174);
        for(int i=0;i<rows.size();i++){String[] row=rows.get(i);int yy=(tab.equals("Settings")?138:106)+i*36;out.text(font,font.plainSubstrByWidth(row[0],textWidth),left,yy,0xffe8f1f0,false);out.text(font,font.plainSubstrByWidth(row[1],textWidth),left,yy+13,0xffaabbd0,false);}
        if(rows.isEmpty())out.text(font,tab.equals("Issues")?"No issue sources connected. Your group can work without them.":"Nothing here yet. Use the controls above to get started.",left,110,0xffaabbd0,false);
        super.extractRenderState(out,x,y,delta);out.text(font,font.plainSubstrByWidth(notice,w),left,height-25,0xffe5c07b,false);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(previous);}


    static final class MemberPicker extends Screen {
        final WorkspaceScreen parent;final List<JsonObject> members;final Consumer<String> done;int page;
        MemberPicker(WorkspaceScreen p,List<JsonObject> m,Consumer<String> done){super(Component.literal("Choose an agent"));parent=p;members=m;this.done=done;}
        @Override protected void init(){clearWidgets();int count=Math.max(1,(height-100)/26);page=Math.min(page,Math.max(0,(members.size()-1)/count));for(int i=page*count;i<Math.min(members.size(),(page+1)*count);i++){String id=str(members.get(i),"terminal");addRenderableWidget(Button.builder(Component.literal(parent.label(id)),b->{minecraft.gui.setScreen(parent);done.accept(id);}).bounds(20,40+(i-page*count)*26,width-40,20).build());}addRenderableWidget(Button.builder(Component.literal("Previous"),b->{page=Math.max(0,page-1);init();}).bounds(20,height-32,80,20).build());addRenderableWidget(Button.builder(Component.literal("Next"),b->{page++;init();}).bounds(106,height-32,60,20).build());addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width-84,height-32,64,20).build());}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,0xf20a101a);g.text(font,title,20,16,0xff7cdbb6,false);if(members.isEmpty())g.text(font,"Add an agent to the group first.",20,48,0xffbdcce0,false);super.extractRenderState(g,x,y,d);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}
    }
    static final class ProjectPicker extends Screen {
        final WorkspaceScreen parent;JsonObject data=new JsonObject();String notice="Loading projects…",cursor="";boolean busy;int page;
        ProjectPicker(WorkspaceScreen p){super(Component.literal("Connect a Linear project"));parent=p;}
        void load(){busy=true;call("projects",args("cursor",cursor)).whenComplete((r,e)->minecraft.execute(()->{busy=false;if(e!=null)notice=failure(e);else{data=r.getAsJsonObject("projects");notice="Only projects you connect will appear in this group's issue browser.";page=0;}if(minecraft.gui.screen()==this)init();}));}
        @Override protected void init(){clearWidgets();int count=Math.max(1,(height-105)/26);var list=objects(data,"items");for(int i=page*count;i<Math.min(list.size(),(page+1)*count);i++){var project=list.get(i);addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(str(project,"name"),width-60)),b->{parent.run("source",args("group",parent.selected,"provider","linear","scope",str(project,"id"),"name",str(project,"name")));onClose();}).bounds(20,40+(i-page*count)*26,width-40,20).build());}addRenderableWidget(Button.builder(Component.literal("Previous"),b->{page=Math.max(0,page-1);init();}).bounds(20,height-52,80,20).build());addRenderableWidget(Button.builder(Component.literal("Next"),b->{if((page+1)*count<list.size()){page++;init();}else if(!str(data,"cursor").isEmpty()){cursor=str(data,"cursor");load();}}).bounds(106,height-52,60,20).build()).active=!busy;addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width-84,height-52,64,20).build());if(!data.has("items") && notice.equals("Loading projects…") && !busy)load();}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,0xf20a101a);g.text(font,title,20,16,0xff7cdbb6,false);super.extractRenderState(g,x,y,d);int yy=height-26;for(var line:font.split(Component.literal(notice),width-40)){g.text(font,line,20,yy,0xffe5c07b,false);yy+=10;}}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}
    }

    static final class Form extends Screen {
        private final Screen previous;private final LinkedHashMap<String,String> values;private final Consumer<Map<String,String>> done;
        private final Map<String,EditBox> inputs=new LinkedHashMap<>();
        Form(Screen previous,String title,LinkedHashMap<String,String> values,Consumer<Map<String,String>> done){super(Component.literal(title));this.previous=previous;this.values=values;this.done=done;}
        @Override protected void init(){
            inputs.forEach((key,box)->values.put(key,box.getValue()));inputs.clear();clearWidgets();
            int w=Math.min(580,width-32),left=(width-w)/2,i=0;
            for(var entry:values.entrySet()){EditBox box=new EditBox(font,left,50+i*Math.max(30,Math.min(36,(height-90)/Math.max(1,values.size()))),w,20,Component.literal(entry.getKey()));box.setMaxLength(16000);box.setValue(entry.getValue());inputs.put(entry.getKey(),box);addRenderableWidget(box);i++;}
            addRenderableWidget(Button.builder(Component.literal("Save / continue"),b->{inputs.forEach((k,v)->values.put(k,v.getValue()));done.accept(values);}).bounds(left,height-30,130,20).build());
            addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(left+w-80,height-30,80,20).build());
            if(!inputs.isEmpty())setInitialFocus(inputs.values().iterator().next());
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){g.fill(0,0,width,height,0xf20a101a);int left=(width-Math.min(580,width-32))/2;g.text(font,title,left,16,0xff7cdbb6,false);int i=0;for(String key:values.keySet())g.text(font,key,left,39+i++*Math.max(30,Math.min(36,(height-90)/Math.max(1,values.size()))),0xffbdcce0,false);super.extractRenderState(g,x,y,delta);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){minecraft.gui.setScreen(previous);}
    }
    static final class TaskView extends Screen {
        private final WorkspaceScreen parent;private final JsonObject task,group;
        TaskView(WorkspaceScreen p,JsonObject t,JsonObject g){super(Component.literal(str(t,"title")));parent=p;task=t;group=g;}
        @Override protected void init(){
            int left=16;String[] states={"todo","working","blocked","review","done"};
            for(int i=0;i<states.length;i++){String s=states[i];addRenderableWidget(Button.builder(Component.literal(s),b->{parent.run("update-task",args("group",str(group,"id"),"task",str(task,"id"),"status",s));onClose();}).bounds(left+i*76,height-60,72,20).build());}
            addRenderableWidget(Button.builder(Component.literal("Assign"),b->{var members=objects(group,"members");minecraft.gui.setScreen(new Assignment(this,parent,task,group,members));}).bounds(left,height-34,80,20).build());
            addRenderableWidget(Button.builder(Component.literal("Send to assignee"),b->{parent.run("send",args("group",str(group,"id"),"terminal",str(task,"assignee"),"message",str(task,"title")+"\n"+str(task,"description")+(task.has("issue")?"\n"+str(task.getAsJsonObject("issue"),"url"):"")));onClose();}).bounds(left+86,height-34,140,20).build()).active=!str(task,"assignee").isEmpty();
            addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width-80,height-34,64,20).build());
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,0xf20a101a);g.text(font,font.plainSubstrByWidth(title.getString(),width-32),16,16,0xff7cdbb6,false);String body=str(task,"description");if(task.has("issue"))body=str(task.getAsJsonObject("issue"),"url")+"\n"+body;int yy=38;for(var line:font.split(Component.literal(body),width-32)){if(yy>height-80)break;g.text(font,line,16,yy,0xffbdcce0,false);yy+=11;}super.extractRenderState(g,x,y,d);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}
    }
    static final class Assignment extends Screen {
        final Screen previous;final WorkspaceScreen parent;final JsonObject task,group;final List<JsonObject> members;int page;
        Assignment(Screen s,WorkspaceScreen p,JsonObject t,JsonObject g,List<JsonObject> m){super(Component.literal("Assign task"));previous=s;parent=p;task=t;group=g;members=m;}
        void choose(String id){parent.run("update-task",args("group",str(group,"id"),"task",str(task,"id"),"assignee",id));minecraft.gui.setScreen(parent);}
        @Override protected void init(){clearWidgets();int count=Math.max(1,(height-100)/24);page=Math.min(page,Math.max(0,(members.size()-1)/count));for(int i=page*count;i<Math.min(members.size(),(page+1)*count);i++){String id=str(members.get(i),"terminal");addRenderableWidget(Button.builder(Component.literal(parent.label(id)),b->choose(id)).bounds(20,40+(i-page*count)*24,width-40,20).build());}addRenderableWidget(Button.builder(Component.literal("Unassign"),b->choose("")).bounds(20,height-32,80,20).build());addRenderableWidget(Button.builder(Component.literal("Previous"),b->{page=Math.max(0,page-1);init();}).bounds(106,height-32,80,20).build());addRenderableWidget(Button.builder(Component.literal("Next"),b->{page++;init();}).bounds(192,height-32,60,20).build());}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,0xf20a101a);g.text(font,title,20,16,0xff7cdbb6,false);super.extractRenderState(g,x,y,d);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){minecraft.gui.setScreen(previous);}
    }
    static final class IssueView extends Screen {
        final WorkspaceScreen parent;final JsonObject source;JsonObject data=new JsonObject();String notice="Loading…",cursor="";boolean busy;int page;
        IssueView(WorkspaceScreen p,JsonObject s){super(Component.literal("Issues · "+str(s,"scope")));parent=p;source=s;}
        void load(){busy=true;call("issues",args("group",parent.selected,"source",str(source,"id"),"cursor",cursor)).whenComplete((r,e)->minecraft.execute(()->{busy=false;if(e!=null)notice=failure(e);else{data=r.getAsJsonObject("issues");notice="Import selected issues; agents do not start automatically.";page=0;}if(minecraft.gui.screen()==this)init();}));}
        @Override protected void init(){clearWidgets();int count=Math.max(1,(height-100)/34);var items=objects(data,"items");for(int i=page*count;i<Math.min(items.size(),(page+1)*count);i++){var issue=items.get(i);addRenderableWidget(Button.builder(Component.literal("Import "+str(issue,"identifier")),b->{parent.run("import",args("group",parent.selected,"source",str(source,"id"),"issue",str(issue,"identifier")));onClose();}).bounds(width-132,48+(i-page*count)*34,112,20).build());}addRenderableWidget(Button.builder(Component.literal("Next"),b->{if((page+1)*count<items.size()){page++;init();}else if(!str(data,"cursor").isEmpty()){cursor=str(data,"cursor");load();}}).bounds(20,height-48,66,20).build()).active=!busy;addRenderableWidget(Button.builder(Component.literal("Reload"),b->{cursor="";load();}).bounds(92,height-48,70,20).build()).active=!busy;addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width-88,height-48,68,20).build());if(!data.has("items") && notice.equals("Loading…") && !busy)load();}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,0xf20a101a);g.text(font,font.plainSubstrByWidth(title.getString(),width-40),20,16,0xff7cdbb6,false);int count=Math.max(1,(height-100)/34);var items=objects(data,"items");for(int i=page*count;i<Math.min(items.size(),(page+1)*count);i++)g.text(font,font.plainSubstrByWidth(str(items.get(i),"title"),width-166),20,48+(i-page*count)*34,0xffbdcce0,false);super.extractRenderState(g,x,y,d);g.text(font,font.plainSubstrByWidth(notice,width-40),20,height-22,0xffe5c07b,false);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}
    }
}
