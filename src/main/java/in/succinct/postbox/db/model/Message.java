package in.succinct.postbox.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.COLUMN_SIZE;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.pm.PARTICIPANT;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.User;

import java.io.InputStream;

public interface Message extends Model {

    @IS_NULLABLE(value = false)
    public Long getChannelId();
    public void setChannelId(Long id);
    public Channel getChannel();
    
    @IS_NULLABLE(value = false)
    Long getOwnerId();
    void setOwnerId(Long id);
    User getOwner();
    


    @COLUMN_SIZE(2048)
    public String getHeaders();
    public void setHeaders(String headers);


    public InputStream  getPayLoad();
    public void setPayLoad(InputStream inputStream);


    @COLUMN_DEF(StandardDefault.ZERO)
    public long getExpiresAt();
    public void setExpiresAt(long expiresAt);

}
