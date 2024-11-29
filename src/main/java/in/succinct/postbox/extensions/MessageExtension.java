package in.succinct.postbox.extensions;

import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.ModelOperationExtension;
import in.succinct.postbox.db.model.Message;

public class MessageExtension extends ModelOperationExtension<Message> {
    static {
        registerExtension(new MessageExtension());
    }
    
    @Override
    protected void beforeValidate(Message instance) {
        super.beforeValidate(instance);
        if (instance.getRawRecord().isNewRecord()) {
            instance.setExpiresAt(System.currentTimeMillis() + instance.getChannel().getExpiryMillis());
            instance.setOwnerId(instance.getChannel().getCreatorUserId());
        }else if (instance.isDirty()){
            if (instance.getChannel().getCreatorUserId() != Database.getInstance().getCurrentUser().getId()){
                throw new RuntimeException("Cannot modify message in some one else's channel.");
            }
        }
        
    }
    
    @Override
    protected void beforeDestroy(Message instance) {
        super.beforeDestroy(instance);
        if (instance.getOwnerId() != Database.getInstance().getCurrentUser().getId()){
            throw new RuntimeException("Cannot delete message in some one else's channel.");
        }
    }
}
