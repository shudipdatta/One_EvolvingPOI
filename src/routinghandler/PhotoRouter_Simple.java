package routinghandler;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import DataHandler.Constant;
import DataHandler.Metadata;
import core.DTNHost;
import core.Message;
import core.SimClock;
import report.PhotoReport;
import routing.PhotoRouter;

public class PhotoRouter_Simple {
	PhotoRouter router;
	
	public PhotoRouter_Simple (PhotoRouter router) {
		this.router = router;
	}
	/*
	 * 
	 */
	public void Initialize() {
		
	}
	/*
	 * 
	 */
	public Metadata.Photo findLeastImpForeignPhoto(ArrayList<Metadata.Photo> existingPhotoList, Metadata.Photo candidatePhoto) {
		//now calculate the poi sequence
		HashMap<Metadata.POI, Double> priorityMap = new HashMap<Metadata.POI, Double>();
		for(Metadata.POI poi: router.poiList) {
			Double priority = Constant.Beta * poi.numPhoto + Constant.Gamma * poi.avgDist;
			//if(poi.tid > 0) priority *= 4;
			priorityMap.put(poi, priority);
		}
	    
	    //find minimum weighted photo
	    Metadata.Photo replaceable = null;
	    Double minWeight = Constant.Infinite * 1.0;
	    for(Metadata.Photo photo: existingPhotoList) {
	    	if(photo.source != router.getHost().getAddress()) {
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
		    		int cvg = router.metadata.CalculateProbableCoverage(Constant.IF_DELETED, poi, photo);
		    		Double photoWeight = Constant.Phi * priorityMap.get(poi) + (1-Constant.Phi) * cvg;
		    		if(photoWeight < minWeight) {
		    			minWeight = photoWeight;
		    			replaceable = photo;
		    		}
				}
	    	}
	    }
	    //candidate photo weight
	    ArrayList<Metadata.POI> pois = new ArrayList<Metadata.POI>();
		if (candidatePhoto.tid != -1) {
			pois.add(router.metadata.FindPoiByID(candidatePhoto.tid, router.poiList));
		}
		else {
			for(Integer uid: candidatePhoto.uid) {
				pois.add(router.metadata.FindPoiByID(uid, router.poiList));
			}
		}
		Double minCandWeight = Constant.Infinite * 1.0;
		for(Metadata.POI poi: pois) {
		    int cvg = router.metadata.CalculateProbableCoverage(Constant.IF_ADDED, poi, candidatePhoto);
			Double candidateWeight = Constant.Phi * priorityMap.get(poi) + (1-Constant.Phi) * cvg;
			if(candidateWeight < minCandWeight) minCandWeight = candidateWeight;
		}
		//check if replaceable
		if(minCandWeight > minWeight) {
			return replaceable;
		}
		else {
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	public int checkSequenceMapEntry(Metadata.POI poi, Metadata.Photo photo, ArrayList<Metadata.POI> tempPoiList ) {
		Metadata.POI tempPoi = null;
		for(Metadata.POI tPoi: tempPoiList) {
			if(tPoi.tid == poi.tid) {
				tempPoi = tPoi;
				break;
			}
		}
		if(tempPoi == null) {
			tempPoi = router.metadata.new POI(poi.tid, poi.tloc, false, 0l);
			tempPoi.cvgDetail = (ArrayList<Integer>)poi.cvgDetail.clone();
			tempPoi.cvgTotal = poi.cvgTotal;
			tempPoiList.add(tempPoi);
		}
		int cvg = router.metadata.CalculateProbableCoverage(Constant.IF_ADDED, tempPoi, photo);
		if(cvg > 0) {
			tempPoi.cvgDetail.add(router.metadata.GetLowerDirAngle(poi, photo));
			tempPoi.cvgTotal += cvg;
		}
		return cvg;
	}
	/*
	 * 
	 */
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
			ArrayList<Metadata.Photo> newPhotoList = new ArrayList<Metadata.Photo>();
			ArrayList<Metadata.Photo> rcvdPhotoList = (ArrayList<Metadata.Photo>) msg.getProperty(Constant.PHOTO_LIST);
			
			for(Metadata.Photo photo: rcvdPhotoList) {
				Metadata.Photo p = router.metadata.FindPhotoByID(photo.pid, router.photoList);	

				if(photo.tid != -1) {
					//update the respective poi
					Metadata.POI poi = router.metadata.FindPoiByID(photo.tid, router.poiList);
					//if it is a new metadata
					if(p == null) { 
						newPhotoList.add(photo);
						poi.avgDist = poi.avgDist + (router.metadata.GetDistance(poi.tloc, photo.ploc) - poi.avgDist) / (poi.numPhoto + 1); //if s is the previous avg, s' = s + ([n+1th avg] - s)/(n+1)
						poi.numPhoto = poi.numPhoto + 1;
					}
					//if it is a new metadata and delivered || if it is already stored and just learned the photo has been delivered
					if ( (p == null && photo.delivered == true) || (p != null &&  p.delivered == false && photo.delivered == true) ) {	
						//update photo
						if(p!=null) {
							p.delivered = true;
							p.exist = false;
						}
						//update coverage related properties.
						int lowerDirAngle = router.metadata.GetLowerDirAngle(poi, photo);
						if(poi.cvgDetail.contains(lowerDirAngle) == false) {
							poi.cvgDetail.add(lowerDirAngle);
							poi.cvgTotal = router.metadata.TotalCoverage(poi.cvgDetail);
						}
					}
				}
				else {
					//if it is a new metadata
					if(p == null) { 
						newPhotoList.add(photo);
					}
					//if it is already stored and just learned the photo has been delivered
					if (p != null &&  p.delivered == false && photo.delivered == true)  {	
						//update photo
						p.delivered = true;
						p.exist = false;
					}
				}
			}
			
			//update undefined poi list and their properties.
			long startTime = router.getUserTime();
			if(router.scenario == Constant.SimpleCluster)
				router.metadata.ModifyClusterPOI(router.poiList, router.photoList, newPhotoList, router.undefCluster, router.clusterRange);
			else
				router.metadata.ModifyUndefinedPOI(router.poiList, router.photoList, newPhotoList);
			long endTime = router.getUserTime();
			long diffTime = endTime - startTime;
			PhotoReport.additionalPoiTime += diffTime;
			
			//List<Entry<Metadata.Photo, Double>> sortedPhotos = calculatePhotoSequence(newPhotoList);
			
			//now calculate the poi sequence
			HashMap<Metadata.POI, Double> priorityMap = new HashMap<Metadata.POI, Double>();
			for(Metadata.POI poi: router.poiList) {
				Double priority = Constant.Beta * poi.numPhoto + Constant.Gamma * poi.avgDist;
				//if(poi.tid > 0) priority *= 4;
				priorityMap.put(poi, priority);
			}
			
			//now calculate the photo sequence
		    HashMap<Metadata.Photo, Double> sequenceMap = new HashMap<Metadata.Photo, Double>();
		    ArrayList<Metadata.POI> tempPoiList = new ArrayList<Metadata.POI>();
		    
			//update photolist
		    //check the photos from other node
		    for(Metadata.Photo photo: newPhotoList) {
		    	if(photo.exist == true && photo.delivered == false) {
		    		photo.exist = false;
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
						if(sequenceMap.containsKey(photo) == false) {							
							int cvg = checkSequenceMapEntry(poi, photo, tempPoiList);	
							if(cvg > 0) sequenceMap.put(photo, Constant.Phi * priorityMap.get(poi) + (1-Constant.Phi) * cvg);
							
//				    		int cvg = router.metadata.CalculateProbableCoverage(Constant.IF_ADDED, poi, photo);
//				    		if(cvg > 0) sequenceMap.put(photo, Constant.Phi * priorityMap.get(poi) + (1-Constant.Phi) * cvg);
						}
					}
		    	}
		    }
			
			//add new photo list to existing
		    //photoList.addAll(newPhotoList);
		    for(Metadata.Photo p: newPhotoList) {
		    	if(router.photoList.contains(p) == false) 
		    		if(p.exist == true) p.exist = false;
		    		router.photoList.add(p);
		    }
		    
		    //now this node photolist contains all received photo list
		    for(Metadata.Photo photo: rcvdPhotoList) {
		    	if(photo.exist == true && photo.delivered == false) {
		    		Metadata.Photo p = router.metadata.FindPhotoByID(photo.pid, router.photoList);
		    		if(p.exist == false) {
			    		ArrayList<Metadata.POI> pois = new ArrayList<Metadata.POI>();
						if (p.tid != -1) {
							pois.add(router.metadata.FindPoiByID(p.tid, router.poiList));
						}
						else {
							for(Integer uid: p.uid) {
								pois.add(router.metadata.FindPoiByID(uid, router.poiList));
							}
						}
						for(Metadata.POI poi: pois) {
							if(sequenceMap.containsKey(p) == false) {
								int cvg = checkSequenceMapEntry(poi, p, tempPoiList);	
								if(cvg > 0) sequenceMap.put(p, Constant.Phi * priorityMap.get(poi) + (1-Constant.Phi) * cvg);
								
//								int cvg = router.metadata.CalculateProbableCoverage(Constant.IF_ADDED, poi, p);
//								if(cvg > 0) sequenceMap.put(p, Constant.Phi * priorityMap.get(poi) + (1-Constant.Phi) * cvg);
							}
						}
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
		    	if(photo.exist == false) {
		    		router.sendPhotoSeqMap.get(from.getAddress()).add(photo.clone()); //System.out.println("Send photo sequence: " + getHost().toString() + "_" + from.toString() + " ||| " + photo.pid);
		    	}
		    }				

		    router.messageStatus.get(from.getAddress()).rcvdMetadata = true;
		}

		//if this is the sequence message
		else if(msg.getId().startsWith("s")) {
			router.rcvdPhotoSeqMap.put(from.getAddress(), (ArrayList<Metadata.Photo>)msg.getProperty(Constant.PHOTO_REQ));			
			router.messageStatus.get(from.getAddress()).rcvdSequence = true;
		}
		
		//if this is the photo message
		else if(msg.getId().startsWith("p")) {
			
			String msgId = (String)msg.getProperty(Constant.PHOTO_ID);
			int photoId = Integer.parseInt(msgId.split("_")[1]); //System.out.println("Received photo: " + getHost().toString() + "_" + from.toString() + " ||| " + photoId);
			//messageStatus.get(from.getAddress()).tempRcvdPhotoIdCache.add(photoId);
			Metadata.Photo photo = router.metadata.FindPhotoByID(photoId, router.photoList);
			//if photo is not there, store it
			if(photo.exist == false) {
				//before storing, see if buffer is available
				ArrayList<Metadata.Photo> existingPhotoList = new ArrayList<Metadata.Photo>();
				existingPhotoList.addAll(router.metadata.FilteredExistingPhoto(router.photoList));	
				
				if(existingPhotoList.size() < this.router.photoLimit) {
					photo.exist = true;
					
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
				//if buffer is full
				else {
					//find a replaceable photo if possible
					Metadata.Photo replaceable = findLeastImpForeignPhoto(existingPhotoList, photo);
					if(replaceable != null) {
						//System.out.println(Constant.test++);
						ArrayList<Metadata.POI> pois;
						int lowerDirAngle;
						//delete the old one
						replaceable.exist = false;
						pois = new ArrayList<Metadata.POI>();
						if (replaceable.tid != -1) {
							pois.add(router.metadata.FindPoiByID(replaceable.tid, router.poiList));
						}
						else {
							for(Integer uid: replaceable.uid) {
								pois.add(router.metadata.FindPoiByID(uid, router.poiList));
							}
						}
						for(Metadata.POI poi: pois) {
							lowerDirAngle = router.metadata.GetLowerDirAngle(poi, replaceable);
							if(poi.cvgDetail.contains(lowerDirAngle) == true) {
								poi.cvgDetail.remove((Integer)lowerDirAngle);
								poi.cvgTotal = router.metadata.TotalCoverage(poi.cvgDetail);
							}
						}
						//add the new one
						photo.exist = true;
						pois = new ArrayList<Metadata.POI>();
						if (photo.tid != -1) {
							pois.add(router.metadata.FindPoiByID(photo.tid, router.poiList));
						}
						else {
							for(Integer uid: photo.uid) {
								pois.add(router.metadata.FindPoiByID(uid, router.poiList));
							}
						}
						for(Metadata.POI poi: pois) {
							lowerDirAngle = router.metadata.GetLowerDirAngle(poi, photo);
							if(poi.cvgDetail.contains(lowerDirAngle) == false) {
								poi.cvgDetail.add(lowerDirAngle);
								poi.cvgTotal = router.metadata.TotalCoverage(poi.cvgDetail);
							}
						}
					}
				}
			}
		}
	}
	
	public void messageTransferredRelayToServer(Message msg, DTNHost from) {
		//if this is the metadata message
		if(msg.getId().startsWith("m")) {
			ArrayList<Metadata.Photo> newPhotoList = new ArrayList<Metadata.Photo>();
			@SuppressWarnings("unchecked")
			ArrayList<Metadata.Photo> rcvdPhotoList = (ArrayList<Metadata.Photo>) msg.getProperty(Constant.PHOTO_LIST);
			
			for(Metadata.Photo photo: rcvdPhotoList) {
				Metadata.Photo p = router.metadata.FindPhotoByID(photo.pid, PhotoReport.photoList);	
				
				if(p == null) {
					if(photo.tid != -1) {
						Metadata.POI poi = router.metadata.FindPoiByID(photo.tid, PhotoReport.poiList);
						poi.avgDist = poi.avgDist + (router.metadata.GetDistance(poi.tloc, photo.ploc) - poi.avgDist) / (poi.numPhoto + 1); //if s is the previous avg, s' = s + ([n+1th avg] - s)/(n+1)
						poi.numPhoto = poi.numPhoto + 1;
					}
					newPhotoList.add(photo);
				}
			}
			//update undefined poi list and their properties.
			if(router.scenario == Constant.SimpleCluster)
				router.metadata.ModifyClusterPOI(PhotoReport.poiList, PhotoReport.photoList, newPhotoList, PhotoReport.undefCluster, router.clusterRange);
			else
				router.metadata.ModifyUndefinedPOI(PhotoReport.poiList, PhotoReport.photoList, newPhotoList);
			
			//now calculate the poi sequence
			HashMap<Metadata.POI, Double> priorityMap = new HashMap<Metadata.POI, Double>();
			for(Metadata.POI poi: PhotoReport.poiList) {
				Double priority = Constant.Beta * poi.numPhoto + Constant.Gamma * poi.avgDist;
				//if(poi.tid > 0) priority *= 4;
				priorityMap.put(poi, priority);
			}
			//now calculate the photo sequence
		    HashMap<Metadata.Photo, Double> sequenceMap = new HashMap<Metadata.Photo, Double>();
		    ArrayList<Metadata.POI> tempPoiList = new ArrayList<Metadata.POI>();

			for(Metadata.Photo photo: newPhotoList) {
		    	if(photo.exist == true && photo.delivered == false) {
		    		photo.exist = false;
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
						if(sequenceMap.containsKey(photo) == false) {
							int cvg = checkSequenceMapEntry(poi, photo, tempPoiList);	
							if(cvg > 0) sequenceMap.put(photo, Constant.Phi * priorityMap.get(poi) + (1-Constant.Phi) * cvg);
							
//				    		int cvg = router.metadata.CalculateProbableCoverage(Constant.IF_ADDED, poi, photo);
//				    		if(cvg > 0) sequenceMap.put(photo, Constant.Phi * priorityMap.get(poi) + (1-Constant.Phi) * cvg);
						}
					}
		    	}
		    }
			
			//add new photo list to existing
			for(Metadata.Photo p: newPhotoList) {
				if(PhotoReport.photoList.contains(p) == false)
					if(p.exist == true) p.exist = false;
					PhotoReport.photoList.add(p);
			}
		    //PhotoReport.photoList.addAll(newPhotoList);
		    //for(Metadata.Photo photo: newPhotoList) {
		    for(Metadata.Photo photo: rcvdPhotoList) {
		    	if(photo.exist == true && photo.delivered == false) {
		    		Metadata.Photo p = router.metadata.FindPhotoByID(photo.pid, PhotoReport.photoList);
		    		if(p.exist == false) {
			    		ArrayList<Metadata.POI> pois = new ArrayList<Metadata.POI>();
						if (p.tid != -1) {
							pois.add(router.metadata.FindPoiByID(p.tid, PhotoReport.poiList));
						}
						else {
							for(Integer uid: p.uid) {
								pois.add(router.metadata.FindPoiByID(uid, PhotoReport.poiList));
							}
						}
						for(Metadata.POI poi: pois) {
				    		if(sequenceMap.containsKey(p) == false) {
								int cvg = checkSequenceMapEntry(poi, p, tempPoiList);	
								if(cvg > 0) sequenceMap.put(p, Constant.Phi * priorityMap.get(poi) + (1-Constant.Phi) * cvg);
								
//					    		int cvg = router.metadata.CalculateProbableCoverage(Constant.IF_ADDED, poi, p);
//					    		if(cvg > 0) sequenceMap.put(p, Constant.Phi * priorityMap.get(poi) + (1-Constant.Phi) * cvg);
				    		}
						}
		    		}
		    	}
		    }
//		    //test block
//		    Constant.test += sequenceMap.size();
//			System.out.println("["+ Constant.test +"]");
		    
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
		    	if(photo.exist == false) {
		    		router.sendPhotoSeqMap.get(from.getAddress()).add(photo.clone()); //System.out.println("Send photo sequence: " + getHost().toString() + "_" + from.toString() + " ||| " + photo.pid);
		    		//Constant.testCounter.put(photo.pid, 0);
		    	}
		    }	
		    //System.out.println("[" + Constant.testCounter.size() + "]");

		    router.messageStatus.get(from.getAddress()).rcvdMetadata = true;
		}
		
		//if this is the photo message
		else if(msg.getId().startsWith("p")) {
			//System.out.println(Constant.test++);
			
			String msgId = (String)msg.getProperty(Constant.PHOTO_ID);
			int photoId = Integer.parseInt(msgId.split("_")[1]); //System.out.println("Received photo: " + getHost().toString() + "_" + from.toString() + " ||| " + photoId);
			//messageStatus.get(from.getAddress()).tempRcvdPhotoIdCache.add(photoId);
			Metadata.Photo photo = router.metadata.FindPhotoByID(photoId, PhotoReport.photoList);
			//if photo is not there, store it
			if(photo.exist == false) {			
				photo.exist = true;
				PhotoReport.PhotoAtDestinationCounter++;
				//photo.delivered = true; //done in message transferred.
				//System.out.println("Photo Got " + photo.pid);
				//System.out.println(Constant.testCounter++);
				
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
						//reporting half coverage time
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
