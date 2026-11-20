package io.github.stevexyh.cecstandby;
import java.util.*;

public final class ProfileTest {
    private static int checks;
    static Map<String,Object> fixture() {
        Map<String,Object>d=new HashMap<>();
        d.put("schemaVersion",1);d.put("id","synthetic");d.put("manufacturer","Example");d.put("model","TV");d.put("sdk",28);d.put("firmware","test.1");
        d.put("vendorId",4346);d.put("port",1);d.put("inputId","example.input/.Hdmi/HW1");d.put("player","example.player/.Main");
        d.put("action","example.player.OPEN");d.put("inputExtra","input");d.put("inputValue",23);d.put("launchers",Arrays.asList("example.home/.Main"));
        d.put("keepAdb",false);d.put("enabled",true);return d;
    }
    static Map<String,Object> tclFixture() {
        Map<String,Object>d=fixture();for(String k:Profile.HANDOFF_KEYS)d.remove(k);
        d.put("schemaVersion",2);d.put("events","tcl");d.put("inputSource","tv-input-session");d.put("standbyKey","SLEEP");d.put("wakeHandoff",false);return d;
    }
    static void rejectTcl(String key,Object value){Map<String,Object>d=tclFixture();d.put(key,value);try{new Profile(d);throw new AssertionError("Accepted "+key);}catch(IllegalArgumentException expected){checks++;}}
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Check "+checks);}
    static void reject(String key,Object value){Map<String,Object>d=fixture();d.put(key,value);try{new Profile(d);throw new AssertionError("Accepted "+key);}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[]args){
        Profile p=new Profile(fixture());check(p.matches("example","TV",28,"test.1"));check(!p.matches("Other","TV",28,"test.1"));check(!p.matches("Example","TV",29,"test.1"));check(!p.matches("Example","TV",28,"other"));
        check(p.isLauncher("example.home/example.home.Main"));check(!p.isLauncher("example.other/.Main"));
        reject("port",0);reject("port",true);reject("vendorId",16777215);reject("sdk",29);reject("action","bad;command");reject("player","x/$(touch)");reject("keepAdb","true");reject("enabled",1);reject("launchers",Arrays.asList("bad"));reject("unknown",1);
        check("xiaomi".equals(p.events)&&"setting".equals(p.inputSource)&&"POWER".equals(p.standbyKey)&&p.wakeHandoff);
        Profile tcl=new Profile(tclFixture());check(tcl.schemaVersion==2&&"tcl".equals(tcl.events)&&"SLEEP".equals(tcl.standbyKey)&&!tcl.wakeHandoff&&tcl.launchers.isEmpty()&&!tcl.isLauncher("example.home/.Main"));
        Map<String,Object> handoff=fixture();handoff.put("schemaVersion",2);handoff.put("events","xiaomi");handoff.put("inputSource","setting");handoff.put("standbyKey","POWER");handoff.put("wakeHandoff",true);
        check(new Profile(handoff).wakeHandoff);
        rejectTcl("events","lg");rejectTcl("inputSource","guess");rejectTcl("standbyKey","HOME");rejectTcl("standbyKey","POWER; reboot");rejectTcl("wakeHandoff","false");rejectTcl("action","example.player.OPEN");rejectTcl("schemaVersion",3);
        Map<String,Object> missing=tclFixture();missing.put("wakeHandoff",true);try{new Profile(missing);throw new AssertionError("Accepted handoff without its keys");}catch(IllegalArgumentException expected){checks++;}
        Map<String,Object> v1extra=fixture();v1extra.put("events","tcl");try{new Profile(v1extra);throw new AssertionError("Accepted schema 2 key in schema 1");}catch(IllegalArgumentException expected){checks++;}
        RecoveryPolicy recovery=new RecoveryPolicy();check(!recovery.shouldRestore(false,true,0));check(!recovery.shouldRestore(true,false,0));check(recovery.shouldRestore(true,true,0));check(!recovery.shouldRestore(true,true,59_999));check(recovery.shouldRestore(true,true,60_000));
        AppleDevices devices=new AppleDevices(1234);String device="mHdmiControlEnabled: true\nCEC: logical_address: 0x04 device_type: 4 vendor_id: 1234 physical_address: 0x1000 port_id: 2";
        devices.observeSnapshot(device,1000);check(devices.portFor(4,1000)==2);devices.observeSnapshot(device.replace("vendor_id: 1234","vendor_id: 4346"),1000);check(devices.portFor(4,1000)==-1);
        System.out.println("Profile/recovery: "+checks+" checks passed");
    }
}
