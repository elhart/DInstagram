package models;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;

import play.db.ebean.Model;

@Entity
public class TagInfo extends Model{

	private static final long serialVersionUID = 1L;
	
	@Id
	public Long id;	
	public String tagName;
	public Long numberOfImages;
	
	public static Finder<Long, TagInfo> find = new Finder<Long, TagInfo>(Long.class, TagInfo.class);

	
	public static List<TagInfo> all() {
		return find.all();
	}
	
	public static TagInfo addNew(TagInfo dl) {
		dl.save();
		return dl;
	}
	
	//	Deletes a tag with a given id
	public static void delete(Long id) {
		find.ref(id).delete();
	}
	
	
	public static TagInfo get(Long id){
		return find.ref(id);
	}
	
	public static boolean contains(Long id){
		TagInfo tag = TagInfo.find.byId(id);
		if(tag == null){
			return false;
		}else{
			return true;
		}//if else
	}
	
	public static boolean contains(String t){
		TagInfo tag = TagInfo.find.byId((long)1);
		if(tag.tagName.equals(t)){
			return true;
		}else{
			return false;
		}//if else
	}
	
	public TagInfo(String tagName, Long numberOfImages) {
		this.id = (long) 1;
		this.tagName = tagName;
		this.numberOfImages = numberOfImages;
	}
	
	
}
