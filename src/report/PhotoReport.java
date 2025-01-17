package report;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;

import DataHandler.GenerateScenario;
import DataHandler.Metadata;
import DataHandler.PhotoPOI;
import DataHandler.Metadata.Photo;

class CvgAndRed {
	public double avgPhotoNum;
	public int aboveAvgPhoto;
	public int belowAvgPhoto;
	
	public double avgRedundancy;
	public int aboveAvgRedCount;
	public int belowAvgRedCount;
	public int aboveAvgRedPhoto;
	public int belowAvgRedPhoto;
	public int networkRedundancy;
	
	public double avgCoverage;
	public int aboveAvgCvgCount;
	public int belowAvgCvgCount;
	public int aboveAvgCvgPhoto;
	public int belowAvgCvgPhoto;
	public int networkCoverage;
	
	public CvgAndRed( double avgPhotoNum, int aboveAvgPhoto, int belowAvgPhoto,
			double avgCoverage, int aboveAvgCvgCount, int belowAvgCvgCount, 
			int aboveAvgCvgPhoto, int belowAvgCvgPhoto, int networkCoverage,
			double avgRedundancy, int aboveAvgRedCount, int belowAvgRedCount, 
			int aboveAvgRedPhoto, int belowAvgRedPhoto, int networkRedundancy) {
		
		this.avgPhotoNum = avgPhotoNum;
		this.aboveAvgPhoto = aboveAvgPhoto;
		this.belowAvgPhoto = belowAvgPhoto;
		
		this.avgCoverage = avgCoverage;
		this.aboveAvgCvgCount = aboveAvgCvgCount;
		this.belowAvgCvgCount = belowAvgCvgCount;
		this.aboveAvgCvgPhoto = aboveAvgCvgPhoto;
		this.belowAvgCvgPhoto = belowAvgCvgPhoto;
		this.networkCoverage = networkCoverage;
		
		this.avgRedundancy = avgRedundancy;
		this.aboveAvgRedCount = aboveAvgRedCount;
		this.belowAvgRedCount = belowAvgRedCount;
		this.aboveAvgRedPhoto = aboveAvgRedPhoto;
		this.belowAvgRedPhoto = belowAvgRedPhoto;
		this.networkRedundancy = networkRedundancy;
	}
}

public class PhotoReport extends Report {
	
	DecimalFormat df;
	
	//server variables
	public static GenerateScenario scenario;
	public static Metadata metadata;
	public static ArrayList<Metadata.POI> poiList;
	public static ArrayList<Metadata.POI> actualPoiList;
	public static ArrayList<Metadata.POI> hiddenPoiList;
	public static ArrayList<Metadata.Photo> photoList;
	
	//reporting variables
	public static HashMap<Integer, Integer> initialCoverageValue;
	public static HashMap<Integer, Integer> initialRedundantCoverageValue;
	public static HashMap<Double, CvgAndRed> cvgAndRedMap;
	
	public static HashMap<Integer, Integer> coverageValue;
	public static ArrayList<Integer> totalKCoverageValue;
	public static HashMap<Integer, Double> halfCoverageTime;
	public static HashMap<Integer, Integer> redundantCoverageValue;
	public static HashMap<Integer, Integer> numberOfPoint;
	public static HashMap<Integer, Double> standardDeviation;
	
	public static int TransferredPhotoCounter;
	public static HashMap<Double, Integer> TransferredPhotoTime;
	public static int PhotoAtDestinationCounter;
	
	//undefined photo cluster variable	
	public static ArrayList<Metadata.UndefCluster> undefCluster;
	
	//overhead variables
	public static long additionalPoiTime;
	public static long algorithmTime;
	public static long serverTime;
	public static int TransferredMetadataCounter;
	

	public PhotoReport() {
		init();
	}

	@Override
	protected void init() {
		super.init();
		
		//init report variables
		coverageValue = new HashMap<Integer, Integer>();
		totalKCoverageValue = new ArrayList<Integer>();
		halfCoverageTime = new HashMap<Integer, Double>();
		redundantCoverageValue = new HashMap<Integer, Integer>();
		numberOfPoint = new HashMap<Integer, Integer>();
		standardDeviation = new HashMap<Integer, Double>();
		
		TransferredPhotoCounter = 0;
		TransferredPhotoTime = new HashMap<Double, Integer>();
		PhotoAtDestinationCounter = 0;
		
		initialCoverageValue = new HashMap<Integer, Integer>();
		initialRedundantCoverageValue = new HashMap<Integer, Integer>();	
		cvgAndRedMap = new HashMap<Double, CvgAndRed>();
		df = new DecimalFormat(".##"); 
		
		undefCluster = new ArrayList<Metadata.UndefCluster>();
		
		additionalPoiTime = 0;
		algorithmTime = 0;
		serverTime = 0;
		TransferredMetadataCounter = 0;
	}
	
	public static void SetServerVariables(GenerateScenario scenario) {
		PhotoReport.scenario = scenario;
		metadata = new Metadata(scenario.theta);
		
		//init server variables
		poiList = new ArrayList<Metadata.POI>();
		actualPoiList = new ArrayList<Metadata.POI>();
		hiddenPoiList = new ArrayList<Metadata.POI>();
		photoList = new ArrayList<Metadata.Photo>();
		
		int poiCounter = 0;
		for(PhotoPOI.POI poi: scenario.actualPoiList) {
			poiList.add(metadata.new POI(++poiCounter, metadata.new Point(poi.tloc.x, poi.tloc.y), poi.expired, poi.ts));
			actualPoiList.add(metadata.new POI(poiCounter, metadata.new Point(poi.tloc.x, poi.tloc.y), poi.expired, poi.ts));
		}
		for(PhotoPOI.POI poi: scenario.hiddenPoiList) {
			hiddenPoiList.add(metadata.new POI(++poiCounter, metadata.new Point(poi.tloc.x, poi.tloc.y), poi.expired, poi.ts));
		}
	}
	
	public void CalculatePOICoverage() {
		//fill up actual poi list
		for(int i=0; i<actualPoiList.size(); i++) {
			actualPoiList.get(i).cvgTotal = poiList.get(i).cvgTotal;
			actualPoiList.get(i).cvgDetail.addAll(poiList.get(i).cvgDetail);
		}
		//fill up hidden poi list
		for(Metadata.POI poi: hiddenPoiList) {
			Collection<Photo> undefinedPhotoList = metadata.FilteredUndefinedPhoto(poi.tid, photoList);
			for(Metadata.Photo photo: undefinedPhotoList) {
				if(photo.exist == true) {
					int lowerDirAngle = metadata.GetLowerDirAngle(poi, photo);
					if(poi.cvgDetail.contains(lowerDirAngle) == false) {
						poi.cvgDetail.add(lowerDirAngle);
					}
				}
			}
			poi.cvgTotal = metadata.TotalCoverage(poi.cvgDetail);			
		}
		//copy the coveragevalue
		int maxKList = 0;
		HashMap<Integer, int[]> kCoverageValue = new HashMap<Integer, int[]>();
		for(int i=0; i<actualPoiList.size()+hiddenPoiList.size(); i++) {
			Metadata.POI poi;
			if(i<actualPoiList.size()) poi = actualPoiList.get(i);
			else poi = hiddenPoiList.get(i-actualPoiList.size());
			
			coverageValue.put(i+1, poi.cvgTotal);
			redundantCoverageValue.put(i+1, metadata.RedundantCoverage(poi.cvgDetail));
			
			//now find k coverage, the k coverage in kcvgList starts from 0 coverage, 1 coverage, 2 coverage, .....
			int[] cvgDegree = new int[360];
			for(int c=0; c<cvgDegree.length; c++) {
				cvgDegree[c] = 0;
			}
			for (int degree: poi.cvgDetail) {
				for (int d=degree; d<degree+2*metadata.getTheta(); d++) {
					cvgDegree[d % cvgDegree.length]++;
				}
			}
			int maxK = 0;
			for(int c=0; c<cvgDegree.length; c++) {
				if (cvgDegree[c] > maxK)
					maxK = cvgDegree[c];
			}
			if (maxK > maxKList) maxKList = maxK; //finding the max length of the array for each poi
			int[] kcvgList = new int[maxK+1];
			for(int k=0; k<kcvgList.length; k++) {
				kcvgList[k] = 0;
			}
			for(int c=0; c<cvgDegree.length; c++) {
				kcvgList[cvgDegree[c]]++;
			}
			kCoverageValue.put(i+1, kcvgList);
		}
		int[] sumKcvgList = new int[maxKList+1];
		for(int k=0; k<sumKcvgList.length; k++) {
			sumKcvgList[k] = 0;
		}
		for(int[] kcvgList: kCoverageValue.values()) {
			for (int k=0; k<kcvgList.length; k++) {
				sumKcvgList[k] += kcvgList[k];
			}
		}
		for(int k=0; k<sumKcvgList.length; k++) {
			totalKCoverageValue.add(sumKcvgList[k]);
		}
	}
	
	public static void CalculateRedundancy(double simTime, double avgPhotoNum, int aboveAvgPhoto, int belowAvgPhoto, 
			double avgCoverage, int aboveAvgCvgCount, int belowAvgCvgCount, 
			int aboveAvgCvgPhoto, int belowAvgCvgPhoto, int networkCoverage,
			double avgRedundancy, int aboveAvgRedCount, int belowAvgRedCount, 
			int aboveAvgRedPhoto, int belowAvgRedPhoto, int networkRedundancy) {
		
		CvgAndRed cvgAndRed = new CvgAndRed(avgPhotoNum, aboveAvgPhoto, belowAvgPhoto,
				avgCoverage, aboveAvgCvgCount, belowAvgCvgCount, aboveAvgCvgPhoto, belowAvgCvgPhoto, networkCoverage,
				avgRedundancy, aboveAvgRedCount, belowAvgRedCount, aboveAvgRedPhoto, belowAvgRedPhoto, networkRedundancy);		
		PhotoReport.cvgAndRedMap.put(simTime, cvgAndRed);
	}
	
	public void CalculatePointNum() {
		//fill how many number of points a hidden poi includes
		for(Metadata.POI apoi: poiList) {
			if(apoi.tid < 0){
				Metadata.Photo leadPhoto = metadata.FindPhotoByID(-apoi.tid, photoList);
				Metadata.POI hpoi = metadata.FindPoiByID(leadPhoto.hid, hiddenPoiList);
				hpoi.numOfPoint++;
				//hpoi.photoIDList.addAll(apoi.photoIDList);
				/*
				int sumX = 0, sumY = 0;
				for(int pid: apoi.photoIDList) {
					Metadata.Photo photo = metadata.FindPhotoByID(pid, photoList);
					sumX += photo.pointFocus.x;
					sumY += photo.pointFocus.y;
				}
				int size = apoi.photoIDList.size();
				if(size > 0) hpoi.centroid.add(metadata.new DPoint((double)sumX/size, (double)sumY/size));
				*/
				hpoi.centroid.add(metadata.new DPoint((double)leadPhoto.pointFocus.x, (double)leadPhoto.pointFocus.y));
			}
		}
		int actualPoiSize = actualPoiList.size();
		for(int i=0; i<actualPoiSize; i++) {
			numberOfPoint.put(i+1, 1);
			standardDeviation.put(i+1, 0.0);
		}
		for(int i=0; i<hiddenPoiList.size(); i++) {
			Metadata.POI hpoi = hiddenPoiList.get(i);
			numberOfPoint.put(i+1+actualPoiSize, hpoi.numOfPoint);
			
			//calculating Standard deviation
			//step 1: calculate sum of squared distance
			double ssd = 0;
			for(Metadata.DPoint dpoint: hpoi.centroid) {
				ssd += Math.pow(dpoint.x-hpoi.tloc.x, 2) + Math.pow(dpoint.y-hpoi.tloc.y, 2);
			}
			//step 2: get the avg or variance
			int size = hpoi.centroid.size();
			if(size > 0) ssd/=size;
			//step 3: square root the variance to get standard deviation
			standardDeviation.put(i+1+actualPoiSize, Math.sqrt(ssd)/10); //divide by 10 because we assumed 1 meter = 10 pixel
		}
	}
	
	public void CalculateHalfTime() {
		for(int i=0; i<actualPoiList.size()+hiddenPoiList.size(); i++) {
			if(halfCoverageTime.containsKey(i+1) == false)
				halfCoverageTime.put(i+1, 0.0);
		}
	}
	
	@Override
	public void done() {

		scenario.CalculatePOICoverage(initialCoverageValue, initialRedundantCoverageValue);
		CalculatePOICoverage();
		CalculatePointNum();
		CalculateHalfTime();
		
		write("Message stats for scenario " + getScenarioName() + 
				"\nsim_time: " + format(getSimTime()));
		
		String statsText = "\n";
		
		statsText += "Photo List Size: " + photoList.size() + "\n";
		
		statsText += "coverageValue-----------------\t";
		for(Integer v: coverageValue.values()) statsText += v + "\t";
		statsText += "\n";
		
		statsText += "redundantCoverageValue--------\t";
		for(Integer v: redundantCoverageValue.values()) statsText += v + "\t";
		statsText += "\n";
		
		statsText += "halfCoverageTime--------------\t";
		for(Double v: halfCoverageTime.values()) statsText += df.format(v) + "\t";
		statsText += "\n";
		
		statsText += "numberOfPoint-----------------\t";
		for(Integer v: numberOfPoint.values()) statsText += v + "\t";
		statsText += "\n";
		
		statsText += "standardDeviation-------------\t";
		for(Double v: standardDeviation.values()) statsText += df.format(v) + "\t";
		statsText += "\n";
		
		
		statsText += "initialCoverageValue----------\t";
		for(Integer v: initialCoverageValue.values()) statsText += v + "\t";
		statsText += "\n";
		
		statsText += "initialRedundantCoverageValue-\t";
		for(Integer v: initialRedundantCoverageValue.values()) statsText += v + "\t";
		statsText += "\n";
		
		statsText += "additionalPoiTime-------------\t";
		statsText += additionalPoiTime/1000.00 + "\n"; //microsecond
		
		statsText += "algorithmTime-----------------\t";
		statsText += (algorithmTime-additionalPoiTime)/1000.00 + "\n"; //microsecond 
			
		statsText += "serverTime--------------------\t";
		statsText += serverTime/1000.00 + "\n"; //microsecond
		
		statsText += "Total Transferred Metadata----\t";
		statsText += TransferredMetadataCounter + "\n"; 
		
		
		//create distribution data
		statsText += "\n";
		statsText += "Photo List Size: " + photoList.size() + "\n";
		int poiListSize = actualPoiList.size()+hiddenPoiList.size();
		
//		//create average coverage value
//		int sum = 0;
//		for(Integer v: coverageValue.values()) sum+=v;
//		int avgCoverage = sum / (poiListSize);
//		statsText += "Average Coverage--------------\t" + avgCoverage +"\n";
//		
//		//create redundant average coverage value
//		sum = 0;
//		for(Integer v: redundantCoverageValue.values()) sum+=v;
//		int avgRedundant = sum / (poiListSize);
//		statsText += "Average Redundancy------------\t" + avgRedundant +"\n";
		
		//create percentage coverage
		int cvgSum = 0, initCvgSum = 0;
		for(Integer v: coverageValue.values()) cvgSum+=v;
		for(Integer v: initialCoverageValue.values()) initCvgSum+=v;
		statsText += "Gained Coverage %-------------\t" + cvgSum/(1.0*initCvgSum) +"\n";
		
		//create percentage redundancy
		int redSum = 0, initRedSum = 0;
		for(Integer v: redundantCoverageValue.values()) redSum+=v;
		for(Integer v: initialRedundantCoverageValue.values()) initRedSum+=v;
		statsText += "Gained Redundancy %-----------\t" + redSum/(1.0*initRedSum) +"\n";
		
		//create percentage k coverage
		statsText += "Gained K Coverage %-----------\t";
		for(int kcvg: totalKCoverageValue) {
			statsText += kcvg/(1.0*initCvgSum) + "\t";
		}
		statsText += "\n";
		
		//how many clusters so far
		statsText += "Total Cluster Created---------\t" + undefCluster.size() +"\n";
		
		//bandwidth consumed total
		statsText += "Total Transferred Photo-------\t" + TransferredPhotoCounter + "\n";
		
		//photos at destination
		statsText += "Total Photo at Destination----\t" + PhotoAtDestinationCounter + "\n";
		
		//create coverage value distribution
		int angelInterval = 30;
		int angelTotal = 360;
		int[] cvgArray = new int[angelTotal/angelInterval];
		statsText += "Coverage Group----------------\t";
		for(int i=0; i<cvgArray.length; i++) {
			cvgArray[i]=0;
			statsText += i*angelInterval+1 + "-" +  (i+1)*angelInterval + "\t";
		}
		statsText += "\n";
		for(Integer v: coverageValue.values()) {
			if(v==0) cvgArray[v]++;
			else cvgArray[(int)Math.ceil(v/(double)angelInterval) - 1]++;
		}
		statsText += "Coverage Distribution---------\t";
		for(int i=0; i<cvgArray.length; i++) {
			statsText += cvgArray[i] + "\t";
		}
		statsText += "\n";
		
		//create coverage ratio
		double ratioInterval = 0.1;
		int[] ratioArray = new int[11]; 
		statsText += "Ratio Group-------------------\t";
		for(int i=0; i<ratioArray.length; i++) {
			ratioArray[i]=0;
			statsText += df.format(i*ratioInterval) + "\t";
		}
		statsText += "\n";
		int[] initArr = new int[poiListSize];
		int[] gotArr = new int[poiListSize];
		int index=0;
		for(Integer v: initialCoverageValue.values()) initArr[index++] = v;
		index=0;
		for(Integer v: coverageValue.values()) gotArr[index++] = v;
		for(int i=0; i<initArr.length; i++) {
			if(initArr[i] != 0) {
				double ratio = gotArr[i]/(double)initArr[i];
				ratio = Math.ceil(ratio*10);
				ratioArray[(int)ratio]++;
			}
		}
		statsText += "Coverage Ratio----------------\t";
		for(int i=0; i<ratioArray.length; i++) {
			statsText += ratioArray[i] + "\t";
		}
		statsText += "\n";
		
		//create redundant ratio
		for(int i=0; i<ratioArray.length; i++) ratioArray[i]=0;
		index = 0;
		for(Integer v: initialRedundantCoverageValue.values()) initArr[index++] = v;
		index=0;
		for(Integer v: redundantCoverageValue.values()) gotArr[index++] = v;
		for(int i=0; i<initArr.length; i++) {
			if(initArr[i] != 0) {
				double ratio = gotArr[i]/(double)initArr[i];
				ratio = Math.ceil(ratio*10);
				ratioArray[(int)ratio]++;
			}
		}
		statsText += "Redundant Ratio---------------\t";
		for(int i=0; i<ratioArray.length; i++) {
			statsText += ratioArray[i] + "\t";
		}
		statsText += "\n";
		
		//create number of point distribution		
		statsText += "NumberOfPoint Group-----------\t";
		int[] numPoint = new int[Collections.max(numberOfPoint.values())+1];
		for(int i=0; i<numPoint.length; i++) {
			numPoint[i] = 0;
			statsText += i + "\t";
		}
		statsText += "\n";
		for(int i=0; i<hiddenPoiList.size(); i++) numPoint[hiddenPoiList.get(i).numOfPoint]++;
		statsText += "NumOfPoint Distribution-------\t";
		for(int i=0; i<numPoint.length; i++) {
			statsText += numPoint[i] + "\t";
		}
		statsText += "\n";
		
		//create standard deviation distribution
		int[] sdArray = new int[(int) Math.ceil(Collections.max(standardDeviation.values()))];
		statsText += "SD Group----------------------\t";
		for(int i=0; i<sdArray.length; i++) {
			sdArray[i] = 0;
			statsText += i + "-" + (i+1) + "\t";
		}
		statsText += "\n";
		for(Double v: standardDeviation.values()) {
			//if(v!=0) sdArray[(int) Math.ceil(v) - 1] ++;
			sdArray[(int) Math.floor(v)] ++;
		}
		statsText += "SD Distribution---------------\t";
		for(int i=0; i<sdArray.length; i++) {
			statsText += sdArray[i] + "\t";
		}
		statsText += "\n";
		
		//create half coverage value time
		int simTotal = (int) getSimTime();
		int simInterval = simTotal/10; //1000;
		int[] halfCvgArray = new int[simTotal/simInterval+1];
		statsText += "Half Coverage Group-----------\t";
		for(int i=0; i<halfCvgArray.length; i++) {
			halfCvgArray[i]=0;
			statsText += i*simInterval + "-" +  (i+1)*simInterval + "\t";
		}
		statsText += "\n";
		for(Double v: halfCoverageTime.values()) {
			if(v!=0) halfCvgArray[(int)Math.ceil(v/simInterval) - 1]++;
		}
		statsText += "Half Cvg Time Distribution----\t";
		for(int i=0; i<halfCvgArray.length; i++) {
			statsText += halfCvgArray[i] + "\t";
		}
		statsText += "\n";
		
		//create bandwidth consumption distribution
		int[] transPhotoArray = new int[simTotal/simInterval+1];
		statsText += "Transferred Photo Group-------\t";
		for(int i=0; i<transPhotoArray.length; i++) {
			transPhotoArray[i]=0;
			statsText += i*simInterval + "-" +  (i+1)*simInterval + "\t";
		}
		statsText += "\n";
		for (Entry<Double, Integer> entry : TransferredPhotoTime.entrySet()) { 
			double simTime = entry.getKey();
			transPhotoArray[(int)Math.ceil(simTime/simInterval) - 1] += entry.getValue();
		}
		statsText += "Photo Time Distribution-------\t";
		for(int i=0; i<transPhotoArray.length; i++) {
			statsText += transPhotoArray[i] + "\t";
		}
		statsText += "\n\n";
		
		//create coverage and redundancy distribution
		String simTimeString =		"Cvg&Red Distribution----------\t";
		
		String photoString1 = "Average PhotoNum--------------\t";
		String photoString2 = "Nodes Above Average-----------\t";
		String photoString3 = "Nodes Below Average-----------\t"; 
		
		String cvgString1 =	"Average Coverage--------------\t";
		String cvgString2 =	"Nodes Above Average-----------\t";
		String cvgString3 =	"Nodes Below Average-----------\t";
		String cvgString4 = "Photos Above Average----------\t";
		String cvgString5 = "Photos Below Average----------\t";
		String cvgString6 =	"Network Coverage--------------\t";
		
		String redString1 =	"Average Redundancy------------\t";
		String redString2 =	"Nodes Above Average-----------\t";
		String redString3 =	"Nodes Below Average-----------\t";
		String redString4 = "Photos Above Average----------\t";
		String redString5 = "Photos Below Average----------\t";
		String redString6 =	"Network Redundancy------------\t";
		
		for (Entry<Double, CvgAndRed> entry : cvgAndRedMap.entrySet()) { 
			double simTime = entry.getKey();
			CvgAndRed cvgAndRed = entry.getValue();
			simTimeString += simTime + "\t";
			
			photoString1 += cvgAndRed.avgPhotoNum + "\t";
			photoString2 += cvgAndRed.aboveAvgPhoto + "\t";
			photoString3 += cvgAndRed.belowAvgPhoto + "\t";
			
			cvgString1 += cvgAndRed.avgCoverage + "\t";
			cvgString2 += cvgAndRed.aboveAvgCvgCount + "\t";
			cvgString3 += cvgAndRed.belowAvgCvgCount + "\t";
			cvgString4 += cvgAndRed.aboveAvgCvgPhoto + "\t";
			cvgString5 += cvgAndRed.belowAvgCvgPhoto + "\t";
			cvgString6 += cvgAndRed.networkCoverage + "\t";
			
			redString1 += cvgAndRed.avgRedundancy + "\t";
			redString2 += cvgAndRed.aboveAvgRedCount + "\t";
			redString3 += cvgAndRed.belowAvgRedCount + "\t";
			redString4 += cvgAndRed.aboveAvgRedPhoto + "\t";
			redString5 += cvgAndRed.belowAvgRedPhoto + "\t";
			redString6 += cvgAndRed.networkRedundancy + "\t";
		}
		statsText += simTimeString + "\n";
		statsText += photoString1 + "\n";
		statsText += photoString2 + "\n";
		statsText += photoString3 + "\n";
		statsText += "\n";
		statsText += cvgString1 + "\n";
		statsText += cvgString2 + "\n";
		statsText += cvgString3 + "\n";
		statsText += cvgString4 + "\n";
		statsText += cvgString5 + "\n";
		statsText += cvgString6 + "\n";
		statsText += "\n";
		statsText += redString1 + "\n";
		statsText += redString2 + "\n";
		statsText += redString3 + "\n";
		statsText += redString4 + "\n";
		statsText += redString5 + "\n";
		statsText += redString6 + "\n";
		
				
		write(statsText);
		super.done();
	}
}
