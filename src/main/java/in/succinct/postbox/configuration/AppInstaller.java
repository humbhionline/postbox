package in.succinct.postbox.configuration;

import com.venky.swf.configuration.Installer;
import com.venky.swf.db.Database;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.postbox.db.model.Message;
import in.succinct.postbox.db.model.Order;
import in.succinct.postbox.util.NetworkManager;

import java.util.List;

public class AppInstaller implements Installer {
    
    public void install() {
        summarizeOrders();
        NetworkManager.getInstance().subscribe();
        
    }
    
    private void summarizeOrders() {
        if (Database.getTable(Order.class).recordCount() == 0) {
            Select select = new Select().from(Message.class);
            select.where(new Expression(select.getPool(), "ARCHIVED", Operator.EQ, true))
                    .add(" and not exists (select 1 from orders where orders.message_id = messages.id)");
            List<Message> messageList = select.execute();
            for (Message message : messageList) {
                message.summarize(true);
            }
        }
    }
}

