package routinghandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import DataHandler.Constant;
import DataHandler.Metadata;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.SimClock;
import report.PhotoReport;
import routing.PhotoRouter;
import routing.PhotoRouter.MessageStatus;

public class PhotoRouter_RAware {
	
	PhotoRouter router;	
	//How many seconds one time unit is when calculating aging of delivery predictions. Should be tweaked for the scenario.
	private int secondsInTimeUnit;
	//delivery predictability transitivity scaling constant default value
	private double beta;
	//delivery predictability aging constant
	private double gamma;
	//delivery predictability initialization constant
	private double p_init;
	//number of encounter
	private int encounter;
	//delivery probability to destination
	private double destDelProb;

	//contact rates
	private Map<DTNHost, Double> lambdas;
	//delivery probability
	private Map<DTNHost, Double> deliveryProbs;
	//metadata cache
	private Map<DTNHost, ArrayList<Metadata.Photo>> cache;
	//contact times
	private Map<DTNHost, Double> contacts;
	/** delivery predictabilities */
	private Map<DTNHost, Double> preds;
	/** last delivery predictability update (sim)time */
	private double lastAgeUpdate;
	
	public HashMap<Integer, ArrayList<Integer>> sendRqstPhotoIDList;
	
	public PhotoRouter_RAware (PhotoRouter router) {
		this.router = router;
	}
	
	public void Initialize() {
		this.secondsInTimeUnit = 10;
		this.beta =  0.25;
		this.gamma = 0.98;
		this.p_init = 0.75;
		this.encounter = 0;
		this.preds = new HashMap<DTNHost, Double>();
		this.cache = new HashMap<DTNHost, ArrayList<Metadata.Photo>>();
		this.lambdas = new HashMap<DTNHost, Double>();
		this.deliveryProbs = new HashMap<DTNHost, Double>();
		this.contacts = new HashMap<DTNHost, Double>();
		
		this.sendRqstPhotoIDList = new HashMap<Integer, ArrayList<Integer>>();
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
//		double sumProb = 0.0;
		ArrayList<Double> destProbs = new ArrayList<Double>();
		for(Entry<DTNHost, Double> entry: deliveryProb.entrySet()) {
			if(entry.getKey().toString().startsWith("v") == true) {
				destProbs.add(entry.getValue());
			}
//			else{
//				sumProb += entry.getValue();
//			}
		}
		int n = destProbs.size();
//		if(n == 0) {
//			destDelProb = sumProb-1.0; //make it negative because it is not actual delivery probability
//		}
//		else
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
	
	//function to get all possible permutation of an binary number
	private void permutationUtil(int n, Integer[] resultArr, ArrayList<Integer[]> resultPerm) {
		if (n <= 0) {
			resultPerm.add(resultArr.clone());
		} else {
			resultArr[n - 1] = 0;
			permutationUtil(n - 1, resultArr, resultPerm);
			resultArr[n - 1] = 1;
			permutationUtil(n - 1, resultArr, resultPerm);
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
	private void calculatePhotoSequence(DTNHost from, ArrayList<Metadata.Photo> rcvdNonDeliveredPhotos) {
		
		final ArrayList<Metadata.Photo> thisDeliveredPhotos = (ArrayList<Metadata.Photo>) router.metadata.FilteredDeliveredPhoto(router.photoList);
		ArrayList<Metadata.Photo> existingPhotoList = (ArrayList<DataHandler.Metadata.Photo>) router.metadata.FilteredExistingPhoto(router.photoList);
		final ArrayList<Metadata.Photo> thisNonDeliveredPhotos = (ArrayList<Metadata.Photo>) router.metadata.FilteredNonDeliveredPhoto(existingPhotoList);
		
		//consider this node a, valid nodes m1,m2..., but not node b
		int n = this.cache.size() + 1; //+1 for this node
		ArrayList<DTNHost> cacheKeys = new ArrayList<DTNHost>(cache.keySet());
		ArrayList<ArrayList<Metadata.Photo>> cacheValues = new ArrayList<ArrayList<Metadata.Photo>>(cache.values());
		
		ArrayList<Integer[]> resultPerm = new ArrayList<Integer[]>();
		permutationUtil(n, new Integer[n], resultPerm);
		
		//mix node a & b's non delivered photo as the selection pool
		ArrayList<Metadata.Photo> selectionPool = new ArrayList<Metadata.Photo>();
		selectionPool.addAll(thisNonDeliveredPhotos);
		selectionPool.addAll(rcvdNonDeliveredPhotos);
		
		//for each photo, check which one gives maximum coverage
		ArrayList<Metadata.Photo> rqstPhotoList = new ArrayList<Metadata.Photo>();
		int selectionPoolSize = selectionPool.size();
		for(int counter=1; counter<=selectionPoolSize; counter++ ) {
			Double maxExCvg = -1.0;
			Metadata.Photo maxExPhoto = null;
			for(Metadata.Photo photo: selectionPool) {
				Double exCvg = 0.0;
				for(Integer[] aCase: resultPerm) {
					
					//get total photo coverage for this case
					ArrayList<Metadata.Photo> allPhotos = new ArrayList<Metadata.Photo>();
					allPhotos.addAll(thisDeliveredPhotos); //first entry is for command center
					allPhotos.add(photo);
					for(Integer i=0; i<aCase.length; i++) {
						if(aCase[i] == 1) {
							if(i == aCase.length-1) allPhotos.addAll(rqstPhotoList); //last entry is for this node
							else allPhotos.addAll(cacheValues.get(i));
						}
					}
//					int sumCvg = router.metadata.TotalCoverageByPhotoSet(router.poiList, allPhotos);
					int sumCvg = router.metadata.TotalCoverageByDegreeVal(router.poiList, allPhotos, router.kcvg);
					
					//get probability product for this case
					Double mulProb = 1.0;
					for(Integer i=0; i<aCase.length; i++) {
						Double delProb = (i == aCase.length-1)? this.destDelProb: this.deliveryProbs.get(cacheKeys.get(i));
						if(aCase[i] == 1) mulProb *= delProb;
						else mulProb += (1.0-delProb);
					}
					
					//get expected coverage for this case
					exCvg += sumCvg*mulProb;
				}
				if(exCvg > maxExCvg) {
					maxExCvg = exCvg;
					maxExPhoto = photo;
				}
			}
			if(maxExPhoto == null) 
				break; //no more benefit can be acheived
			rqstPhotoList.add(maxExPhoto);
			selectionPool.remove(maxExPhoto);
			//if(rqstPhotoList.size() > router.photoLimit) break; //storage is full
		}
		
		//create sequence map
		router.sendPhotoSeqMap.put(from.getAddress(), new ArrayList<Metadata.Photo>());
		this.sendRqstPhotoIDList.put(from.getAddress(), new ArrayList<Integer>());
	    for(Metadata.Photo photo: rqstPhotoList) {
	    	this.sendRqstPhotoIDList.get(from.getAddress()).add(photo.pid);
	    	
	    	if(router.metadata.FindPhotoByID(photo.pid, router.photoList) == null) {
	    		router.sendPhotoSeqMap.get(from.getAddress()).add(photo.clone()); 
	    	}
	    }		
	    router.messageStatus.get(from.getAddress()).rcvdMetadata = true;
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
			long startTime = router.getUserTime();
			messageTransferredRelayToServer(msg, from);
			long endTime = router.getUserTime();
			long diffTime = endTime - startTime;
			PhotoReport.serverTime += diffTime;
		}			
		//case 2: sender = server, receiver = relay
		else if(from.toString().startsWith("v") == true && router.getHost().toString().startsWith("v") == false) {
			messageTransferredServerToRelay(msg, from);
		}
	}
	
	@SuppressWarnings({ "unchecked" })
	public void messageTransferredRelayToRelay(Message msg, DTNHost from) {
		//if this is the metadata message
		if(msg.getId().startsWith("m")) {
			
			//store rcvd metadata and lambda from the sender node
			ArrayList<Metadata.Photo> rcvdPhotoList = (ArrayList<Metadata.Photo>) msg.getProperty(Constant.PHOTO_LIST);
			double rcvdLambda = (Double) msg.getProperty(Constant.LAMBDA);
			double rcvdProb = (Double) msg.getProperty(Constant.PROBABILITY);
			
			boolean isFirstTime = false;
			if(this.destDelProb < rcvdProb) {
				//if it is the second priority node, chech if the metadata has been received first time
				if(router.sendPhotoSeqMap.containsKey(from.getAddress()) == false) {
					isFirstTime = true;
				}
				else {
					if(router.sendPhotoSeqMap.get(from.getAddress()).size() == 0) {
						isFirstTime = true;
					}
					else {
						if(router.sendPhotoSeqMap.get(from.getAddress()).get(0).pid != -1) {
							isFirstTime = true;
						}
					}
				}
				
				if(isFirstTime == true){
					router.sendPhotoSeqMap.put(from.getAddress(), new ArrayList<Metadata.Photo>());
					router.sendPhotoSeqMap.get(from.getAddress()).add(router.metadata.new Photo(-1,0,null,0,0,0,false,false,0,null,null,null,null));
				}
			}
			
			if(isFirstTime == false) {
			
				final ArrayList<Metadata.Photo> rcvdNonDeliveredPhotos = (ArrayList<Metadata.Photo>) router.metadata.FilteredNonDeliveredPhoto(rcvdPhotoList);
				final ArrayList<Metadata.Photo> rcvdDeliveredPhotos = (ArrayList<Metadata.Photo>) router.metadata.FilteredDeliveredPhoto(rcvdPhotoList);
				ArrayList<Metadata.Photo> newDeliveredPhotos = new ArrayList<Metadata.Photo>();
				
				//add recently found delivered photo to current photolist
				for(Metadata.Photo photo: rcvdDeliveredPhotos) {
					if(router.metadata.FindPhotoByID(photo.pid, router.photoList) == null) {
						photo.exist = false; 
						router.photoList.add(photo);
						newDeliveredPhotos.add(photo);
					}
				}
				
				//find obsolete metadata in cache and delete them
				ArrayList<DTNHost> hostToRemove = new ArrayList<DTNHost>();
				for(Entry<DTNHost, Double> entry: this.lambdas.entrySet()) {
			    	DTNHost host = entry.getKey();
			    	double hostLambda = entry.getValue();
			    	double timeElapsed = SimClock.getTime() - this.contacts.get(host);
			    	double currentProbability = 1-Math.exp(-hostLambda*timeElapsed);
			    	
			    	if(currentProbability > Constant.Threshold) {//if obsolete
			    		if(this.cache.containsKey(host)) {
			    			hostToRemove.add(host);
			    		}
			    	}
				}
				for(DTNHost host: hostToRemove) {
	    			this.cache.remove(host);
	    			this.contacts.remove(host);
	    			this.lambdas.remove(host);
	    			this.deliveryProbs.remove(host);
				}
				
				//delete this newly delivered photo metadata from existing cache
				for(Metadata.Photo photo: newDeliveredPhotos) {
					for(Entry<DTNHost, ArrayList<Metadata.Photo>> entry: this.cache.entrySet()) {
						router.metadata.DeletePhotoByID(photo.pid, entry.getValue());
					}
				}
				
				//if this is the host to receive photo first
				if(this.destDelProb > rcvdProb) {
					calculatePhotoSequence(from, rcvdNonDeliveredPhotos);
				}
				
				//store the received data
				//if the met node has delivery prob 0, no benefit to save its info for later
				if(rcvdProb > 0.0) {
					this.cache.put(from, rcvdNonDeliveredPhotos);
					this.lambdas.put(from, rcvdLambda);
					this.deliveryProbs.put(from, rcvdProb);
					this.contacts.put(from, SimClock.getTime());
				}
	
				//if it is second priority node and data is second time, do some operation
				if(this.destDelProb < rcvdProb) {
					calculatePhotoSequence(from, rcvdNonDeliveredPhotos);
				}
			}
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
			
			boolean replacable = true;
			ArrayList<Metadata.Photo> existingPhotoList = (ArrayList<DataHandler.Metadata.Photo>) router.metadata.FilteredExistingPhoto(router.photoList);
			if(existingPhotoList.size() >= router.photoLimit) {
				
				//test
				//int r = router.getHost().getAddress();
				//if (r == 1 || r == 30 || r ==101){
				//	System.out.println(r);
				//}
				
				replacable = false;
				for(Metadata.Photo photo: existingPhotoList) {
					if(this.sendRqstPhotoIDList.get(from.getAddress()).contains(photo.pid) == false) {
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
							if(poi != null) { //as you didn't do modify , so no poi for some undefined photo (came from other node) is possible
								int lowerDirAngle = router.metadata.GetLowerDirAngle(poi, photo);
								if(poi.cvgDetail.contains(lowerDirAngle) == true) {
									poi.cvgDetail.remove((Integer)lowerDirAngle);
									poi.cvgTotal = router.metadata.TotalCoverage(poi.cvgDetail);
								}
								if(poi.photoIDList.contains(photo.pid)) {
									poi.photoIDList.remove((Integer)photo.pid);
								}
							}
						}
						
						replacable = router.metadata.DeletePhotoByID(photo.pid, router.photoList); // on investigation
						//replacable = true;
						break;
					}
				}
			}
			
			if(router.sendPhotoSeqMap.containsKey(from.getAddress()) && router.sendPhotoSeqMap.get(from.getAddress()).size() > 0) {
				if(replacable == true) {
					Metadata.Photo photo = (Metadata.Photo) msg.getProperty(Constant.PHOTO_CONTENT);
					if(photo != null) { //if sender able to send the photo content
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
							//router.metadata.DeletePhotoByID(photo.pid, router.sendPhotoSeqMap.get(from.getAddress()));
						}
						//else {
							//router.metadata.DeletePhotoByID(p.pid, router.sendPhotoSeqMap.get(from.getAddress()));
						//}
						
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
				//else {
					router.metadata.DeletePhotoByID(photoId, router.sendPhotoSeqMap.get(from.getAddress()));
				//}
			}
			//all requested photo has been received
			if(router.sendPhotoSeqMap.containsKey(from.getAddress()) && router.sendPhotoSeqMap.get(from.getAddress()).size() == 0) {
				//check if it is the first one, it should send its redefined metadata again to the second one
				//now if it is second one, there must be rcvdprob stored, check with that
				if(this.deliveryProbs.containsKey(from) == false || this.destDelProb > this.deliveryProbs.get(from)) {//so it is must the first node
					router.messageStatus.get(from.getAddress()).sent = MessageStatus.nothing_sent;
					router.messageStatus.get(from.getAddress()).rcvdMetadata = false;
					router.messageStatus.get(from.getAddress()).rcvdSequence = false;
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
