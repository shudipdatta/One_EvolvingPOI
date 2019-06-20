package DataHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class SmartPhoto {
	
	int theta;
	public SmartPhoto(int theta) {
		this.theta = theta;
	}

	public class PhotoSelectionSequence implements Cloneable {
		public double timeStamp;
		public ArrayList<Integer> photoIds;
		public ArrayList<Double> priority;
		
		public PhotoSelectionSequence() {
			timeStamp = 0.0;
			photoIds = new ArrayList<Integer>();
			priority = new ArrayList<Double>();
		}
		
		public PhotoSelectionSequence clone() {
            try {
				return (PhotoSelectionSequence) super.clone();
			} catch (CloneNotSupportedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}
	}
	
	
	public class Element {
		int startAngle;
		int endAngle;
		int totalAngle;
		boolean isCovered;
		
		public Element() {
			isCovered = false;
		}
	}
	
	public class PhotoElement {
		public Metadata.Photo photo;
		ArrayList<Element> elementList;
		public int totalCvg;
		
		public PhotoElement() {
			elementList = new ArrayList<Element>();
			totalCvg = 0;
		}
	}
	
	public class CustomComparator implements Comparator<PhotoElement> {
	    @Override
	    public int compare(PhotoElement o1, PhotoElement o2) {
	        return o1.totalCvg - o2.totalCvg;
	    }
	}
	
	public boolean isBetween(int start, int end, int mid) {     
	    end = (end - start) < 0 ? end - start + 360 : end - start;    
	    mid = (mid - start) < 0 ? mid - start + 360 : mid - start; 
	    return (mid <= end); 
	}
	
	public ArrayList<PhotoElement> calculatePhotoElementList(Metadata.POI poi, ArrayList<Metadata.Photo> photoList) {	
		ArrayList<Integer> lowerAngles = new ArrayList<Integer>();
		ArrayList<Integer> upperAngles = new ArrayList<Integer>();
		for (Metadata.Photo photo: photoList) {
			int lowerAngle = MetadataManagement.GetLowerDirAngle(poi, photo, this.theta);
			int upperAngle = (lowerAngle + 2*theta) % 360;
			lowerAngles.add(lowerAngle);
			upperAngles.add(upperAngle);
		}
		
		ArrayList<Integer> edgeAngles = new ArrayList<Integer>();
//		edgeAngles.addAll(lowerAngles);
//		edgeAngles.addAll(upperAngles);	
		for(Integer angle: lowerAngles) 
			if (edgeAngles.contains(angle) == false) edgeAngles.add(angle);
		for(Integer angle: upperAngles) 
			if (edgeAngles.contains(angle) == false) edgeAngles.add(angle);
		
		Collections.sort(edgeAngles, new Comparator<Integer>() {
			@Override
			public int compare(Integer o1, Integer o2) {
				return o1.compareTo(o2);
			}
	    });
		if(edgeAngles.isEmpty() == false && edgeAngles.get(0) != 0) {
			edgeAngles.add(0, 0); //starting angle = 0
		}
		if(edgeAngles.isEmpty() == false && edgeAngles.get(edgeAngles.size()-1) != 359) {
			edgeAngles.add(359); //ending angle = 359
		}

		ArrayList<Element> elementList = new ArrayList<Element>();
		for(int i=0; i<edgeAngles.size()-1; i++) {
			Element e = new Element();
			e.startAngle = edgeAngles.get(i);
			e.endAngle = edgeAngles.get(i+1);
			e.totalAngle = (e.endAngle - e.startAngle + 360) % 360;
			elementList.add(e);
		}
		
		ArrayList<PhotoElement> photoElemList = new ArrayList<PhotoElement>();
		for (int i=0; i<photoList.size(); i++) {
			Metadata.Photo photo = photoList.get(i);
			int lowerAngle = lowerAngles.get(i);
			int upperAngle = upperAngles.get(i);
			
			PhotoElement photoElem = new PhotoElement();
			photoElem.photo = photo;
			for(Element element: elementList) {
				if( this.isBetween(lowerAngle, upperAngle, element.startAngle)  
					&&
					this.isBetween(lowerAngle, upperAngle, element.endAngle) ) {
					photoElem.elementList.add(element);
					photoElem.totalCvg += element.totalAngle;
				}
			}
			photoElemList.add(photoElem);
		}
		return photoElemList;
	}
	
	public ArrayList<PhotoElement> calculatePhotoSequence(Metadata.POI poi, ArrayList<Metadata.Photo> photoList) {	
		
		ArrayList<PhotoElement> photoElemList = calculatePhotoElementList(poi, photoList);
		
//		Collections.sort(photoElemList, new Comparator<PhotoElement>() {
//		    @Override
//		    public int compare(PhotoElement o1, PhotoElement o2) {
//		        return o2.totalCvg - o1.totalCvg;
//		    }
//		});

//		Collections.sort(photoElemList, new CustomComparator());
		ArrayList<PhotoElement> resultPhotoElemList = new ArrayList<PhotoElement>();
		int size = photoElemList.size();
		for (int i=0; i<size; i++) {
			PhotoElement maxPhotoElem = Collections.max(photoElemList, new CustomComparator());
			ArrayList<Element> elemList = maxPhotoElem.elementList;
			resultPhotoElemList.add(maxPhotoElem);
			photoElemList.remove(maxPhotoElem);
			for(Element e: elemList) {
				for (PhotoElement photoElem: photoElemList) {
					if(photoElem.elementList.contains(e)) {
						photoElem.elementList.remove(e);
						photoElem.totalCvg -= e.totalAngle;
					}
				}
			}
		}
		return resultPhotoElemList;
	}
	
	public ArrayList<PhotoElement> calculatePhotoSelectionSequence(Metadata.POI poi, ArrayList<Metadata.Photo> photoList) {	
			
			ArrayList<PhotoElement> photoElemList = calculatePhotoElementList(poi, photoList);
			ArrayList<PhotoElement> removePhotoElemList = new ArrayList<PhotoElement>();
			int size = photoElemList.size();
			for(int i=0; i<size; i++) {
				PhotoElement existedPhotoElem = photoElemList.get(i);
				ArrayList<Element> elemList = existedPhotoElem.elementList;
				if(existedPhotoElem.photo.exist == true) {
					for(Element e: elemList) {
						for (PhotoElement photoElem: photoElemList) {
							if(existedPhotoElem.photo.pid != photoElem.photo.pid && photoElem.elementList.contains(e)) {
								photoElem.elementList.remove(e);
								photoElem.totalCvg -= e.totalAngle;
							}
						}
					}
					removePhotoElemList.add(existedPhotoElem);
				}
			}
			photoElemList.removeAll(removePhotoElemList);
			
			ArrayList<PhotoElement> resultPhotoElemList = new ArrayList<PhotoElement>();
			size = photoElemList.size();
			for (int i=0; i<size; i++) {
				PhotoElement maxPhotoElem = Collections.max(photoElemList, new CustomComparator());
				ArrayList<Element> elemList = maxPhotoElem.elementList;
				resultPhotoElemList.add(maxPhotoElem);
				photoElemList.remove(maxPhotoElem);
				for(Element e: elemList) {
					for (PhotoElement photoElem: photoElemList) {
						if(photoElem.elementList.contains(e)) {
							photoElem.elementList.remove(e);
							photoElem.totalCvg -= e.totalAngle;
						}
					}
				}
			}
			return resultPhotoElemList;
	}
}
