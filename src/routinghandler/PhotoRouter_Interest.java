package routinghandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import DataHandler.ChoosePhoto;
import DataHandler.ChoosePhoto.*;
import DataHandler.Constant;
import DataHandler.Metadata;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.SimClock;
import report.PhotoReport;
import routing.PhotoRouter;

public class PhotoRouter_Interest {
	
	PhotoRouter router;	
	ChoosePhoto choosePhoto;
	public PhotoGroupSequence rcvdPhotoGroupSeq;
	//number of encounter
	private int encounter;
	//How many seconds one time unit is when calculating aging of delivery predictions. Should be tweaked for the scenario.
	private int secondsInTimeUnit;
	//delivery predictability transitivity scaling constant default value
	private double beta;
	//delivery predictability aging constant
	private double gamma;
	//delivery predictability initialization constant
	private double p_init;
	//delivery probability to destination
	private double destDelProb;
	/** delivery predictabilities */
	private Map<DTNHost, Double> preds;
	/** last delivery predictability update (sim)time */
	private double lastAgeUpdate;
	
	//ids of pois to which a node is interested in
	public int totalPoiNum = 0;
	private int interestNum;
	private ArrayList<Integer> interestList;
	/*
	 * 
	 */
	public PhotoRouter_Interest (PhotoRouter router) {
		this.router = router;
		this.choosePhoto = new ChoosePhoto(router.theta);
		this.rcvdPhotoGroupSeq = this.choosePhoto.new PhotoGroupSequence();
	}
	/*
	 * 
	 */
	public void Initialize() {
		for(Metadata.POI poi: router.poiList) {
			choosePhoto.DoGrouping(poi, router.photoList);
			poi.hasGroup = true;
		}
		
		this.secondsInTimeUnit = 10;
		this.beta =  0.25;
		this.gamma = 0.98;
		this.p_init = 0.75;
		this.encounter = 0;
		this.preds = new HashMap<DTNHost, Double>();
		new HashMap<DTNHost, ArrayList<Metadata.Photo>>();
		new HashMap<DTNHost, Double>();
		
		this.interestNum = (int) Math.ceil((totalPoiNum) / (1.0*router.nodeNum) * Constant.MinInterestNum);
		this.interestNum = (interestNum < Constant.MinInterestNum)? Constant.MinInterestNum : interestNum;
		this.interestList = new ArrayList<Integer>();
		for(int i=1; i<=this.interestNum; i++) {
			int interestId = ((router.getHost().getAddress() + i * this.interestNum) % totalPoiNum) + 1;
			this.interestList.add(interestId);
		}
	}
	/**
	 * Updates delivery predictions for a host.
	 * <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT</CODE>
	 * @param host The host we just met
	 */
	private void updateDeliveryPredFor(DTNHost host) {
		double oldValue = getPredFor(host);
		double newValue = oldValue + (1 - oldValue) * p_init;
		preds.put(host, newValue);
	}
	
	/**
	 * Returns the current prediction (P) value for a host or 0 if entry for
	 * the host doesn't exist.
	 * @param host The host to look the P for
	 * @return the current P value
	 */
	public double getPredFor(DTNHost host) {
		ageDeliveryPreds(); // make sure preds are updated before getting
		if (preds.containsKey(host)) {
			return preds.get(host);
		}
		else {
			return 0;
		}
	}
	
	/**
	 * Updates transitive (A->B->C) delivery predictions.
	 * <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
	 * </CODE>
	 * @param host The B host who we just met
	 */
	private void updateTransitivePreds(DTNHost host) {
		
		double pForHost = getPredFor(host); // P(a,b)
		Map<DTNHost, Double> othersPreds = getDeliveryPreds();
		
		for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
			if (e.getKey() == router.getHost()) {
				continue; // don't add yourself
			}
			
			double pOld = getPredFor(e.getKey()); // P(a,c)_old
			double pNew = pOld + ( 1 - pOld) * pForHost * e.getValue() * beta;
			preds.put(e.getKey(), pNew);
		}
	}
	
	/**
	 * Ages all entries in the delivery predictions.
	 * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of
	 * time units that have elapsed since the last time the metric was aged.
	 * @see #SECONDS_IN_UNIT_S
	 */
	private void ageDeliveryPreds() {
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) / secondsInTimeUnit;
		
		if (timeDiff == 0) {
			return;
		}
		
		double mult = Math.pow(gamma, timeDiff);
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			e.setValue(e.getValue()*mult);
		}
		
		this.lastAgeUpdate = SimClock.getTime();
	}
	
	/**
	 * Returns a map of this router's delivery predictions
	 * @return a map of this router's delivery predictions
	 */
	private Map<DTNHost, Double> getDeliveryPreds() {
		ageDeliveryPreds(); // make sure the aging is done
		return this.preds;
	}
	
	//calculate delivery probability to server by using preds and combinations
	public Double destDeliveryProb() {
		Double destDelProb = 0.0;
		
		Map<DTNHost, Double> deliveryProb = this.getDeliveryPreds();
		ArrayList<Double> destProbs = new ArrayList<Double>();
		for(Entry<DTNHost, Double> entry: deliveryProb.entrySet()) {
			if(entry.getKey().toString().startsWith("v") == true) {
				destProbs.add(entry.getValue());
			}
		}
		int n = destProbs.size();
		if (n > 0) {
			for(int r=1; r<=n; r++) {
				ArrayList<ArrayList<Double>> combinations = new ArrayList<ArrayList<Double>>();
				combinationUtil(combinations, destProbs, new Double[r], 0, n-1, 0, r);
				Double sum = 0.0;
				for(int i=0; i<combinations.size(); i++) {
					Double mult = 1.0;
					for(Double elem: combinations.get(i)) {
						mult *= elem;
					}
					sum += mult;
				}
				if(r%2==1) destDelProb += sum; //if odd iteration
				else destDelProb -= sum;
			}
		}
		this.destDelProb = destDelProb;
		return destDelProb; //return the result destination delivery probability
	}
	
	//function to get all possible combinations of an inputarray.
	private <T> void combinationUtil(ArrayList<ArrayList<T>> combinations, ArrayList<T> input, T indexdata[], int start, int end, int index, int r) {
		// Current combination is ready to be printed, print it
		if (index == r) {
			ArrayList<T> elements = new ArrayList<T>();
			for (int j=0; j<r; j++) {
				elements.add(indexdata[j]);
			}
			combinations.add(elements);
			return;
		}
		
		for (int i=start; i<=end && end-i+1 >= r-index; i++) {
			indexdata[index] = input.get(i);
			combinationUtil(combinations, input, indexdata, i+1, end, index+1, r);
		}
	}
	
	//@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(router.getHost());
			updateDeliveryPredFor(otherHost);
			updateTransitivePreds(otherHost);
			this.encounter++; //when sending metadata, send lambda along with that
		}
	}
	
	public double getLambda() {
		return this.encounter/SimClock.getTime();
	}
	
	
	/*
	 * 
	 */
	public Metadata.Photo findLeastImpForeignPhoto(ArrayList<Metadata.Photo> existingPhotoList, Metadata.Photo candidatePhoto) {
		if(rcvdPhotoGroupSeq.timeStamp > 0.0) {
			//if the candidate photo has no value for server, don't accept
			if(rcvdPhotoGroupSeq.photoIds.contains(candidatePhoto.pid) == false) {
				return null;
			}
			//if candidate one is required for server
			else {
				//for each foreign existing photo
				int maxIndex = -1; 
				Metadata.Photo replaceable = null;
				for(Metadata.Photo photo: existingPhotoList) {
					if(photo.source != router.getHost().getAddress()) {
						//if we can find a existing foreign photo that server doesn't want, just replace it
						if(rcvdPhotoGroupSeq.photoIds.contains(photo.pid) == false) {
							return photo;
						}
						//the existing foreign photo is also required by the server! So find the last imp photo to replace
						else {
							int index = rcvdPhotoGroupSeq.photoIds.indexOf(photo.pid);
							if(index > maxIndex) {
								maxIndex = index;
								replaceable = photo;
							}
						}
					}
				}
				return replaceable;
			}
		}
		else {
			//if candidate photo's is not in interest group, don't need to replace
			if(candidatePhoto.tid != -1) {
				if(this.interestList.contains(candidatePhoto.tid) == false) {//checking the interest
					return null;
				}
			}
			//adding interest for hidden pois
			else {
				if(this.interestList.contains(candidatePhoto.hid) == false) {//checking the interest
					return null;
				}
			}
			//find a foreign photo that is not in the interest list
			for(Metadata.Photo photo: existingPhotoList) {
				if(photo.source != router.getHost().getAddress()) {
					if(photo.tid != -1) {
						if(this.interestList.contains(photo.tid) == false) {//checking the interest
							return photo;
						}
					}
					else {
						if(this.interestList.contains(photo.hid) == false) {//checking the interest
							return photo;
						}
					}
				}
			}
			return null;
		}
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
				
			PhotoGroupSequence photoGroupSeq = (PhotoGroupSequence)msg.getProperty(Constant.PHOTO_GROUP);
			if(photoGroupSeq.timeStamp > rcvdPhotoGroupSeq.timeStamp) {
				rcvdPhotoGroupSeq = photoGroupSeq;
			}
			
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
//						poi.avgDist = poi.avgDist + (router.metadata.GetDistance(poi.tloc, photo.ploc) - poi.avgDist) / (poi.numPhoto + 1); //if s is the previous avg, s' = s + ([n+1th avg] - s)/(n+1)
//						poi.numPhoto = poi.numPhoto + 1;
					}
					//if it is a new metadata and delivered || if it is already stored and just learned the photo has been delivered
					if ( (p == null && photo.delivered == true) || (p != null &&  p.delivered == false && photo.delivered == true) ) {	
						//update photo
						if(p!=null) {
							p.delivered = true;
							p.exist = false;
						}
//						//update coverage related properties.
//						int lowerDirAngle = router.metadata.GetLowerDirAngle(poi, photo);
//						if(poi.cvgDetail.contains(lowerDirAngle) == false) {
//							poi.cvgDetail.add(lowerDirAngle);
//							poi.cvgTotal = router.metadata.TotalCoverage(poi.cvgDetail);
//						}
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
			
//			//update undefined poi list and their properties.
//			long startTime = router.getUserTime();
//			router.metadata.ModifyClusterPOI(router.poiList, router.photoList, newPhotoList, router.undefCluster, router.clusterRange);
//			long endTime = router.getUserTime();
//			long diffTime = endTime - startTime;
//			PhotoReport.additionalPoiTime += diffTime;
//			
			//now calculate the photo sequence
		    HashMap<Metadata.Photo, Double> sequenceMap = new HashMap<Metadata.Photo, Double>();
		    ArrayList<Metadata.Photo> eligiblePhotos = new ArrayList<Metadata.Photo>();
		  
		    //check the existing photos from other node whose metadata r new for me
		    for(Metadata.Photo photo: newPhotoList) {
		    	if(photo.exist == true && photo.delivered == false) {
		    		photo.exist = false;
					if(eligiblePhotos.contains(photo) == false) {
						eligiblePhotos.add(photo);
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
			    
		   //if(this.destDelProb > 1-Constant.Threshold) {
		    this.destDeliveryProb();
		    if(this.destDelProb > Constant.DelProb) {
			    	
			    //check the existing photos from other node whose metadata r old for me
			    for(Metadata.Photo photo: rcvdPhotoList) {
			    	if(photo.exist == true && photo.delivered == false) {
			    		Metadata.Photo p = router.metadata.FindPhotoByID(photo.pid, router.photoList);
			    		if(p.exist == false) {
							if(eligiblePhotos.contains(photo) == false) {
								eligiblePhotos.add(p);
							}
			    		}
			    	}
			    }
			    
			    //now check if this node has server requested photo group and update the weight of the photos.
				//test
			    //for(int i=this.nodeMet*5; i<rcvdPhotoGroupSeq.photoIds.size(); i++) {
			    for(int i=0; i<rcvdPhotoGroupSeq.photoIds.size(); i++) {
					int pid = rcvdPhotoGroupSeq.photoIds.get(i);
					for(Metadata.Photo photo: eligiblePhotos) {
						if(pid == photo.pid) {
							//adding interest for fixed pois
							if(photo.tid != -1) {
								if(this.interestList.contains(photo.tid)) {//checking the interest
									sequenceMap.put(photo, rcvdPhotoGroupSeq.priority.get(i)*10);
								}
								else {
									sequenceMap.put(photo, rcvdPhotoGroupSeq.priority.get(i));
								}
							}
							//adding interest for hidden pois
							else {
								if(this.interestList.contains(photo.hid)) {//checking the interest
									sequenceMap.put(photo, rcvdPhotoGroupSeq.priority.get(i)*10);
								}
								else {
									sequenceMap.put(photo, rcvdPhotoGroupSeq.priority.get(i));
								}
							}
							break;
						}
					}
				}
				//add extra photo as the sequence
			    ArrayList<Metadata.Photo> extraPhotos = new ArrayList<Metadata.Photo>();
				for(Metadata.Photo photo: eligiblePhotos) {
					if(sequenceMap.containsKey(photo) == false) {
						extraPhotos.add(photo);
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
			    		router.sendPhotoSeqMap.get(from.getAddress()).add(photo.clone());
			    	}
			    }
			    
			    for(Metadata.Photo photo: extraPhotos) {
			    	if(photo.exist == false) {
			    		router.sendPhotoSeqMap.get(from.getAddress()).add(photo.clone());
			    	}
			    }
			}
			else {
				router.sendPhotoSeqMap.put(from.getAddress(), new ArrayList<Metadata.Photo>());
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
			Metadata.Photo photo = router.metadata.FindPhotoByID(photoId, router.photoList);
			
			//if photo is not there, store it
			if(photo.exist == false) {
				//before storing, see if buffer is available
				ArrayList<Metadata.Photo> existingPhotoList = new ArrayList<Metadata.Photo>();
				existingPhotoList.addAll(router.metadata.FilteredExistingPhoto(router.photoList));	
				if(existingPhotoList.size() < this.router.photoLimit) {
					photo.exist = true;
					
//					ArrayList<Metadata.POI> pois = new ArrayList<Metadata.POI>();
//					if (photo.tid != -1) {
//						pois.add(router.metadata.FindPoiByID(photo.tid, router.poiList));
//					}
//					else {
//						for(Integer uid: photo.uid) {
//							pois.add(router.metadata.FindPoiByID(uid, router.poiList));
//						}
//					}
//					for(Metadata.POI poi: pois) {
//						int lowerDirAngle = router.metadata.GetLowerDirAngle(poi, photo);
//						if(poi.cvgDetail.contains(lowerDirAngle) == false) {
//							poi.cvgDetail.add(lowerDirAngle);
//							poi.cvgTotal = router.metadata.TotalCoverage(poi.cvgDetail);
//						}
//					}
				}
				//if buffer is full
				else {
					//find a replaceable photo if possible
					Metadata.Photo replaceable = findLeastImpForeignPhoto(existingPhotoList, photo);
					if(replaceable != null) {
						int lowerDirAngle;
						//delete the old one
						replaceable.exist = false;
//						ArrayList<Metadata.POI> pois = new ArrayList<Metadata.POI>();
//						if (replaceable.tid != -1) {
//							pois.add(router.metadata.FindPoiByID(replaceable.tid, router.poiList));
//						}
//						else {
//							for(Integer uid: replaceable.uid) {
//								pois.add(router.metadata.FindPoiByID(uid, router.poiList));
//							}
//						}
//						for(Metadata.POI poi: pois) {
//							lowerDirAngle = router.metadata.GetLowerDirAngle(poi, replaceable);
//							if(poi.cvgDetail.contains(lowerDirAngle) == true) {
//								poi.cvgDetail.remove((Integer)lowerDirAngle);
//								poi.cvgTotal = router.metadata.TotalCoverage(poi.cvgDetail);
//							}
//						}
						//add the new one
						photo.exist = true;
//						pois = new ArrayList<Metadata.POI>();
//						if (photo.tid != -1) {
//							pois.add(router.metadata.FindPoiByID(photo.tid, router.poiList));
//						}
//						else {
//							for(Integer uid: photo.uid) {
//								pois.add(router.metadata.FindPoiByID(uid, router.poiList));
//							}
//						}
//						for(Metadata.POI poi: pois) {
//							lowerDirAngle = router.metadata.GetLowerDirAngle(poi, photo);
//							if(poi.cvgDetail.contains(lowerDirAngle) == false) {
//								poi.cvgDetail.add(lowerDirAngle);
//								poi.cvgTotal = router.metadata.TotalCoverage(poi.cvgDetail);
//							}
//						}
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
			router.metadata.ModifyClusterPOI(PhotoReport.poiList, PhotoReport.photoList, newPhotoList, PhotoReport.undefCluster, router.clusterRange);
			
			//now calculate the poi sequence
			HashMap<Metadata.POI, Double> priorityMap = new HashMap<Metadata.POI, Double>();
			for(Metadata.POI poi: PhotoReport.poiList) {
				Double priority = Constant.Beta * poi.numPhoto + Constant.Gamma * poi.avgDist;
				//if(poi.tid > 0) priority *= 4;
				priorityMap.put(poi, priority);
			}
			//now calculate the photo sequence
		    HashMap<Metadata.Photo, Double> sequenceMap = new HashMap<Metadata.Photo, Double>();
		    HashMap<Metadata.POI, ArrayList<Metadata.Photo>> eligiblePhotos = new HashMap<Metadata.POI, ArrayList<Metadata.Photo>>();

		  //check the existing photos from other node whose metadata r old for me
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
						if(eligiblePhotos.containsKey(poi) == false) {
							eligiblePhotos.put(poi, new ArrayList<Metadata.Photo>());
						}
						eligiblePhotos.get(poi).add(photo);
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
			
			//check the existing photos from other node whose metadata r old for me
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
							if(eligiblePhotos.containsKey(poi) == false) {
								eligiblePhotos.put(poi, new ArrayList<Metadata.Photo>());
							}
							eligiblePhotos.get(poi).add(p);
						}
		    		}
		    	}
		    }

			//update old or add newly added poi's clusters
		    this.choosePhoto.DoOldGrouping(PhotoReport.poiList, newPhotoList);
			this.choosePhoto.DoNewGrouping(PhotoReport.poiList, PhotoReport.photoList);
			
			//from all eligible photos, decide which to fetch based on the clusters.
			for (Entry<Metadata.POI, ArrayList<Metadata.Photo>> entry : eligiblePhotos.entrySet()) { 
				Metadata.POI poi = entry.getKey();
							
				for(ChoosePhoto.PhotoGroup cluster: poi.clusters) {
					for(Metadata.Photo p: entry.getValue()) {
						if( (p.pid == cluster.edges[0].pid || p.pid == cluster.edges[1].pid) && sequenceMap.containsKey(p) == false) {
							int cvg = router.metadata.CalculateProbableCoverage(Constant.IF_ADDED, poi, p);
							if(cvg > 0) {
								sequenceMap.put(p, Constant.Phi * priorityMap.get(poi) + (1-Constant.Phi) * (360-poi.cvgTotal));
							}
						}
					}
				}
				//Constant.test += poi.clusters.size();
			}
			for (Entry<Metadata.POI, ArrayList<Metadata.Photo>> entry : eligiblePhotos.entrySet()) { 
				Metadata.POI poi = entry.getKey();
				ArrayList<Metadata.Photo> photos = entry.getValue();
							
				for(Metadata.Photo photo: photos) {
					if(sequenceMap.containsKey(photo) == false) {
						int cvg = router.metadata.CalculateProbableCoverage(Constant.IF_ADDED, poi, photo);
						if(cvg > 0) {
							sequenceMap.put(photo,  0.0);
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
		    		//Constant.testCounter.put(photo.pid, 0);
		    	}
		    }
			//System.out.println("[" + Constant.testCounter.size() + "]");
		    
		    //now server also wants to distribute the photo id array with sequence and a timestamp
		    //sort the poi sequence
		    List<Entry<Metadata.POI, Double>> sortedPois = new ArrayList<Entry<Metadata.POI, Double>>(priorityMap.entrySet());
		    Collections.sort(sortedPois, 
	            new Comparator<Entry<Metadata.POI, Double>>() {
	                @Override
	                public int compare(Entry<Metadata.POI, Double> e1, Entry<Metadata.POI, Double> e2) {
	                    return e2.getValue().compareTo(e1.getValue());
	                }
	            }
		    );
		    //group the all necessary photos to cover the pois
		    PhotoGroupSequence photoGroupSec = choosePhoto.new PhotoGroupSequence();
		    for(Entry<Metadata.POI, Double> entry: sortedPois) {
		    	Metadata.POI poi = entry.getKey();
		    	for(ChoosePhoto.PhotoGroup cluster: poi.clusters) { 
		    		Metadata.Photo p0 = router.metadata.FindPhotoByID(cluster.edges[0].pid, PhotoReport.photoList);
		    		if(p0.exist == false
		    				&& sequenceMap.containsKey(p0) == false
		    				&& photoGroupSec.photoIds.contains(p0.pid) == false) {
		    			
		    			int cvg = router.metadata.CalculateProbableCoverage(Constant.IF_ADDED, poi, p0);
						if(cvg > 0) {
			    			photoGroupSec.photoIds.add(p0.pid);
			    			photoGroupSec.priority.add(Constant.Phi * priorityMap.get(poi) + (1-Constant.Phi) * cvg);
						}
		    		}
		    		Metadata.Photo p1 = router.metadata.FindPhotoByID(cluster.edges[1].pid, PhotoReport.photoList);
		    		if(p1.exist == false 
		    				&& sequenceMap.containsKey(p1) == false
		    				&& photoGroupSec.photoIds.contains(p1.pid) == false) {
		    			
		    			int cvg = router.metadata.CalculateProbableCoverage(Constant.IF_ADDED, poi, p1);
						if(cvg > 0) {
			    			photoGroupSec.photoIds.add(p1.pid);
			    			photoGroupSec.priority.add(Constant.Phi * priorityMap.get(poi) + (1-Constant.Phi) * cvg);
						}
		    		}
		    	}
		    }
		    photoGroupSec.timeStamp = SimClock.getTime();
		    photoGroupSec.lambda = this.getLambda();
		    router.sendPhotoGroupSeqMap.put(from.getAddress(), photoGroupSec.clone());
		    
		     
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
			this.rcvdPhotoGroupSeq = (PhotoGroupSequence)msg.getProperty(Constant.PHOTO_GROUP);		
			router.messageStatus.get(from.getAddress()).rcvdSequence = true;
		}
	}
	
}
