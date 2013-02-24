package models;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;

import play.db.ebean.Model;

@Entity
public class AppLogger extends Model{

	private static final long serialVersionUID = 1L;
	
	@Id
	public Long id;
	
	public String appName;
	public String event;
	public String timestamp;
	public String content;
	public String displayID;
	
	public static Finder<Long, AppLogger> find = new Finder<Long, AppLogger>(Long.class, AppLogger.class);

	
	public static List<AppLogger> all() {
		return find.all();
	}
	
	public static AppLogger addNew(AppLogger dl) {
		dl.save();
		return dl;
	}
	
	public static AppLogger get(Long id){
		return find.ref(id);
	}
	
	public AppLogger(String appName, String event, String timestamp, String content, String displayID) {
		this.appName = appName;
		this.event = event;
		this.timestamp = timestamp;
		this.content = content;
		this.displayID = displayID;
	}
	
	
}
