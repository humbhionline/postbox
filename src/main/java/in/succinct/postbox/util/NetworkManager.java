package in.succinct.postbox.util;

import com.venky.swf.db.model.CryptoKey;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.beckn.Request;
import in.succinct.beckn.Subscriber;
import in.succinct.onet.core.adaptor.NetworkAdaptor;
import in.succinct.onet.core.adaptor.NetworkAdaptorFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class NetworkManager {
    private static volatile NetworkManager sSoleInstance;
    
    //private constructor.
    private NetworkManager() {
        //Prevent form the reflection api.
        if (sSoleInstance != null) {
            throw new RuntimeException("Use getInstance() method to get the single instance of this class.");
        }
    }
    
    public static NetworkManager getInstance() {
        if (sSoleInstance == null) { //if there is no instance available... create new one
            synchronized (NetworkManager.class) {
                if (sSoleInstance == null) sSoleInstance = new NetworkManager();
            }
        }
        
        return sSoleInstance;
    }
    
    //Make singleton from serialize and deserialize operation.
    protected NetworkManager readResolve() {
        return getInstance();
    }
    
    
    public void subscribe(){
        TaskManager.instance().executeAsync((Task) () ->
                getNetworkAdaptor().
                        subscribe(getSubscriber()),false);
    }
    public NetworkAdaptor getNetworkAdaptor(){
        return NetworkAdaptorFactory.getInstance().getAdaptor();
    }
    public Subscriber getSubscriber(){
        String selfKeyId;
        CryptoKey latestSigning = getLatestKey(Request.SIGNATURE_ALGO);
        if (latestSigning != null){
            selfKeyId = latestSigning.getAlias();
        }else {
            selfKeyId = "%s.k1".formatted(Config.instance().getHostName());
        }
        
        return new Subscriber(){{
            setSubscriberId(Config.instance().getHostName());
            setPubKeyId(selfKeyId);
            setNonce(Base64.getEncoder().encodeToString(String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8)));
            setSubscriberUrl(Config.instance().getServerBaseUrl() + "/bpp");
            setType(Subscriber.SUBSCRIBER_TYPE_BPP);
            NetworkAdaptorFactory.getInstance().getAdaptor().getSubscriptionJson(this);
        }};
    }
    public CryptoKey getLatestKey(String purpose){
        List<CryptoKey> latest = new Select().from(CryptoKey.class).
                where(new Expression(ModelReflector.instance(CryptoKey.class).getPool(), "PURPOSE",
                        Operator.EQ, purpose)).
                orderBy("ID DESC").execute(1);
        if (!latest.isEmpty()) {
            return latest.get(0);
        }
        return null;
    }
    
    
}
