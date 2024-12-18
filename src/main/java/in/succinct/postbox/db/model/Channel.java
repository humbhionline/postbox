package in.succinct.postbox.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.pm.PARTICIPANT;
import com.venky.swf.db.annotations.column.ui.PROTECTION;
import com.venky.swf.db.annotations.model.MENU;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.User;

import java.util.List;

@MENU("Channel")
public interface Channel extends Model {

    @UNIQUE_KEY
    @PROTECTION
    public String getName();
    public void setName(String name);
    

    @COLUMN_DEF(StandardDefault.ZERO)
    public long getExpiryMillis();
    public void setExpiryMillis(long expiryMillis);

    List<Message> getMessages();
}
