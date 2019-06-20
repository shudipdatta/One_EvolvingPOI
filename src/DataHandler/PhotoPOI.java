package DataHandler;

import java.util.ArrayList;

public class PhotoPOI {
	
	public class Point {
		public int x;
		public int y;
		public Point (int x, int y) {
			this.x = x;
			this.y = y;
		}
	}
	
	public class POI {
		public Point tloc;
		ArrayList<Integer> photoIdList;
		public boolean expired;
		public long ts;
		
		public POI(int x, int y) {
			this.tloc = new Point(x, y);
			this.photoIdList = new ArrayList<Integer>();
			this.expired = false;
			this.ts = System.currentTimeMillis();
		}
	}
	

	public class Photo {
		public int id;
		public int focus;
		public int range;
		int angle;
		public Point ploc;	
		public int direction;
		
		public Point pointDir;
		public Point pointFocus;
		public Point point1;
		public Point point2;
		public int tid;
		public int hid;
		
		public Photo(int id, int x, int y, int direction, int poiIndex, int focus, int angle) {
			this.ploc = new Point(x, y);
			this.id = id;
			this.direction=direction;
			this.focus=focus;
			this.range=focus*2;
			this.angle=angle;
			this.tid=poiIndex;

			CalculateEndPoints();
		}
		
		private void CalculateEndPoints() {
			double dirRadian;
			int x, y;
			
			dirRadian = Math.toRadians(this.direction);
			x = (int) (this.range * Math.cos(dirRadian)) + this.ploc.x;
			y = (int) (this.range * Math.sin(dirRadian)) + this.ploc.y;
			this.pointDir = new Point(x,y);
			
			dirRadian = Math.toRadians(this.direction);
			x = (int) (this.focus * Math.cos(dirRadian)) + this.ploc.x;
			y = (int) (this.focus * Math.sin(dirRadian)) + this.ploc.y;
			this.pointFocus = new Point(x,y);
			
			dirRadian = Math.toRadians(this.direction + this.angle/2);
			x = (int) (this.range * Math.cos(dirRadian)) + this.ploc.x;
			y = (int) (this.range * Math.sin(dirRadian)) + this.ploc.y;
			this.point1 = new Point(x,y);
			
			dirRadian = Math.toRadians(this.direction - this.angle/2);
			x = (int) (this.range * Math.cos(dirRadian)) + this.ploc.x;
			y = (int) (this.range * Math.sin(dirRadian)) + this.ploc.y;
			this.point2 = new Point(x,y);
		}
	}
}
