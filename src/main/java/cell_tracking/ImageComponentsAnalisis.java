package cell_tracking;

import ij.process.ImageProcessor;
import inra.ijpb.binary.BinaryImages;

public class ImageComponentsAnalisis {
	private ImageProcessor image;	//image with components
	private int w,h;
	
	private int nComponents;	//number of components
	private ComponentProperties[] properties;	//component properties
	
	/* initialize class from image with components */
	public ImageComponentsAnalisis(ImageProcessor ip) {
		w = ip.getWidth();
		h = ip.getHeight();
		image = BinaryImages.componentsLabeling(ip, 4, 16);
		nComponents = (int)image.getMax();
		System.out.println(nComponents);
		properties = new ComponentProperties[nComponents];
		for (int i=0; i < nComponents; i++) properties[i] = new ComponentProperties();
		fillBasicProperties();
		fillCircularity();
	}
	
	public int getComponentArea(int compNumber) {
		return properties[compNumber].area;
	}
	
	public int getComponentPerimeter(int compNumber) {
		return properties[compNumber].perimeter;
	}
	
	public float getComponentCircularity(int compNumber) {
		return properties[compNumber].circularity;
	}
	
	/* calculates bounding box corners, perimeter, area for components and fills the "properties" array */
	public void fillBasicProperties() {
		// presetting values to find containing rectangle
		for (int i=0; i < nComponents; i++) {
			properties[i].xmin = w - 1;
			properties[i].xmax = 0;
			properties[i].ymin = h - 1;
			properties[i].ymax = 0;
		}
		int v; // component label (intensity - 1)
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				v = image.get(x, y);
				if (v > 0) {
					v = v - 1;
					properties[v].intensity = properties[v].intensity == 0 ? v+1 : properties[v].intensity;					
					properties[v].area++;
					if (isBorderPixel4C(image, x, y)) 
						properties[v].perimeter++;
					
					if (properties[v].xmin > x) properties[v].xmin = x;
					if (properties[v].xmax < x) properties[v].xmin = x;
					if (properties[v].ymin > y) properties[v].xmin = y;
					if (properties[v].ymax < y) properties[v].xmin = y;
				}
			}
		}
	}
	
	/* calculates and fills the circularity property */
	private void fillCircularity() {
		for (int i=0; i<nComponents; i++)
			properties[i].calcCircularity();
	}
	
	/* returns true if any of the neighbouring pixels (4-connectivity) has value 0 */
	private boolean isBorderPixel4C(ImageProcessor ip, int x, int y) {
		if (x > 0 && ip.get(x - 1, y) == 0) return true;
		if (x < ip.getWidth() && ip.get(x+1,y) == 0) return true;
		if (y > 0 && ip.get(x, y-1) == 0) return true;
		if (y < ip.getHeight() && ip.get(x, y+1) == 0) return true;
		return false;
	}
	 
	@Override
	public String toString() {
		String res = new String();
		for (int i=0; i < nComponents; i++) res+=properties[i].toString() + '\n';
		return res;
	}
}
