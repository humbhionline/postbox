package in.succinct.postbox.controller;

import com.venky.core.collections.SequenceSet;
import com.venky.swf.controller.ModelController;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.AsyncTaskManagerFactory;
import com.venky.swf.plugins.background.core.DbTask;
import com.venky.swf.pm.DataSecurityFilter;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.View;
import in.succinct.postbox.db.model.Channel;
import in.succinct.postbox.db.model.Message;
import in.succinct.postbox.db.model.User;
import org.json.simple.JSONObject;

import java.io.InputStreamReader;
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
        
        Select select = new Select().from(Channel.class);
        select.where(new Expression(select.getPool(), "NAME", Operator.LK,
                getPath().getSessionUser().getRawRecord().getAsProxy(User.class).getProviderId() + "%"));
        SequenceSet<Long> ids = DataSecurityFilter.getIds(select.execute());
        if (!ids.isEmpty()) {
            where.add(new Expression(getReflector().getPool(), "CHANNEL_ID", Operator.IN, ids.toArray()));
        }else {
            where.add(new Expression(getReflector().getPool(), "CHANNEL_ID", Operator.EQ));
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
    
    
}
