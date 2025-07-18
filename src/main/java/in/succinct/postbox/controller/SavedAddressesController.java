package in.succinct.postbox.controller;

import com.venky.core.collections.SequenceSet;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.ModelController;
import com.venky.swf.path.Path;
import com.venky.swf.pm.DataSecurityFilter;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.View;
import in.succinct.postbox.db.model.Channel;
import in.succinct.postbox.db.model.SavedAddress;
import in.succinct.postbox.db.model.User;

import java.util.ArrayList;
import java.util.List;

public class SavedAddressesController extends ModelController<SavedAddress> {
    public SavedAddressesController(Path path) {
        super(path);
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
            where.add(addl);
        }
        
        return where;
    }
    
    @Override
    protected String[] getIncludedFields() {
        String[] fields =  super.getIncludedFields();
        if (fields == null){
            List<String> out = getReflector().getVisibleFields(new ArrayList<>());
            out.remove("CHANNEL_ID");
            fields = out.toArray(new String[]{});
        }
        return fields;
    }
}
