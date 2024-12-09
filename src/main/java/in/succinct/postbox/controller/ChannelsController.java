package in.succinct.postbox.controller;

import com.venky.swf.controller.ModelController;
import com.venky.swf.path.Path;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import in.succinct.postbox.db.model.Channel;
import in.succinct.postbox.db.model.User;

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
