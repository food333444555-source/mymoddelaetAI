import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.regex.*;

public class Console {

    static final String RESET   = "\033[0m";
    static final String RED     = "\033[91m";
    static final String YELLOW  = "\033[93m";
    static final String GREEN   = "\033[92m";
    static final String CYAN    = "\033[96m";
    static final String WHITE   = "\033[97m";
    static final String GRAY    = "\033[37m";
    static final String ORANGE  = "\033[33m";
    static final String MAGENTA = "\033[95m";
    static final String PINK    = "\033[35m";
    static final String BLUE    = "\033[94m";
    static final String AMBER   = "\033[38;5;208m";
    static final String PURPLE  = "\033[38;5;135m";
    static final String GOLD    = "\033[38;5;220m";

    static final String ORANGE_WARN = "\033[38;5;214m";
    static final String RED_ORANGE  = "\033[38;5;202m";
    static final String BRIGHT_RED  = "\033[38;5;196m";
    static final String DEEP_RED    = "\033[38;5;160m";
    static final String CRIMSON     = "\033[38;5;197m";
    static final String VIOLET      = "\033[38;5;165m";

    static class MobData {
        final int id; final String type; final int x, y, z;
        final String name; final boolean hurt; final String raw;
        MobData(int id, String type, int x, int y, int z, String name, boolean hurt, String raw) {
            this.id=id; this.type=type; this.x=x; this.y=y; this.z=z;
            this.name=name; this.hurt=hurt; this.raw=raw;
        }
        boolean isPlayer(){return type.equals("player");}
    }

    static final Pattern MOB_PATTERN = Pattern.compile("^\\[([\\w.]+)\\]\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s+id=(\\d+)(.*)");
    static final Pattern NAME_PAT = Pattern.compile("\\bname=(\\S+)");

    static final Map<Integer,String> tracker=new LinkedHashMap<>();
    static final Map<Integer,String> seenSpecial=new LinkedHashMap<>();
    static final Set<Integer> prevFrameIds=new HashSet<>();
    static final Set<Integer> seenHostileIds=new HashSet<>();
    static final Set<Integer> reloadedChunkIds=new HashSet<>();
    static final Set<Integer> trackedSessionIds=new HashSet<>();
    static final Set<Integer> returnedIds=new HashSet<>();
    static final Map<Integer,int[]> lastMobPos=new HashMap<>();
    static final Map<Integer,int[]> mobPosWhenLeft=new HashMap<>();
    static final Map<Integer,Double> maxDistWhileGone=new HashMap<>();
    static volatile int[] curPlayerPos=null;
    static long lastRenderTime=0;
    static final long RENDER_INTERVAL_MS=150;
    static final double MIN_DIST_FOR_RETURNED=83.0;
    static long maxId=0;
    static final long SUSPICIOUS_GAP=100_000L;
    static Set<Integer> alertedIds=new HashSet<>();

    // === CENTER DETECTION (ALERT OR RETURNED HOSTILES) ===
    static final Map<Integer, MobData> hostileMarkers = new HashMap<>();
    static int centerCX = Integer.MIN_VALUE;
    static int centerCZ = Integer.MIN_VALUE;
    static long centerLastUpdate = 0;
    static final long CENTER_TIMEOUT_MS = 60_000;
    static final int CENTER_CLEAR_DISTANCE_CHUNKS = 8;
    static final int MIN_HOSTILE_MARKERS = 2;
    static final int MAX_SPREAD_BLOCKS = 128;
    // === END CENTER ===

    static MobData parseMobData(String line){
        Matcher m=MOB_PATTERN.matcher(line.trim());
        if(!m.find())return null;
        String full=m.group(1); int dot=full.lastIndexOf('.');
        String type=dot>=0?full.substring(dot+1):full;
        int x=Integer.parseInt(m.group(2)),y=Integer.parseInt(m.group(3)),z=Integer.parseInt(m.group(4));
        int id=Integer.parseInt(m.group(5)); boolean hurt=line.contains("[HURT]");
        String name=null; Matcher nm=NAME_PAT.matcher(line);
        if(nm.find())name=nm.group(1);
        return new MobData(id,type,x,y,z,name,hurt,line);
    }

    static double dist(int[]a,int[]b){
        if(a==null||b==null)return 0;
        double dx=a[0]-b[0],dy=a[1]-b[1],dz=a[2]-b[2];
        return Math.sqrt(dx*dx+dy*dy+dz*dz);
    }

    static boolean isHostile(String t){
        switch(t){
            case "zombie":case"skeleton":case"creeper":case"spider":case"cave_spider":
            case "enderman":case"endermite":case"silverfish":case"blaze":case"ghast":
            case "magma_cube":case"witch":case"wither_skeleton":case"phantom":
            case "drowned":case"husk":case"stray":case"pillager":case"vindicator":
            case "evoker":case"vex":case"ravager":case"guardian":case"elder_guardian":
            case "shulker":case"hoglin":case"zoglin":case"piglin_brute":case"bogged":
            case "breeze":case"charged_creeper":case"slime":case"zombie_villager":
            case "zombified_piglin":case"piglin":return true;
        }return false;
    }

    static boolean isSpecialMob(String t){
        switch(t){case"villager":case"wandering_trader":case"blaze":case"iron_golem":
        case "breeze":case"charged_creeper":return true;}return false;
    }

    static boolean isSessionMob(String t){
        switch(t){case"zombie":case"skeleton":case"creeper":case"spider":case"cave_spider":
        case "witch":case"slime":case"phantom":case"stray":case"zombie_villager":
        case "silverfish":case"endermite":return true;}return false;
    }

    static String getPercentColor(double p){
        if(p>=50)return GRAY; if(p>=40)return AMBER; if(p>=30)return ORANGE_WARN;
        if(p>=20)return RED_ORANGE; if(p>=10)return CRIMSON; if(p>=5)return BRIGHT_RED;
        if(p>=3)return DEEP_RED; return RED;
    }

    static String buildMobDisplay(MobData d,boolean alert,boolean returned,boolean chunkLoaded){
        boolean isPlayer=d.isPlayer(),isSpecial=isSpecialMob(d.type),isHostileMob=isHostile(d.type);
        boolean onlyHurt=d.hurt&&!alert&&!returned&&!chunkLoaded&&!isSpecial;
        String prefix; if(isPlayer&&d.name!=null)prefix="["+d.name+"] "+d.x+" "+d.y+" "+d.z;
        else prefix="["+d.type+"] "+d.x+" "+d.y+" "+d.z;

        String baseColor;
        if(isPlayer)baseColor=BLUE; else if(isSpecial){
            switch(d.type){case"charged_creeper":baseColor=RED;break;case"villager":case"wandering_trader":baseColor=GREEN;break;
            case"blaze":baseColor=ORANGE;break;case"iron_golem":baseColor=WHITE;break;case"breeze":baseColor=CYAN;break;
            case"evoker":baseColor=MAGENTA;break;case"vex":baseColor=PINK;break;default:baseColor=GRAY;}
        }else if(onlyHurt)baseColor=RED;else if(chunkLoaded)baseColor=YELLOW;else if(returned)baseColor=PURPLE;
        else if(alert)baseColor=GRAY;else baseColor=GRAY;

        String idColor;
        if(onlyHurt)idColor=RED;else if(alert&&isHostileMob)idColor=GOLD;else if(chunkLoaded)idColor=YELLOW;
        else if(returned)idColor=PURPLE;else if(isPlayer)idColor=BLUE;
        else if(!isHostileMob&&maxId>0){if(d.id<10_000)idColor=VIOLET;else idColor=CYAN;}else idColor=GRAY;

        String percentStr="";
        if(!isHostileMob&&!isPlayer&&maxId>0){double p=(d.id*100.0)/maxId;String pc=getPercentColor(p);percentStr=String.format(" (%s%.2f%%%s)",pc,p,RESET);}

        StringBuilder sb=new StringBuilder();sb.append(baseColor).append(prefix).append(RESET);sb.append(idColor).append(" ID-").append(d.id).append(RESET);sb.append(percentStr);
        if(alert){String ac=isHostileMob?GOLD:CYAN;sb.append(ac).append(" [ALERT]").append(RESET);}
        if(d.hurt)sb.append(RED).append(" [HURT]").append(RESET);
        if(returned)sb.append(PURPLE).append(" [RETURNED]").append(RESET);
        if(chunkLoaded)sb.append(YELLOW).append(" [CHUNK LOADED]").append(RESET);
        return sb.toString();
    }

    // === CENTER LOGIC ===
    static void clearCenter(){
        centerCX=Integer.MIN_VALUE;
        centerCZ=Integer.MIN_VALUE;
        centerLastUpdate=0;
    }

    static void collectHostileMarkers(List<MobData> buffer){
        // From current frame
        for(MobData d:buffer){
            if((alertedIds.contains(d.id) || returnedIds.contains(d.id)) && isHostile(d.type)){
                hostileMarkers.put(d.id, d);
            }
        }
        // From session history (seenSpecial)
        for(Map.Entry<Integer,String> entry : seenSpecial.entrySet()){
            int id = entry.getKey();
            if(alertedIds.contains(id) || returnedIds.contains(id)){
                MobData d = parseMobData(entry.getValue());
                if(d != null && isHostile(d.type)){
                    hostileMarkers.put(id, d);
                }
            }
        }
    }

    static void calculateCenter(long nowMs){
        if(hostileMarkers.size() < MIN_HOSTILE_MARKERS){
            clearCenter();
            return;
        }

        List<Integer> xs = new ArrayList<>();
        List<Integer> zs = new ArrayList<>();

        for(MobData d:hostileMarkers.values()){
            xs.add(d.x);
            zs.add(d.z);
        }

        Collections.sort(xs);
        Collections.sort(zs);

        int medianX = xs.get(xs.size()/2);
        int medianZ = zs.get(zs.size()/2);

        int minX = xs.get(0), maxX = xs.get(xs.size()-1);
        int minZ = zs.get(0), maxZ = zs.get(zs.size()-1);

        int spread = Math.max(maxX-minX, maxZ-minZ);
        if(spread > MAX_SPREAD_BLOCKS){
            clearCenter();
            return;
        }

        centerCX = medianX >> 4;
        centerCZ = medianZ >> 4;
        centerLastUpdate = nowMs;
    }

    static void checkAutoClear(long nowMs){
        if(centerCX == Integer.MIN_VALUE) return;

        if(nowMs - centerLastUpdate > CENTER_TIMEOUT_MS){
            clearCenter();
            return;
        }

        int[] pp = curPlayerPos;
        if(pp != null){
            int distChunks = Math.max(Math.abs((pp[0]>>4)-centerCX), Math.abs((pp[2]>>4)-centerCZ));
            if(distChunks >= CENTER_CLEAR_DISTANCE_CHUNKS){
                clearCenter();
            }
        }
    }

    static void appendCenter(StringBuilder frame){
        if(centerCX == Integer.MIN_VALUE) return;

        int bx = centerCX * 16;
        int bz = centerCZ * 16;
        int centerX = bx + 8;
        int centerZ = bz + 8;

        double distance = -1;
        int[] pp = curPlayerPos;
        if(pp != null){
            double dx = pp[0] - centerX;
            double dz = pp[2] - centerZ;
            distance = Math.sqrt(dx*dx + dz*dz);
        }

        frame.append('\n').append(GOLD).append("  [~ CENTER ~]").append(RESET).append('\n');
        frame.append("    ").append(GOLD)
             .append(String.format("X: %d  Z: %d", centerX, centerZ))
             .append(RESET).append('\n');

        if(distance >= 0){
            frame.append("    ").append(GRAY)
                 .append(String.format("Distance: %.0f blocks | Chunk [%d, %d] | Markers: %d",
                         distance, centerCX, centerCZ, hostileMarkers.size()))
                 .append(RESET).append('\n');
        }else{
            frame.append("    ").append(GRAY)
                 .append(String.format("Chunk [%d, %d] | Markers: %d", centerCX, centerCZ, hostileMarkers.size()))
                 .append(RESET).append('\n');
        }

        frame.append("    ").append(GRAY)
             .append(String.format("Area: [%d %d] to [%d %d]",
                     bx, bz, bx+15, bz+15))
             .append(RESET).append('\n');
    }
    // === END CENTER ===

    public static void main(String[] args) throws Exception {
        PrintStream utf8out = new PrintStream(System.out, true, "UTF-8"); System.setOut(utf8out);
        System.out.println("Connecting to Minecraft...");
        try(Socket s=new Socket("localhost",25560);
            BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream(),"UTF-8"));
            PrintWriter out=new PrintWriter(new OutputStreamWriter(s.getOutputStream(),"UTF-8"),true)){
            System.out.println("Connected!");
            System.out.println("=== HOTKEYS: [5]=session [7]=alerts+maxID [9]=clear center [0]=full ===");

            Thread reader=new Thread(){
                public void run(){
                    try{
                        String line; List<MobData> buffer=new ArrayList<>(512); boolean collecting=false;
                        int cntMobs=0,cntPlayers=0; Set<Integer> currentIds=new HashSet<>(512);
                        Set<Integer> tempSuspicious=new HashSet<>(64);
                        while((line=in.readLine())!=null){
                            if(line.equals("--- UPDATE ---")){buffer.clear();collecting=true;cntMobs=0;cntPlayers=0;}
                            else if(line.equals("--- END ---")){
                                collecting=false; long nowMs=System.currentTimeMillis(); currentIds.clear(); tempSuspicious.clear();
                                for(MobData d:buffer){if(d.id>maxId)maxId=d.id; currentIds.add(d.id);
                                    if(!d.isPlayer()&&!d.type.equals("enderman"))if(maxId>SUSPICIOUS_GAP&&(maxId-d.id)>SUSPICIOUS_GAP)tempSuspicious.add(d.id);}
                                for(int id:tempSuspicious)alertedIds.add(id);
                                for(MobData d:buffer)if(d.isPlayer()){curPlayerPos=new int[]{d.x,d.y,d.z};break;}
                                for(int prevId:prevFrameIds)if(!currentIds.contains(prevId)&&lastMobPos.containsKey(prevId)){mobPosWhenLeft.put(prevId,lastMobPos.get(prevId).clone());maxDistWhileGone.put(prevId,0.0);}
                                if(curPlayerPos!=null)for(Iterator<Map.Entry<Integer,Double>>it=maxDistWhileGone.entrySet().iterator();it.hasNext();){Map.Entry<Integer,Double>e=it.next();if(!currentIds.contains(e.getKey())){int[]mPos=mobPosWhenLeft.get(e.getKey());if(mPos!=null){double di=dist(curPlayerPos,mPos);if(di>e.getValue())e.setValue(di);}}}
                                for(MobData d:buffer)lastMobPos.put(d.id,new int[]{d.x,d.y,d.z});
                                for(MobData d:buffer){
                                    if(d.isPlayer())cntPlayers++;else cntMobs++;
                                    if(isHostile(d.type)){if(seenHostileIds.contains(d.id)&&!prevFrameIds.contains(d.id))reloadedChunkIds.add(d.id);seenHostileIds.add(d.id);}
                                    String raw=d.raw; if(d.hurt)seenSpecial.put(d.id,raw);
                                    if(isSpecialMob(d.type)){if(seenSpecial.containsKey(d.id)&&!prevFrameIds.contains(d.id))returnedIds.add(d.id);seenSpecial.put(d.id,raw);}
                                    if(isSessionMob(d.type)){if(trackedSessionIds.contains(d.id)&&!prevFrameIds.contains(d.id)){double maxDist=maxDistWhileGone.getOrDefault(d.id,0.0);if(maxDist>=MIN_DIST_FOR_RETURNED){returnedIds.add(d.id);seenSpecial.put(d.id,raw);}maxDistWhileGone.remove(d.id);mobPosWhenLeft.remove(d.id);}trackedSessionIds.add(d.id);}
                                    if(alertedIds.contains(d.id)&&isHostile(d.type)&&!seenSpecial.containsKey(d.id))seenSpecial.put(d.id,raw);
                                }
                                prevFrameIds.clear();prevFrameIds.addAll(currentIds);

                                // === CENTER UPDATE ===
                                collectHostileMarkers(buffer);
                                calculateCenter(nowMs);
                                checkAutoClear(nowMs);
                                // === END CENTER ===

                                if(nowMs-lastRenderTime<RENDER_INTERVAL_MS)continue; lastRenderTime=nowMs;
                                StringBuilder frame=new StringBuilder(8192); frame.append("\033[H\033[2J");
                                for(MobData d:buffer){boolean alert=alertedIds.contains(d.id),ret=returnedIds.contains(d.id),chunk=reloadedChunkIds.contains(d.id);frame.append(buildMobDisplay(d,alert,ret,chunk)).append('\n');}
                                frame.append("  ").append(BLUE).append("Mobs: ").append(cntMobs).append(RESET).append("  ").append(BLUE).append("Players: ").append(cntPlayers).append(RESET).append("  ").append(CYAN).append("MAX ID: ").append(maxId).append(RESET).append("  ").append(CYAN).append("ALERTS: ").append(alertedIds.size()).append(RESET).append('\n');
                                if(!seenSpecial.isEmpty()){frame.append(YELLOW).append("  [~ SESSION ~]").append(RESET).append('\n');for(Map.Entry<Integer,String>entry:seenSpecial.entrySet()){MobData sd=parseMobData(entry.getValue());if(sd!=null){boolean alert=alertedIds.contains(entry.getKey()),ret=returnedIds.contains(entry.getKey()),chunk=reloadedChunkIds.contains(entry.getKey());frame.append("    ").append(buildMobDisplay(sd,alert,ret,chunk)).append('\n');}}}

                                appendCenter(frame);

                                System.out.print(frame); System.out.flush();
                            }else if(collecting){MobData parsed=parseMobData(line);if(parsed!=null)buffer.add(parsed);}
                        }
                    }catch(Exception ignored){}
                }
            }; reader.setDaemon(true); reader.start();

            while (true) {
                int key = System.in.read();
                if (key == '5') {
                    seenSpecial.clear(); returnedIds.clear();
                } else if (key == '7') {
                    maxId = 0; alertedIds.clear();
                } else if (key == '9') {
                    hostileMarkers.clear(); clearCenter();
                } else if (key == '0') {
                    seenSpecial.clear(); returnedIds.clear(); tracker.clear();
                    prevFrameIds.clear(); seenHostileIds.clear(); reloadedChunkIds.clear();
                    trackedSessionIds.clear(); lastMobPos.clear(); mobPosWhenLeft.clear();
                    maxDistWhileGone.clear(); maxId = 0; alertedIds.clear();
                    hostileMarkers.clear(); clearCenter();
                    out.println("cleartracker");
                    System.out.print("\033[H\033[2J"); System.out.flush();
                }
            }
        }catch(Exception e){System.out.println("Error: "+e.getMessage());}
    }
}