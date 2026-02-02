package in.succinct.postbox.extensions;

import com.venky.cache.UnboundedCache;
import com.venky.core.io.StringReader;
import com.venky.core.math.DoubleUtils;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Registry;
import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.ModelOperationExtension;
import com.venky.swf.plugins.collab.db.model.user.Phone;
import in.succinct.beckn.Address;
import in.succinct.beckn.Agent;
import in.succinct.beckn.Billing;
import in.succinct.beckn.City;
import in.succinct.beckn.Contact;
import in.succinct.beckn.Country;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.Fulfillment.FulfillmentStatus;
import in.succinct.beckn.FulfillmentStops;
import in.succinct.beckn.Invoice;
import in.succinct.beckn.Item;
import in.succinct.beckn.Location;
import in.succinct.beckn.Order;
import in.succinct.beckn.Order.Status;
import in.succinct.beckn.Payment;
import in.succinct.beckn.Payment.PaymentStatus;
import in.succinct.beckn.Payments;
import in.succinct.beckn.Request;
import in.succinct.postbox.db.model.Message;
import in.succinct.postbox.db.model.User;
import in.succinct.postbox.util.NetworkManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MessageExtension extends ModelOperationExtension<Message> {
    static {
        registerExtension(new MessageExtension());
    }
    
    @Override
    protected void beforeValidate(Message instance) {
        super.beforeValidate(instance);
        if (ObjectUtil.isVoid(instance.getMessageId())) {
            instance.setMessageId(UUID.randomUUID().toString());
        }
        if (instance.getRawRecord().isNewRecord()) {
            instance.setExpiresAt(System.currentTimeMillis() + instance.getChannel().getExpiryMillis());
            updateOrderStatus(instance);
        } else if (instance.isDirty()) {
            com.venky.swf.db.model.User currentUser = Database.getInstance().getCurrentUser();
            if (currentUser != null) {
                User user = currentUser.getRawRecord().getAsProxy(User.class);
                boolean isUserDeliveryPartner = (ObjectUtil.equals(user.getPhoneNumber(), instance.getDeliveryPartnerPhoneNumber()));
                boolean isUserSeller = (!ObjectUtil.isVoid(user.getProviderId()) && instance.getChannel().getName().startsWith(user.getProviderId()));
                if (!isUserDeliveryPartner && !isUserSeller) {
                    throw new RuntimeException("Cannot modify message in some one else's channel.");
                }
            }
            updateOrderStatus(instance); // absorb BAP updates
        }
        if (!instance.isArchived()) {
            Registry.instance().callExtensions(Message.class.getSimpleName() + ".archive.check", instance);
        }
    }
    
    private void updateOrderStatus(Message instance) {
        Request request = new Request(StringUtil.read(instance.getPayLoad()));
        request.setObjectCreator(NetworkManager.getInstance().getNetworkAdaptor().getObjectCreator(request.getContext().getDomain()));
        
        Order order = request.getMessage().getOrder();
        if (order.getStatus() == null) {
            order.setStatus(Status.Created);
        }
        Map<FulfillmentStatus, Bucket> fulfillmentStatusBucketMap = new UnboundedCache<>() {
            @Override
            protected Bucket getValue(FulfillmentStatus key) {
                return new Bucket();
            }
        };
        for (Fulfillment fulfillment : order.getFulfillments()) {
            FulfillmentStatus fulfillmentStatus = fulfillment.getFulfillmentStatus();
            if (fulfillmentStatus == null) {
                fulfillmentStatus = FulfillmentStatus.Created;
                fulfillment.setFulfillmentStatus(fulfillmentStatus);
            }
            fulfillmentStatusBucketMap.get(fulfillmentStatus).increment();
            if (fulfillmentStatus.isOpen() && !order.getStatus().isOpen()) {
                order.setStatus(Status.Created); //Reset.
            }
        }
        
        if (fulfillmentStatusBucketMap.isEmpty()) {
            if (order.getStatus().ordinal() < Status.Awaiting_Acceptance.ordinal()) {
                order.setStatus(Status.Awaiting_Acceptance);
            }
        } else if (fulfillmentStatusBucketMap.get(FulfillmentStatus.Preparing).intValue() > 0) {
            if (order.getStatus().ordinal() < Status.Accepted.ordinal()) {
                order.setStatus(Status.Accepted);
            }
        } else if (fulfillmentStatusBucketMap.get(FulfillmentStatus.Prepared).intValue() > 0) {
            if (order.getStatus().ordinal() < Status.Prepared.ordinal()) {
                order.setStatus(Status.Prepared);
            }
        } else if (fulfillmentStatusBucketMap.get(FulfillmentStatus.In_Transit).intValue() > 0) {
            if (order.getStatus().ordinal() < Status.In_Transit.ordinal()) {
                order.setStatus(Status.In_Transit);
            }
        } else if (fulfillmentStatusBucketMap.get(FulfillmentStatus.Completed).intValue() > 0) {
            if (order.getStatus().ordinal() < Status.Completed.ordinal()) {
                order.setStatus(Status.Completed);
            }
        } else if (fulfillmentStatusBucketMap.get(FulfillmentStatus.Cancelled).intValue() > 0) {
            if (order.getStatus().ordinal() < Status.Cancelled.ordinal()) {
                order.setStatus(Status.Cancelled);
            }
        }
        Agent agent = order.getFulfillment().getAgent();
        if (agent != null) {
            Contact contact = agent.getContact();
            if (contact != null) {
                instance.setDeliveryPartnerPhoneNumber(Phone.sanitizePhoneNumber(contact.getPhone()));
            }
        }
        Fulfillment fulfillment = order.getFulfillment();
        Bucket price = new Bucket();
        for (Item item : order.getItems()) {
            price.increment(item.getPrice().getValue()*item.getItemQuantity().getSelected().getCount());
        }
        Payments terms = order.getPayments();
        if (terms.size() == 1){
            Payment term = terms.get(0);
            term.setFulfillmentId(fulfillment.getId());
            if (term.getInvoiceEvent() == null){
                term.setInvoiceEvent(FulfillmentStatus.Completed);
            }
            if (!DoubleUtils.equals(term.getParams().getAmount() ,price.doubleValue())){
                term.getParams().setAmount(price.doubleValue());
            }
        }
        
        List<Invoice> unpaidInvoices = new ArrayList<>();
        Bucket invoicedAmount = new Bucket();
        Bucket unpaidAmount = new Bucket();
        
        for (Invoice invoice : order.getInvoices()){
            invoicedAmount.increment(invoice.getAmount());
            if (invoice.getPaymentTransactions().isEmpty()){
                unpaidInvoices.add(invoice);
            }
            unpaidAmount.increment(invoice.getUnpaidAmount().doubleValue());
        }
        
        if (DoubleUtils.compareTo(invoicedAmount.doubleValue(),price.doubleValue()) != 0){
            // all invoices created.
            Bucket tbi = new Bucket(price.doubleValue() - invoicedAmount.doubleValue());
            
            for (Invoice invoice : unpaidInvoices){
                double adj = Math.max(-invoice.getAmount(),tbi.doubleValue());
                invoice.setAmount(invoice.getAmount()+adj);
                tbi.decrement(adj);
                invoicedAmount.increment(adj);
                unpaidAmount.increment(adj);
            }
            if (DoubleUtils.compareTo(tbi.doubleValue(),0.0D)>0){
                int invoiceCount = order.getInvoices().size() ;
                order.getInvoices().add(new Invoice(){{
                    setId(order.getId() + "-" + (invoiceCount+1));
                    setDate(new Date());
                    setFulfillmentId(fulfillment.getId());
                    setCurrency(order.getItems().get(0).getPrice().getCurrency());
                    setAmount(tbi.doubleValue());
                }});
                invoicedAmount.increment(tbi.doubleValue());
                unpaidAmount.increment(tbi.doubleValue());
                tbi.decrement(tbi.doubleValue());
            }
        }
        if (DoubleUtils.compareTo(unpaidAmount.doubleValue() ,0.0) == 0){
            for (Payment payment : order.getPayments()) {
                if (!payment.getStatus().isPaid()) {
                    payment.setStatus(PaymentStatus.PAID);
                }
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
        User user = Database.getInstance().getCurrentUser().getRawRecord().getAsProxy(User.class);
        
        if (!instance.getChannel().getName().startsWith(user.getProviderId())) {
            throw new RuntimeException("Cannot delete message in some one else's channel.");
        }
    }
    
    @Override
    protected void afterSave(Message instance) {
        super.afterSave(instance);
    }
    
    
}
