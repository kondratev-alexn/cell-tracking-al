package cellTracking;

import java.util.ArrayList;

import graph.Arc;
import graph.Graph;
import graph.Node;
import ij.process.ImageProcessor;
import ij.ImagePlus;
import ij.ImageStack;
import point.Point;

public class NearestNeighbourTracking {
	private Graph cellGraph;

	private int currSlice;
	private int slicesCount;

	/*
	 * List of components classes, containing image with labels and information
	 * about them. Should be formed during processing and then processed.
	 */
	private ArrayList<ImageComponentsAnalysis> componentsList;

	public Graph getGraph() {
		return cellGraph;
	}

	public NearestNeighbourTracking(int slicesCount) {
		this.slicesCount = slicesCount;
		componentsList = new ArrayList<ImageComponentsAnalysis>(slicesCount);
		cellGraph = new Graph();
	}

	public void addComponentsAnalysis(ImageComponentsAnalysis comps) {
		componentsList.add(comps);
	}

	/*
	 * finds nearest components in comp1-comp2 not further than 'radius' pixels. (t1
	 * and t2 refers to time points of comp1 and comp2 respectively) this should
	 * refer to the i -> i+1 step
	 */
	public void findNearestComponents(ImageComponentsAnalysis comp1, int t1, ImageComponentsAnalysis comp2, int t2,
			double radius) {
		Point m1;
		int closestIndex;
		Node v1, v2;
		for (int i = 0; i < comp1.getComponentsCount(); i++) {
			if (comp1.getComponentChildCount(i) > 0) {
				continue;
			}
			m1 = comp1.getComponentMassCenter(i);
			closestIndex = findClosestPointIndex(m1, comp2, radius);
			if (closestIndex != -1) { // closest component found, remove from list, add to graph
				if (comp2.getComponentHasParent(closestIndex)) {
					continue;
				}
				v1 = new Node(t1, i);
				v2 = new Node(t2, closestIndex);
				cellGraph.addArcFromToAddable(v1, v2);
				comp2.setComponentHasParent(closestIndex);
				comp1.incComponentChildCount(i);
			} else {
				// closest component for current component in comp1 was not found - may be
				// remove it from detection (it may not be correct detection)
			}
		}
	}

	/* this is for back tracking (mitosys should be tracked with this steps) */
	public void findNearestComponentsBackStep(ImageComponentsAnalysis comp2, int t2, ImageComponentsAnalysis comp1,
			int t1, double radius) {
		Point m2;
		int closestIndex;
		Node v1, v2;
		for (int i = 0; i < comp2.getComponentsCount(); i++) {
			if (comp2.getComponentHasParent(i)) {
				continue;
			}
			m2 = comp2.getComponentMassCenter(i); // component without parent
			closestIndex = findClosestPointIndex(m2, comp1, radius);
			if (closestIndex != -1) { // closest component found, add to graph
				if (comp2.getComponentChildCount(i) == 0 && comp1.getComponentChildCount(closestIndex) > 0) {
					continue; // if closest "parent" has children. then add this only if it has children
				}
				v1 = new Node(t1, closestIndex);
				v2 = new Node(t2, i);
				System.out.println("Arc made during back tracking: " + v1 + " -- " + v2);
				cellGraph.addArcFromToAddable(v1, v2);
				comp2.setComponentHasParent(i);
				comp1.incComponentChildCount(closestIndex);
			} else {
				// closest component for current component in comp2 was not found
			}
		}
	}

	/*
	 * components list should be filled tracking algorithm: first, find nearest
	 * neighbours going from 0 to T time slice then, back tracking: find nearest
	 * component of i in i-1 that wasn't tracked
	 */
	public void trackComponents(double radius, double radiusBackTracking, int n_lookThroughSlices) {
		// 0 -> T tracking

		for (int j = 0; j < n_lookThroughSlices; j++) {
			for (int i = 1; i < componentsList.size(); ++i) {
				if (i + j < componentsList.size())
					findNearestComponents(componentsList.get(i - 1), i - 1, componentsList.get(i + j), i + j, radius);
			}
		}

		// back tracking T -> 0
		for (int j = 0; j < n_lookThroughSlices; j++) {
			for (int i = componentsList.size() - 1; i > 0; --i) {
				if (i - j > 0) {
					findNearestComponentsBackStep(componentsList.get(i), i, componentsList.get(i - 1 - j), i - 1 - j,
							radiusBackTracking);
				}
			}
		}
	}

	/*
	 * returns index of the component in comp, which mass center is the closest to p
	 */
	private int findClosestPointIndex(Point p, ImageComponentsAnalysis comp, double radius) {
		int min_i = -1;
		double min_dist = Double.MAX_VALUE;
		double dist;
		for (int i = 0; i < comp.getComponentsCount(); i++) {
			dist = Point.dist(p, comp.getComponentMassCenter(i));
			if (dist < radius && dist < min_dist) {
				min_dist = dist;
				min_i = i;
			}
		}
		return min_i;
	}

	/* draws cellGraph as tracks on ip */
	public void drawTracksIp(ImageProcessor ip) {
		ArrayList<Arc> arcs = cellGraph.getArcs();
		int i0, i1, t0, t1;
		Node n0, n1;
		Point p0, p1;
		int x0, x1, y0, y1;
		for (int k = 0; k < arcs.size(); k++) {
			n0 = arcs.get(k).getFromNode();
			n1 = arcs.get(k).getToNode();
			i0 = n0.get_i();
			i1 = n1.get_i();
			t0 = n0.get_t();
			t1 = n1.get_t();

			p0 = componentsList.get(t0).getComponentMassCenter(i0);
			p1 = componentsList.get(t1).getComponentMassCenter(i1);

			x0 = (int) p0.getX();
			y0 = (int) p0.getY();
			x1 = (int) p1.getX();
			y1 = (int) p1.getY();
			ImageFunctions.drawX(ip, x0, y0);
			ImageFunctions.drawX(ip, x1, y1);
			ImageFunctions.drawLine(ip, x0, y0, x1, y1);
		}
	}

	/* draws arcs on ip */
	public void drawTracksIp(ImageProcessor ip, ArrayList<Arc> arcs) {
		int i0, i1, t0, t1;
		Node n0, n1;
		Point p0, p1;
		int x0, x1, y0, y1;
		for (int k = 0; k < arcs.size(); k++) {
			n0 = arcs.get(k).getFromNode();
			n1 = arcs.get(k).getToNode();
			i0 = n0.get_i();
			i1 = n1.get_i();
			t0 = n0.get_t();
			t1 = n1.get_t();

			p0 = componentsList.get(t0).getComponentMassCenter(i0);
			p1 = componentsList.get(t1).getComponentMassCenter(i1);

			x0 = (int) p0.getX();
			y0 = (int) p0.getY();
			x1 = (int) p1.getX();
			y1 = (int) p1.getY();
			ImageFunctions.drawX(ip, x0, y0);
			ImageFunctions.drawX(ip, x1, y1);
			ImageFunctions.drawLine(ip, x0, y0, x1, y1);
		}
	}

	/* draws tracking result on each slice and return the result */
	ImagePlus drawTracksImagePlus(ImagePlus image) {
		ImagePlus result = image.createImagePlus();

		ImageStack stack = image.getStack();

		System.out.println(stack.getSize());
		System.out.println(image.getNSlices());
		stack.setProcessor(image.getStack().getProcessor(1), 1);

		ImageProcessor ip;
		for (int i = 2; i <= image.getNSlices(); i++) { // slices are from 1 to n_slices
			ip = image.getStack().getProcessor(i).duplicate();
			drawTracksIp(ip, cellGraph.getArcsBeforeTimeSlice(i - 1));
			stack.setProcessor(ip, i);
		}

		result.setStack(stack);
		return result;
	}
}
