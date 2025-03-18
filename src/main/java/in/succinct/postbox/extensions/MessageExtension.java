package in.succinct.postbox.extensions;

import com.venky.cache.Cache;
import com.venky.cache.UnboundedCache;
import com.venky.core.collections.IgnoreCaseMap;
import com.venky.core.io.StringReader;
import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Registry;
import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.ModelOperationExtension;
import com.venky.swf.db.model.CryptoKey;
import com.venky.swf.exceptions.AccessDeniedException;
import com.venky.swf.integration.api.Call;
import com.venky.swf.path._IPath;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.beckn.tasks.BppTask;
import com.venky.swf.plugins.collab.db.model.user.Phone;
import in.succinct.beckn.Agent;
import in.succinct.beckn.Contact;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.Fulfillment.FulfillmentStatus;
import in.succinct.beckn.Order;
import in.succinct.beckn.Order.Status;
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
            if (!ObjectUtil.isVoid(user.getProviderId()) && !instance.getChannel().getName().
                    startsWith(user.getProviderId())){
                throw new RuntimeException("Cannot modify message in some one else's channel.");
            }else if (!ObjectUtil.equals(user.getPhoneNumber(),instance.getDeliveryPartnerPhoneNumber())){
                throw new AccessDeniedException();
            }
            updateOrderStatus(instance);
        }
        if (!instance.isArchived()) {
            Registry.instance().callExtensions(Message.class.getSimpleName() + ".archive.check", instance);
        }
    }
    
    private void updateOrderStatus(Message instance) {
        Request request = new Request(StringUtil.read(instance.getPayLoad()));
        request.setObjectCreator(NetworkManager.getInstance().getNetworkAdaptor().getObjectCreator(request.getContext().getDomain()));
        
        Order order = request.getMessage().getOrder();
        if (order.getStatus() == null){
            order.setStatus(Status.Created);
        }
        Map<FulfillmentStatus,Bucket> fulfillmentStatusBucketMap = new UnboundedCache<>() {
            @Override
            protected Bucket getValue(FulfillmentStatus key) {
                return new Bucket();
            }
        };
        for (Fulfillment fulfillment : order.getFulfillments()) {
            FulfillmentStatus fulfillmentStatus = fulfillment.getFulfillmentStatus();
            if (fulfillmentStatus == null){
                fulfillmentStatus = FulfillmentStatus.Created;
                fulfillment.setFulfillmentStatus(fulfillmentStatus);
            }
            fulfillmentStatusBucketMap.get(fulfillmentStatus).increment();
            if (fulfillmentStatus.isOpen() && !order.getStatus().isOpen()){
                order.setStatus(Status.Created); //Reset.
            }
        }
        
        if (fulfillmentStatusBucketMap.isEmpty()){
            if (order.getStatus().ordinal() < Status.Awaiting_Acceptance.ordinal()) {
                order.setStatus(Status.Awaiting_Acceptance);
            }
        }else if (fulfillmentStatusBucketMap.get(FulfillmentStatus.Preparing).intValue() > 0){
            if (order.getStatus().ordinal() < Status.Accepted.ordinal()) {
                order.setStatus(Status.Accepted);
            }
        }else if (fulfillmentStatusBucketMap.get(FulfillmentStatus.Prepared).intValue() > 0){
            if (order.getStatus().ordinal() < Status.Prepared.ordinal()) {
                order.setStatus(Status.Prepared);
            }
        }else if (fulfillmentStatusBucketMap.get(FulfillmentStatus.In_Transit).intValue() > 0){
            if (order.getStatus().ordinal() < Status.In_Transit.ordinal()) {
                order.setStatus(Status.In_Transit);
            }
        }else if (fulfillmentStatusBucketMap.get(FulfillmentStatus.Completed).intValue() > 0){
            if (order.getStatus().ordinal() < Status.Completed.ordinal()) {
                order.setStatus(Status.Completed);
            }
        }else if (fulfillmentStatusBucketMap.get(FulfillmentStatus.Cancelled).intValue() > 0){
            if (order.getStatus().ordinal() < Status.Cancelled.ordinal()) {
                order.setStatus(Status.Cancelled);
            }
        }
        Agent  agent  = order.getFulfillment().getAgent();
        if (agent != null){
            Contact contact  = agent.getContact();
            if (contact != null){
                instance.setDeliveryPartnerPhoneNumber(Phone.sanitizePhoneNumber(contact.getPhone()));
            }
        }
        
        instance.setPayLoad(new StringReader(request.getInner().toString()));
        
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
