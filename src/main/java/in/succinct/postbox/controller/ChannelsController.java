package in.succinct.postbox.controller;

import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.table.Table;
import com.venky.swf.path.Path;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.views.View;
import in.succinct.postbox.db.model.Channel;
import in.succinct.postbox.db.model.User;

import java.util.Set;

public class ChannelsController extends ModelController<Channel> {
    
    
    public ChannelsController(Path path) {
        super(path);
    }
    
    @Override
    protected Expression getWhereClause() {
        Expression where = super.getWhereClause();
        if (getPath().getSessionUserId() != null) {
            where.add(new Expression(getReflector().getPool(), "NAME", Operator.LK,
                    getPath().getSessionUser().getRawRecord().getAsProxy(User.class).getProviderId() + "%"));
        }
        return where;
    }
}
