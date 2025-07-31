package in.succinct.postbox.db.model;

import com.venky.core.io.StringReader;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.plugins.audit.db.model.ModelAudit;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.FulfillmentStop;
import in.succinct.beckn.FulfillmentStops;
import in.succinct.beckn.Invoice;
import in.succinct.beckn.Order;
import in.succinct.beckn.Payment;
import in.succinct.beckn.Payment.PaymentStatus;
import in.succinct.beckn.Payment.PaymentTransaction;
import in.succinct.beckn.Payment.PaymentTransaction.PaymentTransactions;
import in.succinct.beckn.PaymentMethod;
import in.succinct.beckn.Provider.Directories;
import in.succinct.beckn.Request;
import in.succinct.beckn.SellerException;
import in.succinct.beckn.Subscriber;
import in.succinct.events.PaymentStatusEvent;
import in.succinct.json.JSONAwareWrapper;
import in.succinct.json.JSONAwareWrapper.JSONAwareWrapperCreator;
import in.succinct.onet.core.adaptor.NetworkAdaptor;
import in.succinct.postbox.util.NetworkManager;
import org.json.simple.JSONObject;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

public class MessageImpl extends ModelImpl<Message> {
    public MessageImpl(Message message){
        super(message);
    }
    public void createPaymentLink() {
        Message message = getProxy();
        Request request = new Request(StringUtil.read(message.getPayLoad()));
        request.setObjectCreator(NetworkManager.getInstance().getNetworkAdaptor().getObjectCreator(request.getContext().getDomain()));
        
        String providerId = request.getMessage().getOrder().getProvider().getId();
        request.setPayload(request.getInner().toString());
        Subscriber self = NetworkManager.getInstance().getSubscriber();
        
        
        Call<JSONObject> call = new Call<JSONObject>().url(NetworkManager.getInstance().getNetworkAdaptor().getBaseUrl()+"/payment/createLink/%s".formatted(providerId)).
                header("Authorization",request.generateAuthorizationHeader(self.getSubscriberId(),self.getPubKeyId())).
                header("Content-Type", MimeType.APPLICATION_JSON.toString()).
                input(request.getInner()).inputFormat(InputFormat.JSON);
        
        JSONObject object = call.getResponseAsJson();
        Request tmp = new Request(object);
        tmp.setObjectCreator(request.getObjectCreator());
        request.getMessage().getOrder().setInvoices(tmp.getMessage().getOrder().getInvoices());
        Order order = request.getMessage().getOrder();
        for (Invoice invoice : order.getInvoices()){
            if (!invoice.isEstimate() && invoice.getPaymentTransactions().isEmpty()){
                //Unpaid invoice
                Payment term = null;
                for (Payment payment : order.getPayments()){
                    if (payment.getFulfillmentId() == null || ObjectUtil.equals(payment.getFulfillmentId(),invoice.getFulfillmentId())){
                        if (PaymentStatus.valueOf(payment.getStatus().literal()) == PaymentStatus.NOT_PAID) {
                            term = payment;
                            term.setFulfillmentId(invoice.getFulfillmentId());
                            term.getParams().setAmount(invoice.getAmount());
                            term.setUri(invoice.getTag("payment_link","uri"));
                            break;
                        }else {
                            throw new SellerException.PaymentNotSupported("Cannot create payment link when payment is already initiated.");
                        }
                    }
                }
                //Fix the first unpaid payment on payment object,
                break;
            }
        }
        
        message.setPayLoad(new StringReader(request.getInner().toString()));
        message.save();
        
    }
    public void updatePayment(PaymentStatusEvent event){
        Message message = getProxy();
        Request request = new Request(StringUtil.read(message.getPayLoad()));
        request.setObjectCreator(NetworkManager.getInstance().getNetworkAdaptor().getObjectCreator(request.getContext().getDomain()));
        Order order = request.getMessage().getOrder();
        for (Invoice invoice : order.getInvoices()) {
            String paymentUri = invoice.getTag("payment_link","uri");
            
            if (invoice.getPaymentTransactions().isEmpty() && ObjectUtil.equals(paymentUri,event.getUri())){
                if (event.getAmountPaid() >0 ) {
                    invoice.setPaymentTransactions(new PaymentTransactions() {{
                        add(new PaymentTransaction() {{
                            this.setAmount(event.getAmountPaid());// wE don't allow partial payment.
                            this.setPaymentStatus(PaymentStatus.convertor.valueOf(event.getStatus()));
                            this.setPaymentMethod(PaymentMethod.ONLINE_TRANSFER);
                            this.setTransactionId(event.getTxnReference());
                            this.setRemarks("Payment for Order %s".formatted(order.getId()));
                            this.setDate(new Date());
                        }});
                    }});
                }
                for (Payment payment : order.getPayments()){
                    if (ObjectUtil.equals(payment.getUri(),paymentUri)){
                        payment.setStatus(PaymentStatus.convertor.valueOf(event.getStatus()));
                        payment.getParams().setAmount(event.getAmountPaid());
                    }
                }
            }
        }
        message.setPayLoad(new StringReader(request.getInner().toString()));
        message.save();
    }
    public void summarize(boolean force){
        Message m = getProxy();
        NetworkAdaptor adaptor = NetworkManager.getInstance().getNetworkAdaptor();
        
        
        Request becknRequest= new Request(StringUtil.read(m.getPayLoad()));
        becknRequest.setObjectCreator(adaptor.getObjectCreator(becknRequest.getContext().getDomain()));
        
        Order becknOrder = becknRequest.getMessage().getOrder();
        
        in.succinct.postbox.db.model.Order order = Database.getTable(in.succinct.postbox.db.model.Order.class).newRecord();
        order.setMessageId(m.getId());
        order = Database.getTable(in.succinct.postbox.db.model.Order.class).getRefreshed(order);
        if (!order.getRawRecord().isNewRecord() && !force){
            return;
        }
        
        order.setOrderId(becknOrder.getId());
        order.setTransactionId(becknRequest.getContext().getTransactionId());
        
        String logisticsTransactionId = becknOrder.getFulfillment().getTag("delivery_order","transaction_id");
        String logisticsOrderId = becknOrder.getFulfillment().getTag("delivery_order","order_id");
        String selfManaged = becknOrder.getFulfillment().getTag("delivery_order","self_managed");
        order.setLogisticsOrderId(logisticsOrderId);
        order.setLogisticsTransactionId(logisticsTransactionId);
        order.setLogisticsSelfManaged(order.getReflector().getJdbcTypeHelper().getTypeRef(boolean.class).getTypeConverter().valueOf(selfManaged));
        
        order.setEnvironment(becknOrder.getProvider().getTag("network","environment"));
        Directories directories  =becknOrder.getProvider().getDirectories();
        
        if (directories.isEmpty()){
            order.setMarketedVia("Self");
        }else {
            Descriptor descriptor = directories.get(0).getDescriptor();
            if (descriptor == null || ObjectUtil.isVoid(descriptor.getName())){
                order.setMarketedVia("Self");
            }else {
                order.setMarketedVia(descriptor.getName());
            }
        }
        
        FulfillmentStops stops = becknOrder.getFulfillment().getFulfillmentStops();
        if (stops.size() > 1){
            FulfillmentStop stop = stops.get(stops.size()-1);
            order.setCustomerAddress(stop.getLocation().get("address"));
            order.setCity(stop.getLocation().getCity().getName());
            order.setPinCode(stop.getLocation().getPinCode());
            order.setPhoneNumber(stop.getContact().getPhone());
            
        }else {
            order.setCustomerAddress(becknOrder.getBilling().get("address"));
            order.setCity(becknOrder.getBilling().getCity().getName());
            order.setPinCode(becknOrder.getBilling().getPinCode());
            order.setPhoneNumber(becknOrder.getBilling().getPhone());
        }
        
        order.setFullfilledAt(m.getReflector().getJdbcTypeHelper().getTypeRef(Timestamp.class).getTypeConverter().toStringISO(getArchivedDate(m)));
        order.setOrderCreatedAt(m.getReflector().getJdbcTypeHelper().getTypeRef(Timestamp.class).getTypeConverter().toStringISO(m.getCreatedAt()));

        order.setStatus(becknOrder.getStatus().toString());
        order.setPaymentType(becknOrder.getPayments().get(0)._getPaymentType());
        order.setFulfillmentType(becknOrder.getFulfillment().getType());
        order.setPaymentStatus(becknOrder.getPayments().get(0).getStatus().toString());
        order.setInvoiceAmount(becknOrder.getPayments().get(0).getParams().getAmount());
        order.setArchived(m.isArchived());
        order.setChannelId(m.getChannelId());
        order.setDeliveryPartnerPhoneNumber(m.getDeliveryPartnerPhoneNumber());
        
        order.save();
    }
    
    private Timestamp getArchivedDate(Message m){
        if (!m.isArchived()){
            return null;
        }
        Select select = new Select().from(ModelAudit.class);
        select.where(new Expression(select.getPool(), Conjunction.AND).
                add(new Expression(select.getPool(),"NAME", Operator.EQ,m.getReflector().getModelClass().getSimpleName())).
                add(new Expression(select.getPool(),"MODEL_ID",Operator.EQ,m.getId())));
        
        List<ModelAudit> audits = select.orderBy("ID DESC").execute();
        Request finalValue = new Request(StringUtil.read(m.getPayLoad()));
        
        NetworkAdaptor adaptor = NetworkManager.getInstance().getNetworkAdaptor();
        JSONAwareWrapperCreator objectCreator  = adaptor.getObjectCreator(finalValue.getContext().getDomain());
        finalValue.setObjectCreator(objectCreator);
        
        for (ModelAudit audit : audits){
            try {
                JSONObject object = JSONAwareWrapper.parse(StringUtil.read(audit.getComment()));
                JSONObject archivedAudit = (JSONObject) object.get("ARCHIVED");
                
                if (archivedAudit != null) {
                    String oldValue = (String) archivedAudit.get("old");
                    String newValue = (String) archivedAudit.get("new");
                    if (!ObjectUtil.equals(oldValue, newValue)) {
                        return audit.getCreatedAt();
                    }
                }
            }catch (Exception ex){
                //;
            }
            
        }
        return m.getUpdatedAt();
    }
}
