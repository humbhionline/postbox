package in.succinct.postbox.controller;

import com.venky.core.collections.SequenceSet;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.ModelController;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.path.Path;
import com.venky.swf.pm.DataSecurityFilter;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;
import in.succinct.json.JSONAwareWrapper;
import in.succinct.onet.core.adaptor.NetworkAdaptor;
import in.succinct.postbox.db.model.Channel;
import in.succinct.postbox.db.model.Order;
import in.succinct.postbox.db.model.User;
import in.succinct.postbox.util.NetworkManager;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.simple.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class OrdersController extends ModelController<Order> {
    public OrdersController(Path path) {
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
    public View download_orders(){
        ensureIntegrationMethod(HttpMethod.POST);
        
        int numMonths ;
        boolean includeCurrentMonth ;
        try {
            JSONObject input = JSONAwareWrapper.parse(getPath().getInputStream());
            includeCurrentMonth = getReflector().getJdbcTypeHelper().getTypeRef(boolean.class).getTypeConverter().valueOf(input.getOrDefault("IncludeCurrentMonth",false));
            
            numMonths = getReflector().getJdbcTypeHelper().getTypeRef(int.class).getTypeConverter().valueOf(input.getOrDefault("NumMonths",0));
            if (!includeCurrentMonth){
                numMonths = Math.max(numMonths,1);
            }
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }
        
        long endDate  = includeCurrentMonth ? System.currentTimeMillis() : getCurrentMonthStartDate();
        long startDate = getOrderReportStartDate(numMonths);
        
        
        Select q = new Select().from(in.succinct.postbox.db.model.Order.class);
        
        Expression where = getWhereClause();
        where.add(new Expression(q.getPool(),Conjunction.AND).
                add(new Expression(q.getPool(),"ARCHIVED",Operator.EQ,true)).
                add(new Expression(q.getPool(),"ORDER_CREATED_AT",Operator.GE,new Timestamp(startDate))).
                add(new Expression(q.getPool(),"ORDER_CREATED_AT",Operator.LT,new Timestamp(endDate))));
        
        List<Order> orders =  q.where(where).execute(in.succinct.postbox.db.model.Order.class,0,getFilter());
        
        Workbook wb = new XSSFWorkbook();
        super.exportxls(in.succinct.postbox.db.model.Order.class, wb,orders);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            wb.write(os);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Calendar calendar  = Calendar.getInstance();
        calendar.setTimeInMillis(startDate);
        
        return new BytesView(getPath(), os.toByteArray(), MimeType.APPLICATION_XLSX, "content-disposition", "attachment; filename=" +  "orders-%d-%d.xlsx".formatted(calendar.get(Calendar.MONTH),calendar.get(Calendar.YEAR)));
    }
    
    
    private long getCurrentMonthStartDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.DAY_OF_MONTH,1);
        calendar.set(Calendar.HOUR,0);
        calendar.set(Calendar.MINUTE,0);
        calendar.set(Calendar.SECOND,0);
        calendar.set(Calendar.MILLISECOND,0);
        return calendar.getTimeInMillis();
    }
    private long getOrderReportStartDate(int numMonthsToInclude) {
        long monthStart = getCurrentMonthStartDate();
        
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(monthStart);
        
        for (int i = 0 ; i < numMonthsToInclude; i ++){
            calendar.setTimeInMillis(calendar.getTimeInMillis() - 1L);
            calendar.set(Calendar.DAY_OF_MONTH,1);
            calendar.set(Calendar.HOUR,0);
            calendar.set(Calendar.MINUTE,0);
            calendar.set(Calendar.SECOND,0);
            calendar.set(Calendar.MILLISECOND,0);
        }
        
        return calendar.getTimeInMillis();
    }
    
}
