package DataHandler;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class GenerateScenario {
	
	int actualPoi;
	int hiddenPoi;
	public int theta;
	
	public ArrayList<PhotoPOI.POI> actualPoiList;
	public ArrayList<PhotoPOI.POI> hiddenPoiList;
	public ArrayList<PhotoPOI.Photo> photoList;
	
	PhotoPOI photoPoi;
	private static HashMap<Integer, ArrayList<PhotoPOI.Photo>> totalPhotoMap;
	
	private static GenerateScenario generateScenario;

	private GenerateScenario(int actualPoi,
			int hiddenPoi,
			int theta,
			int maxX,
			int maxY,
			int focus) 
	{
		this.actualPoi=actualPoi;
		this.hiddenPoi=hiddenPoi;
		this.theta=theta;
		
		int minX = 0;
		int minY = 0;
		int range = focus * 2;
		
		this.photoPoi = new PhotoPOI();		
		this.actualPoiList = new ArrayList<PhotoPOI.POI>();
		this.hiddenPoiList = new ArrayList<PhotoPOI.POI>();
		this.photoList = new ArrayList<PhotoPOI.Photo>();
		totalPhotoMap = new HashMap<Integer, ArrayList<PhotoPOI.Photo>>();
	
		for(int i=0; i<this.actualPoi+this.hiddenPoi; i++) {
			int x = (range) + (int)(Math.random() * ((maxX - minX - 2 * range) + 1));
			int y = (range) + (int)(Math.random() * ((maxY - minY - 2 * range) + 1));
			if(i<this.actualPoi) this.actualPoiList.add(photoPoi.new POI(x,y));
			else this.hiddenPoiList.add(photoPoi.new POI(x,y));
		}
		
	}
	
	private ArrayList<PhotoPOI.Photo> PositionPhotos(int totalPhoto, int focus, int angle, int dataSource) {
		String photoFileName = "../src/reports/" + totalPhoto + "_" + dataSource + ".txt";
		ArrayList<PhotoPOI.Photo> photoList = new ArrayList<PhotoPOI.Photo>();
			
//		try {
//			FileInputStream fstream = new FileInputStream(photoFileName);
//			BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
//			String strLine;
//			while ((strLine = br.readLine()) != null)   {
//				String[] str = strLine.split("\t");
//				PhotoPOI.Photo photo = photoPoi.new Photo(Integer.parseInt(str[0]), Integer.parseInt(str[1]), Integer.parseInt(str[2]), Integer.parseInt(str[3]), Integer.parseInt(str[4]), focus, angle);
//				photo.hid = Integer.parseInt(str[5]);
//				photoList.add(photo);
//			}
//			br.close();
//			return photoList;
//		} 
//		catch (IOException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
			
		try {
			BufferedWriter oneWriter = new BufferedWriter(new FileWriter(photoFileName));
		
			int range = focus * 2;	
			for(int i=0; i<totalPhoto; i++) {
				int rand = (int) (Math.random() * (this.actualPoi+this.hiddenPoi));
				int randX = rand<this.actualPoi? this.actualPoiList.get(rand).tloc.x: this.hiddenPoiList.get(rand-this.actualPoi).tloc.x;
				int randY = rand<this.actualPoi? this.actualPoiList.get(rand).tloc.y: this.hiddenPoiList.get(rand-this.actualPoi).tloc.y;
				
				int x = (int)(Math.random() * (range + 1)) + (randX - range/2);
				int y = (int)(Math.random() * (range + 1)) + (randY - range/2);
				
				double radian = Math.atan2(y-randY, x-randX);
				if(radian < 0) radian += 2 * Math.PI;
				int dirAngle = (int) (180 * (radian / Math.PI));
				dirAngle -= angle/2;
				dirAngle += (int) (Math.random() * angle) - 360/2;
				if(dirAngle < 0) dirAngle+=360;
				int d = dirAngle;
				
				int p = rand<this.actualPoi? rand+1: -1;
				PhotoPOI.Photo photo = photoPoi.new Photo(i+1, x, y, d, p, focus, angle);
				photo.hid = rand+1;
				photoList.add(photo);
				
				oneWriter.write(i+1 + "\t" + x + "\t" + y + "\t" + d + "\t" + p + "\t" + photo.hid + "\n");
			}
			oneWriter.close();
		} 
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return photoList;
	}
	
	public void CalculatePOICoverage(HashMap<Integer, Integer> coverage, HashMap<Integer, Integer> redundant) {
		for(int i=0; i<actualPoiList.size()+hiddenPoiList.size(); i++) {
			PhotoPOI.POI poi;
			if(i<actualPoiList.size()) poi = actualPoiList.get(i);
			else poi = hiddenPoiList.get(i-actualPoiList.size());
			
			ArrayList<Integer> cvgDetail = new ArrayList<Integer>();
			for(PhotoPOI.Photo photo: photoList) {
				if(photo.hid == i+1) {
					int lowerDirAngle = GetLowerDirAngle(poi, photo, theta);
					if(cvgDetail.contains(lowerDirAngle) == false) {
						cvgDetail.add(lowerDirAngle);
					}
				}
			}
			coverage.put(i+1, TotalCoverage(cvgDetail, theta));
			redundant.put(i+1, RedundantCoverage(cvgDetail, theta));
		}
	}
	
	public int TotalCoverage(ArrayList<Integer> lowerDirAngles, int theta) {
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
	
	public int RedundantCoverage(ArrayList<Integer> lowerDirAngles, int theta) {
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
	
	public int GetLowerDirAngle(PhotoPOI.POI poi, PhotoPOI.Photo photo, int theta) {
		double dir = PhotoPOIDirectionAngle(poi.tloc, photo);
		int dirAngle = (int) (180 * (dir / Math.PI));
		int lowerDirAngle = dirAngle-theta;
		if(lowerDirAngle < 0) lowerDirAngle+=360;
		if(lowerDirAngle >= 360) lowerDirAngle %= 360;
		return lowerDirAngle;
	}
	
	private double PhotoPOIDirectionAngle(PhotoPOI.Point loc, PhotoPOI.Photo photo) {
		double radian = Math.atan2((loc.y-photo.ploc.y), (loc.x-photo.ploc.x));
		if(radian < 0) radian += 2 * Math.PI;
		return radian;
	}
	
	public static GenerateScenario GetInstance(int actualPoi,
			int hiddenPoi,
			int theta,
			int totalPhoto,
			int maxX,
			int maxY,
			int focus,
			int angle,
			int dataSource) {
		if(generateScenario == null) {
			generateScenario = new GenerateScenario(actualPoi,
					hiddenPoi,
					theta,
					maxX,
					maxY,
					focus);
		}
		if(totalPhotoMap.containsKey(totalPhoto) == false) {
			totalPhotoMap.put(totalPhoto, generateScenario.PositionPhotos(totalPhoto, focus, angle, dataSource));
		}
		if(generateScenario.photoList.size() != totalPhoto) {
			generateScenario.photoList = totalPhotoMap.get(totalPhoto);
		}
		
		return generateScenario;
	}
	
	public static void Reset() {
		generateScenario = null;
	}
}
