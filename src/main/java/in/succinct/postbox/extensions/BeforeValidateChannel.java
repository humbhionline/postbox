package in.succinct.postbox.extensions;

import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import com.venky.swf.routing.Config;
import in.succinct.postbox.db.model.Channel;

public class BeforeValidateChannel extends BeforeModelValidateExtension<Channel> {
    static {
        registerExtension(new BeforeValidateChannel());
    }
    @Override
    public void beforeValidate(Channel model) {
        if (model.getExpiryMillis() == 0){
            model.setExpiryMillis(Config.instance().getLongProperty("postbox.message.expiry.defaultMillis",120*1000L));
        }
    }
}
