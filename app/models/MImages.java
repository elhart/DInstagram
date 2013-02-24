package models;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;

import play.db.ebean.Model;

@Entity
public class MImages extends Model{

	private static final long serialVersionUID = 1L;
	
	@Id
	public Long id;						//[1,2,...)
	public String idInstagram;			//[xx_y)
	public String url;					//instagram web address of the image
	public String source;				//{local, instagram}
	public String authorName;			//full name of the image author
	public String authorPic;			//web address of the author's instagram profile picture
	public String timeCreated;			//number of seconds since 1970
	public Long numberOfLikesIns;		//number of likes of the image on Instagram
	public Long numberOfLikesLocall;	//number of likes on the displays
	
	public static Finder<Long, MImages> find = new Finder<Long, MImages>(Long.class, MImages.class);

	
	public static List<MImages> all() {
		return find.all();
	}
	
	public static MImages addNew(MImages dl) {
		dl.save();
		return dl;
	}
	
	//	Deletes a tag with a given id
	public static void delete(Long id) {
		find.ref(id).delete();
	}
	
	
	public static MImages get(Long id){
		return find.ref(id);		
	}
	
	public static int getCount(){
		return find.all().size();
	}
	
	public static boolean contains(Long id){
		MImages tag = MImages.find.byId(id);
		if(tag == null){
			return false;
		}else{
			return true;
		}//if else
	}
	
	public void updateNumberOfLikesIns(){
		this.numberOfLikesIns ++;
	}
	
	public void updateNumberOfLikesLocal(){
		this.numberOfLikesLocall ++;
	}
	
	public MImages(String idInstagram, String url, String source,String authorName, String authorPic, String timeCreated, Long numberOfLikesIns, Long numberOfLikesLocall) {
		this.idInstagram = idInstagram;
		this.url = url;
		this.source = source;
		this.authorName = authorName;
		this.authorPic = authorPic;
		this.timeCreated = timeCreated;
		this.numberOfLikesIns = numberOfLikesIns;
		this.numberOfLikesLocall = numberOfLikesLocall;
		
	}
	
	
}
