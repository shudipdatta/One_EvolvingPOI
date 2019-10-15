package DataHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import DataHandler.Metadata.*;

public class MetadataManagement {
	static Metadata metadata;
	
	public static void SetInitialValues(ArrayList<POI> poiList, ArrayList<Photo> photoList, int theta) {
		metadata = new Metadata(theta);

		for(final POI poi: poiList) {
			
			Collection<Photo> filteredPhoto = FilteredPhoto(poi.tid, photoList);
			double sum = 0;
			for(Photo photo: filteredPhoto) {
				sum += GetDistance(poi.tloc, photo.ploc);
				int lowerDirAngle = GetLowerDirAngle(poi, photo, theta);
				if(poi.cvgDetail.contains(lowerDirAngle) == false) {
					poi.cvgDetail.add(lowerDirAngle);
				}
			}
			if(filteredPhoto.size() > 0) {
				poi.avgDist = sum/filteredPhoto.size();
				poi.numPhoto = filteredPhoto.size();
				poi.cvgTotal = TotalCoverage(poi.cvgDetail, theta);
				poi.redTotal = RedundantCoverage(poi.cvgDetail, theta);
			}
		}
		
		CalculateAdditionalPOI(poiList, photoList, theta);
	}
	
	public static void CalculateAdditionalPOI(ArrayList<POI> poiList, ArrayList<Photo> photoList, int theta) {
		//get all undefined photos
		Collection<Photo> undefinedPhotoList = FilteredPhoto(-1, photoList);
		ArrayList<POI> focusPOI = new ArrayList<POI>();
		
		for(Photo photoAsPoi: undefinedPhotoList) {

			POI poi = metadata.new POI(-photoAsPoi.pid, photoAsPoi.pointFocus, false, 0l);
			int sum = 0;
			int size = 0;
			
			photoAsPoi.uid = new ArrayList<Integer>();
			ArrayList<Integer> lowerDirAngles = new ArrayList<Integer>();
			
			for(Photo undefinedPhoto: undefinedPhotoList) {
				if(IsPointCoverage(poi.tloc, undefinedPhoto)) {
					sum += GetDistance(poi.tloc, undefinedPhoto.ploc);
					size++;
					
					int lowerDirAngle = GetLowerDirAngle(poi, undefinedPhoto, theta);
					if(undefinedPhoto.exist == true || undefinedPhoto.delivered == true) {
						if(poi.cvgDetail.contains(lowerDirAngle) == false) {
							poi.cvgDetail.add(lowerDirAngle);
						}
					}
					poi.photoIDList.add(undefinedPhoto.pid);
					lowerDirAngles.add(GetLowerDirAngle(poi, undefinedPhoto, theta));
				}
			}
			if(size > 0) {
				poi.avgDist = sum/size;
				poi.numPhoto = size;
				poi.cvgTotal = TotalCoverage(poi.cvgDetail, theta);
				poi.redTotal = RedundantCoverage(poi.cvgDetail, theta);
				poi.metaCvgTotal = TotalCoverage(lowerDirAngles, theta);
			}
			focusPOI.add(poi);
		}
		
		Collections.sort(focusPOI, new Comparator<POI>() {
			@Override
		    public int compare(POI o1, POI o2) {
		        return o2.metaCvgTotal - o1.metaCvgTotal;
		    }
		});
		
		for(int i=0; i<focusPOI.size(); i++) {
			POI poi = focusPOI.get(i);
			
			if(poi.expired == false) {
				poi.metaCvgTotal = 0;
				poiList.add(poi);
				//invalidate following temp pois if they are covered by the poi
				for(int pid: poi.photoIDList) {
					Photo p = FindPhotoByID(pid, undefinedPhotoList);
					p.uid.add(poi.tid);
					if(poi.tid != -pid) {
						
						for(int j=i+1; j<focusPOI.size(); j++)  {
							if(focusPOI.get(j).tid == -pid) {
								focusPOI.get(j).expired = true;
							}
						}
					}
				}
			}
		}
	}
	
	public static void ModifyClusterPOI(
			ArrayList<POI> poiList, 
			ArrayList<Photo> photoList, 
			ArrayList<Photo> newPhotoList, 
			int theta, 
			ArrayList<UndefCluster> undefCluster,
			int clusterRange) {
		
		Collection<POI> addedPoiList = FilteredPOI(poiList);
		Collection<Photo> undefinedPhotoList = FilteredPhoto(-1, photoList);
		Collection<Photo> newUndefinedPhotoList = FilteredPhoto(-1, newPhotoList);
		ArrayList<POI> focusPOI = new ArrayList<POI>();
		
		//if no cluster, then create cluster
		if(undefCluster.isEmpty()) {
			MakeCluster(undefinedPhotoList, undefCluster, clusterRange);
		}
		
		//if cluster exist, update cluster
		MakeCluster(newUndefinedPhotoList, undefCluster, clusterRange);
		
		//select the clusters that contains the new photos
		ArrayList<Metadata.UndefCluster> selectedCluster = new ArrayList<UndefCluster>();
		for(Photo newPhoto: newUndefinedPhotoList) {
			for(UndefCluster uc: undefCluster) {
				if(uc.members.contains(newPhoto)) {
					if(selectedCluster.contains(uc) == false) {
						selectedCluster.add(uc);
					}
					break;
				}
			}
		}
		//find the added poi from those selected cluster
		for(UndefCluster uc: selectedCluster) {
			for (POI poi: addedPoiList) {
				for(Photo p: uc.members) {
					if(p.pid == -1*poi.tid) {
						if(focusPOI.contains(poi) == false){
							focusPOI.add(poi);
						}
						break;
					}
				}
			}
		}
		
		
		//delete those selected temp poi from actual poi list
		//add those pois member photo with new photolist
		ArrayList<Photo> selectedPhotoList = new ArrayList<Photo>();
		for (POI poi: focusPOI) {		
			for (int pid: poi.photoIDList) {
				Photo photo = FindPhotoByID(pid, undefinedPhotoList);
				if(selectedPhotoList.contains(photo) == false)
					selectedPhotoList.add(photo);
			}
			//poi.updatedCluster = -1;
			poiList.remove(poi);
		}
		
		//make new photos not existing
		ArrayList<Photo> newExistedPhotoList = new ArrayList<Photo>();
		for(Photo newPhoto: newUndefinedPhotoList) {
			newPhoto.exist = false;
			newExistedPhotoList.add(newPhoto);
			selectedPhotoList.add(newPhoto);
		}
		
		CalculateAdditionalPOI(poiList, selectedPhotoList, theta);
		
		//make them existing again
		for(Photo newExitedPhoto: newExistedPhotoList) {
			newExitedPhoto.exist = true;
		}
	}
	
	public static void ModifyUndefinedPOI(ArrayList<POI> poiList, ArrayList<Photo> photoList, ArrayList<Photo> newPhotoList, int theta) {
		Collection<POI> addedPoiList = FilteredPOI(poiList);
		Collection<Photo> undefinedPhotoList = FilteredPhoto(-1, photoList);
		Collection<Photo> newUndefinedPhotoList = FilteredPhoto(-1, newPhotoList);
		ArrayList<POI> focusPOI = new ArrayList<POI>();
		
		//find which added poi's member photo should be considered for further action
		for (POI poi: addedPoiList) {
			boolean flag = false;
			for (int pid: poi.photoIDList) {
				if (flag) continue;
				Photo photo = FindPhotoByID(pid, undefinedPhotoList);
				if(photo != null) {
					for(Photo newPhoto: newUndefinedPhotoList) {
						if(IsPointCoverage(photo.pointFocus, newPhoto) || IsPointCoverage(newPhoto.pointFocus, photo)) {
							focusPOI.add(poi);
							flag = true;
							break;
						}
					}
				}
			}
		}
		//delete those selected temp poi from actual poi list
		//add those pois member photo with new photolist
		ArrayList<Photo> selectedPhotoList = new ArrayList<Photo>();
		for (POI poi: focusPOI) {		
			for (int pid: poi.photoIDList) {
				Photo photo = FindPhotoByID(pid, undefinedPhotoList);
				if(photo != null && selectedPhotoList.contains(photo) == false)
					selectedPhotoList.add(photo);
			}
			//poi.updatedCluster = -1;
			poiList.remove(poi);
		}
		
		//make new photos not existing
		ArrayList<Photo> newExistedPhotoList = new ArrayList<Photo>();
		for(Photo newPhoto: newUndefinedPhotoList) {
			newPhoto.exist = false;
			newExistedPhotoList.add(newPhoto);
			selectedPhotoList.add(newPhoto);
		}
		
		CalculateAdditionalPOI(poiList, selectedPhotoList, theta);
		
		//make them existing again
		for(Photo newExitedPhoto: newExistedPhotoList) {
			newExitedPhoto.exist = true;
		}
	}
	
	public static void MakeCluster(Collection<Photo> undefinedPhotoList, ArrayList<UndefCluster> undefCluster, int clusterRange) {
		for(Photo photo: undefinedPhotoList) {
			double minDist = 10000000;
			UndefCluster selectedUC = null;
			for(UndefCluster uc: undefCluster) {
				double dist = GetDistance(uc.centroid, photo.pointFocus);
				if(dist < minDist) {
					minDist = dist;
					selectedUC = uc;
				}
			}
			if(minDist < clusterRange) {
				int xc = selectedUC.members.size()*selectedUC.centroid.x + photo.pointFocus.x;
				int yc = selectedUC.members.size()*selectedUC.centroid.y + photo.pointFocus.y;
				selectedUC.members.add(photo);
				Point c = metadata.new Point(xc/selectedUC.members.size(), yc/selectedUC.members.size());
				selectedUC.centroid = c;
			}
			else {
				UndefCluster newUC = metadata.new UndefCluster();
				newUC.centroid = photo.pointFocus;
				newUC.members.add(photo);
				undefCluster.add(newUC);
			}
		}
	}
	
	private interface Predicate<T> { boolean apply(T type); }
	 
	private static <T> Collection<T> filter(Collection<T> col, Predicate<T> predicate) {
		Collection<T> result = new ArrayList<T>();
		for (T element: col) {
			if (predicate.apply(element)) {
				result.add(element);
			}
		}
		return result;
	}
	
	public static Collection<Photo> FilteredPhoto(final int tid, ArrayList<Photo> photoList) {
		Predicate<Photo> filteredPredicate = new Predicate<Photo>() {
			public boolean apply(Photo photo) {
				return photo.tid == tid;
			}
		};
		return filter(photoList, filteredPredicate);
	}
	
	public static Collection<Photo> FilteredUndefinedPhoto(final int hid, ArrayList<Photo> photoList) {
		Predicate<Photo> filteredPredicate = new Predicate<Photo>() {
			public boolean apply(Photo photo) {
				return photo.hid == hid;
			}
		};
		return filter(photoList, filteredPredicate);
	}
	
	public static Collection<Photo> FilteredExistingPhoto(ArrayList<Photo> photoList) {
		Predicate<Photo> filteredPredicate = new Predicate<Photo>() {
			public boolean apply(Photo photo) {
				return photo.exist == true;
			}
		};
		return filter(photoList, filteredPredicate);
	}
	
	public static Collection<Photo> FilteredDeliveredPhoto(ArrayList<Photo> photoList) {
		Predicate<Photo> filteredPredicate = new Predicate<Photo>() {
			public boolean apply(Photo photo) {
				return photo.delivered == true;
			}
		};
		return filter(photoList, filteredPredicate);
	}
	
	public static Collection<Photo> FilteredNonDeliveredPhoto(ArrayList<Photo> photoList) {
		Predicate<Photo> filteredPredicate = new Predicate<Photo>() {
			public boolean apply(Photo photo) {
				return photo.delivered == false;
			}
		};
		return filter(photoList, filteredPredicate);
	}
	
	private static Collection<POI> FilteredPOI(ArrayList<POI> poiList) {
		Predicate<POI> filteredPredicate = new Predicate<POI>() {
			public boolean apply(POI poi) {
				return poi.tid < 0;
			}
		};
		return filter(poiList, filteredPredicate);
	}
	
	
	private static <T> T filterOne(Collection<T> col, Predicate<T> predicate) {		
		for (T element: col) {
			if (predicate.apply(element)) {
				return element;
			}
		}
		return null;
	}
	
	public static Photo FindPhotoByID(final int pid, Collection<Photo> photoList) {
		Predicate<Photo> filteredPredicate = new Predicate<Photo>() {
			public boolean apply(Photo photo) {
				return photo.pid == pid;
			}
		};
		return filterOne(photoList, filteredPredicate);
	}	
	
	public static POI FindPoiByID(final int tid, Collection<POI> poiList) {
		Predicate<POI> filteredPredicate = new Predicate<POI>() {
			public boolean apply(POI poi) {
				return poi.tid == tid;
			}
		};
		return filterOne(poiList, filteredPredicate);
	}
	
	public static boolean DeletePhotoByID(final int pid, Collection<Photo> photoList) {
		for(Photo photo: photoList) {
			if(photo.pid == pid) {
				photoList.remove(photo);
				return true;
			}
		}
		return false;
	}
	
	
	public static double PhotoPOIDirectionAngle(Point loc, Photo photo) {
		//double radian = Math.atan2( (photo.pointDir.y-photo.ploc.y), (photo.pointDir.x-photo.ploc.x)) - 
		//		Math.atan2((loc.y-photo.ploc.y), (loc.x-photo.ploc.x));
		double radian = Math.atan2((loc.y-photo.ploc.y), (loc.x-photo.ploc.x));
		if(radian < 0) radian += 2 * Math.PI;
		return radian;
	}

	public static double GetDistance(Point p, Point q) {
		return Math.sqrt( (p.x-q.x)*(p.x-q.x) + (p.y-q.y)*(p.y-q.y));
	}
	
	private static boolean IsPointCoverage(Point loc, Photo photo) {
		int pointX = loc.x-photo.ploc.x;
		int pointY = loc.y-photo.ploc.y;
		
		int point1X = photo.point1.x-photo.ploc.x;
		int point1Y = photo.point1.y-photo.ploc.y;
		
		int point2X = photo.point2.x-photo.ploc.x;
		int point2Y = photo.point2.y-photo.ploc.y;
		
		int projectionP1 = -point1X*pointY + point1Y*pointX;
		int projectionP2 = -point2X*pointY + point2Y*pointX;
		int radiusSquared = pointX*pointX + pointY*pointY;
		
		if(projectionP1>0 && projectionP2<=0 && radiusSquared <= photo.range*photo.range) {
			return true;
		}
		else {
			return false;
		}
	}
	
	public static int GetLowerDirAngle(POI poi, Photo photo, int theta) {
		double dir = PhotoPOIDirectionAngle(poi.tloc, photo);
		int dirAngle = (int) (180 * (dir / Math.PI));
		int lowerDirAngle = dirAngle-theta;
		if(lowerDirAngle < 0) lowerDirAngle+=360;
		if(lowerDirAngle >= 360) lowerDirAngle %= 360;
		return lowerDirAngle;
	}
	
	public static int TotalCoverage(ArrayList<Integer> lowerDirAngles, int theta) {
		Collections.sort(lowerDirAngles, new Comparator<Integer>() {
			@Override
			public int compare(Integer o1, Integer o2) {
				return o1.compareTo(o2);
			}
	    });
		int sum=0;
		for(int i=0; i<lowerDirAngles.size(); i++) {
			int dir1 = lowerDirAngles.get(i);
			int dir2 = lowerDirAngles.get((i+1)%lowerDirAngles.size());
			if((i+1)%lowerDirAngles.size() == 0) dir2+=360;
			
			if((dir1 + 2*theta) < dir2) {
				sum += 2*theta;
			}
			else {
				sum += (dir2 - dir1);
			}
		}
		return sum;
	}

	public static int RedundantCoverage(ArrayList<Integer> lowerDirAngles, int theta) {
		Collections.sort(lowerDirAngles, new Comparator<Integer>() {
			@Override
			public int compare(Integer o1, Integer o2) {
				return o1.compareTo(o2);
			}
	    });
		int sum=0;
		for(int i=0; i<lowerDirAngles.size(); i++) {
			int dir1 = lowerDirAngles.get(i);
			int dir2 = lowerDirAngles.get((i+1)%lowerDirAngles.size());
			if((i+1)%lowerDirAngles.size() == 0) dir2+=360;
			
			if((dir1 + 2*theta) > dir2) {
				sum += dir1 + 2*theta - dir2;
			}
		}
		return sum;
	}	
	
	public static int CalculateProbableCoverage(String operation, POI poi, Photo photo, int theta) {
		int lowerDirAngle = GetLowerDirAngle(poi, photo, theta);
		@SuppressWarnings("unchecked")
		ArrayList<Integer> lowerDirAngles = (ArrayList<Integer>)poi.cvgDetail.clone();
		
		if(operation.equals(Constant.IF_ADDED)) {
			lowerDirAngles.add(lowerDirAngle);		
			int totalCoverage = TotalCoverage(lowerDirAngles, theta);	
			return totalCoverage - poi.cvgTotal;
		}
		else if(operation.equals(Constant.IF_DELETED)) {
			lowerDirAngles.remove((Integer)lowerDirAngle);		
			int totalCoverage = TotalCoverage(lowerDirAngles, theta);	
			return poi.cvgTotal - totalCoverage;
		}
		else return 0;
	}
	
	public static int TotalCoverageByPhotoSet(ArrayList<Metadata.POI> poiList, ArrayList<Metadata.Photo> photoList, int theta) {
		Map<Integer, ArrayList<Integer>> poiPhotoDegreeMap = new HashMap<Integer, ArrayList<Integer>>();
		for(Metadata.Photo photo: photoList) {
			
			ArrayList<Metadata.POI> pois = new ArrayList<Metadata.POI>();
			if (photo.tid != -1) {
				pois.add(FindPoiByID(photo.tid, poiList));
			}
			else {
				for(Integer uid: photo.uid) {
					pois.add(FindPoiByID(uid, poiList));
				}
			}
			for(Metadata.POI poi: pois) {
				if(poi != null) { //only take known photos
					int lowerDirAngle = GetLowerDirAngle(poi, photo, theta);
					if(poiPhotoDegreeMap.containsKey(poi.tid) == false) {
						poiPhotoDegreeMap.put(poi.tid, new ArrayList<Integer>());
					}
					poiPhotoDegreeMap.get(poi.tid).add(lowerDirAngle);
				}
			}
		}
		
		int totalCvg = 0;
		for(Entry<Integer, ArrayList<Integer>> entry: poiPhotoDegreeMap.entrySet()) {
			totalCvg += TotalCoverage(entry.getValue(), theta);
		}
		return totalCvg;
	}
	
	public static int TotalCoverageByDegreeVal(ArrayList<Metadata.POI> poiList, ArrayList<Metadata.Photo> photoList, int kcvg, int theta) {
		Map<Integer, ArrayList<Integer>> poiPhotoDegreeMap = new HashMap<Integer, ArrayList<Integer>>();
		for(Metadata.Photo photo: photoList) {
			
			ArrayList<Metadata.POI> pois = new ArrayList<Metadata.POI>();
			if (photo.tid != -1) {
				pois.add(FindPoiByID(photo.tid, poiList));
			}
			else {
				for(Integer uid: photo.uid) {
					pois.add(FindPoiByID(uid, poiList));
				}
			}
			for(Metadata.POI poi: pois) {
				if(poi != null) { //only take known photos
					int lowerDirAngle = GetLowerDirAngle(poi, photo, theta);
					if(poiPhotoDegreeMap.containsKey(poi.tid) == false) {
						poiPhotoDegreeMap.put(poi.tid, new ArrayList<Integer>());
					}
					poiPhotoDegreeMap.get(poi.tid).add(lowerDirAngle);
				}
			}
		}
		
		int totalCvg = 0;
		for(Entry<Integer, ArrayList<Integer>> entry: poiPhotoDegreeMap.entrySet()) {
			//instead of the commented line below, we do that to find out the total k cvg
			int[] cvgDegree = new int[360];
			for(int c=0; c<cvgDegree.length; c++) {
				cvgDegree[c] = 0;
			}
			for (int degree: entry.getValue()) {
				for (int d=degree; d<degree+theta; d++) {
					if (cvgDegree[d % cvgDegree.length] < kcvg) cvgDegree[d % cvgDegree.length]++;
				}
			}
			int thisCvg = 0;
			for(int c=0; c<cvgDegree.length; c++) {
				thisCvg += cvgDegree[c];
			}
			totalCvg += thisCvg;
			//totalCvg += TotalCoverage(entry.getValue(), theta);
		}
		return totalCvg;
	}
}
