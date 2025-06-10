package in.succinct.postbox.db.model;

import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.model.Model;

public interface SavedAddress extends Model {
    @IS_NULLABLE(value = false)
    @UNIQUE_KEY
    public Long getChannelId();
    public void setChannelId(Long id);
    public Channel getChannel();
    
    @UNIQUE_KEY
    public String getDoor();
    public void setDoor(String door);
    
    @UNIQUE_KEY
    public String getName();
    public void setName(String name);
    
    @UNIQUE_KEY
    public String getBuilding();
    public void setBuilding(String building);
    
    @UNIQUE_KEY
    public String getStreet();
    public void setStreet(String street);
    
    @UNIQUE_KEY
    public String getLocality();
    public void setLocality(String locality);
    
    @UNIQUE_KEY
    public String getLandmark();
    public void setLandmark(String landmark);
    
    @UNIQUE_KEY
    public String getWard();
    public void setWard(String ward);
    
    @UNIQUE_KEY
    public String getCity();
    public void setCity(String city);
    
    @UNIQUE_KEY
    public String getAreaCode();
    public void setAreaCode(String areaCode);
    
    
    @UNIQUE_KEY
    String getPhone();
    void setPhone(String phone);
    
    
    
}
