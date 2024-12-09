package in.succinct.postbox.extensions;

import com.venky.core.collections.IgnoreCaseMap;
import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Registry;
import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.ModelOperationExtension;
import com.venky.swf.db.model.CryptoKey;
import com.venky.swf.integration.api.Call;
import com.venky.swf.path._IPath;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.beckn.tasks.BppTask;
import in.succinct.beckn.Request;
import in.succinct.json.JSONObjectWrapper;
import in.succinct.postbox.db.model.Message;
import in.succinct.postbox.db.model.User;
import in.succinct.postbox.util.NetworkManager;
import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MessageExtension extends ModelOperationExtension<Message> {
    static {
        registerExtension(new MessageExtension());
    }
    
    @Override
    protected void beforeValidate(Message instance) {
        super.beforeValidate(instance);
        if (ObjectUtil.isVoid(instance.getMessageId())){
            instance.setMessageId(UUID.randomUUID().toString());
        }
        if (instance.getRawRecord().isNewRecord()) {
            instance.setExpiresAt(System.currentTimeMillis() + instance.getChannel().getExpiryMillis());
            instance.setOwnerId(instance.getChannel().getCreatorUserId());
        }else if (instance.isDirty()){
            User user = Database.getInstance().getCurrentUser().getRawRecord().getAsProxy(User.class);
            if (!instance.getChannel().getName().
                    startsWith(user.getProviderId())){
                throw new RuntimeException("Cannot modify message in some one else's channel.");
            }
        }
        if (!instance.isArchived()) {
            Registry.instance().callExtensions(Message.class.getSimpleName() + ".archive.check", instance);
        }
    }
    
    /*
    private void sendOnStatus(Request request) {
        Map<String,Object> attributes = Database.getInstance().getCurrentTransaction().getAttributes();
        Map<String,Object> context = Database.getInstance().getContext();
        TaskManager.instance().executeAsync(new BppTask( request, new HashMap<>()) {
            @Override
            public Request generateCallBackRequest() {
                Database.getInstance().getCurrentTransaction().setAttributes(attributes);
                if (context != null){
                    context.remove(_IPath.class.getName());
                    Database.getInstance().setContext(context);
                }
                registerSignatureHeaders("Authorization");
                Request response = new Request(getRequest().toString());
                response.getContext().setAction("on_status");
                response.setObjectCreator(getRequest().getObjectCreator());
                response.setPayload(response.getInner().toString());
                
                return response;
            }
            
        }, false);
    }
    */
    
    @Override
    protected void beforeDestroy(Message instance) {
        super.beforeDestroy(instance);
        if (instance.getOwnerId() != Database.getInstance().getCurrentUser().getId()){
            throw new RuntimeException("Cannot delete message in some one else's channel.");
        }
    }
}
