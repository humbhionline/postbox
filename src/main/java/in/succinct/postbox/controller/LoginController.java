package in.succinct.postbox.controller;

import com.venky.swf.controller.Controller;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.path.Path;
import com.venky.swf.views.RedirectorView;
import com.venky.swf.views.View;

public class LoginController extends Controller {
    public LoginController(Path path) {
        super(path);
    }
    
    @Override
    @RequireLogin(false)
    public View index() {
        return new RedirectorView(getPath(),"/oid","login?SELECTED_OPEN_ID=HUMBOL");
    }
}
