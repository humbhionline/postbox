package in.succinct.postbox.extensions;

import com.venky.core.math.DoubleUtils;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.extension.Extension;
import com.venky.extension.Registry;
import in.succinct.beckn.Item;
import in.succinct.beckn.Order;
import in.succinct.beckn.Payment;
import in.succinct.beckn.Payment.PaymentStatus;
import in.succinct.beckn.Payments;
import in.succinct.beckn.Request;
import in.succinct.postbox.db.model.Message;
import in.succinct.postbox.util.NetworkManager;

public class MessageArchiveCheck implements Extension {
    static {
        Registry.instance().registerExtension(Message.class.getSimpleName() + ".archive.check",new MessageArchiveCheck());
    }
    @Override
    public void invoke(Object... context) {
        Message message = (Message) context[0];
        Request request = new Request(StringUtil.read(message.getPayLoad(),true));
        request.setObjectCreator(NetworkManager.getInstance().getNetworkAdaptor().getObjectCreator(request.getContext().getDomain()));
        
        Order order = request.getMessage().getOrder();
        if (!order.getStatus().isOpen()){
            Payments payments = request.getMessage().getOrder().getPayments();
            Bucket expected = new Bucket();
            if (order.getStatus().isPaymentRequired()) {
                for (Item item : order.getItems()) {
                    expected.increment(item.getItemQuantity().getSelected().getCount() * item.getPrice().getValue());
                }
            }
            
            Bucket paid  =new Bucket(0);
            for (Payment p : payments){
                if (p.getStatus() == PaymentStatus.PAID) {
                    if (p.getParams() != null && p.getParams().getAmount() > 0){
                        paid.increment(p.getParams().getAmount());
                    }else {
                        paid.decrement(paid.doubleValue());
                        paid.increment(expected.doubleValue());
                        break;
                    }
                }
            }
            message.setArchived(DoubleUtils.compareTo(expected.doubleValue() ,paid.doubleValue())<=0);
        }else {
            message.setArchived(false);
        }
        
        
        
    }
}
