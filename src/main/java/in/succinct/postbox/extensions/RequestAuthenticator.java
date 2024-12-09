package in.succinct.postbox.extensions;

import com.venky.core.util.ObjectHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.controller.OidController;
import com.venky.swf.controller.OidController.OIDProvider;
import com.venky.swf.db.JdbcTypeHelper.TypeConverter;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.User;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.path.Path;
import com.venky.swf.routing.Config;
import in.succinct.postbox.util.OidProvider;
import org.json.simple.JSONObject;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class RequestAuthenticator implements Extension {
    static{
        Registry.instance().registerExtension(Path.REQUEST_AUTHENTICATOR,new RequestAuthenticator());
    }
    
    @Override
    public void invoke(Object... context) {
        Path path = (Path)context[0];
        ObjectHolder<User> userObjectHolder = (ObjectHolder<User>)context[1];

        String apiKey = getHeader(path,"ApiKey");
        String lat = getHeader( path,"Lat");
        String lng = getHeader( path, "Lng");


        if (!ObjectUtil.isVoid(apiKey)){
            User user = path.getUser("api_key",apiKey);
            Map<String, Map<String,String>> oidProviders =  OidProvider.getHumBolProviders();
            Map<String,String> oidProps = oidProviders.get("HUMBOL");
            if (user == null && oidProps != null && !oidProviders.isEmpty()) {
                String resourceUrl = oidProps.get("resource.url");
                Call<JSONObject> call = new Call<JSONObject>().url(resourceUrl).headers(new HashMap<>(){{
                    put("content-type", MimeType.APPLICATION_JSON.toString());
                    put("ApiKey",apiKey);
                    if (lat != null && lng != null) {
                        put("X-Lat", lat);
                        put("X-Lng", lng);
                    }
                    
                }}).method(HttpMethod.GET);
                
                JSONObject response = call.getResponseAsJson();
                if (response != null) {
                    JSONObject userInfo = new HumBolUserInfoExtractor().extractUserInfo(response);
                    user = OidProvider.initializeUser(userInfo);
                    userObjectHolder.set(user);
                }else {
                    path.addErrorMessage(call.getError());
                }
            }else if (userObjectHolder.get() != null){
                User alreadySet = userObjectHolder.get();
                if (alreadySet.getId() != user.getId()){
                    userObjectHolder.set(null);
                    path.addErrorMessage("User not identifiable");
                }
            }else {
                userObjectHolder.set(user);
            }
        }
    }

    public String getHeader(Path path, String key){
        return path.getHeader(key);
    }
}
