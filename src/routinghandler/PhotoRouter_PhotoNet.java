package routinghandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Date;

import DataHandler.Constant;
import DataHandler.Metadata;
import DataHandler.Metadata.Photo;
import DataHandler.ChoosePhoto.PhotoGroupSequence;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.SimClock;
import report.PhotoReport;
import routing.PhotoRouter;
import routing.PhotoRouter.MessageStatus;

public class PhotoRouter_PhotoNet {
	
	//final long histCompTime = 8800000000;
	long histCompTime = 8800000*1000;
	PhotoRouter router;	
	public ArrayList<ClusterPivot> clusterPivots;
	HashMap<Metadata.Photo, Long> timestampMap;
	
	public PhotoRouter_PhotoNet (PhotoRouter router) {
		this.router = router;
	}
	
	public void Initialize() {
		this.clusterPivots = new ArrayList<ClusterPivot>();		
		this.timestampMap = new HashMap<Metadata.Photo, Long>();
	}
	
	public class ClusterPivot implements Cloneable {
		int locx;
		int locy;
		int angle;
		
		public ClusterPivot() {
			locx = 0;
			locy = 0;
			angle = 0;
		}
		
		public ClusterPivot clone() {
            try {
				return (ClusterPivot) super.clone();
			} catch (CloneNotSupportedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}
	}
	
	public void messageTransferred(Message msg, DTNHost from) {
		//case 1: sender = relay, receiver = relay
		if(from.toString().startsWith("v") == false && router.getHost().toString().startsWith("v") == false) {
			long startTime = router.getUserTime();
			messageTransferredRelayToRelay(msg, from);
			long endTime = router.getUserTime();
			long diffTime = endTime - startTime;
			PhotoReport.algorithmTime += diffTime;
		}			
		//case 2: sender = relay, receiver = server
		else if(from.toString().startsWith("v") == false && router.getHost().toString().startsWith("v") == true) {
			messageTransferredRelayToServer(msg, from);
		}			
		//case 2: sender = server, receiver = relay
		else if(from.toString().startsWith("v") == true && router.getHost().toString().startsWith("v") == false) {
			messageTransferredServerToRelay(msg, from);
		}
	}
	
	@SuppressWarnings("unchecked")
	public void messageTransferredRelayToRelay(Message msg, DTNHost from) {
		//if this is the metadata message
		if(msg.getId().startsWith("m")) {			
			//store rcvd metadata from the sender node
			ArrayList<Metadata.Photo> rcvdPhotoList = (ArrayList<Metadata.Photo>) msg.getProperty(Constant.PHOTO_LIST);
			HashMap<Metadata.Photo, Double> sequenceMap = new HashMap<Metadata.Photo, Double>();
			
			for(Metadata.POI poi: router.poiList) {
				ArrayList<Metadata.Photo> myphotos = (ArrayList<Photo>) router.metadata.FilteredPhoto(poi.tid, router.photoList);
				ArrayList<Metadata.Photo> otherphotos = (ArrayList<Photo>) router.metadata.FilteredPhoto(poi.tid, rcvdPhotoList);
				for(Metadata.Photo mphoto: myphotos) {
					if (router.metadata.FindPhotoByID(mphoto.pid, otherphotos) == null) {
						double diffmo = 0;
						for(Metadata.Photo ophoto: otherphotos) {
							double diffL = Math.pow(mphoto.ploc.x - ophoto.ploc.x, 2) + Math.pow(mphoto.ploc.y - ophoto.ploc.y, 2);
							double alpha = Math.exp(-10000 / diffL);
							diffmo += alpha * diffL + (1-alpha) * Math.pow(mphoto.direction - ophoto.direction, 2);
						}
						int size = otherphotos.size() * (otherphotos.size() - 1);
						double diversity = size == 0? 0: diffmo / size;
						sequenceMap.put(mphoto, diversity);
						
						PhotoReport.algorithmTime += histCompTime;
					}
				}
			}
			
			//sort the photo sequence
		    List<Entry<Metadata.Photo, Double>> sortedPhotos = new ArrayList<Entry<Metadata.Photo, Double>>(sequenceMap.entrySet());
		    Collections.sort(sortedPhotos, 
	            new Comparator<Entry<Metadata.Photo, Double>>() {
	                @Override
	                public int compare(Entry<Metadata.Photo, Double> e1, Entry<Metadata.Photo, Double> e2) {
	                    return e2.getValue().compareTo(e1.getValue());
	                }
	            }
		    );
		    
		    router.sendPhotoSeqMap.put(from.getAddress(), new ArrayList<Metadata.Photo>());
		    for(Entry<Metadata.Photo, Double> entry: sortedPhotos) {
		    	Metadata.Photo photo = entry.getKey();
		    	router.sendPhotoSeqMap.get(from.getAddress()).add(photo.clone());
		    }				

		    router.messageStatus.get(from.getAddress()).rcvdMetadata = true;
		}
		
		//if this is the photo message
		else if(msg.getId().startsWith("p")) {
			
			String msgId = (String)msg.getProperty(Constant.PHOTO_ID);
			int photoId = Integer.parseInt(msgId.split("_")[1]);
			
			Metadata.Photo photo = (Metadata.Photo) msg.getProperty(Constant.PHOTO_CONTENT);
			if(photo != null) { //if sender able to send the photo content
				
				if(router.photoList.size() == this.router.photoLimit) {	
					List<Entry<Metadata.Photo, Long>> sortedPhotos = new ArrayList<Entry<Metadata.Photo, Long>>(this.timestampMap.entrySet());
				    Collections.sort(sortedPhotos, 
			            new Comparator<Entry<Metadata.Photo, Long>>() {
			                @Override
			                public int compare(Entry<Metadata.Photo, Long> e1, Entry<Metadata.Photo, Long> e2) {
			                    return e1.getValue().compareTo(e2.getValue());
			                }
			            }
				    );
				    Metadata.Photo replacable = sortedPhotos.get(0).getKey();
				    router.photoList.remove(replacable);
				    this.timestampMap.remove(replacable);
				}			
				ArrayList<Metadata.Photo> newPhotoList = new ArrayList<Metadata.Photo>();
				newPhotoList.add(photo);
				long startTime = router.getUserTime();
				router.metadata.ModifyUndefinedPOI(router.poiList, router.photoList, newPhotoList);
				long endTime = router.getUserTime();
				long diffTime = endTime - startTime;
				PhotoReport.additionalPoiTime += diffTime;
				
				//if photo is not there, store it
				Metadata.Photo p = router.metadata.FindPhotoByID(photoId, router.photoList);
				if(p == null) {
					photo.exist = true;	
					router.photoList.add(photo);
					this.timestampMap.put(photo, new Date().getTime());
				}
				
				ArrayList<Metadata.POI> pois = new ArrayList<Metadata.POI>();
				if (photo.tid != -1) {
					pois.add(router.metadata.FindPoiByID(photo.tid, router.poiList));
				}
				else {
					for(Integer uid: photo.uid) {
						pois.add(router.metadata.FindPoiByID(uid, router.poiList));
					}
				}
				for(Metadata.POI poi: pois) {
					int lowerDirAngle = router.metadata.GetLowerDirAngle(poi, photo);
					if(poi.cvgDetail.contains(lowerDirAngle) == false) {
						poi.cvgDetail.add(lowerDirAngle);
						poi.cvgTotal = router.metadata.TotalCoverage(poi.cvgDetail);
					}
				}
				
			}
		}
	}
	
	public void messageTransferredRelayToServer(Message msg, DTNHost from) {
		//if this is the metadata message
		if(msg.getId().startsWith("m")) {
			@SuppressWarnings("unchecked")
			ArrayList<Metadata.Photo> rcvdPhotoList = (ArrayList<Metadata.Photo>) msg.getProperty(Constant.PHOTO_LIST);
			final ArrayList<Metadata.Photo> rcvdNonDeliveredPhotos = (ArrayList<Metadata.Photo>) router.metadata.FilteredNonDeliveredPhoto(rcvdPhotoList);
			
			router.sendPhotoSeqMap.put(from.getAddress(), new ArrayList<Metadata.Photo>());
		    for(Metadata.Photo photo: rcvdNonDeliveredPhotos) {
		    	Metadata.Photo p = router.metadata.FindPhotoByID(photo.pid, PhotoReport.photoList);
		    	if(p==null) {
		    		router.sendPhotoSeqMap.get(from.getAddress()).add(photo.clone()); 
		    	}
		    }		    
		    router.messageStatus.get(from.getAddress()).rcvdMetadata = true;
		}
		//if this is the photo message
		else if(msg.getId().startsWith("p")) {
			PhotoReport.PhotoAtDestinationCounter++;
			
			String msgId = (String)msg.getProperty(Constant.PHOTO_ID);
			int photoId = Integer.parseInt(msgId.split("_")[1]);
			Metadata.Photo photo = (Metadata.Photo) msg.getProperty(Constant.PHOTO_CONTENT);
			ArrayList<Metadata.Photo> newPhotoList = new ArrayList<Metadata.Photo>();
			newPhotoList.add(photo);
			router.metadata.ModifyUndefinedPOI(PhotoReport.poiList, PhotoReport.photoList, newPhotoList);
			
			
			Metadata.Photo p = router.metadata.FindPhotoByID(photoId, PhotoReport.photoList);

			if(p == null) {
				photo.exist = true;	
				PhotoReport.photoList.add(photo);
			}
			
			ArrayList<Metadata.POI> pois = new ArrayList<Metadata.POI>();
			if (photo.tid != -1) {
				pois.add(router.metadata.FindPoiByID(photo.tid, PhotoReport.poiList));
			}
			else {
				for(Integer uid: photo.uid) {
					pois.add(router.metadata.FindPoiByID(uid, PhotoReport.poiList));
				}
			}		
			
			for(Metadata.POI poi: pois) {
				int lowerDirAngle = router.metadata.GetLowerDirAngle(poi, photo);
				if(poi.cvgDetail.contains(lowerDirAngle) == false) {
					poi.cvgDetail.add(lowerDirAngle);
					poi.cvgTotal = router.metadata.TotalCoverage(poi.cvgDetail);
					
					if(photo.tid == -1) {
						Metadata.POI hpoi = router.metadata.FindPoiByID(photo.hid, PhotoReport.hiddenPoiList);
						int ldr = router.metadata.GetLowerDirAngle(hpoi, photo);
						if(hpoi.cvgDetail.contains(ldr) == false) {
							hpoi.cvgDetail.add(ldr);
						}
						hpoi.cvgTotal = router.metadata.TotalCoverage(hpoi.cvgDetail);
						if(hpoi.cvgTotal >= 180) {
							int index = (PhotoReport.hiddenPoiList.indexOf(hpoi)+1) + PhotoReport.actualPoiList.size();
							if(PhotoReport.halfCoverageTime.containsKey(index) == false) {
								PhotoReport.halfCoverageTime.put(index, SimClock.getTime());
							}
						}
					}
					else {
						if(poi.cvgTotal >= 180) {
							int index = PhotoReport.poiList.indexOf(poi)+1;
							if(PhotoReport.halfCoverageTime.containsKey(index) == false) {
								PhotoReport.halfCoverageTime.put(index, SimClock.getTime());
							}
						}
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void messageTransferredServerToRelay(Message msg, DTNHost from) {
		//if this is the sequence message
		if(msg.getId().startsWith("s")) {
			router.rcvdPhotoSeqMap.put(from.getAddress(), (ArrayList<Metadata.Photo>)msg.getProperty(Constant.PHOTO_REQ));			
			router.messageStatus.get(from.getAddress()).rcvdSequence = true;
		}
	}
}
