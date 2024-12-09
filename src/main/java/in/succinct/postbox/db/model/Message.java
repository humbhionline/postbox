package in.succinct.postbox.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.COLUMN_SIZE;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.indexing.Index;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.User;

import java.io.InputStream;
import java.io.Reader;

public interface Message extends Model {

    @IS_NULLABLE(value = false)
    public Long getChannelId();
    public void setChannelId(Long id);
    public Channel getChannel();
    
    
    @IS_NULLABLE(false)
    @UNIQUE_KEY
    String getMessageId();
    void setMessageId(String messageId);
    
    
    
    @IS_NULLABLE(value = false)
    Long getOwnerId();
    void setOwnerId(Long id);
    User getOwner();
    
    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    @Index
    boolean isArchived();
    void setArchived(boolean archived);
    

    @COLUMN_SIZE(2048)
    public String getHeaders();
    public void setHeaders(String headers);


    public Reader  getPayLoad();
    public void setPayLoad(Reader inputStream);


    @COLUMN_DEF(StandardDefault.ZERO)
    public long getExpiresAt();
    public void setExpiresAt(long expiresAt);

}
