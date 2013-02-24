package models;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;

import play.db.ebean.Model;

@Entity
public class LocalInfo extends Model{

	private static final long serialVersionUID = 1L;
	
	@Id
	public Long id;	
	public String urlPath;
	public Long numberOfImages;
	
	public static Finder<Long, LocalInfo> find = new Finder<Long, LocalInfo>(Long.class, LocalInfo.class);

	
	public static List<LocalInfo> all() {
		return find.all();
	}
	
	public static LocalInfo addNew(LocalInfo dl) {
		dl.save();
		return dl;
	}
	
	//	Deletes a tag with a given id
	public static void delete(Long id) {
		find.ref(id).delete();
	}
	
	
	public static LocalInfo get(Long id){
		return find.ref(id);
	}
	
	public static boolean contains(Long id){
		LocalInfo tag = LocalInfo.find.byId(id);
		if(tag == null){
			return false;
		}else{
			return true;
		}//if else
	}
	
	public LocalInfo(String urlPath, Long numberOfImages) {
		this.id = (long) 1;
		this.urlPath = urlPath;
		this.numberOfImages = numberOfImages;
	}
	
	
}
