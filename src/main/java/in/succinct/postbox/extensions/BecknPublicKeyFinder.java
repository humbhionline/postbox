package in.succinct.postbox.extensions;

import com.venky.core.util.ObjectHolder;
import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.routing.Config;
import in.succinct.beckn.Subscriber;
import in.succinct.onet.core.adaptor.NetworkAdaptorFactory;

import java.util.List;

public class BecknPublicKeyFinder implements Extension {
    static {
        Registry.instance().registerExtension("beckn.public.key.get",new BecknPublicKeyFinder());
    }
    
    public String getNetworkId(){
        return Config.instance().getProperty("in.succinct.onet.name","beckn_open");
    }
    @Override
    public void invoke(Object... context) {
        String subscriber_id = (String)context[0];
        String uniqueKeyId = (String)context[1];
        ObjectHolder<String> publicKeyHolder = (ObjectHolder<String>) context[2];
        if (publicKeyHolder.get() != null){
            return;
        }

        Subscriber subscriber = new Subscriber();
        subscriber.setSubscriberId(subscriber_id);
        subscriber.setUniqueKeyId(uniqueKeyId);
        //subscriber.setCountry("IND");


        List<Subscriber> responses = NetworkAdaptorFactory.getInstance().getAdaptor(getNetworkId()).
                lookup(subscriber,true);

        if (!responses.isEmpty()){
            publicKeyHolder.set(responses.get(0).getSigningPublicKey());
        }else {
            Config.instance().getLogger(getClass().getName()).info("Lookup failed for : " + subscriber);
        }

    }

}
