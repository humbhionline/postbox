package in.succinct.postbox.controller;

import com.venky.core.collections.IgnoreCaseMap;
import com.venky.core.io.StringReader;
import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.Controller;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.CryptoKey;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.beckn.tasks.BecknApiCall;
import com.venky.swf.plugins.beckn.tasks.BecknTask;
import com.venky.swf.plugins.beckn.tasks.BppTask;
import com.venky.swf.plugins.collab.db.model.config.Country;
import com.venky.swf.routing.Config;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;
import in.succinct.beckn.Acknowledgement;
import in.succinct.beckn.Acknowledgement.Status;
import in.succinct.beckn.Agent;
import in.succinct.beckn.BecknException;
import in.succinct.beckn.Catalog;
import in.succinct.beckn.Context;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Error;
import in.succinct.beckn.Error.Type;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.Invoice;
import in.succinct.beckn.Invoice.Invoices;
import in.succinct.beckn.Item;
import in.succinct.beckn.Location;
import in.succinct.beckn.Locations;
import in.succinct.beckn.Order;
import in.succinct.beckn.Payment;
import in.succinct.beckn.Payment.PaymentStatus;
import in.succinct.beckn.Payment.PaymentTransaction;
import in.succinct.beckn.Payment.PaymentTransaction.PaymentTransactions;
import in.succinct.beckn.Providers;
import in.succinct.beckn.Quote;
import in.succinct.beckn.Rating;
import in.succinct.beckn.Rating.RatingCategory;
import in.succinct.beckn.Request;
import in.succinct.beckn.Response;
import in.succinct.beckn.SellerException.GenericBusinessError;
import in.succinct.beckn.SellerException.InvalidRequestError;
import in.succinct.beckn.SellerException.InvalidSignature;
import in.succinct.beckn.Subscriber;
import in.succinct.beckn.Xinput;
import in.succinct.onet.core.adaptor.NetworkAdaptor;
import in.succinct.onet.core.adaptor.NetworkAdaptor.Domain;
import in.succinct.onet.core.api.MessageLogger;
import in.succinct.postbox.db.model.Channel;
import in.succinct.postbox.db.model.Message;
import in.succinct.postbox.util.NetworkManager;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;

public class BppController extends Controller {
    public BppController(Path path) {
        super(path);
    }
    

    private Subscriber subscriber = null;
    public Subscriber getSubscriber(){
        if (subscriber == null){
            subscriber = NetworkManager.getInstance().getSubscriber();
        }
        return  subscriber;
    }
    public View subscribe() {
        NetworkManager.getInstance().subscribe();
        return new BytesView(getPath(),"Subscription initiated!".getBytes(StandardCharsets.UTF_8),MimeType.APPLICATION_JSON);
    }

    private View act(){
        Request request = null;
        try {
            Subscriber subscriber = getSubscriber();
            request = new Request(StringUtil.read(getPath().getInputStream()));
            request.setObjectCreator(NetworkManager.getInstance().getNetworkAdaptor().getObjectCreator(request.getContext().getDomain()));
            
            // Go Ahead or reject context based on subsciber!
            Context context = request.getContext();
            if (!ObjectUtil.equals(context.getBppId(),subscriber.getSubscriberId())){
                throw new RuntimeException("Not your Target Subscriber");
            }else{
                request.getContext().setBppUri(subscriber.getSubscriberUrl());
            }
            request.getContext().setBppId(subscriber.getSubscriberId());
            request.getContext().setAction(getPath().action());

            Map<String,String> headers =  new IgnoreCaseMap<>();
            headers.put("Content-Type", MimeType.APPLICATION_JSON.toString());
            headers.put("Accept",MimeType.APPLICATION_JSON.toString());
            if (getPath().getHeader("Authorization") != null) {
                headers.put("Authorization",getPath().getHeader("Authorization"));
            }
            if (getPath().getHeader("Proxy-Authorization") != null) {
                headers.put("Proxy-Authorization",getPath().getHeader("Proxy-Authorization"));
            }
            if (getPath().getHeader("X-Gateway-Authorization") != null) {
                headers.put("X-Gateway-Authorization",getPath().getHeader("X-Gateway-Authorization"));

                Subscriber bg = getGatewaySubscriber(request.extractAuthorizationParams("X-Gateway-Authorization",headers));
                if (bg != null) {
                    request.getExtendedAttributes().set(Request.CALLBACK_URL, bg.getSubscriberUrl());
                }
            }
            Config.instance().getLogger(getClass().getName()).log(Level.INFO,request.toString());
            
            NetworkAdaptor networkAdaptor  = NetworkManager.getInstance().getNetworkAdaptor();
            
            BecknApiCall call = BecknApiCall.build();
            Domain domain = null;
            if (!ObjectUtil.isVoid(context.getDomain())){
                domain = networkAdaptor.getDomains().get(context.getDomain());
            }else if (!networkAdaptor.getDomains().isEmpty()){
                domain = networkAdaptor.getDomains().get(0);
            }
            if (domain != null) {
                call.schema(domain.getSchemaURL());
            }
            
            call.url(getPath().getOriginalRequestUrl()).path("/"+getPath().action()).headers(headers).request(request).validateRequest();
            
            
            BppTask task = createTask(getPath().action(),request,headers);
            task.setSubscriber(new com.venky.swf.plugins.beckn.messaging.Subscriber(subscriber.toString()) {
                @Override
                public <T extends BecknTask> Class<T> getTaskClass(String action) {
                    return null;
                }
            });
            Set<String> importantActions = new HashSet<>(){{
                add("init");
                add("confirm");
                add("cancel");
                add("update");
            }};

            if (isRequestAuthenticated(task,request)){
                boolean persistentTask = importantActions.contains(getPath().action());

                TaskManager.instance().executeAsync(task, false);
                return ack(request);
            }else {
                return nack(request,new InvalidSignature(),request.getContext().getBapId()); // See if throw. !!
            }
        }catch (Exception ex){
            if (request == null){
                throw new RuntimeException(ex);
            }
            Request response  = new Request();
            Error error = new Error();
            response.setContext(request.getContext());
            response.setError(error);
            if (ex instanceof BecknException) {
                error.setCode(((BecknException)ex).getErrorCode());
            }else{
                error.setCode(new GenericBusinessError().getErrorCode());
            }
            error.setMessage(ex.getMessage());
            (NetworkManager.getInstance().getNetworkAdaptor().getApiAdaptor()).log(MessageLogger.FROM_NETWORK,request,getPath().getHeaders(),response,getPath().getOriginalRequestUrl());
            return new BytesView(getPath(),response.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    private BppTask createTask(String action, Request request, Map<String,String> headers){
        return new BppTask(request,headers) {
            @Override
            public Request generateCallBackRequest() {
                String responseAction = action.startsWith("get_") ? action.substring(4) : "on_" + action;
                
                Request response = null ;
                NetworkAdaptor networkAdaptor = NetworkManager.getInstance().getNetworkAdaptor();
                
                Context context = getRequest().getContext();

                Message message = Database.getTable(Message.class).newRecord();
                message.setMessageId(context.getTransactionId()); //Yes transaction_id
                message = Database.getTable(Message.class).getRefreshed(message);
                
                
                if (message.getRawRecord().isNewRecord()) {
                    if (ObjectUtil.equals(context.getAction(),"confirm")) {
                        String channelId = getChannelId();
                        message.setChannelId(getChannel(channelId,true).getId());
                        message.setHeaders(new JSONObject(getHeaders()).toString());
                        getRequest().getMessage().getOrder().setStatus(Order.Status.Created);
                        getRequest().getMessage().getOrder().getPayments().get(0).setStatus(PaymentStatus.NOT_PAID);
                        message.setPayLoad(new StringReader(getRequest().getInner().toString()));
                        message.save();
                        response = new Request(getRequest().toString());
                        response.setObjectCreator(networkAdaptor.getObjectCreator(context.getDomain()));
                        response.getContext().setAction(responseAction);
                    }else {
                        Request tmpResponse = new Request();
                        tmpResponse.setContext(new Context(context.toString()));
                        tmpResponse.getContext().setAction(responseAction);
                        tmpResponse.setMessage(new in.succinct.beckn.Message());
                        tmpResponse.getMessage().setCatalog(new Catalog(){{
                            setProviders(new Providers());
                            setId(context.getBppId());
                            setDescriptor(new Descriptor(){{
                                setCode(context.getBppId());
                                setShortDesc(context.getBppId());
                                setLongDesc(context.getBppId());
                            }});
                        }});
                        response = networkAdaptor.getObjectCreator(context.getDomain()).create(Request.class);
                        response.update(tmpResponse);
                    }
                }else {
                    Request persisted = new Request(StringUtil.read(message.getPayLoad()));
                    Order order = persisted.getMessage().getOrder();
                    
                    if (ObjectUtil.equals(context.getAction(),"status")) {
                        response =  persisted;
                        response.setObjectCreator(networkAdaptor.getObjectCreator(context.getDomain()));
                        response.setContext(new Context(context.toString()));
                        response.getContext().setAction(responseAction);
                    }else if (ObjectUtil.equals(context.getAction(),"rating")) {
                        Request ratingRequest = getRequest();
                        for (Rating rating : ratingRequest.getMessage().getRatings()) {
                            if (RatingCategory.Order == rating.getRatingCategory()){
                                order.setRating(rating.getValue());
                            }else if (RatingCategory.Fulfillment == rating.getRatingCategory()){
                                for (Fulfillment fulfillment : order.getFulfillments()) {
                                    if (ObjectUtil.equals(fulfillment.getId(),rating.getId())){
                                        fulfillment.setRating(rating.getValue());
                                        break;
                                    }
                                }
                            }else if (RatingCategory.Provider == rating.getRatingCategory()){
                                order.getProvider().setRating(rating.getValue());
                            }else if (RatingCategory.Item == rating.getRatingCategory()){
                                for (Item item : order.getItems().all(rating.getId())) {
                                    item.setRating(rating.getValue());
                                }
                            }
                        }
                        message.setPayLoad(new StringReader(persisted.getInner().toString()));
                        message.save();
                        
                        Request tmpResponse = new Request();
                        tmpResponse.setContext(new Context(context.toString()));
                        tmpResponse.getContext().setAction(responseAction);
                        tmpResponse.setMessage(new in.succinct.beckn.Message());
                        tmpResponse.getMessage().setFeedbackForm(new Xinput());//No additional data required
                        response = networkAdaptor.getObjectCreator(context.getDomain()).create(Request.class);
                        response.update(tmpResponse);
                    }else if (ObjectUtil.equals(action,"update")) {
                        Request updateRequest = getRequest();
                        String target = updateRequest.getMessage().getUpdateTarget();
                        
                        if (ObjectUtil.equals(target,"invoices")){
                            for (Invoice invoice :updateRequest.getMessage().getOrder().getInvoices()){
                                Invoice persistedInvoice = order.getInvoices().get(invoice.getId());
                                if (persistedInvoice.getUnpaidAmount().doubleValue() > 0) {
                                    persistedInvoice.setPaymentTransactions(invoice.getPaymentTransactions());
                                }
                            }
                        }else {
                            throw new RuntimeException("Action %s not supported for target %s".formatted(action,target));
                        }
                        message.setPayLoad(new StringReader(persisted.getInner().toString()));
                        message.save();
                        
                        response = new Request(StringUtil.read(message.getPayLoad())); // May get updated in messageExtension. So pick latest.
                        response.setObjectCreator(networkAdaptor.getObjectCreator(context.getDomain()));
                        response.getContext().update(context);
                        response.getContext().setAction(responseAction);
                        
                    }else {
                        throw new RuntimeException("Action %s not supported".formatted(action));
                    }
                    
                }
                response.setPayload(response.getInner().toString());
                
                
                return response;
            }
            
            
            
            private String getChannelId() {
                in.succinct.beckn.Message becknMessage = getRequest().getMessage();
                String providerId = becknMessage.getOrder().getProvider().getId();
                Locations locations = becknMessage.getOrder().getProvider().getLocations();
                if (locations.size() != 1){
                    throw new RuntimeException("Order must be for a single location");
                }
                Location location = locations.get(0);
                String channelId = location.getId();
                if (!channelId.contains(providerId)){
                    //Ensure name space,
                    channelId = String.format("%s.%s",providerId,channelId);
                }
                return channelId;
            }
        };
    }
    
    
    public Channel getChannel(String channelName, boolean createIfAbsent){
        Channel c = Database.getTable(Channel.class).newRecord();
        c.setName(channelName);
        c= Database.getTable(Channel.class).getRefreshed(c,false);
        if (c.getRawRecord().isNewRecord()){
            if (createIfAbsent){
                c.save();
            }else {
                throw new RuntimeException("Invalid channel Name" + channelName);
            }
        }
        
        return c;
    }
    
    private Subscriber getGatewaySubscriber(Map<String, String> authParams) {
        Subscriber bg = null;
        if (!authParams.isEmpty()){
            String keyId = authParams.get("keyId");
            StringTokenizer keyTokenizer = new StringTokenizer(keyId,"|");
            String subscriberId = keyTokenizer.nextToken();

            List<Subscriber> subscriber = NetworkManager.getInstance().getNetworkAdaptor().lookup(new Subscriber(){{
                setSubscriberId(subscriberId);
                setUniqueKeyId(keyId);
                setType(Subscriber.SUBSCRIBER_TYPE_BG);
            }},true);
            if (!subscriber.isEmpty()){
                bg = subscriber.get(0);
            }
        }
        return bg;

    }


    @RequireLogin(false)
    public View search() {
        return act();
    }
    @RequireLogin(false)
    public View select(){
        return act();
    }
    @RequireLogin(false)
    public View cancel(){
        return act();
    }

    @RequireLogin(false)
    public View init(){
        return act();
    }
    @RequireLogin(false)
    public View confirm(){
        return act();
    }

    @RequireLogin(false)
    public View status(){
        return act();
    }

    @RequireLogin(false)
    public View update(){
        return act();
    }


    @RequireLogin(false)
    public View track(){
        return act();
    }

    @RequireLogin(false)
    public View rating(){
        return act();
    }

    @RequireLogin(false)
    public View support(){
        return act();
    }

    @RequireLogin(false)
    public View get_cancellation_reasons(){
        return act();
    }

    @RequireLogin(false)
    public View get_return_reasons(){
        return act();
    }

    @RequireLogin(false)
    public View get_rating_categories(){
        return act();
    }
    @RequireLogin(false)
    public View get_feedback_categories(){
        return act();
    }


    /**/
    public Response nack(Request request, Throwable th){
        Response response = new Response(new Acknowledgement(Status.NACK));
        if (th != null){
            Error error = new Error();
            response.setError(error);
            if (th.getClass().getName().startsWith("org.openapi4j")){
                InvalidRequestError sellerException = new InvalidRequestError();
                error.setType(Type.JSON_SCHEMA_ERROR);
                error.setCode(sellerException.getErrorCode());
                error.setMessage(sellerException.getMessage());
            }else if (th instanceof BecknException bex){
                error.setType(Type.DOMAIN_ERROR);
                error.setCode(bex.getErrorCode());
                error.setMessage(bex.getMessage());
            }else {
                error.setMessage(th.toString());
                error.setCode(new GenericBusinessError().getErrorCode());
                error.setType(Type.DOMAIN_ERROR);
            }
        }
        //BecknUtil.log("FromNetwork",request,getPath().getHeaders(),response,getPath().getOriginalRequestUrl());
        return response;
    }

    public View nack(Request request, Throwable th, String realm){

        Response response = nack(request,th);

        return new BytesView(getPath(),
                response.getInner().toString().getBytes(StandardCharsets.UTF_8),
                MimeType.APPLICATION_JSON,"WWW-Authenticate","Signature realm=\""+realm+"\"",
                "headers=\"(created) (expires) digest\""){
            @Override
            public void write() throws IOException {
                if (th instanceof InvalidSignature){
                    super.write(HttpServletResponse.SC_UNAUTHORIZED);
                }else {
                    super.write(HttpServletResponse.SC_BAD_REQUEST);
                }
                //TODO wanto return 200 in 1.0
            }
        };
    }
    public View ack(Request request){
        Response response = new Response(new Acknowledgement(Status.ACK));

        //BecknUtil.log("FromNetwork",request,getPath().getHeaders(),response,getPath().getOriginalRequestUrl());

        return new BytesView(getPath(),response.getInner().toString().getBytes(StandardCharsets.UTF_8),MimeType.APPLICATION_JSON);
    }

    protected boolean isRequestAuthenticated(BecknTask task, Request request){
        if ( Config.instance().getBooleanProperty("beckn.auth.enabled", false)) {
            if (getPath().getHeader("X-Gateway-Authorization") != null) {
                task.registerSignatureHeaders("X-Gateway-Authorization");
            }
            if (getPath().getHeader("Proxy-Authorization") != null){
                task.registerSignatureHeaders("Proxy-Authorization");
            }
            if (getPath().getHeader("Authorization") != null) {
                task.registerSignatureHeaders("Authorization");
            }
            return task.verifySignatures(false);
        }else {
            return true;
        }
    }

    @RequireLogin(value = false)
    public View on_subscribe() throws Exception{
        String payload = StringUtil.read(getPath().getInputStream());
        JSONObject object = (JSONObject) JSONValue.parseWithException(payload);

        Subscriber registry = NetworkManager.getInstance().getNetworkAdaptor().getRegistry();

        if (registry.getEncrPublicKey() == null){
            throw new RuntimeException("Could not find registry keys for " + registry);
        }



        PrivateKey privateKey = Crypt.getInstance().getPrivateKey(Request.ENCRYPTION_ALGO,NetworkManager.getInstance().getLatestKey(CryptoKey.PURPOSE_ENCRYPTION).getPrivateKey());
        

        PublicKey registryPublicKey = Request.getEncryptionPublicKey(registry.getEncrPublicKey());

        KeyAgreement agreement = KeyAgreement.getInstance(Request.ENCRYPTION_ALGO);
        agreement.init(privateKey);
        agreement.doPhase(registryPublicKey,true);

        SecretKey key = agreement.generateSecret("TlsPremasterSecret");

        JSONObject output = new JSONObject();
        output.put("answer", Crypt.getInstance().decrypt((String)object.get("challenge"),"AES",key));

        return new BytesView(getPath(),output.toString().getBytes(),MimeType.APPLICATION_JSON);
    }

}
