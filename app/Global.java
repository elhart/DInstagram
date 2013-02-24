

import java.util.Date;

import models.AppLogger;
import controllers.DInstagramController;
import play.*;

public class Global extends GlobalSettings {

  @Override
  public void onStart(Application app) {
	AppLogger.addNew(new AppLogger(DInstagramController.appName, "APP START", new Date().toString(), "", "null"));
    Logger.info("Glogal: OnStart: Application has started...");
    //Logger.info("Glogal: OnStart: Starting INITInstagram...");
    DInstagramController.initInstagram();
  }  
  
  @Override
  public void onStop(Application app) {
	AppLogger.addNew(new AppLogger(DInstagramController.appName, "APP END", new Date().toString(), "", "null"));
    Logger.info("Global: OnStop: Application shutdown...");
    
  }  
    
}