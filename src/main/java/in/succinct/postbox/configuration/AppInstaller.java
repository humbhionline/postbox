package in.succinct.postbox.configuration;

import com.venky.swf.configuration.Installer;
import in.succinct.postbox.util.NetworkManager;

public class AppInstaller implements Installer {
    
    public void install() {
        NetworkManager.getInstance().subscribe();
    }
}

