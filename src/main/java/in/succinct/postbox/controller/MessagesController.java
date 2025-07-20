package in.succinct.postbox.controller;

import com.venky.core.collections.SequenceSet;
import com.venky.core.io.StringReader;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.controller.annotations.SingleRecordAction;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.exceptions.AccessDeniedException;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.audit.db.model.ModelAudit;
import com.venky.swf.plugins.background.core.AsyncTaskManagerFactory;
import com.venky.swf.plugins.background.core.DbTask;
import com.venky.swf.pm.DataSecurityFilter;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;
import in.succinct.beckn.Agent;
import in.succinct.beckn.Context;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.Fulfillment.FulfillmentStatus;
import in.succinct.beckn.FulfillmentStop;
import in.succinct.beckn.FulfillmentStops;
import in.succinct.beckn.Order;
import in.succinct.beckn.Organization;
import in.succinct.beckn.Request;
import in.succinct.beckn.SellerException;
import in.succinct.events.PaymentStatusEvent;
import in.succinct.json.JSONAwareWrapper;
import in.succinct.json.JSONAwareWrapper.JSONAwareWrapperCreator;
import in.succinct.onet.core.adaptor.NetworkAdaptor;
import in.succinct.onet.core.adaptor.NetworkAdaptor.Domain;
import in.succinct.onet.core.adaptor.NetworkAdaptor.DomainCategory;
import in.succinct.postbox.db.model.Channel;
import in.succinct.postbox.db.model.Message;
import in.succinct.postbox.db.model.User;
import in.succinct.postbox.util.NetworkManager;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public class MessagesController extends ModelController<Message> {
    public MessagesController(Path path) {
        super(path);
    }

    private static final ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
    private static class MessageEvictionTask implements DbTask{
        long id ;
        public MessageEvictionTask(long id){
            this.id = id;
        }

        @Override
        public void execute() {
            Message message = Database.getTable(Message.class).get(id);
            if (Database.getInstance().getCurrentUser() == null){
                User user = Database.getTable(User.class).newRecord();
                user.setProviderId(message.getChannel().getName());
                user = Database.getTable(User.class).getRefreshed(user);
                Database.getInstance().open(user);
            }
            if (message != null){
                message.destroy();
            }
        }
    }
    
    
    protected Expression getWhereClause(){
        
        Expression where = super.getWhereClause();
        User user = getPath().getSessionUser().getRawRecord().getAsProxy(User.class);
        if  (user.getId() > 1) {
            Expression addl = new Expression(getReflector().getPool(), Conjunction.OR);
            
            String providerId = user.getProviderId();
            if (!ObjectUtil.isVoid(providerId)) {
                Select select = new Select().from(Channel.class);
                select.where(new Expression(select.getPool(), "NAME", Operator.LK,
                        getPath().getSessionUser().getRawRecord().getAsProxy(User.class).getProviderId() + "%"));
                SequenceSet<Long> ids = DataSecurityFilter.getIds(select.execute());
                if (!ids.isEmpty()) {
                    addl.add(new Expression(getReflector().getPool(), "CHANNEL_ID", Operator.IN, ids.toArray()));
                } else {
                    addl.add(new Expression(getReflector().getPool(), "CHANNEL_ID", Operator.EQ));
                }
            }
            if (!ObjectUtil.isVoid(user.getPhoneNumber())) {
                addl.add(new Expression(getReflector().getPool(), "DELIVERY_PARTNER_PHONE_NUMBER", Operator.EQ, user.getPhoneNumber()));
            }
            where.add(addl);
        }
        
        return where;
    }
    
    
    @SuppressWarnings("unchecked")
    public View post(String channelName){
        ensureIntegrationMethod(HttpMethod.POST);
        try {
            Message message = Database.getTable(Message.class).newRecord();
            Channel c = getChannel(channelName,false);

            message.setChannelId(c.getId());
            message.setPayLoad(new InputStreamReader(getPath().getInputStream()));
            JSONObject headers = new JSONObject();
            headers.putAll(getPath().getHeaders());
            message.setHeaders(headers.toString());
            message.save();

            if (c.getExpiryMillis() > 0 ) {
                service.schedule(new EvictionSchedule(message.getId()), c.getExpiryMillis(), TimeUnit.MILLISECONDS);
            }

            return getIntegrationAdaptor().createStatusResponse(getPath(),null,"Message sent");
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }
    }
    private static class EvictionSchedule implements  Runnable{
        long id;
        public EvictionSchedule(long id){
            this.id = id;
        }
        public void run(){
            AsyncTaskManagerFactory.getInstance().addAll(Collections.singleton(new MessageEvictionTask(id)));
        }
    }
    public Channel getChannel(String channelName, boolean ensureAccessibleByLoggedInUser){
        Channel c = Database.getTable(Channel.class).newRecord();
        c.setName(channelName);
        c= Database.getTable(Channel.class).getRefreshed(c,ensureAccessibleByLoggedInUser);
        if (c.getRawRecord().isNewRecord()){
            throw new RuntimeException("Invalid channel Name" + channelName);
        }

        return c;
    }

    public View pull(String channelName){
        //Convert to event stream!!
        ensureIntegrationMethod(HttpMethod.GET);
        Channel c = getChannel(channelName,true);

        int maxRecords = getReflector().getJdbcTypeHelper().getTypeRef(Integer.class).getTypeConverter().valueOf(getPath().getFormFields().getOrDefault("maxRecords",1));

        List<Message> messageList = new Select().from(Message.class).where(new Expression(getReflector().getPool(), "CHANNEL_ID", Operator.EQ,c.getId())).execute(maxRecords);

        messageList.forEach(Message::destroy);

        return list(messageList,maxRecords == Select.MAX_RECORDS_ALL_RECORDS || messageList.size() < maxRecords);
    }
    
    @SingleRecordAction(icon = "fa-link")
    public View createPaymentLink(long id){
        Message message = Database.getTable(getModelClass()).get(id);
        message.createPaymentLink();
        return show(id);
    }
    
    /**
     * new JSONObject() {{
     *                 put("txn_reference", link.getTxnReference());
     *                 put("status", link.getStatus());
     *                 put("active",link.isActive());
     *                 put("uri", link.getLinkUri());
     *             }}
     * @return
     */
    
    @RequireLogin(false)
    public View updatePayment(){
        try {
            String payload = StringUtil.read(getPath().getInputStream());
            Request request = new Request(payload);
            if (!request.verifySignature("Authorization",getPath().getHeaders())){
                throw new SellerException.InvalidSignature();
            }
            
            PaymentStatusEvent event = new PaymentStatusEvent(payload);
            
            Message message = Database.getTable(Message.class).newRecord();
            message.setMessageId(event.getTransactionId());
            message = Database.getTable(Message.class).getRefreshed(message);
            if (message.getRawRecord().isNewRecord()){
                throw new RuntimeException("Cannot identify beckn transaction_id");
            }
            message.updatePayment(event);
            return no_content();
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }
        
        
    }
    
    public View refresh(long id){
        Message m  = Database.getTable(getModelClass()).get(id);
            if (m == null || !m.isAccessibleBy(getSessionUser())){
            throw new AccessDeniedException();
        }
        Request request = new Request(StringUtil.read(m.getPayLoad()));
        request.setObjectCreator(NetworkManager.getInstance().getNetworkAdaptor().getObjectCreator(request.getContext().getDomain()));
        String deliverySubscriber = getDeliverySubscriber(request);
        Fulfillment fulfillment = request.getMessage().getOrder().getFulfillment();
        
        if (!ObjectUtil.isVoid(deliverySubscriber) && fulfillment != null){
            String txnId = fulfillment.getTag("delivery_order","transaction_id");
            String orderId = fulfillment.getTag("delivery_order","order_id");
            if (!ObjectUtil.isVoid(orderId)){
                Request deliveryStatus = createStatusResponse(txnId,orderId,deliverySubscriber);
                if (deliveryStatus != null){
                    Order deliveryOrder = deliveryStatus.getMessage().getOrder();
                    switch (deliveryOrder.getStatus()){
                        case Completed -> {
                            fulfillment.setFulfillmentStatus(FulfillmentStatus.Completed);
                        }
                        case In_Transit -> {
                            fulfillment.setFulfillmentStatus(FulfillmentStatus.In_Transit);
                        }
                    }
                }
            }
            m.setPayLoad(new StringReader(request.getInner().toString()));
            m.save();
        }
        
        return show(m);
    }
    
    private Request createStatusResponse(String txnId, String orderId, String deliverySubscriber) {
        Domain logistics = null;
        for (Domain domain : NetworkManager.getInstance().getNetworkAdaptor().getDomains()) {
            if (domain.getDomainCategory() == DomainCategory.HIRE_TRANSPORT_SERVICE){
                logistics = domain;
                break;
            }
        }
        Domain request_domain = logistics;
        Request request = new Request(){{
           setContext(new Context(){{
               setBppId(deliverySubscriber);
               setTransactionId(txnId);
               setAction("status");
               setNetworkId(NetworkManager.getInstance().getNetworkAdaptor().getId());
               setDomain(request_domain == null ? null : request_domain.getId());
           }});
           setMessage(new in.succinct.beckn.Message(){{
               setOrder(new Order(){{
                   setId(orderId);
               }});
           }});
        }};
        Request networkRequest = NetworkManager.getInstance().getNetworkAdaptor().getObjectCreator(request.getContext().getDomain()).create(Request.class);
        networkRequest.update(request);
        
        String bg = NetworkManager.getInstance().getNetworkAdaptor().getSearchProvider().getSubscriberUrl();
        JSONAware response = new Call<JSONObject>().url(bg,"status").inputFormat(InputFormat.JSON).input(networkRequest.getInner()).
                header("Content-type","application/json").header("X-CallBackToBeSynchronized","Y").header("ApiKey",getSessionUser().getApiKey()).
                getResponseAsJson();
        if (response != null) {
            if (response instanceof JSONArray responses){
                response = responses.size() != 1 ? null : (JSONObject)responses.get(0);
            }
        }
        if (response != null){
            Request deliveryResponse = networkRequest.getObjectCreator().create(Request.class);
            deliveryResponse.setInner((JSONObject) response);
            return  deliveryResponse;
        }
        
        return null;
    }
    
    private String getDeliverySubscriber(Request request) {
        Order order =  request.getMessage().getOrder();
        Fulfillment fulfillment  = order.getFulfillment();
        Agent agent  = fulfillment == null ? null : fulfillment.getAgent();
        Organization organization = agent == null ? null : agent.getOrganization();
        Descriptor descriptor = organization == null ? null : organization.getDescriptor();
        return descriptor == null ? null : descriptor.getCode();
    }
    
    public View download_orders(){
        ensureIntegrationMethod(HttpMethod.POST);
        
        int numMonths ;
        boolean includeCurrentMonth ;
        try {
            JSONObject input = JSONAwareWrapper.parse(getPath().getInputStream());
            includeCurrentMonth = getReflector().getJdbcTypeHelper().getTypeRef(boolean.class).getTypeConverter().valueOf(input.getOrDefault("IncludeCurrentMonth",false));

            numMonths = getReflector().getJdbcTypeHelper().getTypeRef(int.class).getTypeConverter().valueOf(input.getOrDefault("NumMonths",0));
            if (!includeCurrentMonth){
                numMonths = Math.max(numMonths,1);
            }
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }
        
        long endDate  = includeCurrentMonth ? System.currentTimeMillis() : getCurrentMonthStartDate();
        long startDate = getOrderReportStartDate(numMonths);
       
        
        Select q = new Select("ID","PAY_LOAD", "ARCHIVED").from(getModelClass());
        
        Expression where = getWhereClause();
        where.add(new Expression(q.getPool(),Conjunction.AND).
                add(new Expression(q.getPool(),"ARCHIVED",Operator.EQ,true)).
                add(new Expression(q.getPool(),"CREATED_AT",Operator.GE,new Timestamp(startDate))).
                add(new Expression(q.getPool(),"CREATED_AT",Operator.LT,new Timestamp(endDate))));
        
        
        
                
        List<Message> records =  q.where(where).orderBy(getReflector().getOrderBy()).execute(getModelClass(),0,getFilter());
        List<in.succinct.postbox.db.model.Order> orders = new ArrayList<>();
        NetworkAdaptor  adaptor = NetworkManager.getInstance().getNetworkAdaptor();
        
        records.forEach(m->{
            in.succinct.postbox.db.model.Order order = Database.getTable(in.succinct.postbox.db.model.Order.class).newRecord();
            
            Request becknRequest= new Request(StringUtil.read(m.getPayLoad()));
            becknRequest.setObjectCreator(adaptor.getObjectCreator(becknRequest.getContext().getDomain()));
            
            Order becknOrder = becknRequest.getMessage().getOrder();
            order.setTransactionId(becknRequest.getContext().getTransactionId());
            order.setEnvironment(becknOrder.getProvider().getTag("network","environment"));
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
            order.setStatus(becknOrder.getStatus().toString());
            order.setPaymentType(becknOrder.getPayments().get(0)._getPaymentType());
            order.setFulfillmentType(becknOrder.getFulfillment().getType());
            order.setPaymentStatus(becknOrder.getPayments().get(0).getStatus().toString());
            order.setInvoiceAmount(becknOrder.getPayments().get(0).getParams().getAmount());
            order.setFullfilledAt(m.getReflector().getJdbcTypeHelper().getTypeRef(Timestamp.class).getTypeConverter().toStringISO(getArchivedDate(m)));
            order.setOrderCreatedAt(m.getReflector().getJdbcTypeHelper().getTypeRef(Timestamp.class).getTypeConverter().toStringISO(m.getCreatedAt()));
            
            
            orders.add(order);
        });
        Workbook wb = new XSSFWorkbook();
        super.exportxls(in.succinct.postbox.db.model.Order.class, wb,orders);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            wb.write(os);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Calendar calendar  = Calendar.getInstance();
        calendar.setTimeInMillis(startDate);
        
        return new BytesView(getPath(), os.toByteArray(), MimeType.APPLICATION_XLSX, "content-disposition", "attachment; filename=" +  "orders-%d-%d.xlsx".formatted(calendar.get(Calendar.MONTH),calendar.get(Calendar.YEAR)));
    }
    public Timestamp getArchivedDate(Message m){
        if (!m.isArchived()){
            return null;
        }
        Select select = new Select().from(ModelAudit.class);
        select.where(new Expression(select.getPool(),Conjunction.AND).
                add(new Expression(select.getPool(),"NAME",Operator.EQ,m.getReflector().getModelClass().getSimpleName())).
                add(new Expression(select.getPool(),"MODEL_ID",Operator.EQ,m.getId())));
        
        List<ModelAudit> audits = select.orderBy("ID DESC").execute();
        Request finalValue = new Request(StringUtil.read(m.getPayLoad()));
        
        NetworkAdaptor adaptor = NetworkManager.getInstance().getNetworkAdaptor();
        JSONAwareWrapperCreator objectCreator  = adaptor.getObjectCreator(finalValue.getContext().getDomain());
        finalValue.setObjectCreator(objectCreator);
        
        for (ModelAudit audit : audits){
            JSONObject object = JSONAwareWrapper.parse(StringUtil.read(audit.getComment()));
            JSONObject archivedAudit = (JSONObject) object.get("ARCHIVED");
            
            if (archivedAudit != null){
                String oldValue  = (String) archivedAudit.get("old");
                String newValue  = (String) archivedAudit.get("new");
                if (!ObjectUtil.equals(oldValue,newValue) ){
                    return audit.getCreatedAt();
                }
            }
            
        }
        return m.getCreatedAt();
    }
    
    private long getCurrentMonthStartDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.DAY_OF_MONTH,1);
        calendar.set(Calendar.HOUR,0);
        calendar.set(Calendar.MINUTE,0);
        calendar.set(Calendar.SECOND,0);
        calendar.set(Calendar.MILLISECOND,0);
        return calendar.getTimeInMillis();
    }
    private long getOrderReportStartDate(int numMonthsToInclude) {
        long monthStart = getCurrentMonthStartDate();
        
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(monthStart);
        
        for (int i = 0 ; i < numMonthsToInclude; i ++){
            calendar.setTimeInMillis(calendar.getTimeInMillis() - 1L);
            calendar.set(Calendar.DAY_OF_MONTH,1);
            calendar.set(Calendar.HOUR,0);
            calendar.set(Calendar.MINUTE,0);
            calendar.set(Calendar.SECOND,0);
            calendar.set(Calendar.MILLISECOND,0);
        }
        
        return calendar.getTimeInMillis();
    }
}
