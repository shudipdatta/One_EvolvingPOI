package DataHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class ChoosePhoto {
	
	public class PhotoGroup implements Cloneable {
		public Metadata.Photo[] edges;
		public ArrayList<Metadata.Photo> members;
		
		public PhotoGroup() {
			edges = new Metadata.Photo[2];
			members = new ArrayList<Metadata.Photo>();
		}
		
		public PhotoGroup clone() {
            try {
				return (PhotoGroup) super.clone();
			} catch (CloneNotSupportedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}
	}
	
	public class PhotoGroupSequence implements Cloneable {
		public double timeStamp;
		public double lambda;
		public ArrayList<Integer> photoIds;
		public ArrayList<Double> priority;
		
		public PhotoGroupSequence() {
			timeStamp = 0.0;
			lambda = 0.0;
			photoIds = new ArrayList<Integer>();
			priority = new ArrayList<Double>();
		}
		
		public PhotoGroupSequence clone() {
            try {
				return (PhotoGroupSequence) super.clone();
			} catch (CloneNotSupportedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}
	}
	
	int theta;
	public ChoosePhoto(int theta) {
		this.theta = theta;
	}
	/*
	 * 
	 */
	public void UpdateGroup(Metadata.POI poi, Metadata.Photo photo) {
		int threshold = this.theta * 2;
		
		boolean makeNewCluster = true;
		PhotoGroup selectedC = null;
		double maxDistance = -1;
		
		double photoDir = MetadataManagement.PhotoPOIDirectionAngle(poi.tloc, photo);
		double photoDirAngle = 180 * (photoDir / Math.PI);
		double selectedEdge0DirAngle = -1;
		double selectedEdge1DirAngle = -1;
		
		for(PhotoGroup c: poi.clusters) {
			double edge0Dir = MetadataManagement.PhotoPOIDirectionAngle(poi.tloc, c.edges[0]);
			double edge0DirAngle = 180 * (edge0Dir / Math.PI);
			
			double edge1Dir = MetadataManagement.PhotoPOIDirectionAngle(poi.tloc, c.edges[1]);
			double edge1DirAngle = 180 * (edge1Dir / Math.PI);
			
			double dist0 = Math.abs(edge0DirAngle-photoDirAngle);
			if (dist0 > 180) dist0 = 360-dist0;
			
			double dist1 = Math.abs(edge1DirAngle-photoDirAngle);
			if (dist1 > 180) dist1 = 360-dist1;
			
			if( (dist0 <= threshold/2 && dist1 <= threshold)
			||  (dist0 <= threshold && dist1 <= threshold/2)
			||  (dist0 == dist1 && dist0 <= threshold))//(dist0 == dist1 && dist1 <= threshold), same thing
			{				
				if(dist0+dist1 > maxDistance) {
					maxDistance = dist0+dist1;
					selectedC = c;
					selectedEdge0DirAngle = edge0DirAngle;
					selectedEdge1DirAngle = edge1DirAngle;
				}
			}
		}
		if (selectedC != null) {
			makeNewCluster = false;
			selectedC.members.add(photo);
			double currentDist = Math.abs(selectedEdge0DirAngle-selectedEdge1DirAngle);
			if(currentDist > 180) currentDist = 360 - currentDist;
			double edge0toDist = Math.abs(selectedEdge0DirAngle-photoDirAngle);
			if(edge0toDist > 180) edge0toDist = 360 - edge0toDist;
			double edge1toDist = Math.abs(selectedEdge1DirAngle-photoDirAngle);
			if(edge1toDist > 180) edge1toDist = 360 - edge1toDist;
			
			if(edge0toDist + edge1toDist > currentDist) {
				if(edge0toDist < edge1toDist) selectedC.edges[0] = photo;
				else selectedC.edges[1] = photo;
			}				
		}
		if(makeNewCluster == true) {
			PhotoGroup c = new PhotoGroup();
			c.edges[0] = photo;
			c.edges[1] = photo;
			c.members.add(photo);
			poi.clusters.add(c);
		}
	}
	/*
	 * 
	 */
	public void DoGrouping(Metadata.POI poi, ArrayList<Metadata.Photo> photoList) {
		
		ArrayList<Metadata.Photo> photos = new ArrayList<Metadata.Photo>();
		if(poi.tid > 0) {
			photos.addAll(MetadataManagement.FilteredPhoto(poi.tid, photoList));		
		}
		else {
			for(Metadata.Photo photo: photoList) {
				if(photo.tid == -1 && photo.uid.contains(poi.tid)) {
					photos.add(photo);
				}
			}
		}
		
		//sort the photos before updating group
		HashMap<Metadata.Photo,Integer> lowerDirAngles = new HashMap<Metadata.Photo,Integer>();		
		for(Metadata.Photo photo: photos) {
			lowerDirAngles.put(photo, MetadataManagement.GetLowerDirAngle(poi, photo, theta));
		}
		List<Entry<Metadata.Photo, Integer>> sortedPhotos = new ArrayList<Entry<Metadata.Photo, Integer>>(lowerDirAngles.entrySet());
	    Collections.sort(sortedPhotos, 
            new Comparator<Entry<Metadata.Photo, Integer>>() {
                @Override
                public int compare(Entry<Metadata.Photo, Integer> e1, Entry<Metadata.Photo, Integer> e2) {
                    return e1.getValue().compareTo(e2.getValue());
                }
            }
	    );

	    for(Entry<Metadata.Photo, Integer> entry: sortedPhotos) {
			UpdateGroup(poi, entry.getKey());
		}
		
		poi.cvgCluster = TotalCoverage(poi);
		poi.redCluster = RedundantCoverage(poi);
	}
	/*
	 * 
	 */
	public int TotalCoverage(Metadata.POI poi) {
		ArrayList<Integer> lowerDirAngles = new ArrayList<Integer>();		
		for(PhotoGroup c: poi.clusters) {
			lowerDirAngles.add(MetadataManagement.GetLowerDirAngle(poi, c.edges[0], theta));
			lowerDirAngles.add(MetadataManagement.GetLowerDirAngle(poi, c.edges[1], theta));
		}
		
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

	public int RedundantCoverage(Metadata.POI poi) {
		ArrayList<Integer> lowerDirAngles = new ArrayList<Integer>();
		for(PhotoGroup c: poi.clusters) {
			lowerDirAngles.add(MetadataManagement.GetLowerDirAngle(poi, c.edges[0], theta));
			lowerDirAngles.add(MetadataManagement.GetLowerDirAngle(poi, c.edges[1], theta));
		}
		
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
	
	/*
	 * 
	 */
	public void DoOldGrouping(ArrayList<Metadata.POI> poiList, ArrayList<Metadata.Photo> photoList) {
		//adding new pois cluster
		for(Metadata.POI poi: poiList) {
			if(poi.hasGroup == true) {
				DoGrouping(poi, photoList);
			}
		}
	}
	public void DoNewGrouping(ArrayList<Metadata.POI> poiList, ArrayList<Metadata.Photo> photoList) {
		//adding new pois cluster
		for(Metadata.POI poi: poiList) {
			if(poi.hasGroup == false) {
				DoGrouping(poi, photoList);
				poi.hasGroup = true;
			}
		}
	}
	/*
	 * 
	 */
	public ArrayList<Metadata.Photo> ProbableGrouping(Metadata.POI poi, ArrayList<Metadata.Photo> photoList) {
		
		int threshold = this.theta * 2;
		ArrayList<PhotoGroup> clusters = new ArrayList<PhotoGroup>();
		for(PhotoGroup c: poi.clusters) {
			clusters.add(c.clone());
		}
		
		for(Metadata.Photo photo: photoList) {
			boolean makeNewCluster = true;
			PhotoGroup selectedC = null;
			double maxDistance = -1;
			
			double photoDir = MetadataManagement.PhotoPOIDirectionAngle(poi.tloc, photo);
			double photoDirAngle = 180 * (photoDir / Math.PI);
			double selectedEdge0DirAngle = -1;
			double selectedEdge1DirAngle = -1;
			
			for(PhotoGroup c: clusters) {
				double edge0Dir = MetadataManagement.PhotoPOIDirectionAngle(poi.tloc, c.edges[0]);
				double edge0DirAngle = 180 * (edge0Dir / Math.PI);
				
				double edge1Dir = MetadataManagement.PhotoPOIDirectionAngle(poi.tloc, c.edges[1]);
				double edge1DirAngle = 180 * (edge1Dir / Math.PI);
				
				double dist0 = Math.abs(edge0DirAngle-photoDirAngle);
				if (dist0 > 180) dist0 = 360-dist0;
				
				double dist1 = Math.abs(edge1DirAngle-photoDirAngle);
				if (dist1 > 180) dist1 = 360-dist1;
				
				if( (dist0 <= threshold/2 && dist1 <= threshold)
				||  (dist0 <= threshold && dist1 <= threshold/2)
				||  (dist0 == dist1))
				{				
					if(dist0+dist1 < maxDistance) {
						maxDistance = dist0+dist1;
						selectedC = c;
						selectedEdge0DirAngle = edge0DirAngle;
						selectedEdge1DirAngle = edge1DirAngle;
					}
				}
			}
			if (selectedC != null) {
				makeNewCluster = false;
				selectedC.members.add(photo);
				double currentDist = Math.abs(selectedEdge0DirAngle-selectedEdge1DirAngle);
				if(currentDist > 180) currentDist = 360 - currentDist;
				double edge0toDist = Math.abs(selectedEdge0DirAngle-photoDirAngle);
				if(edge0toDist > 180) edge0toDist = 360 - edge0toDist;
				double edge1toDist = Math.abs(selectedEdge1DirAngle-photoDirAngle);
				if(edge1toDist > 180) edge1toDist = 360 - edge1toDist;
				
				if(edge0toDist + edge1toDist > currentDist) {
					if(edge0toDist < edge1toDist) selectedC.edges[0] = photo;
					else selectedC.edges[1] = photo;
				}				
			}
			if(makeNewCluster == true) {
				PhotoGroup c = new PhotoGroup();
				c.edges[0] = photo;
				c.edges[1] = photo;
				c.members.add(photo);
				clusters.add(c);
			}
		}
		
		ArrayList<Metadata.Photo> candidatePhotoList = new ArrayList<Metadata.Photo>();
		for(PhotoGroup c: clusters) {
			if(c.edges[0].exist == false)  candidatePhotoList.add(c.edges[0]);
			if(c.edges[1].exist == false)  candidatePhotoList.add(c.edges[1]);
		}
		return candidatePhotoList;
	}
}
