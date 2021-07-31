package cellTracking;

import java.util.ArrayList;
import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import inra.ijpb.binary.BinaryImages;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.Strel.Shape;
import point.Point;
import inra.ijpb.morphology.Morphology.Operation;

public class ImageComponentsAnalysis {
	private ImageProcessor imageComponents; // image with components
	private ImageProcessor imageIntensity; // image for calculating average intensity
	private int w, h;

	private int nComponents; // number of components
	private ArrayList<ComponentProperties> properties; // component properties

	/*
	 * initialize class from binary image ip with components and intensity image
	 * "intensityImage". Components are re-labeled from 0 to nComp
	 */
	public ImageComponentsAnalysis(ImageProcessor ip, ImageProcessor intensityImage, boolean useLabelling) {
		w = ip.getWidth();
		h = ip.getHeight();
		// imageComponents = ip.duplicate();
		if (useLabelling) {
			imageComponents = BinaryImages.componentsLabeling(ip, 4, 16);
			nComponents = (int) imageComponents.getMax() - (int) imageComponents.getMin() + 1;
		} else {
			imageComponents = ip.duplicate();
			nComponents = imageComponentsCount(imageComponents); // else count them in this function
		}
		// imageComponents = ImageFunctions.operationMorph(imageComponents,
		// Operation.CLOSING, Strel.Shape.DISK, 1);
		// ImagePlus t = new ImagePlus("after closing", imageComponents.duplicate());
		// t.show();
		imageIntensity = intensityImage;

		properties = new ArrayList<ComponentProperties>(nComponents);
		for (int i = 0; i < nComponents; i++)
			properties.add(new ComponentProperties());
		fillBasicProperties();
		fillCircularity();		
	}
	
	

	/*
	 * gets the number of components in labeled image. Components can have different
	 * intensities
	 */
	public int imageComponentsCount(ImageProcessor ip) {
		ArrayList<Integer> foundIntensities = new ArrayList<Integer>(5);
		int count = 0, v;
		for (int i = 0; i < ip.getPixelCount(); i++) {
			v = ip.get(i);
			if (!foundIntensities.contains(v)) {
				foundIntensities.add(v);
				count++;
			}
		}
		return count;
	}

	public int getWidth() {
		return w;
	}

	public int getHeight() {
		return h;
	}

	// getters by index
	public int getComponentArea(int index) {
		return properties.get(index).area;
	}

	public float getComponentPerimeter(int index) {
		return properties.get(index).perimeter;
	}

	public float getComponentCircularity(int index) {
		return properties.get(index).circularity;
	}

	public int getComponentDisplayIntensity(int index) {
		return properties.get(index).displayIntensity;
	}

	public int getComponentX0(int index) {
		return properties.get(index).xmin;
	}

	public int getComponentY0(int index) {
		return properties.get(index).ymin;
	}

	public int getComponentX1(int index) {
		return properties.get(index).xmax;
	}

	public int getComponentY1(int index) {
		return properties.get(index).ymax;
	}

	public Point getComponentMassCenter(int index) {
		return properties.get(index).massCenter;
	}

	public int getComponentChildCount(int index) {
		return properties.get(index).childCount;
	}

	public void setComponentChildCount(int index, int count) {
		properties.get(index).childCount = count;
	}

	public void incComponentChildCount(int index) {
		properties.get(index).childCount += 1;
	}

	public boolean getComponentHasParent(int index) {
		return properties.get(index).hasParent;
	}

	public void setComponentHasParent(int index) {
		properties.get(index).hasParent = true;
	}

	public State getComponentState(int index) {
		return properties.get(index).state;
	}
	
	public boolean isComponentMitosis(int index) {
		return properties.get(index).state == State.MITOSIS;
	}

	public void setComponentState(int index, State state) {
		properties.get(index).state = state;
	}

	public float getComponentAvrgIntensity(int index) {
		return properties.get(index).avrgIntensity;
	}

	public float getComponentAvrgIntensityByIntensity(int intensity) {
		int index = findComponentIndexByDisplayIntensity(intensity);
		return properties.get(index).avrgIntensity;
	}

	public ImageProcessor getIntensityImage() {
		return imageIntensity;
	}

	public ImageProcessor getInvertedIntensityImage() {
		return ImageProcessorCalculator.invertedImage(imageIntensity);
	}

	public int getComponentsCount() {
		return nComponents;
	}
	
	public ImageProcessor getSinglePixelComponents() {
		ImageProcessor res = new ShortProcessor(imageComponents.getWidth(), imageComponents.getHeight());
		for (int i=0; i<properties.size(); ++i) {
			Point p = getComponentMassCenter(i);
			int x = (int)p.getX();
			int y = (int)p.getY();
			res.setf(x, y, 1);
		}
		ImageFunctions.LabelMarker(res);
		return res;
	}

	/*
	 * calculates bounding box corners, perimeter, area, average intensity, mass
	 * center for components and fills the "properties" array
	 */
	public void fillBasicProperties() {
		// presetting values to find containing rectangle
		for (int i = 0; i < properties.size(); i++) {
			properties.get(i).setDefaultValues(w, h);
		}

		int v; // component intensity in the image
		int newIndex = 0; // new component index
		int index; // current index
		int pix4c, pixDc;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				v = imageComponents.get(x, y);
				// components can be indexed in whatever range (but no negatives), dont rely on
				// v=0 to be background. And make background a component
				// if (v > 0) {
				index = findComponentIndexByDisplayIntensity(v);
				if (index == -1)
					index = newIndex++;
				// System.out.println(index);
				properties.get(index).displayIntensity = v;
				properties.get(index).area++;
				if (imageIntensity != null)
					properties.get(index).avrgIntensity += imageIntensity.getf(x, y);
				properties.get(index).massCenter.addIn(new Point(x, y));

				if (isBorderPixel4C(imageComponents, x, y)) { // calculate perimeter
					pix4c = numberOfNeighbours4C(imageComponents, x, y);
					pixDc = numberOfNeighboursDiagC(imageComponents, x, y);
					properties
							.get(index).perimeter += (float) ((pix4c * 1.0f + pixDc * Math.sqrt(2)) / (pix4c + pixDc));
				}
				// if (hasDiagonalBorderNeighbours(image, x, y)) properties.get(v).perimeter +=
				// Math.sqrt(2);
				// else properties.get(v).perimeter++;

				if (properties.get(index).xmin > x)
					properties.get(index).xmin = x;
				if (properties.get(index).xmax < x)
					properties.get(index).xmax = x;
				if (properties.get(index).ymin > y)
					properties.get(index).ymin = y;
				if (properties.get(index).ymax < y)
					properties.get(index).ymax = y;
				if (x==0 || y==0 || x==w-1 || y== h-1) {
					properties.get(index).isOnBorder = true;
				}

				// }
			}
		}

		for (int i = 0; i < properties.size(); i++) { // calculate average intensity, mass center (area is number of
														// pixels)
			properties.get(i).avrgIntensity /= properties.get(i).area;
			properties.get(i).massCenter.divideByConstIn(properties.get(i).area);
		}
	}

	/*
	 * adds a component to property list, fills its properties and draws it on the
	 * image. Mask must be the same size as components image return index of the new
	 * component or -1
	 */
	public int addComponent(ImageProcessor componentMask, int intensityInMask, State state) {
		if (componentMask.getWidth() != w || componentMask.getHeight() != h) {
			System.out.println("component's mask size ( " + componentMask.getWidth() + " x " + componentMask.getHeight()
					+ "was different from components images size (" + w + " x " + " h ");
			return -1;
		}

		int resultIntensity = getNewIntensity();
		ComponentProperties newProperties = new ComponentProperties();
		newProperties.setDefaultValues(componentMask.getWidth(), componentMask.getHeight());
		newProperties.displayIntensity = resultIntensity;
		newProperties.state = state;

		int pix4c, pixDc;
		for (int y = 0; y < componentMask.getHeight(); y++)
			for (int x = 0; x < componentMask.getWidth(); x++) {
				// only look at pixels that are in mask and do not correspond to existing
				// component
				if (componentMask.get(x, y) == intensityInMask && imageComponents.get(x, y) == 0) {
					newProperties.area++;
					if (imageIntensity != null)
						newProperties.avrgIntensity += imageIntensity.getf(x, y);
					newProperties.massCenter.addIn(new Point(x, y));

					if (isBorderPixel4C(imageComponents, x, y)) { // calculate perimeter
						pix4c = numberOfNeighbours4C(imageComponents, x, y);
						pixDc = numberOfNeighboursDiagC(imageComponents, x, y);
						newProperties.perimeter += (float) ((pix4c * 1.0f + pixDc * Math.sqrt(2)) / (pix4c + pixDc));
					}
					imageComponents.set(x, y, resultIntensity);
					if (newProperties.xmin > x)
						newProperties.xmin = x;
					if (newProperties.xmax < x)
						newProperties.xmax = x;
					if (newProperties.ymin > y)
						newProperties.ymin = y;
					if (newProperties.ymax < y)
						newProperties.ymax = y;
				}
			}
		// System.out.println("added component area is " + newProperties.area);
		if (newProperties.area != 0) {
			newProperties.avrgIntensity /= newProperties.area;
			newProperties.massCenter.divideByConstIn(newProperties.area);
			newProperties.calcCircularity();
			properties.add(newProperties);
			return properties.size() - 1;
		} else {
			//showComponentsImage();
		}
		return -1;
	}

	/* returns intensity that is not in the properties (currently max + 1) */
	private int getNewIntensity() {
		int intensity, maxIntensity = -1;
		for (int i = 0; i < properties.size(); i++) {
			intensity = getComponentDisplayIntensity(i);
			if (maxIntensity < intensity)
				maxIntensity = intensity;
		}
		return maxIntensity + 1;
	}

	/*
	 * Checks whether components with given indexes are childs. True if penal score
	 * between them is less than threshold
	 */
	public boolean checkIfChildComponents(int index1, int index2, Point parentCenterPoint, float parentAvrgIntensity,
			double penalThreshold) {
		double penal = calculateChildPenalScore(index1, index2, parentCenterPoint, parentAvrgIntensity);
//		System.out.println(" penal score is " + penal);
		return penal < penalThreshold;
	}

	/* calculates penal score (the less, the better) */
	public double calculateChildPenalScore(int index1, int index2, Point parentCenterPoint, float parentAvrgIntensity) {
		// take into account avrg intensity, size, distance from parent center
		double c_int, c_size, c_distance, c_diff_intensity; // coefficient
		c_int = 1; // for intensity of the child blobs
		c_size = 0.1; // for size of child blobs
		c_distance = 0.8; // for difference in distance between child blobs and parent
		c_diff_intensity = 0; // for difference in intensity between child blobs and parent

		double int1, int2, size1, size2, dist1, dist2, diffInt1, diffInt2;
		int1 = getComponentAvrgIntensity(index1);
		int2 = getComponentAvrgIntensity(index2);

		size1 = getComponentArea(index1);
		size2 = getComponentArea(index2);

		dist1 = Point.dist(getComponentMassCenter(index1), parentCenterPoint);
		dist2 = Point.dist(getComponentMassCenter(index2), parentCenterPoint);

		diffInt1 = parentAvrgIntensity - int1;
		diffInt2 = parentAvrgIntensity - int2;

		double int_score = normVal(int1, int2);
		double size_score = normVal(size1, size2);
		double dist_score = normVal(dist1, dist2);
		double diff_int_score = normVal(diffInt1, diffInt2);

		double coef_sum = c_int + c_size + c_distance + c_diff_intensity;

		return (c_int * int_score + c_size * size_score + c_distance * dist_score + c_diff_intensity * diff_int_score)
				/ coef_sum;
	}

	/* gets difference between value in [0,1] */
	public static double normVal(double v1, double v2) {
		double v = Math.abs(v1 - v2) / Math.sqrt(v1 * v1 + v2 * v2);
		return v;
	}

	public ImageProcessor getImageComponents() {
		return imageComponents;
	}

	/*
	 * Combine components (properties considered), whose markers appear in the
	 * dilated (radius d) mask of previously detected component. "components" is the
	 * image after water segmentation on markers
	 */
	public void mergeComponentsByMarkers(ImageProcessor markers, ImageComponentsAnalysis prevComponents, int d) {
		if (prevComponents == null)
			return;
		ImageProcessor mask;
		int index, intens;
		ArrayList<Integer> list = new ArrayList<Integer>(3);
		for (int i = 0; i < prevComponents.getComponentsCount(); i++) {
			if (prevComponents.getComponentArea(i) < 2000) { // dont do this for big background regions
				System.out.println("component " + i + ", area " + prevComponents.getComponentArea(i));
				mask = prevComponents.getDilatedComponentImage(i, d);
				list = getComponentListByMask(markers, mask, imageComponents, prevComponents.getComponentX0(i),
						prevComponents.getComponentY0(i));
				// first, remove big components from the list
				for (int k = 0; k < list.size(); k++) {
					index = list.get(k);
					System.out.print(index + " ");
					if (this.properties.get(index).area > 1000)
						list.remove(k);
				}

				// !!! problem !!! The same marker can be found in several masks. Remove it if
				// it is the only marker in some component (~)
				// It's better to assign intensity labels to markers in one component and just
				// assign it

				// here change all components intensity to that of the first marker. Properties
				// list should also change
				if (list.size() > 0) {
					// intens = properties.get(list.get(0)).intensity;
					intens = prevComponents.getComponentDisplayIntensity(i);
					for (int k = 1; k < list.size(); k++) {
						changeComponentDisplayIntensityByIndex(list.get(k), intens);
						fillBasicProperties();
						fillCircularity();
					}
				}
			}
		}
		System.out.println("___");
	}

	/*
	 * return list of component indexes, which markers appear in the mask located
	 * from x0,y0
	 */
	private ArrayList<Integer> getComponentListByMask(ImageProcessor markers, ImageProcessor mask,
			ImageProcessor components, int x0, int y0) {
		ArrayList<Integer> result = new ArrayList<Integer>(3);
		int wb = mask.getWidth(), hb = mask.getHeight();
		int v;
		// mb later add something that prevents getting the same marker into the list
		// for
		// different masks...not here tho
		for (int y = y0; y < y0 + hb; y++)
			for (int x = x0; x < x0 + wb; x++) {
				if (x > 0 && x < markers.getWidth() && y > 0 && y < markers.getHeight() && markers.get(x, y) != 0
						&& mask.get(x - x0, y - y0) > 0) {
					v = findComponentIndexByDisplayIntensity(components.get(x, y));
					result.add(v);
				}
			}
		return result;
	}

	/* filter components by area and circularity */
	public void filterComponents(int minArea, int maxArea, float minCirc, float maxCirc, float minAvrgIntensity,
			float maxAvrgIntensity, boolean discardWhiteBlobs, boolean removeBorderComponents) {
		ArrayList<Integer> removeList = new ArrayList<Integer>(20); // what components to filter
		int area;
		float circ, avrgInt;

		// fill list with indexes of component to be removed
		for (int i = 0; i < properties.size(); i++) {
			area = properties.get(i).area;
			circ = properties.get(i).circularity;
			avrgInt = properties.get(i).avrgIntensity;

			if (area < minArea || area > maxArea) {
				removeList.add(i);
				continue;
			}
			if (circ < minCirc || circ > maxCirc) {
				removeList.add(i);
				continue;
			}
			if (avrgInt < minAvrgIntensity || avrgInt > maxAvrgIntensity) {
				removeList.add(i);
				continue;
			}
			if (getComponentState(i) == State.WHITE_BLOB_COMPONENT) {
				removeList.add(i);
				continue;
			}
			// remove border components if they are small enough
			if (properties.get(i).isOnBorder && removeBorderComponents) {
				removeList.add(i);
				continue;
			}
		}

		// delete components
		for (int i = removeList.size() - 1; i >= 0; i--) {
			removeComponent(imageComponents, properties.get(removeList.get(i)).displayIntensity);
		}
	}

	public ImageProcessor getFilteredComponentsIp(int minArea, int maxArea, float minCirc, float maxCirc,
			float minAvrgIntensity, float maxAvrgIntensity, boolean discardWhiteBlobs, boolean removeBorderComponents) {
		filterComponents(minArea, maxArea, minCirc, maxCirc, minAvrgIntensity, maxAvrgIntensity, discardWhiteBlobs, removeBorderComponents);
		return imageComponents;
	}

	public void setComponentsStateByMarks(ImageProcessor marks, State state) {
		int index;
		for (int y = 0; y < marks.getHeight(); y++)
			for (int x = 0; x < marks.getWidth(); x++) {
				if (marks.get(x, y) > 0) { // set component state to mitosis
					index = getComponentIndexByPosition(x, y);
					if (index != -1) {
						setComponentState(index, state);
						// System.out.println("component " +index + " marked as WHITE_BLOB_COMPONENT");
						// seems working
					}
				}
			}
	}

	public void discardWhiteBlobComponents() {
		for (int i = 0; i < getComponentsCount(); i++) {
			if (getComponentState(i) == State.WHITE_BLOB_COMPONENT) {
				removeComponentByIndex(i);
			}
		}
	}

	public int getComponentIndexByPosition(int x, int y) {
		int intensity = imageComponents.get(x, y);
		return findComponentIndexByDisplayIntensity(intensity);
	}

	/*
	 * return box-image containing the component[nComp], dilated by disk with radius
	 * "d"
	 */
	public ImageProcessor getDilatedComponentImage(int nComp, int d) {
		return getMorphedComponentImage(Operation.DILATION, Strel.Shape.DISK, nComp, d, 1);
	}

	public ImageProcessor getMorphedComponentImage(Operation op, Shape shape, int nComp, int d, int intensity) {
		int x0 = properties.get(nComp).xmin;
		int x1 = properties.get(nComp).xmax;
		int y0 = properties.get(nComp).ymin;
		int y1 = properties.get(nComp).ymax;
		ImageProcessor result = new FloatProcessor(x1 - x0 + 1 + 2 * d, y1 - y0 + 1 + 2 * d);

		// copy component into the new image
		float v;
		final int compInt = properties.get(nComp).displayIntensity;
		for (int x = d; x < result.getWidth() - d; x++)
			for (int y = d; y < result.getHeight() - d; y++) {
				v = imageComponents.getf(x0 + x - d, y0 + y - d);
				if (v == compInt)
					result.setf(x, y, intensity);
			}
		result = ImageFunctions.operationMorph(result, op, shape, d);
		return result;
	}

	/* draws morphed component in ip. Only draws on background (i.e. intensity=0) */
	public void drawMorphedComponentOnImage(ImageProcessor ip, Operation op, Shape shape, int nComp, int d) {
		int x0 = properties.get(nComp).xmin;
		int y0 = properties.get(nComp).ymin;
		int intensity = properties.get(nComp).displayIntensity;

		ImageProcessor compImage = getMorphedComponentImage(op, shape, nComp, d, intensity);
		for (int x = 0; x < compImage.getWidth(); x++)
			for (int y = 0; y < compImage.getHeight(); y++) {
				if (x + x0 >= 0 && y + y0 >= 0 && x + x0 < ip.getWidth() && y + y0 < ip.getHeight())
					if (ip.get(x, y) == 0)
						ip.set(x + x0, y + y0, compImage.get(x, y));
			}
	}
	
	/* change all component contours with morphing to improve
	 * now dilation 1 with closing 1+ */
	public void improveComponentContours() {		

		//ImageProcessor compImage = getMorphedComponentImage(op, shape, nComp, d);
		for (int i=0; i<getComponentsCount(); i++) {
			drawMorphedComponentOnImage(imageComponents, Operation.DILATION, Strel.Shape.DISK, i, 1);
		}
		
		for (int i=0; i<getComponentsCount(); i++) {
			drawMorphedComponentOnImage(imageComponents, Operation.CLOSING, Strel.Shape.DISK, i, 2);
		}
	}
	
	public void improveMitosisEndComponentContours() {		

		//ImageProcessor compImage = getMorphedComponentImage(op, shape, nComp, d);
		for (int i=0; i<getComponentsCount(); i++) {
			if (properties.get(i).state != State.MITOSIS_END)
				continue;
			drawMorphedComponentOnImage(imageComponents, Operation.DILATION, Strel.Shape.DISK, i, 2);
		}
	}

	/*
	 * change markers image, so that all markers inside one mask are merged (into
	 * the geometrical center), for one component (x0,y0) is the top-left point of
	 * the box, where mask should be in markers image
	 */
	public static void mergeMarkersByComponentMask(ImageProcessor markers, ImageProcessor mask, int x0, int y0) {
		int wb = mask.getWidth(), hb = mask.getHeight();
		int count = 0;
		float newx = 0, newy = 0;
		for (int y = y0; y < y0 + hb; y++)
			for (int x = x0; x < x0 + wb; x++) {
				if (x > 0 && x < markers.getWidth() && y > 0 && y < markers.getHeight() && markers.get(x, y) != 0
						&& mask.get(x - x0, y - y0) > 0) {
					newx += x;
					newy += y;
					count++;
					markers.setf(x, y, 0);
				}
			}
		markers.setf((int) (newx / count), (int) (newy / count), 255);
	}

	/*
	 * combines components in ip that belong to the same component in the compImage.
	 * compImage nd ip must be images after the BinaryImages.componentsLabelling
	 * operation (i.e. not float, components are labelled from 0)
	 */
	public static ImageProcessor combineComponentsInMask(ImageProcessor ip, ImageProcessor compImage) {
		if (compImage == null) // for stack processing the first image
			return ip;
		ImageProcessor result = ip.duplicate();
		int[] table = new int[(int) ip.getMax() + 1]; // table for component labels
		for (int i = 0; i < table.length; i++)
			table[i] = -1;
		int v1, v2;
		// first iteration through the image - fill table of pairs (initial component
		// number, new component number)
		for (int i = 0; i < ip.getPixelCount(); i++) {
			v1 = ip.get(i);
			v2 = compImage.get(i);
			if (table[v1] == -1)
				table[v1] = v2; // set filling value for not initialized component labels
			else if (table[v1] != v2) // if component was already filled but with another value, then discard it by
										// setting value to zero
				table[v1] = 0;
		}

		// the second iteration - change values of components according to the table
		for (int i = 0; i < ip.getPixelCount(); i++) {
			v1 = ip.get(i);
			v2 = table[v1];
			result.set(i, v2);
		}
		return result;
	}

	/*
	 * combines components in ip that belong to the same component in the compImage.
	 * Different from previous one in a way, that compImage is erosed image and
	 * components are labelled if at least some of them is in the mask (every
	 * pixel). compImage nd ip must be images after the
	 * BinaryImages.componentsLabelling operation (i.e. not float, components are
	 * labelled from 0)
	 */
	public static ImageProcessor combineComponentsInMaskFromInside(ImageProcessor ip, ImageProcessor compImage) {
		if (compImage == null) // for stack processing the first image
			return ip;
		ImageProcessor result = ip.duplicate();
		int[] table = new int[(int) ip.getMax() + 1]; // table for component labels
		for (int i = 0; i < table.length; i++)
			table[i] = -1;
		int v1, v2;
		// first iteration through the image - fill table of pairs (initial component
		// number, new component number)
		for (int i = 0; i < ip.getPixelCount(); i++) {
			v1 = ip.get(i);
			v2 = compImage.get(i);
			if (v2 != 0)
				table[v1] = v2;
		}

		// the second iteration - change values of components according to the table
		for (int i = 0; i < ip.getPixelCount(); i++) {
			v1 = ip.get(i);
			v2 = table[v1];
			result.set(i, v2);
		}
		return result;
	}

	/*
	 * merges components if they have similar avrg intensity. Component with label
	 * '0' should be the canny edge detection thingy
	 */
	public void mergeComponents() {
		int upLabel, downLabel, leftLabel, rightLabel;
		int l;
		ImageProcessor originalComponents = imageComponents.duplicate();
		for (int i = 0; i < properties.size(); i++)
			// System.out.println(properties.get(i).intensity);
			// pass through the image and look for boundary pixels (label '0'). Then look in
			// 4C-neighbourhood if components should be merged */
			for (int y = 1; y < h - 1; y++) {
				for (int x = 1; x < w - 1; x++) {
					l = imageComponents.get(x, y);
					if (l == 0) {
						upLabel = originalComponents.get(x, y - 1);
						downLabel = originalComponents.get(x, y + 1);
						// if up and down has close entensity and different labels, change the down
						// label to that of top
						if (upLabel != 0 && downLabel != 0 && upLabel != downLabel)
							if (Math.abs(getComponentAvrgIntensityByIntensity(upLabel)
									- getComponentAvrgIntensityByIntensity(downLabel)) < 5) {
								changeComponentIntensity(downLabel, upLabel);
							}
						// same for left/right
						// !!! intensity might have changed in the previous step
						leftLabel = originalComponents.get(x - 1, y);
						rightLabel = originalComponents.get(x + 1, y);
						if (leftLabel != 0 && rightLabel != 0 && leftLabel != rightLabel)
							if (Math.abs(getComponentAvrgIntensityByIntensity(leftLabel)
									- getComponentAvrgIntensityByIntensity(rightLabel)) < 5) {
								changeComponentIntensity(rightLabel, leftLabel);
							}
					}
				}
			}
		imageComponents = ImageFunctions.operationMorph(imageComponents, Operation.CLOSING, Strel.Shape.DISK, 1); // to
																													// remove
																													// '0'
																													// label
																													// lines
	}

	/* for merging components; also deletes old intensity component from the list */
	private void changeComponentDisplayIntensityByIndex(int compIndex, int newIntensity) {
		if (getComponentDisplayIntensity(compIndex) == newIntensity) // dont do anything if it's the same component
			return;
		int x0, x1, y0, y1;
		x0 = properties.get(compIndex).xmin;
		x1 = properties.get(compIndex).xmax;
		y0 = properties.get(compIndex).ymin;
		y1 = properties.get(compIndex).ymax;
		int intensity = properties.get(compIndex).displayIntensity;
		for (int y = y0; y <= y1; y++)
			for (int x = x0; x <= x1; x++)
				if (imageComponents.get(x, y) == intensity)
					imageComponents.set(x, y, newIntensity);

		properties.remove(compIndex);
		nComponents--;
	}

	private void changeComponentIntensity(int intensity, int newIntensity) {
		int x0, x1, y0, y1, nComp;
		nComp = findComponentIndexByDisplayIntensity(intensity);
		x0 = properties.get(nComp).xmin;
		x1 = properties.get(nComp).xmax;
		y0 = properties.get(nComp).ymin;
		y1 = properties.get(nComp).ymax;

		for (int y = y0; y <= y1; y++)
			for (int x = x0; x <= x1; x++)
				if (imageComponents.get(x, y) == intensity)
					imageComponents.set(x, y, newIntensity);

		properties.get(nComp).displayIntensity = newIntensity;
		// here we should recalculate all properties...or not here
	}
	
	public Roi getComponentAsRoiByIntensity(int intensity) {
		int index = findComponentIndexByDisplayIntensity(intensity);
		return getComponentAsRoi(index);
	}
	
	public Roi getComponentAsRoi(int index) {
		Roi roi = null;
		Wand w = new Wand(imageComponents);
		int currIntens = getComponentDisplayIntensity(index);	
		w.autoOutline(properties.get(index).xmin, properties.get(index).ymin, currIntens, currIntens);
		if (w.npoints > 0) { // we have a roi from the wand...
			roi = new PolygonRoi(w.xpoints, w.ypoints, w.npoints, Roi.TRACED_ROI);
		}
		return roi;
	}

	/*
	 * Adds rois to roi manager with slice label, with each roi corresponding to one
	 * component in this.imageComponents. Rois are ordered by intensity
	 */
	public void addRoisToManager(RoiManager manager, ImagePlus img, int slice, String prefix) {
		// RoiManager res = new RoiManager();
		Roi roi;
		Wand w = new Wand(imageComponents);
		int currIntens = 1;
		int count = 0, index;
		boolean isWhiteBlob;
		String roiName;

		// the first slice is 1 (not 0)
		img.setSliceWithoutUpdate(slice);

		while (count < properties.size()) {
			index = findComponentIndexByDisplayIntensity(currIntens);
			if (index == -1) { // component with such intensity not found, increase intensity
				currIntens++;
				continue;
			}
			w.autoOutline(properties.get(index).xmin, properties.get(index).ymin, currIntens, currIntens);
			if (w.npoints > 0) { // we have a roi from the wand...

				isWhiteBlob = properties.get(index).state == State.WHITE_BLOB_COMPONENT;
				prefix = "";
				if (properties.get(index).state == State.MITOSIS_START)
					prefix="START_";
				if (properties.get(index).state == State.MITOSIS_END)
					prefix="END_";

				roi = new PolygonRoi(w.xpoints, w.ypoints, w.npoints, Roi.TRACED_ROI);
				roiName = prefix + String.format("%04d", slice);
				roiName += "-" + index;
				if (isWhiteBlob)
					roiName += "_white_blob";
				roi.setName(roiName);
				roi.setPosition(slice);

				manager.addRoi(roi);
			}
			currIntens++;
			count++; // component added
		}
		img.setSliceWithoutUpdate(slice);
	}

	public ImageProcessor getAvrgIntensityImage() {
		ImageProcessor result = imageComponents.duplicate();
		int v;
		for (int i = 0; i < result.getPixelCount(); i++) {
			v = findComponentIndexByDisplayIntensity(result.get(i));
			// System.out.println(result.get(i));
			if (v != -1)
				result.setf(i, properties.get(v).avrgIntensity);
		}
		return result;
	}

	public ImageProcessor componentsBasinsImage(int erosionRadius) {
		int w = imageComponents.getWidth();
		int h = imageComponents.getHeight();
		ImageProcessor result = new ByteProcessor(w, h);
		// for (int i = 0; i < getComponentsCount(); i++) {
		// drawMorphedComponentOmImage(result, Operation.EROSION, Strel.Shape.DISK, i,
		// erosionRadius);
		// }
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				if (isBorderPixel4C(imageComponents, x, y))
					result.set(x, y, 0);
				else
					result.set(x, y, 255);
			}
		return result;
	}

	/*
	 * removes component with given intensity from properties, and deletes it from
	 * image (by setting its intensity to zero)
	 */
	public void removeComponent(ImageProcessor image, int intensity) {
		int x0, x1, y0, y1, nComp;
		nComp = findComponentIndexByDisplayIntensity(intensity);
		x0 = properties.get(nComp).xmin;
		x1 = properties.get(nComp).xmax;
		y0 = properties.get(nComp).ymin;
		y1 = properties.get(nComp).ymax;

		for (int y = y0; y <= y1; y++)
			for (int x = x0; x <= x1; x++)
				if (image.get(x, y) == intensity)
					image.set(x, y, 0);

		properties.remove(nComp); // remove from the list
		nComponents--; // decrease number
	}

	public void removeComponentByIndex(int index) {
		int x0, x1, y0, y1;
		int intensity = getComponentDisplayIntensity(index);
		x0 = properties.get(index).xmin;
		x1 = properties.get(index).xmax;
		y0 = properties.get(index).ymin;
		y1 = properties.get(index).ymax;

		for (int y = y0; y <= y1; y++)
			for (int x = x0; x <= x1; x++)
				if (imageComponents.get(x, y) == intensity)
					imageComponents.set(x, y, 0);

		properties.remove(index); // remove from the list
		nComponents--; // decrease number
	}

	/* return index of component with given intensity. Returns -1 if not found */
	private int findComponentIndexByDisplayIntensity(int displayIntensity) {
		for (int i = 0; i < properties.size(); i++)
			if (properties.get(i).displayIntensity == displayIntensity)
				return i;
		return -1;
	}

	/* calculates and fills the circularity property */
	private void fillCircularity() {
		for (int i = 0; i < nComponents; i++)
			properties.get(i).calcCircularity();
	}

	/* number of neighbour border pixels, 4-connectivity */
	private int numberOfNeighbours4C(ImageProcessor ip, int x, int y) {
		int result = 0;
		if (isBorderPixel4C(ip, x - 1, y))
			result++;
		if (isBorderPixel4C(ip, x + 1, y))
			result++;
		if (isBorderPixel4C(ip, x, y - 1))
			result++;
		if (isBorderPixel4C(ip, x, y + 1))
			result++;
		return result;
	}

	/* number of neighbour border pixels, diagonal-connectivity */
	private int numberOfNeighboursDiagC(ImageProcessor ip, int x, int y) {
		int result = 0;
		if (isBorderPixel4C(ip, x - 1, y - 1))
			result++;
		if (isBorderPixel4C(ip, x - 1, y + 1))
			result++;
		if (isBorderPixel4C(ip, x + 1, y - 1))
			result++;
		if (isBorderPixel4C(ip, x + 1, y + 1))
			result++;
		return result;
	}

	/*
	 * returns true if any of the neighbouring pixels (4-connectivity) has other
	 * intensity value (not 0)
	 */
	private boolean isBorderPixel4C(ImageProcessor ip, int x, int y) {
		if (x < 0 || x > ip.getWidth() - 1 || y < 0 || y > ip.getHeight() - 1)
			return false; // pixels out of the image
		if (x == 0 || y == 0 || x == ip.getWidth() - 1 || y == ip.getHeight() - 1)
			return true; // pixels on the image border
		int v_xy = ip.get(x, y);
		if (x > 0 && ip.get(x - 1, y) != v_xy)
			return true;
		if (x < ip.getWidth() && ip.get(x + 1, y) != v_xy)
			return true;
		if (y > 0 && ip.get(x, y - 1) != v_xy)
			return true;
		if (y < ip.getHeight() && ip.get(x, y + 1) != v_xy)
			return true;
		return false;
	}

	public void showComponentsImage() {
		ImagePlus image = new ImagePlus("components image", imageComponents);
		image.show();
	}

	@Override
	public String toString() {
		String res = new String();
		for (int i = 0; i < properties.size(); i++)
			res += properties.get(i).toString() + '\n';
		return res;
	}
}
