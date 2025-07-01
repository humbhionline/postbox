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
            message.setArchived(!order.getStatus().isPaymentRequired() || order.isPaid());
        }else {
            message.setArchived(false);
        }
        
    }
}
