package controllers;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import models.AppLogger;
import models.LocalInfo;
import models.MImages;
import models.TagInfo;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;
import org.jinstagram.Instagram;
import org.jinstagram.entity.comments.CommentData;
import org.jinstagram.entity.common.Caption;
import org.jinstagram.entity.common.Comments;
import org.jinstagram.entity.common.ImageData;
import org.jinstagram.entity.common.Images;
import org.jinstagram.entity.common.Likes;
import org.jinstagram.entity.common.Location;
import org.jinstagram.entity.common.Pagination;
import org.jinstagram.entity.common.User;
import org.jinstagram.entity.locations.LocationSearchFeed;
import org.jinstagram.entity.tags.TagInfoData;
import org.jinstagram.entity.tags.TagInfoFeed;
import org.jinstagram.entity.tags.TagMediaFeed;
import org.jinstagram.entity.tags.TagSearchFeed;
import org.jinstagram.entity.users.feed.MediaFeed;
import org.jinstagram.entity.users.feed.MediaFeedData;
import org.jinstagram.exceptions.InstagramException;
import org.jinstagram.model.QueryParam;

import play.*;
import play.libs.Json;
import play.libs.F.Callback;
import play.libs.F.Callback0;
import play.mvc.*;
import play.mvc.WebSocket.Out;

public class DInstagramController extends Controller {
	
	//CHANGE appName, wsAddress
	public static String appName = "DInstagram"; //internal app name - do not change!
	public static String appDisplayName = " Insta-Stream";
	//!!!changing the tag will delete all images (with old tag) from the db
	public static String appDisplayNameTag = "orientaTI13";
	public static String wsAddress = "ws://pdnet.inf.unisi.ch:7003/dinstagram/socket";
	//public static String wsAddress = "ws://localhost:7002/dinstagram/socket";
	
	static int maxNumberOfImagesInst = 50;		//max number of instagram images to send to clients
	static int maxNumberOfImagesLoca = 30;		//max number of local images to send to clients
	
	static Instagram instagram = null;
	
	static List<MImages> hotImages = null;
	
	static String urlPath = "http://pdnet.inf.unisi.ch/dphotobooth/images/";
	//-----------
	
	//display size can be: small(600x1080), big(1320x1080), fullscreen(1920x1080)
	//display size: big(1320x1080)
	
	public static Result index(String displayID, String size) {
		Logger.info(appName+".displayConnecting, displayId: "+displayID+" ,size: "+size);
		AppLogger.addNew(new AppLogger(appName, "displayConnecting", new Date().toString(), "", "null"));
		if(displayID == null) displayID = "99";
		if(size == null) size = "big";
		return ok(views.html.dinstagram.render(appDisplayNameTag, appDisplayName, displayID, wsAddress, size));
	}//index()
  
	
	//----------------------------------------------------------------
	//---- Tag -------------------------------------------------------
	
	public static Long checkNumberOfNewImages(Instagram instagram, String tag){	
		Long nImages = getNumberOfImagesByTag(instagram, tag);
		TagInfo tagindb = TagInfo.get((long) 1);

		return nImages - tagindb.numberOfImages;
	}
	
	public static void displayTagInfo(){
		TagInfo tagindb = TagInfo.get((long) 1);
		
		Logger.info("		tag_name  : " + tagindb.tagName);
		Logger.info("		tag_count : " + tagindb.numberOfImages);
		
	}//displayTagInfo
	
	public static Long getNumberOfImagesByTag(Instagram instagram, String tag){
		//Get information about a tag object.
		TagInfoFeed tagFeed = null;
		try {
			tagFeed = instagram.getTagInfo(appDisplayNameTag);
		} catch (InstagramException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		TagInfoData tagData = tagFeed.getTagInfo();
		
		return tagData.getMediaCount();
	}//getNumberOfImagesByTag
	
	public static void tagWatchDog(Instagram instagram){
		Logger.info("  --tag watchdog");
		
		Long nImages = checkNumberOfNewImages(instagram, appDisplayNameTag);
		
		if(nImages > 0){ //there are new images to take care of
			Logger.info("    number of new images: "+nImages);
			
			//get the first page of media feeds (images)
			TagMediaFeed mediaFeed = getMediaFeed(instagram);
			List<MediaFeedData> images = mediaFeed.getData();
		
			//TODO: support adding new images from other pages
			//limit number of new images to the first page
			if(nImages > images.size()){ //number of new images excides the first page
				nImages = (long)images.size();
				Logger.info("    number of limited images: "+nImages);
			}
			
			//TODO check if the image is already in db, double images problem!!!
			//for each new image from the first page
			for(int i=(int)(nImages-1); i>=0; i--){
				//add new images to db
				MediaFeedData image = images.get(i);
				Caption caption = image.getCaption();
				MImages newDbImage = new MImages(image.getId(), image.getImages().getLowResolution().getImageUrl(), "instagram", caption.getFrom().getFullName(), caption.getFrom().getProfilePicture(), image.getCreatedTime(), (long)image.getLikes().getCount(), (long)0);
				MImages.addNew(newDbImage);
				Logger.info("    add image to db: index: "+i+" id: "+image.getId()+" from: "+caption.getFrom().getFullName());
				
				//send the new image to clients
				sendImageToClients(newDbImage);
			}
			
			//update the tag count
			TagInfo.delete((long) 1); 
			Logger.info("		update the tag +");
			TagInfo.addNew(new TagInfo(appDisplayNameTag,getNumberOfImagesByTag(instagram, appDisplayNameTag)));
			displayTagInfo();
			
		}else if(nImages < 0){
			//TODO
			//some images have been deleted
			//for now keep them in the db, later consider deleting them
			
			//update the tag
			TagInfo.delete((long) 1); 
			Logger.info("		update the tag -");
			TagInfo.addNew(new TagInfo(appDisplayNameTag,getNumberOfImagesByTag(instagram, appDisplayNameTag)));
			displayTagInfo();
		}
		
		Logger.info("  --tag watchdog---");
	}//tagWatchDog()
	
	public static void checkTag(Instagram instagram){
		Logger.info("----TAG-----------------------");
				
		//check if the tag is in db
		if(TagInfo.contains((long) 1)){
			//tag found, check if  it's the right tag
			if(TagInfo.contains(appDisplayNameTag)){
				//tag is already in db, start the tag scheduler (tagWatchdog)
				Logger.info("		tag is in the db");
				displayTagInfo(); //tag info in the db
				
				//display local folder path
				Logger.info("		local folder: "+urlPath);
				
			}else{
				//old tag is in the db, update it with the new one
				Logger.info("		old tag is in the db, update the old tag");
				//delete the old tag, add the new tag
				Logger.info("		delete old tag");
				TagInfo.delete((long) 1); 
				Logger.info("		add the new tag");
				TagInfo.addNew(new TagInfo(appDisplayNameTag,getNumberOfImagesByTag(instagram, appDisplayNameTag)));
				displayTagInfo();
				
				//delete old images from the image db
				Logger.info("		delete old images");
				deleteAllImagesFormDb();
				//add new images to the db
				Logger.info("		add the new images");
				addImagesToDB(instagram);
				
				//local
				//add new local folder info
				Logger.info("		delete old local info");
				LocalInfo.delete((long)1);
				Logger.info("		add new local info");
				LocalInfo.addNew(new LocalInfo(urlPath,getNumberOfLocalImages()));
				Logger.info("         localPath: "+LocalInfo.get((long)1).urlPath+" nImages: "+LocalInfo.get((long)1).numberOfImages);
				//add loacal images to the db
				addLocalImagesToDb();
				
				
			}	
		}else{
			//add the new tag to db
			Logger.info("		tag is not in the db");
			Logger.info("		add the new tag");
			TagInfo.addNew(new TagInfo(appDisplayNameTag,getNumberOfImagesByTag(instagram, appDisplayNameTag)));
			displayTagInfo();
			
			//add images of the new tag to the db 
			Logger.info("		add the new images");
			addImagesToDB(instagram);
			
			//local
			//add local folder info
			Logger.info("		add new local info");
			LocalInfo.addNew(new LocalInfo(urlPath,getNumberOfLocalImages()));
			Logger.info("         localPath: "+LocalInfo.get((long)1).urlPath+" nImages: "+LocalInfo.get((long)1).numberOfImages);
			//add loacal images to the db
			addLocalImagesToDb();
		}
		
	}//checkTag
	
	//----------------------------------------------------------------
	//---- Tag Scheduler----------------------------------------------
	
	//start the scheduler from checkTag();
	public static void startTagScheduler(){
		Logger.info("----Tag.scheduler() ---- START SCHEDULER ---");
		final ScheduledFuture<?> beeperHandle = scheduler.scheduleAtFixedRate(beeper, 10, 7, SECONDS);
			//stops the scheduler after the specified time limit
			//scheduler.schedule(new Runnable() {
			//	public void run() { beeperHandle.cancel(true); }
			//}, 1, TimeUnit.DAYS);
	}

	//should end with the application
	public static void stopTagScheduler(){
		Logger.info("----Tag.scheduler() ---- STOP SCHEDULER ---");
		scheduler.shutdown();
	}

	public static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	final static Runnable beeper = new Runnable() {
		public void run() { 
			
			Logger.info("----Tag.scheduler(): check for new images");
			tagWatchDog(instagram);
			//localWatchDog();
			Logger.info("-----------------------------------------");
		
		}//run
	};//bipper
	
	
	//----------------------------------------------------------------
	
	//----------------------------------------------------------------
	//---- Media Information------------------------------------------
	
	public static TagMediaFeed getMediaFeed(Instagram instagram){
		Logger.info("  --Media feed-----------------");
		
		TagMediaFeed mediaFeed = null;
		try {
			mediaFeed = instagram.getRecentMediaTags(appDisplayNameTag);
			
		} catch (InstagramException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return mediaFeed;
	}//getMediaFeed
	
	public static TagMediaFeed getNextMediaFeed(Instagram instagram, String nextMaxId){
		Logger.info("  --Media feed next-------------");
		
		TagMediaFeed mediaFeed = null;
		try {
			//add pagination
			Map<String, String> params = new HashMap<String, String>();

			params.put(QueryParam.MAX_ID, nextMaxId);
			
			//mediaFeed1 = instagram.getRecentMediaTags(tagName);			
			mediaFeed = instagram.getRecentMediaTagsWithParams(appDisplayNameTag, params);
	
		} catch (InstagramException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return mediaFeed;
	}//getNextMediaFeed
	
	public static String getPagination(TagMediaFeed mediaFeed){		
		Pagination pa = mediaFeed.getPagination();
		
		if(pa != null){
			Logger.info("		Pagination.getMinTagId : " + pa.getMinTagId());
			Logger.info("		Pagination.getNextMinId : " + pa.getNextMinId());
			Logger.info("		Pagination.getNextMaxId : " + pa.getNextMaxId()); 
			Logger.info("		Pagination.getNextMaxTagId : " + pa.getNextMaxTagId());
			Logger.info("		Pagination.getNextUrl : " + pa.getNextUrl());
			Logger.info("		----------------------------------------------");
		}//if
		
		return pa.getNextMaxId();
	}//getPagination
	
	//----------------------------------------------------------------
	//---- Send Image(s) to Client(s)---------------------------------
	
	public static void sendHotImagesToClient(WebSocket.Out<JsonNode> out, String clientId){
		Logger.info("----Send hot images to client: "+clientId);
		
		if(hotImages != null){
			for(int i=0; i<4; i++){
				sendImage(hotImages.get(i), out, "hotImage");
			}//for
		}//if
//		List<MImages> images = new ArrayList<MImages>();
//		images = MImages.all();
//		
//		//find 4 images with the most likes
//		Collections.sort(images, new Comparator<MImages>() {
//			public int compare(MImages im1, MImages im2) {
//		        Long nLikes1 = im1.numberOfLikesIns+im1.numberOfLikesLocall;
//		        Long nLikes2 = im2.numberOfLikesIns+im2.numberOfLikesLocall;
//		    	return nLikes1.compareTo(nLikes2);
//		    }
//		});
//		
//		for(int i=images.size()-1; i>images.size()-5; i--){
//			sendImage(images.get(i), out, "hotImage");
//		}//for
		
		//for(MImages image : images) {
		//	Logger.info("		image id: "+image.id+" idIns: "+image.idInstagram+" author: "+image.authorName+" nlikes: "+(image.numberOfLikesIns+image.numberOfLikesLocall));		
		//}//for
		
		Logger.info("-----------------------------");
		
	}//sendHotImagesToClient
	
	public static void sendHotImagesToClients(){
		//tagMediaFeed is an image container
		Logger.info("  --Send hot images to clients");
		//send to all clients
		Set<?> set = displaySockets.entrySet();
		// Get an iterator
		Iterator<?> i = (Iterator<?>) set.iterator();
		// Display elements
		while(i.hasNext()) { //send images to all connected clients
			Map.Entry ds = (Map.Entry)i.next();
			Logger.info("		sand images to displayID="+ds.getKey());
			if(hotImages != null){
				for(int j=0; j<4; j++){
					sendImage(hotImages.get(j), displaySockets.get(ds.getKey()).wOut, "hotImageReordered"+j);
				}//for
			}//if
		}//while 
	}//sendHotImagesToClients
	
	public static void sendImagesToClient(WebSocket.Out<JsonNode> out, String clientId){
		Logger.info("----Send imageS to client: "+clientId);
		List<MImages> imageList = new ArrayList<MImages>();
		imageList = MImages.all();
		
		//send maxNumberOfImagesInst to the client
		int limit = 0;
		if(maxNumberOfImagesInst < imageList.size()){
			limit = imageList.size() - maxNumberOfImagesInst; 
		}
		for(int i=imageList.size()-1; i >= limit; i--){
			sendImage(imageList.get(i), out, "initImage");
		}//for
		
		Logger.info("-----------------------------");
	}//sendImagesToClient
	
	public static void sendImageToClients(MImages mImages){
		//tagMediaFeed is an image container
		Logger.info("  --Send image to clients");
		//send to all clients
		Set<?> set = displaySockets.entrySet();
		// Get an iterator
		Iterator<?> i = (Iterator<?>) set.iterator();
		// Display elements
		while(i.hasNext()) { //send image to all connected clients
			Map.Entry ds = (Map.Entry)i.next();
			Logger.info("		sand image to displayID="+ds.getKey());
			sendImage(mImages, displaySockets.get(ds.getKey()).wOut, "newImage");
		}//while 
	}//sendImageToClients
	
	private static void sendImage(MImages mImages, Out<JsonNode> out, String msgType) {
		if(mImages != null){
			Logger.info("		image id: "+mImages.idInstagram+" autor's name: "+mImages.authorName);
			ObjectNode msg = Json.newObject();
			msg.put("kind", msgType);
			msg.put("id", mImages.idInstagram);
			msg.put("stdImageUrl", mImages.url);
			msg.put("createdTime", mImages.timeCreated);
			msg.put("authorFullName", mImages.authorName);
			msg.put("authorPic", mImages.authorPic);
			msg.put("nLikes", mImages.numberOfLikesIns + mImages.numberOfLikesLocall);
			//msg.put("uLikes", feed.getLikes().getLikesUserList().toString());
			//msg.put("nComments", feed.getComments().getCount());
			//msg.put("uComments", feed.getComments().getComments().toString());
			
			out.write(msg);
		}//if
		
	}//sendImage
		
	public static void sendImage(MediaFeedData feed, WebSocket.Out<JsonNode> out){

		if(feed != null){
			
			Logger.info("			image id:"+feed.getId());
			
			Caption caption = feed.getCaption();			
			Images images = feed.getImages();
			
			ObjectNode msg = Json.newObject();
			msg.put("kind", "newImage");
			msg.put("id", feed.getId());
			msg.put("createdTime", feed.getCreatedTime());
			if(caption != null){ 
				msg.put("authorFullName", caption.getFrom().getFullName());
				msg.put("authorPic", caption.getFrom().getProfilePicture());
			}
			msg.put("nLikes", feed.getLikes().getCount());
			msg.put("uLikes", feed.getLikes().getLikesUserList().toString());
			msg.put("nComments", feed.getComments().getCount());
			msg.put("uComments", feed.getComments().getComments().toString());
			msg.put("stdImageUrl", images.getStandardResolution().getImageUrl());
			out.write(msg);
		}
		
	}//sendImage

	//----------------------------------------------------------------
	//---- Image Database---------------------------------------------
	public static void addImagesToDB(Instagram instagram){
		Logger.info("  --Add images to db-------------");
		
		TagMediaFeed mediaFeed = getMediaFeed(instagram);
		
		List<MediaFeedData> images = mediaFeed.getData();
		List<MediaFeedData> imagesAll = new ArrayList<MediaFeedData>();
	
		//add first media feeds to imagesAll
		for(int i=0; i<images.size(); i++){
			if(imagesAll.size() < maxNumberOfImagesInst){
				imagesAll.add(0, images.get(i));
			}
		}
			
		//add additional media feed to imagesAll[maxNumberOfImagesInst]
		String nextMaxId = getPagination(mediaFeed);
		while((imagesAll.size() < maxNumberOfImagesInst) && (nextMaxId != null)){
			//get new set of images
			TagMediaFeed nextMediaFeed = getNextMediaFeed(instagram, nextMaxId);
			List<MediaFeedData> imagesNext = nextMediaFeed.getData();
			
			//add  imagesNext to imagesAll
			for(int i=0; i<imagesNext.size(); i++){
				if(imagesAll.size() < maxNumberOfImagesInst){
					imagesAll.add(0, imagesNext.get(i));
				}
			}
			
			nextMaxId = getPagination(nextMediaFeed);
			
			//Logger.info("imagesNext: "+imagesNext.size());
			Logger.info("imagesAll: "+imagesAll.size());
			
		}//while
		
		//print all images and add them to the db
		Logger.info("imagesAll: "+imagesAll.size());		
		if(imagesAll != null){
			int counter = 1;
			for (MediaFeedData image : imagesAll) {
				if(image != null){
					Date d = new Date(Long.parseLong(image.getCreatedTime())*1000);
					Caption caption = image.getCaption();
					Logger.info("   "+(counter++)+"."+" id:"+image.getId()+" time: "+d.toString()+" ");
			
					if(caption != null){
						Logger.info("       from, full name: "+caption.getFrom().getFullName());	
						//add image to db
						MImages.addNew(new MImages(image.getId(), image.getImages().getLowResolution().getImageUrl(), "instagram", caption.getFrom().getFullName(), caption.getFrom().getProfilePicture(), image.getCreatedTime(), (long)image.getLikes().getCount(), (long)0));
					}//if				
				}//if
			}//for			
		}//if
		
	}//addImagesToDB
	
	public static void addLocalImagesToDb(){
		Logger.info("  --Add local images to db-------------");
		
		List<String> images = null;
		
		//get the list of local images
		try {
			images = readLocalFolder();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(images != null){
			for(int i=0; i<images.size();i++){
				Logger.info("    image name: "+images.get(i));
				//add local image to db
				//MImages.addNew(new MImages(image.getId(), image.getImages().getLowResolution().getImageUrl(), "instagram", caption.getFrom().getFullName(), caption.getFrom().getProfilePicture(), image.getCreatedTime(), (long)image.getLikes().getCount(), (long)0));
				Date date = new Date();
				String dateS = ""+date.getTime()/1000;
				MImages.addNew(new MImages(images.get(i), urlPath+images.get(i), "local", "Usi Display", "http://pdnet.inf.unisi.ch/devel/public/logo-usi-67540.gif", dateS, (long)0, (long)0));
			}//for
		}//if
		
		
		
		Logger.info("  ------------------------------------");
	}//addLocalImagesToDb
	
	public static void updateImagesInDb(Instagram instagram){
		Logger.info("    updating images...");
			
	}//updateImagesInDb

	public static void deleteAllImagesFormDb(){
		List<MImages> imageList = new ArrayList<MImages>();
		imageList = MImages.all();
		
		for(int i=0; i<imageList.size(); i++){
			MImages.delete(imageList.get(i).id);
		}//for
		
	}//deleteAllImagesFormDb;
	
	//----------------------------------------------------------------
	//---- Likes------------------------------------------------------
	
	public static void initHotImages(){
		Logger.info("----Init hot images");
		List<MImages> images = new ArrayList<MImages>();
		
		images = MImages.all();
		
		//initialize global list that holds 4 top hot images
		hotImages = new ArrayList<MImages>();
		
		//find 4 images with the most likes
		Collections.sort(images, new Comparator<MImages>() {
			public int compare(MImages im1, MImages im2) {
		        Long nLikes1 = im1.numberOfLikesIns+im1.numberOfLikesLocall;
		        Long nLikes2 = im2.numberOfLikesIns+im2.numberOfLikesLocall;
		    	return nLikes1.compareTo(nLikes2);
		    }
		});
		
		for(int i=images.size()-1; i>images.size()-5; i--){
			hotImages.add(images.get(i));
			Logger.info("		image id: "+images.get(i).id+" idIns: "+images.get(i).idInstagram+" author: "+images.get(i).authorName+" nlikes: "+(images.get(i).numberOfLikesIns+images.get(i).numberOfLikesLocall));			
		}//for
		
		Logger.info("-----------------------------");
	}//initHotImages
	
	public static void updateHotImages(){
		Logger.info("----update hot images");
		List<MImages> images = new ArrayList<MImages>();
		images = MImages.all();
		
		//find 4 images with the most likes
		Collections.sort(images, new Comparator<MImages>() {
			public int compare(MImages im1, MImages im2) {
		        Long nLikes1 = im1.numberOfLikesIns+im1.numberOfLikesLocall;
		        Long nLikes2 = im2.numberOfLikesIns+im2.numberOfLikesLocall;
		    	return nLikes1.compareTo(nLikes2);
		    }
		});
		
		int k = 0;
		boolean update = false;
		for(int i=images.size()-1; i>images.size()-5; i--){
			if(!hotImages.get(k).idInstagram.equals(images.get(i).idInstagram)){ //they are not the same
				update = true;
				hotImages.get(k).id = images.get(i).id;
				hotImages.get(k).idInstagram = images.get(i).idInstagram;
				hotImages.get(k).authorName = images.get(i).authorName;
				hotImages.get(k).authorPic = images.get(i).authorPic;
				hotImages.get(k).numberOfLikesIns = images.get(i).numberOfLikesIns;
				hotImages.get(k).numberOfLikesLocall = images.get(i).numberOfLikesLocall;
				hotImages.get(k).source = images.get(i).source;
				hotImages.get(k).timeCreated = images.get(i).timeCreated;
				hotImages.get(k).url = images.get(i).url;
				Logger.info("		image id: "+images.get(i).id+" idIns: "+images.get(i).idInstagram+" author: "+images.get(i).authorName+" nlikes: "+(images.get(i).numberOfLikesIns+images.get(i).numberOfLikesLocall));			
			}else{ //update only number of likes
				hotImages.get(k).numberOfLikesIns = images.get(i).numberOfLikesIns;
				hotImages.get(k).numberOfLikesLocall = images.get(i).numberOfLikesLocall;
			}//else
			k++;
		}//for
		
		if(update){
			sendHotImagesToClients();
		}
		
		Logger.info("-----------------------------");
		
	}//updateHotImages
	
	public static void updateLikesInDbLocal(String imageId, String displayId){
		Logger.info("  --update local likes in db for image: "+imageId);
		List<MImages> images = new ArrayList<MImages>();
		images = MImages.all();
	
		//find the right image based on imageId
		for(int i=0; i<images.size(); i++){
			MImages image = images.get(i);
			if(image.idInstagram.equals(imageId)){
				Logger.info("    i= "+i+" imageId: "+image.idInstagram+" author: "+image.authorName);
				//update likes in db (local likes +1)
				//TODO find a way to update the filed, not to delete and add the same image
				image.updateNumberOfLikesLocal();
				MImages.delete(image.id);
				MImages.addNew(new MImages(image.idInstagram, image.url, "instagram", image.authorName, image.authorPic, image.timeCreated, image.numberOfLikesIns, image.numberOfLikesLocall));
			}//if
		}//for	
		
		//send the like update to all clients except the source
		sendLikeUpdateToClients(imageId, displayId);
		
		//update the hot images
		updateHotImages();
		
		Logger.info("-----------------------------");
	}//updateLikesInDbLocal
	
	public static void sendLikeUpdateToClients(String imageId, String displayId){
		//tagMediaFeed is an image container
		Logger.info("  --send like update to all connected clients");
		//send to all clients
		Set<?> set = displaySockets.entrySet();
		// Get an iterator
		Iterator<?> i = (Iterator<?>) set.iterator();
		while(i.hasNext()) { //send like update to all connected clients
			Map.Entry ds = (Map.Entry)i.next();
			if(!ds.getKey().equals(displayId)){ //sed to all but not to the source of the update
				Logger.info("    sand like update to displayID="+ds.getKey());
			
				sendLikeUpdate(imageId, displaySockets.get(ds.getKey()).wOut);
			}//if
		}//while 
	}
	
	private static void sendLikeUpdate(String imageId, Out<JsonNode> out){
		
		ObjectNode msg = Json.newObject();
		msg.put("kind", "likeUpdate");
		msg.put("imageId", imageId);
		
		out.write(msg);
		
	}//sendLikeUpdate
	
	//----------------------------------------------------------------
	//---- Local folder------------------------------------------------------
	
	public static Long checkNumberOfNewLocalImages(){	
		Long nImages = getNumberOfLocalImages();
		LocalInfo localindb = LocalInfo.get((long) 1);

		return nImages - localindb.numberOfImages;
	}

	public static Long getNumberOfLocalImages(){
		
		//Logger.info("  --get number of local images ");
		
		List<String> images = null;
		Long nImages = (long)0;
		
		//get the list of local images
		try {
			images = readLocalFolder();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(images != null){
			nImages = (long)images.size();
		}
		//Logger.info("     nImages: "+nImages);
		return nImages;
	}//getNumberOfLocalImages
	
	public static List<String> readLocalFolder() throws IOException{

		Logger.info("  --read local folder ");
		
		//read local folder
		
		List<String> imagesLocal = new ArrayList<String>();
		
		URL url = null;		
		url = new URL(urlPath);
		
		final String HTML_A_TAG_PATTERN = "(?i)<a([^>]+)>(.+?)</a>";
		final String HTML_A_HREF_TAG_PATTERN = "\\s*(?i)href\\s*=\\s*(\"([^\"]*\")|'[^']*'|([^'\">\\s]+))";
	
		
		Pattern patternTag, patternLink;
		Matcher matcherTag, matcherLink;
		
		patternTag = Pattern.compile(HTML_A_TAG_PATTERN);
		patternLink = Pattern.compile(HTML_A_HREF_TAG_PATTERN);
		
        BufferedReader in = null;
		in = new BufferedReader(new InputStreamReader(url.openStream()));
		
        String inputLine;
        
		while ((inputLine = in.readLine()) != null){
			
			matcherTag = patternTag.matcher(inputLine);
			
			if(matcherTag.find()){
				 
				String href = matcherTag.group(1); // href
				String linkText = matcherTag.group(2); // link text
	 
				matcherLink = patternLink.matcher(href);
	 
				if(matcherLink.find()){
	 
					String link = matcherLink.group(1); // link
					if(link.contains(".jpg") || link.contains(".JPG")){
						String l = link;
						l = l.substring(1, l.length()-5);
						imagesLocal.add(l);
						//System.out.println(link);
					}
				}//if
			}//if
			
			//System.out.println(inputLine);
		}
		      
		in.close();
		
		
		
		return imagesLocal;
		
	}//readLocalFolder
	
	public static void localWatchDog(){
		Logger.info("  --local watchdog");
		
		Long nImages = checkNumberOfNewLocalImages();
		
		if(nImages > 0){ //there are new images to take care of
			Logger.info("    number of new images: "+nImages);
			
			List<String> images = null;
			
			//get the list of local images
			try {
				images = readLocalFolder();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			//TODO check if the image is already in db, double images problem!!!
			//for each new image
			for(int i=images.size()-1; i>images.size()-1-nImages; i--){
				Logger.info("    image name: "+images.get(i));
				//add new images to db
				Date date = new Date();
				String dateS = ""+date.getTime()/1000;
				MImages newDbImage = new MImages(images.get(i), urlPath+images.get(i), "local", "Usi Display", "http://pdnet.inf.unisi.ch/devel/public/logo-usi-67540.gif", dateS, (long)0, (long)0);
				MImages.addNew(newDbImage);
			
				//send the new image to clients
				sendImageToClients(newDbImage);
			}
			
			//update the tag count
			LocalInfo.delete((long) 1); 
			Logger.info("		update the local info +");
			LocalInfo.addNew(new LocalInfo(urlPath,getNumberOfLocalImages()));
			
		}else if(nImages < 0){
			//TODO
			//some images have been deleted
			//for now keep them in the db, later consider deleting them
			
			//update the tag
			LocalInfo.delete((long) 1); 
			Logger.info("		update the local info -");
			LocalInfo.addNew(new LocalInfo(urlPath,getNumberOfLocalImages()));
		}
		
		Logger.info("  --local watchdog---");
	}//checkLocalFolder
	
	//----------------------------------------------------------------
	//---- Instagram -------------------------------------------------
	public static void initInstagram(){

		Logger.info("initInstagram:");
		
		String  userId = "ecce8a8cfca242409a5a8dc2de2b8ec4";
		instagram = new Instagram(userId);
		
		checkTag(instagram);
		
		initHotImages();
	   	
		//start service/scheduler that checks for new images
		startTagScheduler();
		
	}//INITInstagram()
		
	//----------------------------------------------------------------
  	//---- WS --------------------------------------------------------
	
	public static HashMap<String, Sockets> displaySockets = new HashMap<String, Sockets>();
	public static HashMap<WebSocket.Out<JsonNode>, String> displaySocketReverter = new HashMap<WebSocket.Out<JsonNode>, String>();
	
	public static WebSocket<JsonNode> webSocket() { 
		return new WebSocket<JsonNode>() {
	
			// Called when the Websocket Handshake is done.
			public void onReady(WebSocket.In<JsonNode> in, final WebSocket.Out<JsonNode> out){
	
				// For each event received on the socket 
				in.onMessage(new Callback<JsonNode>() { 
					public void invoke(JsonNode event) {
	
						String messageKind = event.get("kind").asText();						
						
						//ws message - when client is ready
						if(messageKind.equals("appReady")){
							//save the connection for later use
							if(!displaySockets.containsKey(event.get("displayID"))){
								Logger.info(appName+".Socket(): new displayID= "+event.get("displayID")+" size="+event.get("size"));
								
								//TODO
								//do something with the app on display (displayID)
								//create a new instance of a scheduler for each display client and send content messages to the display client
								
								//Send helloWorld message to connected client
								ObjectNode msg = Json.newObject();
								msg.put("kind", "hello");
								msg.put("text", "Hello World!!!");
								out.write(msg);
								
								String displayid = event.get("displayID").asText();
								if(displayid != "null"){
									//register display
									displaySockets.put(event.get("displayID").asText(), new Sockets(out));
									displaySocketReverter.put(out, event.get("displayID").asText());
									
									
								}//if
								AppLogger.addNew(new AppLogger(appName, "displayNew", "data", new Date().toString(), event.get("displayID").asText()));
								
								//initialize instagram, later move this to the app global
								//DInstagramController.initInstagram();
								
								//send images to the client
								DInstagramController.sendImagesToClient(out, event.get("displayID").asText());
								//send 'hot 4'images
								DInstagramController.sendHotImagesToClient(out, event.get("displayID").asText());
							}//if
	
						}//appReady
	
						//ws message - when client is ending connection
						if(messageKind.equals("appClose")){
							//TODO add to app logger
							Logger.info(appName+".webSocket(): appClose - displayID="+event.get("displayID")+" size="+event.get("size"));
							AppLogger.addNew(new AppLogger(appName, "displayClose", new Date().toString(),"data", event.get("displayID").asText()));
						}
						
						//likePlus event form the client, update image.numberOfLikesLocal
						if(messageKind.equals("likePlus")){
							//TODO add to app logger
							Logger.info(appName+".webSocket(): likePlus event | displayID="+event.get("displayID")+" imageId: "+event.get("imageId").asText());
							//update the db
							DInstagramController.updateLikesInDbLocal(event.get("imageId").asText(), event.get("displayID").asText());
							AppLogger.addNew(new AppLogger(appName, "likePlus", new Date().toString(), event.get("imageId").asText(), event.get("displayID").asText()));
						}
						
						//TODO add more messages from clients such as appReady or appClose as needed
						
	
					}//invoke
				});//in.onMessage
	
				// When the socket connection is closed. 
				in.onClose(new Callback0() {
					public void invoke() { 
						String displayID =displaySocketReverter.get(out);
						displaySocketReverter.remove(out);
						displaySockets.remove(displayID);
						AppLogger.addNew(new AppLogger(appName, "displayDisconect", new Date().toString(),"", displayID));
						Logger.info(appName+".Socket(): display "+displayID+" is disconnected; number of connected displays: "+displaySockets.size());
					}//invoke
				});//in.onClose
	
			}//onReady
		};//WebSocket<String>()
	}//webSocket() { 
	
	public static class Sockets {
		public WebSocket.Out<JsonNode> wOut;
	
		public Sockets(Out<JsonNode> out) {
			this.wOut = out;
		}
	}//class
  
    //WS --------------------------------------------------------------------
  
	
	
//	Logger.info("			 image std url: "+images.getStandardResolution().getImageUrl());
//	Logger.info("			 	width:  "+images.getStandardResolution().getImageWidth());
//	Logger.info("			 	height: "+images.getStandardResolution().getImageHeight());
//	Logger.info("			 image low url: "+images.getLowResolution().getImageUrl());
//	Logger.info("			 	width:  "+images.getLowResolution().getImageWidth());
//	Logger.info("			 	height: "+images.getLowResolution().getImageHeight());
//	Logger.info("			 image thu url: "+images.getThumbnail().getImageUrl());
//	Logger.info("			 	width:  "+images.getThumbnail().getImageWidth());
//	Logger.info("			 	height: "+images.getThumbnail().getImageHeight());
//	

	
//	//search for location
//	Logger.info("    LOCATION------------------------");
//	double latitude = 46.0109;
//	double longitude = 8.95825;
//	LocationSearchFeed searchFeed = null;
//	try {
//		searchFeed = instagram.searchLocation(latitude, longitude);
//	} catch (InstagramException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	}
//
//	for (Location location : searchFeed.getLocationList()) {
//		Logger.info("		id : " + location.getId());
//		Logger.info("		name : " + location.getName());
//		Logger.info("		latitude : " + location.getLatitude());
//		Logger.info("		longitude : " + location.getLongitude());
//	}
	
//	//search media by location
//	Logger.info("    MEDIA------------------------------");
//	MediaFeed feed = null;
//	try {
//		feed = instagram.searchMedia(latitude, longitude);
//	} catch (InstagramException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	} 
//	List<MediaFeedData> feeds = feed.getData();
//	
//	for (MediaFeedData mediaData : feeds) {
//		Logger.info("");
//		Logger.info("			id : " + mediaData.getId());
//		Logger.info("			created time : " + mediaData.getCreatedTime());
//		Logger.info("			link : " + mediaData.getLink());
//		Logger.info("			tags : " + mediaData.getTags().toString());
//		Logger.info("			filter : " + mediaData.getImageFilter());
//		Logger.info("			type : " + mediaData.getType());
//
//		Logger.info("			-- Comments --");
//		Comments comments = mediaData.getComments();
//		List<CommentData> commentList = comments.getComments();
//		if(comments != null){
//			for (CommentData comment : commentList) {
//				if(comment != null){
//					Logger.info("				commentId : "    + comment.getId());
//					Logger.info("				created Time : " + comment.getCreatedTime());
//					Logger.info("				text : " + comment.getText());
//					
//					Logger.info("				** From **");
//					Logger.info("				id : "+ comment.getCommentFrom().getId());
//					Logger.info("				full name : " + comment.getCommentFrom().getFullName());
//					Logger.info("				user name : " + comment.getCommentFrom().getUsername());
//					Logger.info("				profile picture : " + comment.getCommentFrom().getProfilePicture());
//				}//if
//			}//for
//		}//if
//		
//		Logger.info("			-- Caption --");
//		Caption caption = mediaData.getCaption();
//		if (caption != null){
//			Logger.info("				id : "              + caption.getId());
//			Logger.info("				created time : "    + caption.getCreatedTime());
//			Logger.info("				text : "            + caption.getText());
//
//			Logger.info("				** From **");
//			Logger.info("				id : "              + caption.getFrom().getId());
//			Logger.info("				full name : "       + caption.getFrom().getFullName());
//			Logger.info("				user name : "       + caption.getFrom().getUsername());
//			Logger.info("				profile picture : " + caption.getFrom().getProfilePicture());
//			}
//		
//		Logger.info("			-- Likes --");
//		Likes likes = mediaData.getLikes();
//		Logger.info("				number of likes : " + likes.getCount());
//		List<User> userList = likes.getLikesUserList();
//		if(userList != null){
//			for (User user : userList) {
//				if(user != null){
//					Logger.info("				full name : "       + user.getFullName());
//					Logger.info("				user name : "       + user.getUserName()); 
//				}//if
//					
//			}//for
//		}//if
//		
//		Logger.info("			-- Images --");
//		Images images = mediaData.getImages();
//
//		ImageData lowResolutionImg = images.getLowResolution();
//		ImageData highResolutionImg = images.getStandardResolution();
//		ImageData thumbnailImg = images.getThumbnail();
//
//		Logger.info("			-- Location --");
//		Location location = mediaData.getLocation();
//		Logger.info("				id : " + location.getId());
//		Logger.info("				name : " + location.getName());
//		Logger.info("				latitude : " + location.getLatitude());
//		Logger.info("				longitude : " + location.getLongitude());
//	}	
  

	
//	//search for tags
//	Logger.info("	-- Search Tags --");
//	String query = "lugano";
//	TagSearchFeed tagSearchFeed = null;
//	try {
//		tagSearchFeed = instagram.searchTags(query);
//	} catch (InstagramException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	}
//
//	List<TagInfoData> tags = tagSearchFeed.getTagList();
//
//	for (TagInfoData tag : tags) {
//		Logger.info("			name : " + tag.getTagName());
//		Logger.info("			media_count : " + tag.getMediaCount());
//	}
//	
	
	
}