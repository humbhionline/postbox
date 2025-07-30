package in.succinct.postbox.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.indexing.Index;
import com.venky.swf.db.annotations.model.EXPORTABLE;
import com.venky.swf.db.model.Model;

public interface Order extends Model {
    @EXPORTABLE(value = false)
    long getId();

    @UNIQUE_KEY
    Long getMessageId();
    void setMessageId(Long id);
    Message getMessage();
    
    String getOrderId();
    void setOrderId(String orderId);
    
    String getTransactionId();
    void setTransactionId(String transactionId);
    
    String getLogisticsOrderId();
    void setLogisticsOrderId(String logisticsOrderId);
    
    String getLogisticsTransactionId();
    void setLogisticsTransactionId(String logisticsTransactionId);
    
    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    boolean isLogisticsSelfManaged();
    void setLogisticsSelfManaged(boolean logisticsSelfManaged);
    
    
    String getEnvironment();
    void setEnvironment(String environment);
    
    String getMarketedVia();
    void setMarketedVia(String marketedVia);
    
    String getCustomerAddress();
    void setCustomerAddress(String customerAddress);
    
    
    String getCity();
    void setCity(String city);
    
    String getPinCode();
    void setPinCode(String pinCode);
    
    String getPhoneNumber();
    void setPhoneNumber(String phoneNumber);
    
    
    String getFullfilledAt();
    void setFullfilledAt(String fullfilledAt);
    
    String getOrderCreatedAt();
    void setOrderCreatedAt(String orderCreatedAt);
    
    String getStatus();
    void setStatus(String status);
    
    String getPaymentType();
    void setPaymentType(String paymentType);
    
    String getFulfillmentType();
    void setFulfillmentType(String fulfillmentType);
    
    String getPaymentStatus();
    void setPaymentStatus(String paymentStatus);
    
    @COLUMN_DEF(StandardDefault.ZERO)
    double getInvoiceAmount();
    void setInvoiceAmount(double invoiceAmount);
    
    
    boolean isArchived();
    void setArchived(boolean archived);
    
    @IS_NULLABLE(value = false)
    public Long getChannelId();
    public void setChannelId(Long id);
    public Channel getChannel();
    
    
    String getDeliveryPartnerPhoneNumber();
    void setDeliveryPartnerPhoneNumber(String deliveryPartnerPhoneNumber);
    
}
