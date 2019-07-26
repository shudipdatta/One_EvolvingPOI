package DataHandler;

import java.util.ArrayList;
import java.util.Collection;



public class Metadata {
	/*
	 * Undefined photo cluster
	 */
	public class UndefCluster {
		public Metadata.Point centroid;
		public ArrayList<Metadata.Photo> members;
		
		public UndefCluster() {
			this.members = new ArrayList<Metadata.Photo>();
		}
	}	
	
	//both POI and Photo id starts with '1'. Because negative value indicates undefined poi and photo.
	int theta;
	public Metadata(int theta) {
		this.theta = theta;
	}
	
	public class Point {
		public int x;
		public int y;
		public Point (int x, int y) {
			this.x = x;
			this.y = y;
		}
	}
	
	public class DPoint {
		public double x;
		public double y;
		public DPoint (double x, double y) {
			this.x = x;
			this.y = y;
		}
	}
	
	public class POI {
		public int tid;
		public Point tloc;
		public int numPhoto;
		public double avgDist;
		public int cvgTotal;
		public int redTotal;
		public int metaCvgTotal;
		public ArrayList<Integer> cvgDetail;
		boolean expired;
		long ts;
		public ArrayList<Integer> photoIDList;
		public int numOfPoint;
		public ArrayList<DPoint> centroid;
		
		public int cvgCluster;
		public int redCluster;
		public boolean hasGroup;
		public ArrayList<ChoosePhoto.PhotoGroup> clusters;
		
		public POI(int tid, Point tloc, boolean expired, long ts) {
			this.tid = tid;
			this.tloc = tloc;
			this.numPhoto = 0;
			this.avgDist = 0;
			this.cvgTotal = 0;
			this.redTotal = 0;
			this.metaCvgTotal = 0;
			this.cvgDetail = new ArrayList<Integer>();
			this.expired = expired;
			this.ts = ts;
			
			this.cvgCluster = 0;
			this.redCluster = 0;
			this.hasGroup = false;
			this.clusters = new ArrayList<ChoosePhoto.PhotoGroup>();
			
			//followings are for hidden poi only
			this.photoIDList = new ArrayList<Integer>();
			this.numOfPoint = 0;
			this.centroid = new ArrayList<DPoint>();
		}
	}
	
	public class Photo  implements Cloneable {
		public int pid;
		public int hid; //id for the actual id of hidden poi that won't be known to any host
		public Point ploc;
		public int tid; //id for target
		public ArrayList<Integer> uid; //id for undefined target, it is local and temporary for each node
		public int direction;
		int focus;
		int range;
		public boolean exist;
		public boolean delivered;
		public int source;
		
		Point pointDir;
		public Point pointFocus;
		Point point1;
		Point point2;
		
		public Photo (int pid,
			int hid,
			Point ploc,
			int tid,
			int direction,
			int focus,
			boolean exist,
			boolean delivered,
			int source,
			
			Point pointDir,
			Point pointFocus,
			Point point1,
			Point point2) {
			
			this.pid = pid;
			this.hid = hid;
			this.uid = new ArrayList<Integer>();
			this.ploc = ploc;
			this.tid = tid;
			this.direction = direction;
			this.focus = focus;
			this.range = focus*2;
			this.exist = exist;
			this.delivered = delivered;
			this.source = source;
			
			this.pointDir = pointDir;
			this.pointFocus = pointFocus;
			this.point1 = point1;
			this.point2 = point2;
		}
		
		public Photo clone() {
            try {
				return (Photo) super.clone();
			} catch (CloneNotSupportedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}
	}
	
	public void SetInitialValues(ArrayList<Metadata.POI> poiList, ArrayList<Metadata.Photo> photoList) {
		MetadataManagement.SetInitialValues(poiList, photoList, theta);
	}
	
	public void CalculateAdditionalPOI(ArrayList<POI> poiList, ArrayList<Photo> photoList) {
		MetadataManagement.CalculateAdditionalPOI(poiList, photoList, theta);
	}
	
	public void ModifyClusterPOI(ArrayList<POI> poiList, ArrayList<Photo> photoList, ArrayList<Photo> newPhotoList,  ArrayList<UndefCluster> undefCluster, int clusterRange) {
		MetadataManagement.ModifyClusterPOI(poiList, photoList, newPhotoList, theta, undefCluster, clusterRange);
	}
	
	public void ModifyUndefinedPOI(ArrayList<POI> poiList, ArrayList<Photo> photoList, ArrayList<Photo> newPhotoList) {
		MetadataManagement.ModifyUndefinedPOI(poiList, photoList, newPhotoList, theta);
	}
	
	public Metadata.Photo FindPhotoByID(final int pid, Collection<Photo> photoList) {
		return MetadataManagement.FindPhotoByID(pid, photoList);
	}
	
	public Metadata.POI FindPoiByID(final int tid, Collection<POI> poiList) {
		return MetadataManagement.FindPoiByID(tid, poiList);
	}
	
	public boolean DeletePhotoByID(final int pid, Collection<Photo> photoList) {
		return MetadataManagement.DeletePhotoByID(pid, photoList);
	}
	
	public Collection<Photo> FilteredExistingPhoto(ArrayList<Photo> photoList) {
		return MetadataManagement.FilteredExistingPhoto(photoList);
	}
	
	public Collection<Photo> FilteredDeliveredPhoto(ArrayList<Photo> photoList) {
		return MetadataManagement.FilteredDeliveredPhoto(photoList);
	}
	
	public Collection<Photo> FilteredNonDeliveredPhoto(ArrayList<Photo> photoList) {
		return MetadataManagement.FilteredNonDeliveredPhoto(photoList);
	}
	
	public Collection<Photo> FilteredUndefinedPhoto(final int hid, ArrayList<Photo> photoList) {
		return MetadataManagement.FilteredUndefinedPhoto(hid, photoList);
	}

	public Collection<Photo> FilteredPhoto(int tid, ArrayList<Photo> photoList) {
		return MetadataManagement.FilteredPhoto(tid, photoList);
	}
	
	public double GetDistance(Point p, Point q) {
		return MetadataManagement.GetDistance(p, q);
	}
	
	public int GetLowerDirAngle(POI poi, Photo photo) {
		return MetadataManagement.GetLowerDirAngle(poi, photo, theta);
	}
	
	public int TotalCoverage(ArrayList<Integer> lowerDirAngles) {
		return MetadataManagement.TotalCoverage(lowerDirAngles, theta);
	}
	
	public int RedundantCoverage(ArrayList<Integer> lowerDirAngles) {
		return MetadataManagement.RedundantCoverage(lowerDirAngles, theta);
	}
	
	public int CalculateProbableCoverage(String operation, POI poi, Photo photo) {
		return MetadataManagement.CalculateProbableCoverage(operation, poi, photo, theta);
	}
	
	public int TotalCoverageByPhotoSet(ArrayList<Metadata.POI> poiList, ArrayList<Metadata.Photo> photoList) {
		return MetadataManagement.TotalCoverageByPhotoSet(poiList, photoList, theta);
	}
}
