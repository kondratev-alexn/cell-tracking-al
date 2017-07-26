package cell_tracking;

import java.util.ArrayList;
import java.util.Vector;

import ij.process.ImageProcessor;
import inra.ijpb.binary.BinaryImages;

public class ImageComponentsAnalysis {
	private ImageProcessor image;	//image with components
	private int w,h;
	
	private int nComponents;	//number of components
	private ArrayList<ComponentProperties> properties;	//component properties
	
	/* initialize class from image with components */
	public ImageComponentsAnalysis(ImageProcessor ip) {
		w = ip.getWidth();
		h = ip.getHeight();
		image = BinaryImages.componentsLabeling(ip, 4, 16);
		nComponents = (int)image.getMax();
		System.out.println(nComponents);
		properties = new ArrayList<ComponentProperties>(nComponents);
		for (int i=0; i<nComponents; i++)
			properties.add(new ComponentProperties());
		fillBasicProperties();
		fillCircularity();
	}
	
	public int getComponentArea(int compNumber) {
		return properties.get(compNumber).area;
	}
	
	public float getComponentPerimeter(int compNumber) {
		return properties.get(compNumber).perimeter;
	}
	
	public float getComponentCircularity(int compNumber) {
		return properties.get(compNumber).circularity;
	}
	
	/* calculates bounding box corners, perimeter, area for components and fills the "properties" array */
	public void fillBasicProperties() {
		// presetting values to find containing rectangle
		for (int i=0; i < properties.size(); i++) {
			properties.get(i).xmin = w - 1;
			properties.get(i).xmax = 0;
			properties.get(i).ymin = h - 1;
			properties.get(i).ymax = 0;
		}
		
		int v; // component label (intensity - 1)
		int pix4c, pixDc;
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				v = image.get(x, y);
				if (v > 0) {
					v = v - 1;
					properties.get(v).intensity = properties.get(v).intensity == 0 ? v+1 : properties.get(v).intensity;					
					properties.get(v).area++;
					
					if (isBorderPixel4C(image, x, y)) { //calculate perimeter
						pix4c = numberOfNeighbours4C(image, x, y);
						pixDc = numberOfNeighboursDiagC(image, x, y);
						properties.get(v).perimeter += (float) ((pix4c*1.0f + pixDc * Math.sqrt(2)) / (pix4c + pixDc));
					}
						//if (hasDiagonalBorderNeighbours(image, x, y)) properties.get(v).perimeter += Math.sqrt(2);
						//else properties.get(v).perimeter++;
					
					if (properties.get(v).xmin > x) properties.get(v).xmin = x;
					if (properties.get(v).xmax < x) properties.get(v).xmax = x;
					if (properties.get(v).ymin > y) properties.get(v).ymin = y;
					if (properties.get(v).ymax < y) properties.get(v).ymax = y;
				}
			}
		}
	}
	
	/* filter components by area and circularity */
	private void filterComponents(int minArea, int maxArea, float minCirc, float maxCirc) {
		ArrayList<Integer> list = new ArrayList<Integer>(10); // what components to filter
		int area;
		float circ;
		//fill list 
		for (int i=0; i<properties.size(); i++) {
			area = properties.get(i).area;
			circ = properties.get(i).circularity;
			if (area < minArea || area > maxArea) {
				list.add(i);
				continue;
			}
			if (circ < minCirc || circ > maxCirc) {
				list.add(i);
				continue;
			}
		}
		
		// delete components
		for (int i=list.size() - 1; i>=0 ; i--) {
			System.out.println(list.get(i)+1);
			System.out.println(properties.get(list.get(i)).intensity);
			removeComponent(image, properties.get(list.get(i)).intensity);
		}
	}
	
	public ImageProcessor getFilteredComponentsIp(int minArea, int maxArea, float minCirc, float maxCirc) {
		filterComponents(minArea, maxArea, minCirc, maxCirc);
		return image;
	}
	
	/* removes component with given intensity from properties, and deletes it from image */
	private void removeComponent(ImageProcessor image, int intensity) {
		int x0,x1,y0,y1, nComp;
		nComp = findComponentIndexByIntencity(intensity);
		x0 = properties.get(nComp).xmin;
		x1 = properties.get(nComp).xmax;
		y0 = properties.get(nComp).ymin;
		y1 = properties.get(nComp).ymax;

		for (int y=y0; y <= y1; y++) 
			for (int x=x0; x <= x1; x++) 
				if (image.get(x, y) == intensity)
					image.set(x, y, 0);

		properties.remove(nComp); //remove from the list
	}
	
	/* return index of component with given intensity. Returns -1 if not found */
	private int findComponentIndexByIntencity(int intensity) {
		for (int i=0; i<properties.size(); i++)
			if (properties.get(i).intensity == intensity) return i;
		return -1;
	}
	
	/* calculates and fills the circularity property */
	private void fillCircularity() {
		for (int i=0; i<nComponents; i++)
			properties.get(i).calcCircularity();
	}
	
	/* number of neighbour border pixels, 4-connectivity*/
	private int numberOfNeighbours4C(ImageProcessor ip, int x, int y) {
		int result = 0;
		if (isBorderPixel4C(ip, x-1, y)) result++;
		if (isBorderPixel4C(ip, x+1, y)) result++;
		if (isBorderPixel4C(ip, x, y-1)) result++;
		if (isBorderPixel4C(ip, x, y+1)) result++;
		return result;
	}
	
	/* number of neighbour border pixels, diagonal-connectivity */
	private int numberOfNeighboursDiagC(ImageProcessor ip, int x, int y) {
		int result = 0;
		if (isBorderPixel4C(ip, x-1, y-1)) result++;
		if (isBorderPixel4C(ip, x-1, y+1)) result++;
		if (isBorderPixel4C(ip, x+1, y-1)) result++;
		if (isBorderPixel4C(ip, x+1, y+1)) result++;
		return result;
	}
	
	/* returns true if any of the neighbouring pixels (4-connectivity) has value 0 */
	private boolean isBorderPixel4C(ImageProcessor ip, int x, int y) {
		if (x < 0 || x > ip.getWidth() - 1 || y < 0 || y > ip.getHeight() - 1) return false;
		if (x == 0 || y==0 || x == ip.getWidth()-1 || y == ip.getHeight()-1) return true; //pixels on the image border
		if (x > 0 && ip.get(x - 1, y) == 0) return true;
		if (x < ip.getWidth() && ip.get(x+1,y) == 0) return true;
		if (y > 0 && ip.get(x, y-1) == 0) return true;
		if (y < ip.getHeight() && ip.get(x, y+1) == 0) return true;
		return false;
	}
	
	/* return true if (x,y) pixel has diagonally connected border neighbouring 'on' pixels */
	private boolean hasDiagonalBorderNeighbours(ImageProcessor ip, int x, int y) {
		return isBorderPixel4C(ip,x-1,y-1) || isBorderPixel4C(ip,x-1,y+1) || isBorderPixel4C(ip,x+1,y-1) || isBorderPixel4C(ip,x+1,y+1);
	}
	
	@Override
	public String toString() {
		String res = new String();
		for (int i=0; i < properties.size(); i++) res+=properties.get(i).toString() + '\n';
		return res;
	}
}
