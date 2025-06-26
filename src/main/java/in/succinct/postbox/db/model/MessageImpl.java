package in.succinct.postbox.db.model;

import com.venky.core.io.StringReader;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.InputFormat;
import in.succinct.beckn.Invoice;
import in.succinct.beckn.Order;
import in.succinct.beckn.Payment;
import in.succinct.beckn.Payment.Params;
import in.succinct.beckn.Payment.PaymentStatus;
import in.succinct.beckn.Payment.PaymentTransaction;
import in.succinct.beckn.Payment.PaymentTransaction.PaymentTransactions;
import in.succinct.beckn.PaymentMethod;
import in.succinct.beckn.Request;
import in.succinct.beckn.SellerException;
import in.succinct.beckn.Subscriber;
import in.succinct.events.PaymentStatusEvent;
import in.succinct.postbox.util.NetworkManager;
import org.json.simple.JSONObject;

import java.util.Date;

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
}
