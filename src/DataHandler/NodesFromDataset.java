package DataHandler;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class NodesFromDataset {
	
	private static NodesFromDataset nodesFromDataset;
	public int[] sortedNodes;
	public int[] nodePhotoNum;
	public ArrayList<Integer>[] nodePhotoList;
	
	@SuppressWarnings("unchecked")
	private NodesFromDataset(int dataset, int photoNum, int nodeNum) throws IOException {
		sortedNodes = new int[nodeNum];
		nodePhotoNum = new int[nodeNum];
		nodePhotoList = new ArrayList[nodeNum];
		for(int n=0; n<nodeNum; n++) {
			nodePhotoList[n] = new ArrayList<Integer>();
		}

		double avgPhotoNum = (photoNum*1.0)/nodeNum;
		int maxPhotoNum = (int) Math.floor(avgPhotoNum + avgPhotoNum/2);
		int minPhotoNum = (int) Math.floor(avgPhotoNum - avgPhotoNum/2);
		int nodesPerGroup = (int) Math.round((nodeNum*1.0)/(maxPhotoNum - minPhotoNum));
		
		switch(dataset) {
		case Constant.Synthetic:
			{
				int n = 0;
				for(int g=maxPhotoNum; g>minPhotoNum; g--) {
					for(int i=0; i<nodesPerGroup; i++) {
						nodePhotoNum[n++] = g;
					}
				}
			}
			break;
		case Constant.Infocom:
			{
				FileInputStream fstream = new FileInputStream("../src/reports/infocom_data/trace/sorted_nodes.txt");
				BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
	
				String strLine;
				int counter = 0;
				while ((strLine = br.readLine()) != null)   {
					sortedNodes[counter++] = Integer.parseInt(strLine);
				}
				br.close();
				
				int n = 0;
				for(int g=maxPhotoNum; g>minPhotoNum; g--) {
					for(int i=0; i<nodesPerGroup && n<nodeNum; i++) {
						nodePhotoNum[sortedNodes[n++]] = g;
					}
				}
			}
			break;
		case Constant.Geolife:
			{
				FileInputStream fstream = new FileInputStream("../src/reports/geolife_data/trace/sorted_nodes.txt");
				BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
	
				String strLine;
				int counter = 0;
				while ((strLine = br.readLine()) != null)   {
					sortedNodes[counter++] = Integer.parseInt(strLine);
				}
				br.close();
				
				int n = 0;
				for(int g=maxPhotoNum; g>minPhotoNum; g--) {
					for(int i=0; i<nodesPerGroup && n<nodeNum; i++) {
						nodePhotoNum[sortedNodes[n++]] = g;
					}
				}
			}
			break;
		case Constant.ASTURIES:
		{
			FileInputStream fstream = new FileInputStream("../src/reports/asturies_data/trace/sorted_nodes.txt");
			BufferedReader br = new BufferedReader(new InputStreamReader(fstream));

			String strLine;
			int counter = 0;
			while ((strLine = br.readLine()) != null)   {
				sortedNodes[counter++] = Integer.parseInt(strLine);
			}
			br.close();
			
			int n = 0;
			for(int g=maxPhotoNum; g>minPhotoNum; g--) {
				for(int i=0; i<nodesPerGroup && n<nodeNum; i++) {
					nodePhotoNum[sortedNodes[n++]] = g;
				}
			}
		}
		break;
		}
		
		//assign specific photo index for different node
		int n = -1;
		for(int i=0; i<photoNum; i++) {
			for(int j=0; j<nodeNum; j++) {
				n++;
				if(nodePhotoList[(n+i)%nodeNum].size() < nodePhotoNum[(n+i)%nodeNum]) {
					nodePhotoList[(n+i)%nodeNum].add(i);
					break;
				}
			}
		}
	}
	
	public static NodesFromDataset GetInstance(int dataset, int photoNum, int nodeNum) {
		if(nodesFromDataset == null) {
			try {
				nodesFromDataset = new NodesFromDataset(dataset, photoNum, nodeNum);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return nodesFromDataset;
	}
	
	public static void Reset() {
		nodesFromDataset = null;
	}
}
