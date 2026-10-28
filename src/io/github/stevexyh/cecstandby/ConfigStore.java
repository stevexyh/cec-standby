package io.github.stevexyh.cecstandby;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class ConfigStore {
    static Profile parse(String json) throws Exception {
        if(json.length()>16384)throw new IllegalArgumentException("Profile too large");
        JSONObject obj=new JSONObject(json);Map<String,Object> map=new HashMap<>();
        Iterator<String> keys=obj.keys();
        while(keys.hasNext()) {
            String key=keys.next();Object value=obj.get(key);
            if(value instanceof JSONArray) {
                List<Object> list=new ArrayList<>();JSONArray a=(JSONArray)value;
                for(int i=0;i<a.length();i++)list.add(a.get(i));value=list;
            }
            map.put(key,value);
        }
        return new Profile(map);
    }
    static String read(Context context) throws Exception {
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"profile.json"));
        if(!file.getBaseFile().exists())return null;
        String json=new String(file.readFully(),StandardCharsets.UTF_8);parse(json);return json;
    }
    static synchronized void save(Context context,String json) throws Exception {
        parse(json);
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"profile.json"));
        FileOutputStream out=file.startWrite();
        try {out.write(json.getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(Exception e){file.failWrite(out);throw e;}
    }
}
