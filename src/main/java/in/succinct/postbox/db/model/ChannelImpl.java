package in.succinct.postbox.db.model;

import com.venky.swf.db.model.User;
import com.venky.swf.db.table.ModelImpl;

public class ChannelImpl extends ModelImpl<Channel> {
    public ChannelImpl(Channel c){
        super(c);
    }
    public Long getAnyUserId(){
        return null;
    }
    public void setAnyUserId(Long id) {
    
    }
    public User getAnyUser() {
        return null;
    }
}
