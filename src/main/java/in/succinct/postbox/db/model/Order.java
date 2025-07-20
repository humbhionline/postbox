package in.succinct.postbox.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.model.EXPORTABLE;
import com.venky.swf.db.model.Model;

@IS_VIRTUAL
public interface Order extends Model {
    @EXPORTABLE(value = false)
    long getId();
    
    String getTransactionId();
    void setTransactionId(String transactionId);
    
    String getEnvironment();
    void setEnvironment(String environment);
    
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
    
    
}
