package in.succinct.postbox.db.model;

import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;

public interface User extends com.venky.swf.plugins.collab.db.model.user.User {
    
    @UNIQUE_KEY(value = "PROVIDER",allowMultipleRecordsWithNull = false)
    String getProviderId();
    void setProviderId(String providerId);
    
    public static User getProvider(String providerId){
        User provider = Database.getTable(User.class).newRecord();
        provider.setProviderId(providerId);
        provider = Database.getTable(User.class).getRefreshed(provider);
        return provider;
    }
}
