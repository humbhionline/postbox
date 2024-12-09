package in.succinct.postbox.util;

import com.venky.cache.Cache;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.User;
import com.venky.swf.db.model.io.ModelIOFactory;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.routing.Config;
import com.venky.swf.routing.KeyCase;
import org.json.simple.JSONObject;

import java.util.Map;

public class OidProvider {
    public static Map<String, Map<String,String>>   getHumBolProviders() {
        Map<String, Map<String,String>> groupMap = new Cache<String, Map<String, String>>() {
            @Override
            protected Map<String, String> getValue(String groupKey) {
                return new Cache<String, String>() {
                    @Override
                    protected String getValue(String key) {
                        return Config.instance().getProperty(String.format("swf.%s.%s",groupKey,key));
                    }
                };
            }
        };
        for (String humBolKeys : Config.instance().getPropertyKeys("swf\\.HUMBOL.*\\..*")){
            String[] group = humBolKeys.split("\\.");
            String groupKey = group[1];
            groupMap.get(groupKey);
        }
        return groupMap;
        
    }
    
    public static  User initializeUser(JSONObject userObject) {
        User u = ModelIOFactory.getReader(User.class, JSONObject.class).read(userObject,true);
        u = Database.getTable(User.class).getRefreshed(u);
        u.save();
        return u;
    }
    
}
