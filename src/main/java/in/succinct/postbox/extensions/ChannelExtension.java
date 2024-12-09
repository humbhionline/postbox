package in.succinct.postbox.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.ModelOperationExtension;
import com.venky.swf.exceptions.AccessDeniedException;
import in.succinct.postbox.db.model.Channel;
import in.succinct.postbox.db.model.User;

public class ChannelExtension extends ModelOperationExtension<Channel> {
    static {
        registerExtension(new ChannelExtension());
    }
    
    @Override
    protected void beforeDestroy(Channel instance) {
        super.beforeDestroy(instance);
        if (Database.getInstance().getCurrentUser() == null){
            return;
        }
        User user  = Database.getInstance().getCurrentUser().getRawRecord().getAsProxy(User.class);
        if (!ObjectUtil.equals(user.getProviderId(),instance.getName())){
            throw new AccessDeniedException();
        }
        
    }
    
    @Override
    protected void beforeValidate(Channel instance) {
        super.beforeValidate(instance);
        if (Database.getInstance().getCurrentUser() == null){
            return;
        }
        if (instance.getRawRecord().isNewRecord()){
            return;
        }
        User user  = Database.getInstance().getCurrentUser().getRawRecord().getAsProxy(User.class);
        if (!ObjectUtil.equals(user.getProviderId(),instance.getName())){
            throw new AccessDeniedException();
        }
    }
}
