package in.succinct.postbox.db.model;

import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.validations.Enumeration;

public interface User extends com.venky.swf.plugins.collab.db.model.user.User {
    
    @UNIQUE_KEY(value = "PROVIDER",allowMultipleRecordsWithNull = false)
    String getProviderId();
    void setProviderId(String providerId);
    
    @IS_NULLABLE(false)
    @COLUMN_DEF(value = StandardDefault.SOME_VALUE,args = "test")
    @Enumeration("test,production")
    String getNetworkEnvironment();
    void setNetworkEnvironment(String networkEnvironment);
    
    public static User getProvider(String providerId){
        User provider = Database.getTable(User.class).newRecord();
        provider.setProviderId(providerId);
        provider = Database.getTable(User.class).getRefreshed(provider);
        return provider;
    }
}
